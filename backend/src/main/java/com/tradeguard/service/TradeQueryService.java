package com.tradeguard.service;

import com.tradeguard.domain.ErrorCode;
import com.tradeguard.web.ApiException;
import com.tradeguard.web.Paging;
import com.tradeguard.web.dto.ErrorDto;
import com.tradeguard.web.dto.PageResponse;
import com.tradeguard.web.dto.TradeDetailDto;
import com.tradeguard.web.dto.TradeDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-side filtered, sorted and paginated trade explorer queries (parameterised SQL). */
@Service
@Transactional(readOnly = true)
public class TradeQueryService {

    /** Whitelist: client sort keys map to SQL expressions, so user input never reaches ORDER BY. */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "timestamp", "t.effective_at",
            "tradeId", "t.trade_id",
            "instrument", "t.instrument",
            "price", "t.price",
            "quantity", "t.quantity",
            "notional", "t.notional",
            "batchId", "t.batch_id",
            "rowNumber", "t.row_number");

    private static final String SELECT = """
            SELECT t.id, t.batch_id, b.filename, t.row_number, t.trade_id, t.instrument, t.trade_timestamp, t.side,
                   t.price, t.quantity, t.currency, t.trader_id, t.notional, t.validation_status,
                   (SELECT count(*) FROM trade_validation_errors e WHERE e.trade_pk = t.id) AS error_count
            FROM trades t
            JOIN trade_import_batches b ON b.id = t.batch_id
            """;

    private static final RowMapper<TradeDto> TRADE_MAPPER = TradeQueryService::mapTrade;

    private final NamedParameterJdbcTemplate jdbc;

    public TradeQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<TradeDto> search(TradeFilter filter, String sort, String direction, Paging paging) {
        String column = SORT_COLUMNS.get(sort == null || sort.isBlank() ? "timestamp" : sort);
        if (column == null) {
            throw ApiException.badRequest("BAD_REQUEST", "Unsupported sort key '" + sort + "'.");
        }
        String dir = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        TradeFilter.Clause clause = filter.toClause(null);
        MapSqlParameterSource params = clause.params();

        Long total = jdbc.queryForObject("SELECT count(*) FROM trades t WHERE " + clause.sql(), params, Long.class);
        params.addValue("limit", paging.size()).addValue("offset", paging.offset());
        List<TradeDto> rows = jdbc.query(SELECT + "WHERE " + clause.sql() + " ORDER BY " + column + " " + dir
                + " NULLS LAST, t.id " + dir + " LIMIT :limit OFFSET :offset", params, TRADE_MAPPER);
        return PageResponse.of(rows, paging.page(), paging.size(), total == null ? 0 : total);
    }

    public TradeDetailDto get(long id) {
        List<TradeDto> found = jdbc.query(SELECT + "WHERE t.id = :id", Map.of("id", id), TRADE_MAPPER);
        if (found.isEmpty()) {
            throw ApiException.notFound("Trade", id);
        }
        String raw = jdbc.queryForObject("SELECT raw_record FROM trades WHERE id = :id", Map.of("id", id),
                String.class);
        List<ErrorDto> errors = jdbc.query("""
                SELECT e.trade_pk, e.batch_id, e.row_number, t.trade_id, e.field_name, e.error_code, e.message,
                       e.rejected_value
                FROM trade_validation_errors e JOIN trades t ON t.id = e.trade_pk
                WHERE e.trade_pk = :id ORDER BY e.id""", Map.of("id", id), (rs, i) -> {
            ErrorCode code = ErrorCode.valueOf(rs.getString("error_code"));
            return new ErrorDto(rs.getLong("trade_pk"), rs.getLong("batch_id"), rs.getInt("row_number"),
                    rs.getString("trade_id"), rs.getString("field_name"), code.name(), code.category().name(),
                    rs.getString("message"), rs.getString("rejected_value"));
        });
        return new TradeDetailDto(found.get(0), raw, errors);
    }

    /** Distinct instruments among stored trades, for the explorer's filter dropdown. */
    public List<String> instruments() {
        return jdbc.getJdbcTemplate().queryForList(
                "SELECT DISTINCT instrument FROM trades WHERE instrument IS NOT NULL ORDER BY instrument",
                String.class);
    }

    private static TradeDto mapTrade(ResultSet rs, int i) throws SQLException {
        OffsetDateTime ts = rs.getObject("trade_timestamp", OffsetDateTime.class);
        return new TradeDto(rs.getLong("id"), rs.getLong("batch_id"), rs.getString("filename"),
                rs.getInt("row_number"), rs.getString("trade_id"), rs.getString("instrument"),
                ts == null ? null : ts.toInstant(), rs.getString("side"), rs.getBigDecimal("price"),
                rs.getBigDecimal("quantity"), rs.getString("currency"), rs.getString("trader_id"),
                rs.getBigDecimal("notional"), rs.getString("validation_status"), rs.getInt("error_count"));
    }
}
