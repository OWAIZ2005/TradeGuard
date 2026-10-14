package com.tradeguard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeguard.domain.ImportBatch;
import com.tradeguard.repository.ImportBatchRepository;
import com.tradeguard.validation.RuleSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and fails batches in their own transactions, so the batch record (and a FAILED status) survives even
 * when the row-processing transaction rolls back.
 */
@Service
public class BatchRegistry {

    private final ImportBatchRepository batches;
    private final ObjectMapper objectMapper;

    public BatchRegistry(ImportBatchRepository batches, ObjectMapper objectMapper) {
        this.batches = batches;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long start(String filename, long sizeBytes, String sha256, RuleSet rules) {
        return batches.save(new ImportBatch(filename, sizeBytes, sha256, snapshot(rules))).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(long batchId, String reason) {
        batches.findById(batchId).ifPresent(b -> {
            b.fail(reason);
            batches.save(b);
        });
    }

    /** Stores the exact rules used, so the batch's quality report can be reproduced later. */
    String snapshot(RuleSet rules) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxUploadSizeMb", rules.maxUploadSizeMb());
        m.put("allowedCurrencies", new TreeSet<>(rules.allowedCurrencies()));
        m.put("allowedInstruments", new TreeSet<>(rules.allowedInstruments()));
        m.put("maxTradeQuantity", rules.maxTradeQuantity().toPlainString());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise rules snapshot", e);
        }
    }
}
