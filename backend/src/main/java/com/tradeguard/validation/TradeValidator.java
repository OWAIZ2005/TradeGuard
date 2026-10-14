package com.tradeguard.validation;

import com.tradeguard.domain.ErrorCode;
import com.tradeguard.domain.Side;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates the fields of a single record. Stateless and free of database access so it is easy to unit test.
 * Duplicate trade-id detection needs batch/database context and lives in the import processor.
 */
@Component
public class TradeValidator {

    static final int MAX_DECIMAL_SCALE = 6;
    static final int MAX_INTEGER_DIGITS = 13;
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)$");

    public ValidationOutcome validate(RawTradeRecord raw, RuleSet rules) {
        List<FieldError> errors = new ArrayList<>();
        if (raw.malformed()) {
            errors.add(new FieldError("row", ErrorCode.MALFORMED_ROW, raw.malformedMessage(), null));
            return new ValidationOutcome(ParsedTrade.empty(), errors);
        }

        String tradeId = requiredText("trade_id", raw.tradeId(), 64, ErrorCode.MISSING_TRADE_ID, "Trade ID", errors);

        String instrument = requiredText("instrument", raw.instrument(), 32, ErrorCode.MISSING_INSTRUMENT,
                "Instrument", errors);
        if (instrument != null) {
            instrument = instrument.toUpperCase(Locale.ROOT);
            if (!rules.allowedInstruments().contains(instrument)) {
                errors.add(new FieldError("instrument", ErrorCode.UNSUPPORTED_INSTRUMENT,
                        "Instrument '" + instrument + "' is not in the allowed instruments list.", raw.instrument()));
            }
        }

        Instant timestamp = parseTimestamp(raw.timestamp(), errors);
        String side = parseSide(raw.side(), errors);

        BigDecimal price = parseDecimal("price", raw.price(), ErrorCode.MISSING_PRICE, ErrorCode.INVALID_PRICE,
                "Price", errors);
        if (price != null && price.signum() <= 0) {
            errors.add(new FieldError("price", ErrorCode.NON_POSITIVE_PRICE,
                    "Price must be greater than zero.", raw.price()));
        }

        BigDecimal quantity = parseDecimal("quantity", raw.quantity(), ErrorCode.MISSING_QUANTITY,
                ErrorCode.INVALID_QUANTITY, "Quantity", errors);
        if (quantity != null) {
            if (quantity.signum() <= 0) {
                errors.add(new FieldError("quantity", ErrorCode.NON_POSITIVE_QUANTITY,
                        "Quantity must be greater than zero.", raw.quantity()));
            } else if (quantity.compareTo(rules.maxTradeQuantity()) > 0) {
                errors.add(new FieldError("quantity", ErrorCode.QUANTITY_EXCEEDS_MAX,
                        "Quantity exceeds the configured maximum of " + rules.maxTradeQuantity().toPlainString() + ".",
                        raw.quantity()));
            }
        }

        String currency = requiredText("currency", raw.currency(), 3, ErrorCode.MISSING_CURRENCY, "Currency", errors);
        if (currency != null) {
            currency = currency.toUpperCase(Locale.ROOT);
            if (!rules.allowedCurrencies().contains(currency)) {
                errors.add(new FieldError("currency", ErrorCode.UNSUPPORTED_CURRENCY,
                        "Currency '" + currency + "' is not in the allowed currencies list.", raw.currency()));
            }
        }

        String traderId = requiredText("trader_id", raw.traderId(), 64, ErrorCode.MISSING_TRADER_ID, "Trader ID",
                errors);

        return new ValidationOutcome(
                new ParsedTrade(tradeId, instrument, timestamp, side, price, quantity, currency, traderId), errors);
    }

    private String requiredText(String field, String value, int maxLength, ErrorCode missing, String label,
                                List<FieldError> errors) {
        if (isBlank(value)) {
            errors.add(new FieldError(field, missing, label + " is required.", value));
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            errors.add(new FieldError(field, ErrorCode.FIELD_TOO_LONG,
                    label + " must be at most " + maxLength + " characters.", value));
            return null;
        }
        return trimmed;
    }

    private Instant parseTimestamp(String value, List<FieldError> errors) {
        if (isBlank(value)) {
            errors.add(new FieldError("trade_timestamp", ErrorCode.MISSING_TIMESTAMP,
                    "Trade timestamp is required.", value));
            return null;
        }
        try {
            Instant parsed = OffsetDateTime.parse(value.trim()).toInstant();
            int year = parsed.atZone(java.time.ZoneOffset.UTC).getYear();
            if (year < 1970 || year > 2100) {
                errors.add(new FieldError("trade_timestamp", ErrorCode.INVALID_TIMESTAMP,
                        "Timestamp year must be between 1970 and 2100.", value));
                return null;
            }
            return parsed;
        } catch (DateTimeParseException e) {
            errors.add(new FieldError("trade_timestamp", ErrorCode.INVALID_TIMESTAMP,
                    "Timestamp must be ISO-8601 with a UTC offset, e.g. 2025-03-14T14:30:00Z.", value));
            return null;
        }
    }

    private String parseSide(String value, List<FieldError> errors) {
        if (isBlank(value)) {
            errors.add(new FieldError("side", ErrorCode.MISSING_SIDE, "Side is required (BUY or SELL).", value));
            return null;
        }
        return Side.parse(value).map(Enum::name).orElseGet(() -> {
            errors.add(new FieldError("side", ErrorCode.UNSUPPORTED_SIDE,
                    "Side must be BUY or SELL.", value));
            return null;
        });
    }

    private BigDecimal parseDecimal(String field, String value, ErrorCode missing, ErrorCode invalid, String label,
                                    List<FieldError> errors) {
        if (isBlank(value)) {
            errors.add(new FieldError(field, missing, label + " is required.", value));
            return null;
        }
        String trimmed = value.trim();
        if (!PLAIN_DECIMAL.matcher(trimmed).matches()) {
            errors.add(new FieldError(field, invalid,
                    label + " must be a plain decimal number (no thousands separators or exponent).", value));
            return null;
        }
        BigDecimal parsed = new BigDecimal(trimmed);
        BigDecimal normalised = parsed.stripTrailingZeros();
        int scale = Math.max(normalised.scale(), 0);
        int integerDigits = normalised.precision() - normalised.scale();
        if (scale > MAX_DECIMAL_SCALE || integerDigits > MAX_INTEGER_DIGITS) {
            errors.add(new FieldError(field, invalid,
                    label + " supports at most " + MAX_INTEGER_DIGITS + " integer digits and "
                            + MAX_DECIMAL_SCALE + " decimal places.", value));
            return null;
        }
        return parsed;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
