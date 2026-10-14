package com.tradeguard.validation;

import java.math.BigDecimal;
import java.util.Set;

/** Immutable snapshot of the configurable validation rules applied to one import. */
public record RuleSet(
        int maxUploadSizeMb,
        Set<String> allowedCurrencies,
        Set<String> allowedInstruments,
        BigDecimal maxTradeQuantity) {

    public static RuleSet defaults() {
        return new RuleSet(10,
                Set.of("USD", "EUR", "GBP", "JPY", "INR", "CHF"),
                Set.of("ES", "NQ", "YM", "CL", "NG", "GC", "SI", "ZN", "ZB", "6E"),
                new BigDecimal("10000"));
    }
}
