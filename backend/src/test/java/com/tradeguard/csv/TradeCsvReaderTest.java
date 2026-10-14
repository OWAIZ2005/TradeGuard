package com.tradeguard.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tradeguard.validation.RawTradeRecord;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeCsvReaderTest {

    private static final String HEADER = "trade_id,instrument,trade_timestamp,side,price,quantity,currency,trader_id";

    private List<RawTradeRecord> readAll(String csv) throws IOException {
        return readAll(csv.getBytes(StandardCharsets.UTF_8));
    }

    private List<RawTradeRecord> readAll(byte[] bytes) throws IOException {
        List<RawTradeRecord> rows = new ArrayList<>();
        try (TradeCsvReader reader = TradeCsvReader.open(new ByteArrayInputStream(bytes))) {
            while (reader.hasNext()) {
                rows.add(reader.next());
            }
        }
        return rows;
    }

    @Test
    void parsesRowsAndKeepsSpreadsheetRowNumbers() throws IOException {
        List<RawTradeRecord> rows = readAll(HEADER + "\nT1,ES,2025-03-14T14:30:00Z,BUY,10.5,3,USD,A\n"
                + "T2,NQ,2025-03-14T14:31:00Z,SELL,20,4,USD,B\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).rowNumber()).isEqualTo(2);
        assertThat(rows.get(0).tradeId()).isEqualTo("T1");
        assertThat(rows.get(1).rowNumber()).isEqualTo(3);
        assertThat(rows.get(1).side()).isEqualTo("SELL");
    }

    @Test
    void handlesQuotedFieldsWithCommasQuotesAndEmbeddedNewlines() throws IOException {
        List<RawTradeRecord> rows = readAll(HEADER
                + "\nT1,ES,2025-03-14T14:30:00Z,BUY,10,3,USD,\"Desk 1, \"\"NY\"\"\"\n"
                + "T2,ES,2025-03-14T14:30:00Z,BUY,10,3,USD,\"multi\nline\"\n"
                + "T3,ES,2025-03-14T14:30:00Z,BUY,10,3,USD,X\n");
        assertThat(rows.get(0).traderId()).isEqualTo("Desk 1, \"NY\"");
        assertThat(rows.get(1).traderId()).isEqualTo("multi\nline");
        assertThat(rows.get(2).tradeId()).isEqualTo("T3");
    }

    @Test
    void skipsBlankLinesButDoesNotShiftRowNumbers() throws IOException {
        List<RawTradeRecord> rows = readAll(HEADER + "\n\nT1,ES,2025-03-14T14:30:00Z,BUY,10,3,USD,A\n\n"
                + "T2,ES,2025-03-14T14:30:00Z,BUY,10,3,USD,A");
        assertThat(rows).extracting(RawTradeRecord::rowNumber).containsExactly(3, 5);
    }

    @Test
    void headersAreCaseInsensitiveOrderIndependentAndBomTolerant() throws IOException {
        byte[] body = ("﻿Trader_ID , SIDE,trade_id,Instrument,trade_timestamp,price,quantity,currency\n"
                + "A,BUY,T1,ES,2025-03-14T14:30:00Z,10,3,USD\n").getBytes(StandardCharsets.UTF_8);
        List<RawTradeRecord> rows = readAll(body);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).tradeId()).isEqualTo("T1");
        assertThat(rows.get(0).traderId()).isEqualTo("A");
        assertThat(rows.get(0).side()).isEqualTo("BUY");
    }

    @Test
    void wrongColumnCountIsReturnedAsMalformedRowNotAnException() throws IOException {
        List<RawTradeRecord> rows = readAll(HEADER + "\nT1,ES,2025-03-14T14:30:00Z,BUY,10,3,USD,A,EXTRA\nT2,ES\n");
        assertThat(rows).hasSize(2).allSatisfy(r -> assertThat(r.malformed()).isTrue());
        assertThat(rows.get(0).malformedMessage()).contains("Expected 8 columns but found 9");
        assertThat(rows.get(1).rowNumber()).isEqualTo(3);
    }

    @Test
    void missingRequiredHeadersAreNamed() {
        assertThatThrownBy(() -> readAll("trade_id,instrument,price\nT1,ES,1\n"))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("trade_timestamp").hasMessageContaining("side")
                .hasMessageContaining("trader_id")
                .extracting(e -> ((CsvFormatException) e).code()).isEqualTo("MISSING_HEADERS");
    }

    @Test
    void duplicateRequiredHeaderIsRejected() {
        assertThatThrownBy(() -> readAll(HEADER + ",price\nT1,ES,x,BUY,1,1,USD,A,2\n"))
                .isInstanceOf(CsvFormatException.class)
                .extracting(e -> ((CsvFormatException) e).code()).isEqualTo("DUPLICATE_HEADERS");
    }

    @Test
    void emptyAndWhitespaceOnlyFilesAreRejected() {
        assertThatThrownBy(() -> readAll("")).isInstanceOf(CsvFormatException.class)
                .extracting(e -> ((CsvFormatException) e).code()).isEqualTo("EMPTY_FILE");
        assertThatThrownBy(() -> readAll("\n\n  \n")).isInstanceOf(CsvFormatException.class)
                .extracting(e -> ((CsvFormatException) e).code()).isEqualTo("EMPTY_FILE");
    }

    @Test
    void headerOnlyFileHasNoRows() throws IOException {
        assertThat(readAll(HEADER + "\n")).isEmpty();
    }

    @Test
    void binaryContentIsRejected() {
        byte[] binary = new byte[]{'P', 'K', 3, 4, 0, 0, 0, 8, 0, 1, 2, 3};
        assertThatThrownBy(() -> readAll(binary)).isInstanceOf(CsvFormatException.class)
                .extracting(e -> ((CsvFormatException) e).code()).isEqualTo("NOT_A_TEXT_FILE");
    }

    @Test
    void unterminatedQuoteSurfacesAsParseError() {
        assertThatThrownBy(() -> readAll(HEADER + "\nT1,ES,2025-03-14T14:30:00Z,BUY,10,3,USD,\"never closed\nT2,"))
                .isInstanceOf(CsvFormatException.class)
                .extracting(e -> ((CsvFormatException) e).code()).isEqualTo("CSV_PARSE_ERROR");
    }

    @Test
    void streamsManyRowsWithoutLoadingTheWholeFile() throws IOException {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < 20_000; i++) {
            sb.append("T").append(i).append(",ES,2025-03-14T14:30:00Z,BUY,10,3,USD,A\n");
        }
        try (TradeCsvReader reader = TradeCsvReader.open(
                new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)))) {
            int count = 0;
            RawTradeRecord last = null;
            while (reader.hasNext()) {
                last = reader.next();
                count++;
            }
            assertThat(count).isEqualTo(20_000);
            assertThat(last.rowNumber()).isEqualTo(20_001);
        }
    }
}
