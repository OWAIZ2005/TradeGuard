package com.tradeguard.web;

import com.tradeguard.service.ImportQueryService;
import com.tradeguard.service.ReportService;
import com.tradeguard.service.TradeFilter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** CSV downloads. Filters are parsed (and rejected with 400 if invalid) before streaming starts. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportService reports;
    private final ImportQueryService imports;

    public ReportController(ReportService reports, ImportQueryService imports) {
        this.reports = reports;
        this.imports = imports;
    }

    @GetMapping("/valid-trades")
    public ResponseEntity<StreamingResponseBody> validTrades(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String instrument, @RequestParam(required = false) String side,
            @RequestParam(required = false) Long batchId, @RequestParam(required = false) String q) {
        TradeFilter filter = TradeFilter.parse(from, to, instrument, side, null, batchId, q);
        return csv("valid-trades.csv", out -> reports.writeValidTrades(filter, out));
    }

    @GetMapping("/validation-errors")
    public ResponseEntity<StreamingResponseBody> validationErrors(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String instrument, @RequestParam(required = false) String side,
            @RequestParam(required = false) Long batchId, @RequestParam(required = false) String code) {
        TradeFilter filter = TradeFilter.parse(from, to, instrument, side, null, batchId, null);
        if (code != null && !code.isBlank()) {
            try {
                com.tradeguard.domain.ErrorCode.valueOf(code.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("BAD_REQUEST", "Unknown error code '" + code + "'.");
            }
        }
        return csv("validation-errors.csv", out -> reports.writeValidationErrors(filter, code, out));
    }

    @GetMapping("/batch-summary/{id}")
    public ResponseEntity<StreamingResponseBody> batchSummary(@PathVariable long id) {
        imports.requireBatch(id);
        return csv("batch-" + id + "-summary.csv", out -> reports.writeBatchSummary(id, out));
    }

    @FunctionalInterface
    private interface CsvBody {
        void write(Writer out) throws Exception;
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, CsvBody body) {
        StreamingResponseBody stream = outputStream -> {
            Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            try {
                body.write(writer);
            } catch (RuntimeException | java.io.IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            writer.flush();
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(CSV)
                .body(stream);
    }
}
