package com.tradeguard.service;

import com.tradeguard.domain.Side;
import com.tradeguard.domain.ValidationStatus;
import com.tradeguard.web.ApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * One filter definition shared by the trade explorer, every analytics query and every CSV report, so that the same
 * parameters always select the same rows. Dates are UTC calendar days, inclusive, applied to
 * {@code trades.effective_at} (the trade timestamp; for rejected rows with no usable timestamp, the upload time).
 */
public record TradeFilter(LocalDate from, LocalDate to, String instrument, Side side, ValidationStatus status,
                          Long batchId, String q) {

    public static TradeFilter parse(LocalDate from, LocalDate to, String instrument, String side, String status,
                                    Long batchId, String q) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("BAD_REQUEST", "'from' must not be after 'to'.");
        }
        Side parsedSide = null;
        if (side != null && !side.isBlank()) {
            parsedSide = Side.parse(side)
                    .orElseThrow(() -> ApiException.badRequest("BAD_REQUEST", "side must be BUY or SELL."));
        }
        ValidationStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                parsedStatus = ValidationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("BAD_REQUEST", "status must be VALID or INVALID.");
            }
        }
        String inst = instrument == null || instrument.isBlank() ? null : instrument.trim().toUpperCase(Locale.ROOT);
        String query = q == null || q.isBlank() ? null : q.trim();
        if (query != null && query.length() > 64) {
            throw ApiException.badRequest("BAD_REQUEST", "Search text must be at most 64 characters.");
        }
        return new TradeFilter(from, to, inst, parsedSide, parsedStatus, batchId, query);
    }

    /** WHERE-clause body (no leading WHERE) over table alias {@code t}, with its bind parameters. */
    public record Clause(String sql, MapSqlParameterSource params) {
    }

    /** @param forcedStatus when non-null it overrides this filter's status (analytics force VALID). */
    public Clause toClause(ValidationStatus forcedStatus) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("1 = 1");
        if (from != null) {
            sql.append(" AND t.effective_at >= :from");
            p.addValue("from", from.atStartOfDay().atOffset(ZoneOffset.UTC));
        }
        if (to != null) {
            sql.append(" AND t.effective_at < :toExclusive");
            OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            p.addValue("toExclusive", end);
        }
        if (instrument != null) {
            sql.append(" AND t.instrument = :instrument");
            p.addValue("instrument", instrument);
        }
        if (side != null) {
            sql.append(" AND t.side = :side");
            p.addValue("side", side.name());
        }
        ValidationStatus effective = forcedStatus != null ? forcedStatus : status;
        if (effective != null) {
            sql.append(" AND t.validation_status = :status");
            p.addValue("status", effective.name());
        }
        if (batchId != null) {
            sql.append(" AND t.batch_id = :batchId");
            p.addValue("batchId", batchId);
        }
        if (q != null) {
            sql.append(" AND (lower(t.trade_id) LIKE :qLike ESCAPE '\\' OR lower(t.trader_id) LIKE :qLike ESCAPE '\\')");
            String escaped = q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            p.addValue("qLike", escaped + "%");
        }
        return new Clause(sql.toString(), p);
    }
}
