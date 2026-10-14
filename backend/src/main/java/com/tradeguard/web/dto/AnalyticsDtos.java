package com.tradeguard.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response shapes of the analytics endpoints. All monetary amounts are per currency; never summed across them. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public static final String CURRENCY_NOTE =
            "Notional values are shown per currency. They are not converted or added across currencies "
                    + "because no exchange-rate source is configured.";

    public record CurrencyAmount(String currency, BigDecimal amount) {
    }

    public record Overview(
            long totalRows,
            long validTrades,
            long invalidTrades,
            long duplicateTrades,
            BigDecimal validationErrorRatePercent,
            BigDecimal totalQuantity,
            List<CurrencyAmount> notionalByCurrency,
            String currencyNote) {
    }

    public record DailyVolume(LocalDate date, long tradeCount, BigDecimal quantity, long buyCount, long sellCount,
                              BigDecimal cumulativeQuantity) {
    }

    public record InstrumentStat(String instrument, long tradeCount, BigDecimal quantity, long buyCount,
                                 long sellCount, List<CurrencyAmount> notionalByCurrency) {
    }

    public record SideStat(String side, long tradeCount, BigDecimal quantity) {
    }

    public record CategoryCount(String category, long occurrences) {
    }

    public record BatchQuality(Long batchId, String filename, Instant uploadedAt, long totalRows, long validRows,
                               long invalidRows, BigDecimal errorRatePercent) {
    }

    public record ValidationStats(
            long totalRows,
            long validRows,
            long invalidRows,
            long duplicateRows,
            BigDecimal errorRatePercent,
            List<ErrorBreakdownItem> errorsByCode,
            List<CategoryCount> errorsByCategory,
            List<BatchQuality> batchQuality) {
    }
}
