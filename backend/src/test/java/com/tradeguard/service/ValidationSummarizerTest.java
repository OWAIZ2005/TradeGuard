package com.tradeguard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradeguard.domain.BatchStatus;
import com.tradeguard.domain.ErrorCode;
import com.tradeguard.web.dto.ImportBatchDto;
import com.tradeguard.web.dto.SummaryDto;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationSummarizerTest {

    private final ValidationSummarizer summarizer = new ValidationSummarizer();

    private ImportBatchDto batch(int total, int valid, int invalid, String rate) {
        return new ImportBatchDto(1L, "f.csv", 10, BatchStatus.COMPLETED, total, valid, invalid, 0, invalid,
                new BigDecimal(rate), null, null, null);
    }

    @Test
    void cleanBatchSaysNoActionNeeded() {
        SummaryDto s = summarizer.summarize(batch(10, 10, 0, "0.00"), Map.of());
        assertThat(s.headline()).isEqualTo("All 10 rows passed validation.");
        assertThat(s.generator()).isEqualTo("rule-based");
    }

    @Test
    void namesTheMostCommonProblemsFirst() {
        Map<ErrorCode, Long> affected = new LinkedHashMap<>();
        affected.put(ErrorCode.DUPLICATE_IN_BATCH, 5L);
        affected.put(ErrorCode.NON_POSITIVE_PRICE, 1L);
        SummaryDto s = summarizer.summarize(batch(20, 14, 6, "30.00"), affected);

        assertThat(s.headline()).contains("6 of 20 rows (30%)").contains("14 rows were accepted");
        assertThat(s.points()).hasSize(2);
        assertThat(s.points().get(0)).startsWith("5 rows: trade ID repeats an earlier row");
        assertThat(s.points().get(1)).startsWith("1 row: price is zero or below");
    }

    @Test
    void emptyBatchIsHandled() {
        assertThat(summarizer.summarize(batch(0, 0, 0, "0"), Map.of()).headline()).contains("No rows");
    }
}
