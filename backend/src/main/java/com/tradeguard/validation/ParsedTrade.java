package com.tradeguard.validation;

import java.math.BigDecimal;
import java.time.Instant;

/** Normalised values of a record. A field is null when it was missing or could not be parsed. */
public record ParsedTrade(
        String tradeId,
        String instrument,
        Instant timestamp,
        String side,
        BigDecimal price,
        BigDecimal quantity,
        String currency,
        String traderId) {

    public static ParsedTrade empty() {
        return new ParsedTrade(null, null, null, null, null, null, null, null);
    }
}
