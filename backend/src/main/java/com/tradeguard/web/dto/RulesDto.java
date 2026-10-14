package com.tradeguard.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RulesDto(
        int maxUploadSizeMb,
        int hardUploadLimitMb,
        List<String> allowedCurrencies,
        List<String> allowedInstruments,
        BigDecimal maxTradeQuantity,
        Instant updatedAt) {
}
