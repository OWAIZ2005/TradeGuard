package com.tradeguard.domain;

/** Every validation failure a row can produce. Validation errors are data problems, never system errors. */
public enum ErrorCode {
    MISSING_TRADE_ID(ErrorCategory.MISSING_DATA),
    MISSING_INSTRUMENT(ErrorCategory.MISSING_DATA),
    MISSING_TIMESTAMP(ErrorCategory.MISSING_DATA),
    MISSING_SIDE(ErrorCategory.MISSING_DATA),
    MISSING_PRICE(ErrorCategory.MISSING_DATA),
    MISSING_QUANTITY(ErrorCategory.MISSING_DATA),
    MISSING_CURRENCY(ErrorCategory.MISSING_DATA),
    MISSING_TRADER_ID(ErrorCategory.MISSING_DATA),
    INVALID_TIMESTAMP(ErrorCategory.INVALID_FORMAT),
    INVALID_PRICE(ErrorCategory.INVALID_FORMAT),
    INVALID_QUANTITY(ErrorCategory.INVALID_FORMAT),
    FIELD_TOO_LONG(ErrorCategory.INVALID_FORMAT),
    UNSUPPORTED_SIDE(ErrorCategory.UNSUPPORTED_VALUE),
    UNSUPPORTED_CURRENCY(ErrorCategory.UNSUPPORTED_VALUE),
    UNSUPPORTED_INSTRUMENT(ErrorCategory.UNSUPPORTED_VALUE),
    NON_POSITIVE_PRICE(ErrorCategory.OUT_OF_RANGE),
    NON_POSITIVE_QUANTITY(ErrorCategory.OUT_OF_RANGE),
    QUANTITY_EXCEEDS_MAX(ErrorCategory.OUT_OF_RANGE),
    DUPLICATE_IN_BATCH(ErrorCategory.DUPLICATE),
    DUPLICATE_EXISTING(ErrorCategory.DUPLICATE),
    MALFORMED_ROW(ErrorCategory.MALFORMED_ROW);

    private final ErrorCategory category;

    ErrorCode(ErrorCategory category) {
        this.category = category;
    }

    public ErrorCategory category() {
        return category;
    }

    public boolean isDuplicate() {
        return category == ErrorCategory.DUPLICATE;
    }
}
