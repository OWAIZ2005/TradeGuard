package com.tradeguard.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trade_validation_errors")
public class TradeValidationError {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "errors_seq")
    @SequenceGenerator(name = "errors_seq", sequenceName = "trade_validation_errors_seq", allocationSize = 50)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "trade_pk", nullable = false)
    private Long tradePk;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", nullable = false)
    private ErrorCode errorCode;

    @Column(nullable = false)
    private String message;

    @Column(name = "rejected_value")
    private String rejectedValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TradeValidationError() {
    }

    public TradeValidationError(Long batchId, Long tradePk, int rowNumber, String fieldName, ErrorCode errorCode,
                                String message, String rejectedValue) {
        this.batchId = batchId;
        this.tradePk = tradePk;
        this.rowNumber = rowNumber;
        this.fieldName = fieldName;
        this.errorCode = errorCode;
        this.message = truncate(message, 500);
        this.rejectedValue = truncate(rejectedValue, 500);
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
}
