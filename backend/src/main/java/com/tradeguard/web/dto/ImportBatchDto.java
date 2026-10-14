package com.tradeguard.web.dto;

import com.tradeguard.domain.BatchStatus;
import com.tradeguard.domain.ImportBatch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record ImportBatchDto(
        Long id,
        String filename,
        long fileSizeBytes,
        BatchStatus status,
        int totalRows,
        int validRows,
        int invalidRows,
        int duplicateRows,
        int errorCount,
        BigDecimal errorRatePercent,
        Instant uploadedAt,
        Instant completedAt,
        String failureReason) {

    public static ImportBatchDto from(ImportBatch b) {
        BigDecimal rate = b.getTotalRows() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(b.getInvalidRows()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(b.getTotalRows()), 2, RoundingMode.HALF_UP);
        return new ImportBatchDto(b.getId(), b.getFilename(), b.getFileSizeBytes(), b.getStatus(), b.getTotalRows(),
                b.getValidRows(), b.getInvalidRows(), b.getDuplicateRows(), b.getErrorCount(), rate,
                b.getUploadedAt(), b.getCompletedAt(), b.getFailureReason());
    }
}
