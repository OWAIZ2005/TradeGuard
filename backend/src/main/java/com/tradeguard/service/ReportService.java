package com.tradeguard.service;

import com.tradeguard.csv.CsvRowWriter;
import com.tradeguard.domain.ErrorCode;
import com.tradeguard.domain.ImportBatch;
import com.tradeguard.domain.ValidationStatus;
import com.tradeguard.repository.ErrorCodeCount;
import com.tradeguard.repository.TradeValidationErrorRepository;
import com.tradeguard.web.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Streams CSV reports row by row (bounded memory). Uses the same {@link TradeFilter} as the explorer and the
 * analytics, so an export always matches what the user filtered on screen.
 */
@Service
public class ReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate readOnlyTx;
    private final ImportQueryService imports;
    private final TradeValidationErrorRepository errorRepository;

    public ReportService(DataSource dataSource, PlatformTransactionManager txManager, ImportQueryService imports,
                         TradeValidationErrorRepository errorRepository) {
        JdbcTemplate streaming = new JdbcTemplate(dataSource);
        streaming.setFetchSize(1000);
        this.jdbc = new NamedParameterJdbcTemplate(streaming);
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
        this.imports = imports;
        this.errorRepository = errorRepository;
    }

    public void writeValidTrades(TradeFilter filter, Writer out) throws IOException {
        TradeFilter.Clause c = filter.toClause(ValidationStatus.VALID);
        CsvRowWriter csv = new CsvRowWriter(out);
        csv.writeRow("trade_id", "instrument", "trade_timestamp", "side", "price", "quantity", "currency",
                "notional", "trader_id", "batch_id", "source_row");
        stream("""
                SELECT t.trade_id, t.instrument, t.trade_timestamp, t.side, t.price, t.quantity, t.currency,
                       t.notional, t.trader_id, t.batch_id, t.row_number
                FROM trades t WHERE (""" + c.sql() + """
                )
                ORDER BY t.effective_at, t.id""", c.params(), rs -> csv.writeRow(
                rs.getString("trade_id"), rs.getString("instrument"),
                rs.getObject("trade_timestamp", OffsetDateTime.class).toInstant(), rs.getString("side"),
                rs.getBigDecimal("price"), rs.getBigDecimal("quantity"), rs.getString("currency"),
                rs.getBigDecimal("notional"), rs.getString("trader_id"), rs.getLong("batch_id"),
                rs.getInt("row_number")));
        csv.flush();
    }

    public void writeValidationErrors(TradeFilter filter, String errorCode, Writer out) throws IOException {
        TradeFilter.Clause c = filter.toClause(null);
        MapSqlParameterSource params = c.params();
        String codeClause = "";
        if (errorCode != null && !errorCode.isBlank()) {
            try {
                params.addValue("code", ErrorCode.valueOf(errorCode.trim().toUpperCase()).name());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("BAD_REQUEST", "Unknown error code '" + errorCode + "'.");
            }
            codeClause = " AND e.error_code = :code";
        }
        CsvRowWriter csv = new CsvRowWriter(out);
        csv.writeRow("batch_id", "filename", "row_number", "trade_id", "field", "error_code", "error_category",
                "message", "rejected_value", "original_row");
        stream("""
                SELECT e.batch_id, b.filename, e.row_number, t.trade_id, e.field_name, e.error_code, e.message,
                       e.rejected_value, t.raw_record
                FROM trade_validation_errors e
                JOIN trades t ON t.id = e.trade_pk
                JOIN trade_import_batches b ON b.id = e.batch_id
                WHERE (""" + c.sql() + ")" + codeClause + """

                ORDER BY e.batch_id, e.row_number, e.id""", params, rs -> {
            ErrorCode code = ErrorCode.valueOf(rs.getString("error_code"));
            csv.writeRow(rs.getLong("batch_id"), rs.getString("filename"), rs.getInt("row_number"),
                    rs.getString("trade_id"), rs.getString("field_name"), code.name(), code.category().name(),
                    rs.getString("message"), rs.getString("rejected_value"), rs.getString("raw_record"));
        });
        csv.flush();
    }

    /** Batch quality report: key metrics, rules snapshot, error distribution, and notional per currency. */
    public void writeBatchSummary(long batchId, Writer out) throws IOException {
        ImportBatch b = imports.requireBatch(batchId);
        CsvRowWriter csv = new CsvRowWriter(out);
        csv.writeRow("section", "metric", "value");
        csv.writeRow("batch", "batch_id", b.getId());
        csv.writeRow("batch", "filename", b.getFilename());
        csv.writeRow("batch", "status", b.getStatus().name());
        csv.writeRow("batch", "uploaded_at", b.getUploadedAt());
        csv.writeRow("batch", "file_sha256", b.getFileSha256());
        csv.writeRow("quality", "total_rows", b.getTotalRows());
        csv.writeRow("quality", "valid_rows", b.getValidRows());
        csv.writeRow("quality", "invalid_rows", b.getInvalidRows());
        csv.writeRow("quality", "duplicate_rows", b.getDuplicateRows());
        csv.writeRow("quality", "error_count", b.getErrorCount());
        csv.writeRow("quality", "error_rate_percent", AnalyticsService.percent(b.getInvalidRows(), b.getTotalRows()));
        csv.writeRow("rules_applied", "snapshot", b.getRulesSnapshot());

        List<ErrorCodeCount> breakdown = errorRepository.breakdownForBatch(batchId);
        for (ErrorCodeCount c : breakdown) {
            csv.writeRow("errors_by_code", c.getCode().name() + " (rows affected)", c.getAffectedRows());
        }
        jdbc.query("""
                SELECT currency, SUM(notional) AS notional, COUNT(*) AS trades
                FROM trades WHERE batch_id = :id AND validation_status = 'VALID'
                GROUP BY currency ORDER BY currency""", new MapSqlParameterSource("id", batchId), rs -> {
            try {
                csv.writeRow("valid_notional_" + rs.getString("currency"), "trades", rs.getLong("trades"));
                BigDecimal n = rs.getBigDecimal("notional");
                csv.writeRow("valid_notional_" + rs.getString("currency"), "notional", n);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        csv.writeRow("note", "currency", "Notional is per currency and is not converted or added across currencies.");
        csv.flush();
    }

    @FunctionalInterface
    private interface RowWriter {
        void write(ResultSet rs) throws Exception;
    }

    private void stream(String sql, MapSqlParameterSource params, RowWriter writer) {
        // A read-only transaction keeps one connection open so PostgreSQL streams rows via a cursor.
        readOnlyTx.executeWithoutResult(status -> jdbc.query(sql, params, rs -> {
            try {
                writer.write(rs);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }));
    }
}
