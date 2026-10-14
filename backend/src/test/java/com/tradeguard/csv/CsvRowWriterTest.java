package com.tradeguard.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvRowWriterTest {

    private String write(Object... values) throws IOException {
        StringWriter out = new StringWriter();
        new CsvRowWriter(out).writeRow(values);
        return out.toString();
    }

    @Test
    void escapesCommasQuotesAndNewlines() throws IOException {
        assertThat(write("plain", "a,b", "say \"hi\"", "line1\nline2"))
                .isEqualTo("plain,\"a,b\",\"say \"\"hi\"\"\",\"line1\nline2\"\r\n");
    }

    @Test
    void writesNullsAsEmptyAndNumbersVerbatim() throws IOException {
        assertThat(write(null, 42L, new BigDecimal("1E+3"), new BigDecimal("-5.50"), Instant.parse("2025-03-14T14:30:00Z")))
                .isEqualTo(",42,1000.00,-5.50,2025-03-14T14:30:00Z\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=SUM(1+1)", "+1+1", "-2+3", "@cmd", "\tTAB", "=HYPERLINK(\"http://x\")"})
    void neutralisesSpreadsheetFormulaInjection(String malicious) throws IOException {
        // The cell may additionally be wrapped in quotes; once unquoted it must start with the neutralising apostrophe.
        String cell = write(malicious).replaceFirst("^\"", "");
        assertThat(cell).startsWith("'");
    }

    @Test
    void formulaNeutralisationAndQuotingCombine() throws IOException {
        assertThat(write("=A1,B1")).isEqualTo("\"'=A1,B1\"\r\n");
    }

    @Test
    void doesNotAlterOrdinaryText() throws IOException {
        assertThat(write("TRD-001", "a=b")).isEqualTo("TRD-001,a=b\r\n");
    }
}
