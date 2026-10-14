package com.tradeguard.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record RulesUpdateRequest(
        @NotNull(message = "maxUploadSizeMb is required")
        @Min(value = 1, message = "maxUploadSizeMb must be at least 1")
        @Max(value = 50, message = "maxUploadSizeMb cannot exceed the 50 MB server limit")
        Integer maxUploadSizeMb,

        @NotEmpty(message = "at least one currency is required")
        @Size(max = 50, message = "at most 50 currencies")
        List<@Pattern(regexp = "^[A-Za-z]{3}$", message = "currencies must be 3-letter ISO codes") String> allowedCurrencies,

        @NotEmpty(message = "at least one instrument is required")
        @Size(max = 200, message = "at most 200 instruments")
        List<@Pattern(regexp = "^[A-Za-z0-9._-]{1,12}$",
                message = "instruments must be 1-12 characters: letters, digits, '.', '_' or '-'") String> allowedInstruments,

        @NotNull(message = "maxTradeQuantity is required")
        @DecimalMin(value = "0", inclusive = false, message = "maxTradeQuantity must be greater than zero")
        @DecimalMax(value = "1000000000", message = "maxTradeQuantity cannot exceed 1,000,000,000")
        @Digits(integer = 13, fraction = 6, message = "maxTradeQuantity supports at most 6 decimal places")
        BigDecimal maxTradeQuantity) {
}
