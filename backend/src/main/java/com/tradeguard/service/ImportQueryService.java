package com.tradeguard.service;

import com.tradeguard.domain.BatchStatus;
import com.tradeguard.domain.ErrorCode;
import com.tradeguard.domain.ImportBatch;
import com.tradeguard.repository.ErrorCodeCount;
import com.tradeguard.repository.ImportBatchRepository;
import com.tradeguard.repository.TradeValidationErrorRepository;
import com.tradeguard.web.ApiException;
import com.tradeguard.web.Paging;
import com.tradeguard.web.dto.BatchDetailDto;
import com.tradeguard.web.dto.ErrorBreakdownItem;
import com.tradeguard.web.dto.ErrorDto;
import com.tradeguard.web.dto.ImportBatchDto;
import com.tradeguard.web.dto.PageResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ImportQueryService {

    private final ImportBatchRepository batches;
    private final TradeValidationErrorRepository errorRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ValidationSummarizer summarizer;

    public ImportQueryService(ImportBatchRepository batches, TradeValidationErrorRepository errorRepository,
                              NamedParameterJdbcTemplate jdbc, ValidationSummarizer summarizer) {
        this.batches = batches;
        this.errorRepository = errorRepository;
        this.jdbc = jdbc;
        this.summarizer = summarizer;
    }

    public PageResponse<ImportBatchDto> list(Paging paging, String status) {
        PageRequest request = PageRequest.of(paging.page(), paging.size(),
                Sort.by(Sort.Direction.DESC, "uploadedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        if (status == null || status.isBlank()) {
            return PageResponse.of(batches.findAll(request).map(ImportBatchDto::from));
        }
        BatchStatus parsed;
        try {
            parsed = BatchStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_REQUEST", "Unknown status '" + status + "'.");
        }
        return PageResponse.of(batches.findByStatus(parsed, request).map(ImportBatchDto::from));
    }

    public ImportBatch requireBatch(long id) {
        return batches.findById(id).orElseThrow(() -> ApiException.notFound("Import batch", id));
    }

    public BatchDetailDto detail(long id) {
        ImportBatchDto batch = ImportBatchDto.from(requireBatch(id));
        List<ErrorCodeCount> counts = errorRepository.breakdownForBatch(id);
        List<ErrorBreakdownItem> breakdown = counts.stream()
                .map(c -> new ErrorBreakdownItem(c.getCode().name(), c.getCode().category().name(),
                        c.getOccurrences(), c.getAffectedRows()))
                .toList();
        Map<ErrorCode, Long> byAffectedRows = new LinkedHashMap<>();
        counts.stream().sorted(Comparator.comparingLong(ErrorCodeCount::getAffectedRows).reversed())
                .forEach(c -> byAffectedRows.put(c.getCode(), c.getAffectedRows()));
        return new BatchDetailDto(batch, breakdown, summarizer.summarize(batch, byAffectedRows));
    }

    public PageResponse<ErrorDto> errors(long batchId, Paging paging, String code) {
        requireBatch(batchId);
        MapSqlParameterSource params = new MapSqlParameterSource("batchId", batchId);
        String codeClause = "";
        if (code != null && !code.isBlank()) {
            try {
                params.addValue("code", ErrorCode.valueOf(code.trim().toUpperCase()).name());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("BAD_REQUEST", "Unknown error code '" + code + "'.");
            }
            codeClause = " AND e.error_code = :code";
        }
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM trade_validation_errors e WHERE e.batch_id = :batchId" + codeClause,
                params, Long.class);
        params.addValue("limit", paging.size()).addValue("offset", paging.offset());
        List<ErrorDto> rows = jdbc.query("""
                SELECT e.trade_pk, e.batch_id, e.row_number, t.trade_id, e.field_name, e.error_code, e.message,
                       e.rejected_value
                FROM trade_validation_errors e
                JOIN trades t ON t.id = e.trade_pk
                WHERE e.batch_id = :batchId""" + codeClause + """

                ORDER BY e.row_number, e.id
                LIMIT :limit OFFSET :offset""", params, (rs, i) -> {
            ErrorCode ec = ErrorCode.valueOf(rs.getString("error_code"));
            return new ErrorDto(rs.getLong("trade_pk"), rs.getLong("batch_id"), rs.getInt("row_number"),
                    rs.getString("trade_id"), rs.getString("field_name"), ec.name(), ec.category().name(),
                    rs.getString("message"), rs.getString("rejected_value"));
        });
        return PageResponse.of(rows, paging.page(), paging.size(), total == null ? 0 : total);
    }
}
