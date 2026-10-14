package com.tradeguard.service;

import com.tradeguard.csv.CsvFormatException;
import com.tradeguard.csv.TradeCsvReader;
import com.tradeguard.validation.RuleSet;
import com.tradeguard.web.ApiError;
import com.tradeguard.web.ApiException;
import com.tradeguard.web.dto.BatchDetailDto;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one upload: file checks, header check, batch creation, transactional processing, failure handling.
 * Processing is synchronous today; {@link ImportProcessor#process} is the unit an async worker would call.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final Set<String> ACCEPTED_CONTENT_TYPES = Set.of(
            "text/csv", "application/csv", "application/vnd.ms-excel", "text/plain", "application/octet-stream");

    /** Re-openable source of file bytes (the file is read twice: once for the checksum, once to parse). */
    @FunctionalInterface
    public interface InputSource {
        InputStream open() throws IOException;
    }

    private final RulesService rulesService;
    private final BatchRegistry registry;
    private final ImportProcessor processor;
    private final ImportQueryService queries;

    public ImportService(RulesService rulesService, BatchRegistry registry, ImportProcessor processor,
                         ImportQueryService queries) {
        this.rulesService = rulesService;
        this.registry = registry;
        this.processor = processor;
        this.queries = queries;
    }

    public BatchDetailDto importCsv(String originalFilename, long sizeBytes, String contentType, InputSource source) {
        RuleSet rules = rulesService.current();
        String filename = sanitiseFilename(originalFilename);

        if (sizeBytes <= 0) {
            throw ApiException.badRequest("EMPTY_FILE", "The uploaded file is empty.");
        }
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                    "Only .csv files are accepted.");
        }
        if (contentType != null && !contentType.isBlank()
                && !ACCEPTED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT).split(";")[0].trim())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                    "Content type '" + contentType + "' is not accepted for CSV uploads.");
        }
        long maxBytes = rules.maxUploadSizeMb() * 1024L * 1024L;
        if (sizeBytes > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "File exceeds the configured maximum upload size of " + rules.maxUploadSizeMb() + " MB.");
        }

        precheck(source);
        String sha256 = checksum(source);
        long batchId = registry.start(filename, sizeBytes, sha256, rules);
        log.info("Import started: batch={} file={} bytes={}", batchId, filename, sizeBytes);

        try (InputStream in = source.open(); TradeCsvReader reader = TradeCsvReader.open(in)) {
            processor.process(batchId, reader, rules);
        } catch (CsvFormatException e) {
            registry.fail(batchId, e.getMessage());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, e.code(), e.getMessage(),
                    List.of(new ApiError.Issue("batchId", String.valueOf(batchId))));
        } catch (IOException | RuntimeException e) {
            if (isConstraintViolation(e)) {
                log.warn("Batch {} rolled back by a database constraint: {}", batchId, e.getMessage());
                registry.fail(batchId, "Rolled back: a concurrent import stored the same trade id. Please retry.");
                throw new ApiException(HttpStatus.CONFLICT, "IMPORT_CONFLICT",
                        "Import rolled back because of a concurrent change. Please retry.",
                        List.of(new ApiError.Issue("batchId", String.valueOf(batchId))));
            }
            log.error("Batch {} failed", batchId, e);
            registry.fail(batchId, "Unexpected processing error. See server logs.");
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMPORT_FAILED",
                    "The import failed due to a system error and was rolled back.",
                    List.of(new ApiError.Issue("batchId", String.valueOf(batchId))));
        }
        return queries.detail(batchId);
    }

    private static boolean isConstraintViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DataIntegrityViolationException || t instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }

    /** Fails fast (HTTP 400, no batch created) on a bad header, binary content, or a file with no data rows. */
    private void precheck(InputSource source) {
        try (InputStream in = source.open(); TradeCsvReader reader = TradeCsvReader.open(in)) {
            if (!reader.hasNext()) {
                throw ApiException.badRequest("EMPTY_FILE", "The CSV has a header but no data rows.");
            }
        } catch (CsvFormatException e) {
            throw ApiException.badRequest(e.code(), e.getMessage());
        } catch (IOException e) {
            throw ApiException.badRequest("UNREADABLE_FILE", "The uploaded file could not be read.");
        }
    }

    private String checksum(InputSource source) {
        try (InputStream in = source.open()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw ApiException.badRequest("UNREADABLE_FILE", "The uploaded file could not be read.");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sanitiseFilename(String original) {
        String name = original == null ? "" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            name = "upload.csv";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
