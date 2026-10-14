package com.tradeguard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AnalyticsAndReportsIntegrationTest extends AbstractIntegrationTest {

    /** 4 valid trades (3 USD, 1 EUR) across 2 days, plus 3 invalid rows that must never reach analytics. */
    private static final String DATA = String.join("\n",
            "V-1,ES,2025-03-10T14:30:00Z,BUY,100.50,10,USD,TRD-1",     // notional 1005.00
            "V-2,ES,2025-03-10T15:30:00Z,SELL,200.25,4,USD,TRD-2",     // notional 801.00
            "V-3,NQ,2025-03-11T09:00:00Z,BUY,50,20,USD,TRD-3",         // notional 1000
            "V-4,GC,2025-03-11T10:00:00Z,SELL,10.5,2,EUR,\"DESK, 1\"", // notional 21.0
            "I-1,ES,2025-03-10T14:30:00Z,BUY,-999,1000,USD,TRD-1",     // negative price
            "I-2,ES,2025-03-10T14:30:00Z,BUY,999,9999999,USD,TRD-1",   // quantity above max
            "V-1,ES,2025-03-12T14:30:00Z,BUY,999,500,USD,=cmd|calc",   // duplicate id
            "");

    @BeforeEach
    void load() throws Exception {
        uploadCsv(DATA).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
    }

    @Test
    void overviewIsComputedFromValidTradesOnly() throws Exception {
        mvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(7))
                .andExpect(jsonPath("$.validTrades").value(4))
                .andExpect(jsonPath("$.invalidTrades").value(3))
                .andExpect(jsonPath("$.duplicateTrades").value(1))
                .andExpect(jsonPath("$.validationErrorRatePercent").value(42.86))
                .andExpect(jsonPath("$.totalQuantity").value(36)) // 10+4+20+2; invalid 1000/9999999/500 excluded
                .andExpect(jsonPath("$.notionalByCurrency", hasSize(2)))
                .andExpect(jsonPath("$.notionalByCurrency[?(@.currency=='USD')].amount").value(2806.0))
                .andExpect(jsonPath("$.notionalByCurrency[?(@.currency=='EUR')].amount").value(21.0))
                .andExpect(jsonPath("$.currencyNote").value(containsString("not converted")));
    }

    @Test
    void dateRangeFiltersAllAnalyticsConsistently() throws Exception {
        mvc.perform(get("/api/analytics/overview").param("from", "2025-03-11").param("to", "2025-03-11"))
                .andExpect(jsonPath("$.validTrades").value(2))
                .andExpect(jsonPath("$.totalQuantity").value(22));
        mvc.perform(get("/api/analytics/daily-volume").param("from", "2025-03-11").param("to", "2025-03-11"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value("2025-03-11"))
                .andExpect(jsonPath("$[0].tradeCount").value(2));
        mvc.perform(get("/api/analytics/instruments").param("from", "2025-03-11").param("to", "2025-03-11"))
                .andExpect(jsonPath("$", hasSize(2)));
        report(get("/api/reports/valid-trades").param("from", "2025-03-11").param("to", "2025-03-11"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("V-1,"))))
                .andExpect(content().string(containsString("V-3,")));
    }

    @Test
    void rejectsBadDateRanges() throws Exception {
        mvc.perform(get("/api/analytics/overview").param("from", "2025-03-12").param("to", "2025-03-11"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/analytics/overview").param("from", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dailyVolumeUsesSqlAggregationAndRunningTotal() throws Exception {
        mvc.perform(get("/api/analytics/daily-volume"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2025-03-10"))
                .andExpect(jsonPath("$[0].tradeCount").value(2))
                .andExpect(jsonPath("$[0].quantity").value(14))
                .andExpect(jsonPath("$[0].buyCount").value(1))
                .andExpect(jsonPath("$[0].sellCount").value(1))
                .andExpect(jsonPath("$[0].cumulativeQuantity").value(14))
                .andExpect(jsonPath("$[1].cumulativeQuantity").value(36));
    }

    @Test
    void instrumentAndSideBreakdowns() throws Exception {
        mvc.perform(get("/api/analytics/instruments"))
                .andExpect(jsonPath("$[0].instrument").value("ES"))
                .andExpect(jsonPath("$[0].tradeCount").value(2))
                .andExpect(jsonPath("$[0].quantity").value(14))
                .andExpect(jsonPath("$[0].notionalByCurrency[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].notionalByCurrency[0].amount").value(1806.0));
        mvc.perform(get("/api/analytics/sides"))
                .andExpect(jsonPath("$[?(@.side=='BUY')].tradeCount").value(2))
                .andExpect(jsonPath("$[?(@.side=='BUY')].quantity").value(30.0))
                .andExpect(jsonPath("$[?(@.side=='SELL')].tradeCount").value(2));
        mvc.perform(get("/api/analytics/instruments").param("side", "buy"))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void validationStatisticsDistributeErrorsByCodeCategoryAndBatch() throws Exception {
        mvc.perform(get("/api/analytics/validation"))
                .andExpect(jsonPath("$.totalRows").value(7))
                .andExpect(jsonPath("$.invalidRows").value(3))
                .andExpect(jsonPath("$.errorsByCode[?(@.code=='NON_POSITIVE_PRICE')].occurrences").value(1))
                .andExpect(jsonPath("$.errorsByCode[?(@.code=='QUANTITY_EXCEEDS_MAX')].occurrences").value(1))
                .andExpect(jsonPath("$.errorsByCode[?(@.code=='DUPLICATE_IN_BATCH')].affectedRows").value(1))
                .andExpect(jsonPath("$.errorsByCategory[?(@.category=='OUT_OF_RANGE')].occurrences").value(2))
                .andExpect(jsonPath("$.batchQuality", hasSize(1)))
                .andExpect(jsonPath("$.batchQuality[0].errorRatePercent").value(42.86));
    }

    @Test
    void tradeExplorerFiltersSortsAndPaginatesOnTheServer() throws Exception {
        mvc.perform(get("/api/trades").param("size", "3").param("page", "0").param("status", "VALID")
                        .param("sort", "tradeId").param("direction", "asc"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].tradeId").value("V-1"))
                .andExpect(jsonPath("$.content[0].notional").value(1005.0));
        mvc.perform(get("/api/trades").param("size", "3").param("page", "1").param("status", "VALID")
                        .param("sort", "tradeId").param("direction", "asc"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].tradeId").value("V-4"));
        mvc.perform(get("/api/trades").param("status", "INVALID"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/trades").param("instrument", "nq"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/trades").param("side", "SELL").param("status", "VALID"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/trades").param("batchId", "1").param("q", "v-"))
                .andExpect(jsonPath("$.totalElements").value(5)); // V-1..V-4 plus the duplicate V-1 row
        mvc.perform(get("/api/trades").param("q", "%"))
                .andExpect(jsonPath("$.totalElements").value(0)); // wildcard characters are escaped
        mvc.perform(get("/api/trades").param("sort", "id; DROP TABLE trades"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/trades").param("size", "100000")).andExpect(status().isBadRequest());
        assertThat(countRows("trades")).isEqualTo(7);
    }

    @Test
    void tradeDetailShowsEveryValidationErrorOfTheRow() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM trades WHERE row_number = 7", Long.class);
        mvc.perform(get("/api/trades/{id}", id))
                .andExpect(jsonPath("$.trade.status").value("INVALID"))
                .andExpect(jsonPath("$.trade.errorCount").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("QUANTITY_EXCEEDS_MAX"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(7))
                .andExpect(jsonPath("$.rawRecord").value(containsString("9999999")));
        mvc.perform(get("/api/trades/{id}", 987654321))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void validTradesReportContainsOnlyValidRowsAndHonoursFilters() throws Exception {
        String body = report(get("/api/reports/valid-trades"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("valid-trades.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString();
        String[] lines = body.split("\r\n");
        assertThat(lines[0]).isEqualTo(
                "trade_id,instrument,trade_timestamp,side,price,quantity,currency,notional,trader_id,batch_id,source_row");
        assertThat(lines).hasSize(5); // header + 4 valid trades
        assertThat(body).doesNotContain("I-1").doesNotContain("I-2");
        assertThat(lines[1]).isEqualTo("V-1,ES,2025-03-10T14:30:00Z,BUY,100.50,10.00,USD,1005.00,TRD-1,1,2");
        assertThat(body).contains("\"DESK, 1\"");

        String filtered = report(get("/api/reports/valid-trades").param("instrument", "ES").param("side", "SELL"))
                .andReturn().getResponse().getContentAsString();
        assertThat(filtered.split("\r\n")).hasSize(2);
        assertThat(filtered).contains("V-2");
    }

    @Test
    void validationErrorReportListsRowsFieldsCodesAndNeutralisesFormulas() throws Exception {
        String body = report(get("/api/reports/validation-errors").param("batchId", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("validation-errors.csv")))
                .andReturn().getResponse().getContentAsString();
        String[] lines = body.split("\r\n");
        assertThat(lines[0]).startsWith("batch_id,filename,row_number,trade_id,field,error_code,error_category,message");
        assertThat(lines).hasSize(4); // header + 3 errors
        assertThat(body).contains("NON_POSITIVE_PRICE").contains("QUANTITY_EXCEEDS_MAX").contains("DUPLICATE_IN_BATCH");
        // a negative number in a text column must not be interpreted as a formula by Excel
        assertThat(body).contains("'-999");
        // the original row is exported as one quoted text cell that starts with the trade id, not with "="
        assertThat(body).contains("\"V-1,ES,2025-03-12T14:30:00Z,BUY,999,500,USD,=cmd|calc\"");

        String onlyDuplicates = report(get("/api/reports/validation-errors").param("code", "DUPLICATE_IN_BATCH"))
                .andReturn().getResponse().getContentAsString();
        assertThat(onlyDuplicates.split("\r\n")).hasSize(2);
        report(get("/api/reports/validation-errors").param("code", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void batchSummaryReportMatchesTheBatch() throws Exception {
        String body = report(get("/api/reports/batch-summary/1")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("quality,total_rows,7").contains("quality,valid_rows,4")
                .contains("quality,invalid_rows,3").contains("quality,error_rate_percent,42.86")
                .contains("errors_by_code,NON_POSITIVE_PRICE (rows affected),1")
                .contains("valid_notional_USD,notional,2806.00")
                .contains("not converted or added across currencies");
        report(get("/api/reports/batch-summary/999")).andExpect(status().isNotFound());
    }

    @Test
    void ruleChangesAreValidatedPersistedAndAppliedToLaterImportsOnly() throws Exception {
        mvc.perform(get("/api/rules")).andExpect(jsonPath("$.maxUploadSizeMb").value(10))
                .andExpect(jsonPath("$.allowedCurrencies", hasSize(6)))
                .andExpect(jsonPath("$.hardUploadLimitMb").value(50));

        // invalid update: nothing is stored, every problem is reported by field
        mvc.perform(put("/api/rules").contentType(MediaType.APPLICATION_JSON).content("""
                        {"maxUploadSizeMb":0,"allowedCurrencies":["US"],"allowedInstruments":[],"maxTradeQuantity":-1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
        mvc.perform(put("/api/rules").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/rules").contentType(MediaType.APPLICATION_JSON).content("""
                        {"maxUploadSizeMb":51,"allowedCurrencies":["USD"],"allowedInstruments":["ES"],"maxTradeQuantity":5}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/rules")).andExpect(jsonPath("$.maxUploadSizeMb").value(10));

        mvc.perform(put("/api/rules").contentType(MediaType.APPLICATION_JSON).content("""
                        {"maxUploadSizeMb":5,"allowedCurrencies":["usd","usd","gbp"],"allowedInstruments":["es","nq"],"maxTradeQuantity":15}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxUploadSizeMb").value(5))
                .andExpect(jsonPath("$.allowedCurrencies[0]").value("GBP"))
                .andExpect(jsonPath("$.allowedCurrencies", hasSize(2)))
                .andExpect(jsonPath("$.allowedInstruments[1]").value("NQ"))
                .andExpect(jsonPath("$.maxTradeQuantity").value(15));

        // already-imported trades are untouched...
        mvc.perform(get("/api/analytics/overview")).andExpect(jsonPath("$.validTrades").value(4));
        // ...but the next import uses the new rules: EUR, GC and quantity 20 are now rejected
        uploadCsv("N-1,GC,2025-03-20T10:00:00Z,BUY,10,1,EUR,A\nN-2,ES,2025-03-20T10:00:00Z,BUY,10,20,USD,A\n"
                + "N-3,ES,2025-03-20T10:00:00Z,BUY,10,15,USD,A\n")
                .andExpect(jsonPath("$.batch.validRows").value(1))
                .andExpect(jsonPath("$.errorBreakdown[*].code").value(
                        org.hamcrest.Matchers.containsInAnyOrder("UNSUPPORTED_INSTRUMENT", "UNSUPPORTED_CURRENCY",
                                "QUANTITY_EXCEEDS_MAX")));
        assertThat(jdbc.queryForObject("SELECT rules_snapshot FROM trade_import_batches WHERE id = 2", String.class))
                .contains("\"maxTradeQuantity\":\"15\"");
    }

    @Test
    void importHistoryListsBatchesNewestFirstWithPaginationAndStatusFilter() throws Exception {
        uploadCsv("H-1,ES,2025-03-20T10:00:00Z,BUY,10,1,USD,A\n").andExpect(status().isCreated());
        mvc.perform(get("/api/imports").param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].filename").value("trades.csv"));
        mvc.perform(get("/api/imports").param("status", "FAILED")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/imports").param("status", "BOGUS")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/imports/{id}", 1))
                .andExpect(jsonPath("$.batch.errorCount").value(3))
                .andExpect(jsonPath("$.errorBreakdown", hasSize(3)))
                .andExpect(jsonPath("$.summary.generator").value("rule-based"));
        mvc.perform(get("/api/imports/{id}", 424242)).andExpect(status().isNotFound());
        mvc.perform(get("/api/imports/abc")).andExpect(status().isBadRequest());
    }
}
