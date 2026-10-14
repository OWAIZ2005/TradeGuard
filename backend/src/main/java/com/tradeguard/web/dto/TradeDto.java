package com.tradeguard.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeDto(
        Long id,
        Long batchId,
        String batchFilename,
        int rowNumber,
        String tradeId,
        String instrument,
        Instant timestamp,
        String side,
        BigDecimal price,
        BigDecimal quantity,
        String currency,
        String traderId,
        BigDecimal notional,
        String status,
        int errorCount) {
}
