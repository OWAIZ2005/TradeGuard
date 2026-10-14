package com.tradeguard.service;

import com.tradeguard.domain.ErrorCode;
import com.tradeguard.domain.ValidationStatus;
import com.tradeguard.web.dto.AnalyticsDtos.BatchQuality;
import com.tradeguard.web.dto.AnalyticsDtos.CategoryCount;
import com.tradeguard.web.dto.AnalyticsDtos.CurrencyAmount;
import com.tradeguard.web.dto.AnalyticsDtos.DailyVolume;
import com.tradeguard.web.dto.AnalyticsDtos.InstrumentStat;
import com.tradeguard.web.dto.AnalyticsDtos.Overview;
import com.tradeguard.web.dto.AnalyticsDtos.SideStat;
import com.tradeguard.web.dto.AnalyticsDtos.ValidationStats;
import com.tradeguard.web.dto.ErrorBreakdownItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analytics computed with SQL aggregation (GROUP BY, FILTER, window functions, joins). Every metric except the
 * validation statistics is restricted to VALID trades, so rejected rows can never contaminate the numbers.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final ValidationStatus VALID = ValidationStatus.VALID;
    private static final String DUPLICATE_CODES = Arrays.stream(ErrorCode.values()).filter(ErrorCode::isDuplicate)
            .map(c -> "'" + c.name() + "'").collect(Collectors.joining(", "));

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Overview overview(TradeFilter filter) {
        Counts counts = rowCounts(filter);

        TradeFilter.Clause valid = filter.toClause(VALID);
        BigDecimal quantity = jdbc.queryForObject(
                "SELECT COALESCE(SUM(t.quantity), 0) FROM trades t WHERE " + valid.sql(), valid.params(),
                BigDecimal.class);
        return new Overview(counts.total, counts.valid, counts.invalid, counts.duplicates,
                percent(counts.invalid, counts.total), quantity, notionalByCurrency(valid),
                com.tradeguard.web.dto.AnalyticsDtos.CURRENCY_NOTE);
    }

    public List<DailyVolume> dailyVolume(TradeFilter filter) {
        TradeFilter.Clause c = filter.toClause(VALID);
        return jdbc.query("""
                SELECT day, trades, qty, buys, sells, SUM(qty) OVER (ORDER BY day) AS cumulative_qty
                FROM (
                    SELECT CAST(t.trade_timestamp AT TIME ZONE 'UTC' AS date) AS day,
                           COUNT(*) AS trades,
                           SUM(t.quantity) AS qty,
                           COUNT(*) FILTER (WHERE t.side = 'BUY') AS buys,
                           COUNT(*) FILTER (WHERE t.side = 'SELL') AS sells
                    FROM trades t
                    WHERE (""" + c.sql() + """
                    )
                    GROUP BY CAST(t.trade_timestamp AT TIME ZONE 'UTC' AS date)
                ) d
                ORDER BY day""", c.params(), (rs, i) -> new DailyVolume(rs.getObject("day", java.time.LocalDate.class),
                rs.getLong("trades"), rs.getBigDecimal("qty"), rs.getLong("buys"), rs.getLong("sells"),
                rs.getBigDecimal("cumulative_qty")));
    }

    public List<InstrumentStat> instruments(TradeFilter filter) {
        TradeFilter.Clause c = filter.toClause(VALID);
        Map<String, List<CurrencyAmount>> notional = new LinkedHashMap<>();
        jdbc.query("""
                SELECT t.instrument, t.currency, SUM(t.notional) AS notional
                FROM trades t WHERE (""" + c.sql() + """
                )
                GROUP BY t.instrument, t.currency ORDER BY t.instrument, t.currency""", c.params(), rs -> {
            notional.computeIfAbsent(rs.getString("instrument"), k -> new ArrayList<>())
                    .add(new CurrencyAmount(rs.getString("currency"), rs.getBigDecimal("notional")));
        });
        return jdbc.query("""
                SELECT t.instrument, COUNT(*) AS trades, SUM(t.quantity) AS qty,
                       COUNT(*) FILTER (WHERE t.side = 'BUY') AS buys,
                       COUNT(*) FILTER (WHERE t.side = 'SELL') AS sells
                FROM trades t WHERE (""" + c.sql() + """
                )
                GROUP BY t.instrument ORDER BY COUNT(*) DESC, t.instrument""", c.params(),
                (rs, i) -> new InstrumentStat(rs.getString("instrument"), rs.getLong("trades"),
                        rs.getBigDecimal("qty"), rs.getLong("buys"), rs.getLong("sells"),
                        notional.getOrDefault(rs.getString("instrument"), List.of())));
    }

    public List<SideStat> sides(TradeFilter filter) {
        TradeFilter.Clause c = filter.toClause(VALID);
        return jdbc.query("""
                SELECT t.side, COUNT(*) AS trades, SUM(t.quantity) AS qty
                FROM trades t WHERE (""" + c.sql() + """
                )
                GROUP BY t.side ORDER BY t.side""", c.params(),
                (rs, i) -> new SideStat(rs.getString("side"), rs.getLong("trades"), rs.getBigDecimal("qty")));
    }

    public ValidationStats validation(TradeFilter filter) {
        Counts counts = rowCounts(filter);
        TradeFilter.Clause c = filter.toClause(null);

        List<ErrorBreakdownItem> byCode = jdbc.query("""
                SELECT e.error_code, COUNT(*) AS occurrences, COUNT(DISTINCT e.trade_pk) AS affected
                FROM trade_validation_errors e
                JOIN trades t ON t.id = e.trade_pk
                WHERE (""" + c.sql() + """
                )
                GROUP BY e.error_code ORDER BY COUNT(*) DESC, e.error_code""", c.params(), (rs, i) -> {
            ErrorCode code = ErrorCode.valueOf(rs.getString("error_code"));
            return new ErrorBreakdownItem(code.name(), code.category().name(), rs.getLong("occurrences"),
                    rs.getLong("affected"));
        });

        Map<String, Long> categories = new LinkedHashMap<>();
        byCode.forEach(item -> categories.merge(item.category(), item.occurrences(), Long::sum));
        List<CategoryCount> byCategory = categories.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new CategoryCount(e.getKey(), e.getValue())).toList();

        List<BatchQuality> quality = jdbc.query("""
                SELECT b.id, b.filename, b.uploaded_at,
                       COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE t.validation_status = 'VALID') AS valid,
                       COUNT(*) FILTER (WHERE t.validation_status = 'INVALID') AS invalid
                FROM trades t
                JOIN trade_import_batches b ON b.id = t.batch_id
                WHERE (""" + c.sql() + """
                )
                GROUP BY b.id, b.filename, b.uploaded_at
                ORDER BY b.id DESC
                LIMIT 20""", c.params(), (rs, i) -> {
            long total = rs.getLong("total");
            long invalid = rs.getLong("invalid");
            OffsetDateTime uploaded = rs.getObject("uploaded_at", OffsetDateTime.class);
            return new BatchQuality(rs.getLong("id"), rs.getString("filename"), uploaded.toInstant(), total,
                    rs.getLong("valid"), invalid, percent(invalid, total));
        });

        return new ValidationStats(counts.total, counts.valid, counts.invalid, counts.duplicates,
                percent(counts.invalid, counts.total), byCode, byCategory, quality);
    }

    private record Counts(long total, long valid, long invalid, long duplicates) {
    }

    private Counts rowCounts(TradeFilter filter) {
        TradeFilter.Clause c = filter.toClause(null);
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE t.validation_status = 'VALID') AS valid,
                       COUNT(*) FILTER (WHERE t.validation_status = 'INVALID') AS invalid,
                       COUNT(*) FILTER (WHERE EXISTS (
                           SELECT 1 FROM trade_validation_errors e
                           WHERE e.trade_pk = t.id AND e.error_code IN (""" + DUPLICATE_CODES + """
                       ))) AS duplicates
                FROM trades t WHERE (""" + c.sql() + ")", c.params(),
                (rs, i) -> new Counts(rs.getLong("total"), rs.getLong("valid"), rs.getLong("invalid"),
                        rs.getLong("duplicates")));
    }

    private List<CurrencyAmount> notionalByCurrency(TradeFilter.Clause c) {
        return jdbc.query("""
                SELECT t.currency, SUM(t.notional) AS notional
                FROM trades t WHERE (""" + c.sql() + """
                )
                GROUP BY t.currency ORDER BY t.currency""", c.params(),
                (rs, i) -> new CurrencyAmount(rs.getString("currency"), rs.getBigDecimal("notional")));
    }

    static BigDecimal percent(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }
}
