package com.tradeguard.service;

import com.tradeguard.domain.ErrorCode;
import com.tradeguard.web.dto.ImportBatchDto;
import com.tradeguard.web.dto.SummaryDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic plain-English summary generated from fixed templates over a batch's error statistics.
 * It only reads results: it never changes trades or bypasses validation, and needs no API key.
 */
@Component
public class ValidationSummarizer {

    public static final String GENERATOR = "rule-based";

    /** @param affectedRowsByCode distinct rows affected per error code, iteration order = importance (largest first). */
    public SummaryDto summarize(ImportBatchDto batch, Map<ErrorCode, Long> affectedRowsByCode) {
        if (batch.totalRows() == 0) {
            return new SummaryDto(GENERATOR, "No rows were processed in this batch.", List.of());
        }
        if (batch.invalidRows() == 0) {
            return new SummaryDto(GENERATOR, "All " + batch.totalRows() + " rows passed validation.",
                    List.of("No action needed. The trades are included in analytics."));
        }
        String headline = batch.invalidRows() + " of " + batch.totalRows() + " rows ("
                + batch.errorRatePercent().stripTrailingZeros().toPlainString() + "%) were rejected and are excluded "
                + "from analytics; " + batch.validRows() + " rows were accepted.";
        List<String> points = new ArrayList<>();
        affectedRowsByCode.entrySet().stream().limit(4)
                .forEach(e -> points.add(describe(e.getKey(), e.getValue())));
        return new SummaryDto(GENERATOR, headline, points);
    }

    static String describe(ErrorCode code, long rows) {
        String n = rows + (rows == 1 ? " row: " : " rows: ");
        return n + switch (code) {
            case DUPLICATE_IN_BATCH -> "trade ID repeats an earlier row of the same file. "
                    + "Make trade IDs unique within the file.";
            case DUPLICATE_EXISTING -> "trade ID was already imported. Existing trades are never "
                    + "overwritten; remove already-loaded rows before re-importing.";
            case NON_POSITIVE_PRICE -> "price is zero or below. Check the price column.";
            case NON_POSITIVE_QUANTITY -> "quantity is zero or below. Check the quantity column.";
            case QUANTITY_EXCEEDS_MAX -> "quantity exceeds the maximum allowed. Review the quantity or adjust "
                    + "the rule if the limit is outdated.";
            case INVALID_TIMESTAMP -> "timestamp is unreadable. Use ISO-8601 with an offset, "
                    + "e.g. 2025-03-14T14:30:00Z.";
            case INVALID_PRICE -> "price is non-numeric or too precise. Use plain decimals.";
            case INVALID_QUANTITY -> "quantity is non-numeric or too precise. Use plain decimals.";
            case UNSUPPORTED_SIDE -> "side is not BUY or SELL.";
            case UNSUPPORTED_CURRENCY -> "currency is not in the allowed list. Check the Validation Rules page.";
            case UNSUPPORTED_INSTRUMENT -> "instrument is not in the allowed list. Check the Validation Rules page.";
            case MALFORMED_ROW -> "wrong number of columns. Check for stray commas or unquoted text.";
            case FIELD_TOO_LONG -> "a value is too long for its column.";
            default -> "a required value is missing ("
                    + code.name().replace("MISSING_", "").toLowerCase().replace('_', ' ') + ").";
        };
    }
}
