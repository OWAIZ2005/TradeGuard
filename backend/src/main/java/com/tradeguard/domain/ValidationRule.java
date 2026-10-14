package com.tradeguard.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "validation_rules")
public class ValidationRule {

    @Id
    @Column(name = "rule_key")
    private String key;

    @Column(name = "rule_value", nullable = false)
    private String value;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ValidationRule() {
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

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public Instant getUpdatedAt() { return updatedAt; }
}
