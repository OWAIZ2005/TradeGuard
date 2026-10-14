package com.tradeguard.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradeguard.domain.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TradeValidatorTest {

    private final TradeValidator validator = new TradeValidator();
    private final RuleSet rules = RuleSet.defaults();

    private RawTradeRecord row(String id, String instrument, String ts, String side, String price, String qty,
                               String currency, String trader) {
        return RawTradeRecord.of(2, id, instrument, ts, side, price, qty, currency, trader);
    }

    private RawTradeRecord good() {
        return row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", "5123.25", "10", "USD", "TRD-001");
    }

    private List<ErrorCode> codes(ValidationOutcome o) {
        return o.errors().stream().map(FieldError::code).toList();
    }

    @Test
    void acceptsValidTradeAndNormalisesValues() {
        ValidationOutcome o = validator.validate(
                row(" T-1 ", "es", "2025-03-14T16:30:00+02:00", "buy", "5123.25", "10", "usd", "TRD-001"), rules);

        assertThat(o.valid()).isTrue();
        ParsedTrade p = o.parsed();
        assertThat(p.tradeId()).isEqualTo("T-1");
        assertThat(p.instrument()).isEqualTo("ES");
        assertThat(p.side()).isEqualTo("BUY");
        assertThat(p.currency()).isEqualTo("USD");
        assertThat(p.timestamp()).isEqualTo(Instant.parse("2025-03-14T14:30:00Z"));
        assertThat(p.price()).isEqualByComparingTo("5123.25");
    }

    @Test
    void rejectsMissingTradeId() {
        ValidationOutcome o = validator.validate(row("  ", "ES", "2025-03-14T14:30:00Z", "BUY", "1", "1", "USD", "T"),
                rules);
        assertThat(codes(o)).containsExactly(ErrorCode.MISSING_TRADE_ID);
        assertThat(o.errors().get(0).field()).isEqualTo("trade_id");
    }

    @Test
    void rejectsMissingInstrumentAndTimestamp() {
        ValidationOutcome o = validator.validate(row("T-1", "", "", "BUY", "1", "1", "USD", "T"), rules);
        assertThat(codes(o)).containsExactlyInAnyOrder(ErrorCode.MISSING_INSTRUMENT, ErrorCode.MISSING_TIMESTAMP);
    }

    @Test
    void rejectsUnsupportedInstrument() {
        ValidationOutcome o = validator.validate(row("T-1", "ZZ", "2025-03-14T14:30:00Z", "BUY", "1", "1", "USD", "T"),
                rules);
        assertThat(codes(o)).containsExactly(ErrorCode.UNSUPPORTED_INSTRUMENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "-0.01", "0.000"})
    void rejectsNonPositivePrice(String price) {
        ValidationOutcome o = validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", price, "1", "USD", "T"), rules);
        assertThat(codes(o)).containsExactly(ErrorCode.NON_POSITIVE_PRICE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12,5", "1e3", "NaN", "1.2.3", "0.1234567", "12345678901234.5"})
    void rejectsInvalidNumericPrice(String price) {
        ValidationOutcome o = validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", price, "1", "USD", "T"), rules);
        assertThat(codes(o)).containsExactly(ErrorCode.INVALID_PRICE);
        assertThat(o.parsed().price()).isNull();
    }

    @Test
    void rejectsMissingPrice() {
        ValidationOutcome o = validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", "", "1", "USD", "T"), rules);
        assertThat(codes(o)).containsExactly(ErrorCode.MISSING_PRICE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-40"})
    void rejectsZeroOrNegativeQuantity(String qty) {
        ValidationOutcome o = validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", "1", qty, "USD", "T"), rules);
        assertThat(codes(o)).containsExactly(ErrorCode.NON_POSITIVE_QUANTITY);
    }

    @Test
    void rejectsNonNumericQuantityAndQuantityAboveConfiguredMaximum() {
        assertThat(codes(validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", "1", "ten", "USD", "T"), rules)))
                .containsExactly(ErrorCode.INVALID_QUANTITY);
        assertThat(codes(validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", "1", "10000.01", "USD", "T"), rules)))
                .containsExactly(ErrorCode.QUANTITY_EXCEEDS_MAX);
        assertThat(validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", "1", "10000", "USD", "T"), rules).valid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOLD", "B", "short", "0"})
    void rejectsUnsupportedSide(String side) {
        ValidationOutcome o = validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", side, "1", "1", "USD", "T"), rules);
        assertThat(codes(o)).containsExactly(ErrorCode.UNSUPPORTED_SIDE);
        assertThat(o.parsed().side()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"03/14/2025 14:30", "2025-03-14 14:30:00", "2025-03-14T14:30:00", "yesterday",
            "2025-13-40T00:00:00Z", "0001-01-01T00:00:00Z"})
    void rejectsMalformedTimestamps(String ts) {
        ValidationOutcome o = validator.validate(row("T-1", "ES", ts, "BUY", "1", "1", "USD", "T"), rules);
        assertThat(codes(o)).containsExactly(ErrorCode.INVALID_TIMESTAMP);
        assertThat(o.errors().get(0).rejectedValue()).isEqualTo(ts);
    }

    @Test
    void currencyIsCaseInsensitiveButMustBeAllowed() {
        assertThat(validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", "1", "1", "eur", "T"), rules).valid()).isTrue();
        assertThat(codes(validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", "1", "1", "XXX", "T"), rules)))
                .containsExactly(ErrorCode.UNSUPPORTED_CURRENCY);
        assertThat(codes(validator.validate(
                row("T-1", "ES", "2025-03-14T14:30:00Z", "BUY", "1", "1", "", "T"), rules)))
                .containsExactly(ErrorCode.MISSING_CURRENCY);
    }

    @Test
    void honoursConfiguredRules() {
        RuleSet custom = new RuleSet(5, Set.of("GBP"), Set.of("FTSE"), new BigDecimal("5"));
        ValidationOutcome o = validator.validate(good(), custom);
        assertThat(codes(o)).containsExactlyInAnyOrder(ErrorCode.UNSUPPORTED_INSTRUMENT,
                ErrorCode.UNSUPPORTED_CURRENCY, ErrorCode.QUANTITY_EXCEEDS_MAX);
    }

    @Test
    void reportsEveryProblemOfARowWithItsFieldAndRejectedValue() {
        ValidationOutcome o = validator.validate(row("", "ES", "bad", "HOLD", "-1", "0", "USD", ""), rules);
        assertThat(o.errors()).extracting(FieldError::field).containsExactlyInAnyOrder(
                "trade_id", "trade_timestamp", "side", "price", "quantity", "trader_id");
        assertThat(o.errors()).allSatisfy(e -> assertThat(e.message()).isNotBlank());
    }

    @Test
    void rejectsOverlongFieldsInsteadOfFailingInTheDatabase() {
        ValidationOutcome o = validator.validate(
                row("x".repeat(65), "ES", "2025-03-14T14:30:00Z", "BUY", "1", "1", "USD", "T"), rules);
        assertThat(codes(o)).containsExactly(ErrorCode.FIELD_TOO_LONG);
        assertThat(o.parsed().tradeId()).isNull();
    }

    @Test
    void malformedRowYieldsSingleMalformedRowError() {
        RawTradeRecord malformed = new RawTradeRecord(7, null, null, null, null, null, null, null, null, "a,b",
                "Expected 8 columns but found 2.");
        ValidationOutcome o = validator.validate(malformed, rules);
        assertThat(codes(o)).containsExactly(ErrorCode.MALFORMED_ROW);
    }
}
