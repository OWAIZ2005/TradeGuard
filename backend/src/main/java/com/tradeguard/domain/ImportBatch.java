package com.tradeguard.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trade_import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "batch_seq")
    @SequenceGenerator(name = "batch_seq", sequenceName = "trade_import_batches_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "rules_snapshot", nullable = false)
    private String rulesSnapshot;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImportBatch() {
    }

    public ImportBatch(String filename, long fileSizeBytes, String fileSha256, String rulesSnapshot) {
        this.filename = filename;
        this.fileSizeBytes = fileSizeBytes;
        this.fileSha256 = fileSha256;
        this.rulesSnapshot = rulesSnapshot;
        this.status = BatchStatus.PROCESSING;
        this.uploadedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void complete(int total, int valid, int invalid, int duplicates, int errors) {
        this.totalRows = total;
        this.validRows = valid;
        this.invalidRows = invalid;
        this.duplicateRows = duplicates;
        this.errorCount = errors;
        this.status = BatchStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = BatchStatus.FAILED;
        this.failureReason = reason != null && reason.length() > 1000 ? reason.substring(0, 1000) : reason;
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getFileSha256() { return fileSha256; }
    public BatchStatus getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getValidRows() { return validRows; }
    public int getInvalidRows() { return invalidRows; }
    public int getDuplicateRows() { return duplicateRows; }
    public int getErrorCount() { return errorCount; }
    public String getRulesSnapshot() { return rulesSnapshot; }
    public String getFailureReason() { return failureReason; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
