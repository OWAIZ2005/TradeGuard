package com.tradeguard.service;

import com.tradeguard.csv.TradeCsvReader;
import com.tradeguard.domain.ErrorCode;
import com.tradeguard.domain.ImportBatch;
import com.tradeguard.domain.Trade;
import com.tradeguard.domain.TradeValidationError;
import com.tradeguard.domain.ValidationStatus;
import com.tradeguard.repository.ExistingTradeRef;
import com.tradeguard.repository.ImportBatchRepository;
import com.tradeguard.repository.TradeRepository;
import com.tradeguard.repository.TradeValidationErrorRepository;
import com.tradeguard.validation.FieldError;
import com.tradeguard.validation.ParsedTrade;
import com.tradeguard.validation.RawTradeRecord;
import com.tradeguard.validation.RuleSet;
import com.tradeguard.validation.TradeValidator;
import com.tradeguard.validation.ValidationOutcome;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates and persists one batch inside a single transaction: either every row of the file is stored (valid and
 * invalid alike, with their errors) or nothing is. Rows are handled in chunks so memory stays bounded.
 *
 * <p>Duplicate policy (first valid occurrence wins, nothing is ever overwritten):
 * <ul>
 *   <li>A trade id already stored as a VALID trade in any earlier batch &rarr; DUPLICATE_EXISTING.</li>
 *   <li>A trade id used by an earlier otherwise-valid row of the same file &rarr; DUPLICATE_IN_BATCH.</li>
 *   <li>Rows that fail other validations do not "claim" their trade id, so a corrected row can still be accepted.</li>
 * </ul>
 * This class is the seam for asynchronous processing: a queue worker could call {@link #process} unchanged.
 */
@Service
public class ImportProcessor {

    static final int CHUNK_SIZE = 500;
    private static final Logger log = LoggerFactory.getLogger(ImportProcessor.class);

    private final TradeValidator validator;
    private final TradeRepository trades;
    private final TradeValidationErrorRepository errors;
    private final ImportBatchRepository batches;
    private final EntityManager em;

    public ImportProcessor(TradeValidator validator, TradeRepository trades, TradeValidationErrorRepository errors,
                           ImportBatchRepository batches, EntityManager em) {
        this.validator = validator;
        this.trades = trades;
        this.errors = errors;
        this.batches = batches;
        this.em = em;
    }

    private static final class Counters {
        int total;
        int valid;
        int invalid;
        int duplicateRows;
        int errorCount;
    }

    @Transactional
    public void process(long batchId, TradeCsvReader reader, RuleSet rules) {
        Instant uploadedAt = batches.findById(batchId).orElseThrow().getUploadedAt();
        Map<String, Integer> claimedIds = new HashMap<>();
        Counters counters = new Counters();
        List<RawTradeRecord> chunk = new ArrayList<>(CHUNK_SIZE);

        while (reader.hasNext()) {
            chunk.add(reader.next());
            if (chunk.size() == CHUNK_SIZE) {
                processChunk(batchId, chunk, rules, uploadedAt, claimedIds, counters);
                chunk.clear();
            }
        }
        if (!chunk.isEmpty()) {
            processChunk(batchId, chunk, rules, uploadedAt, claimedIds, counters);
        }

        em.flush();
        em.clear();
        ImportBatch batch = batches.findById(batchId).orElseThrow();
        batch.complete(counters.total, counters.valid, counters.invalid, counters.duplicateRows, counters.errorCount);
        batches.save(batch);
        log.info("Batch {} processed: total={} valid={} invalid={} duplicates={} errors={}", batchId, counters.total,
                counters.valid, counters.invalid, counters.duplicateRows, counters.errorCount);
    }

    private void processChunk(long batchId, List<RawTradeRecord> chunk, RuleSet rules, Instant uploadedAt,
                              Map<String, Integer> claimedIds, Counters counters) {
        int n = chunk.size();
        List<ValidationOutcome> outcomes = new ArrayList<>(n);
        List<List<FieldError>> rowErrors = new ArrayList<>(n);
        for (RawTradeRecord raw : chunk) {
            ValidationOutcome outcome = validator.validate(raw, rules);
            outcomes.add(outcome);
            rowErrors.add(new ArrayList<>(outcome.errors()));
        }

        // Phase A: duplicates against previously imported valid trades (one query for the whole chunk).
        Set<String> candidateIds = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (rowErrors.get(i).isEmpty()) {
                candidateIds.add(outcomes.get(i).parsed().tradeId());
            }
        }
        Map<String, ExistingTradeRef> existing = new HashMap<>();
        if (!candidateIds.isEmpty()) {
            trades.findValidByTradeIds(candidateIds).forEach(ref -> existing.put(ref.tradeId(), ref));
        }
        for (int i = 0; i < n; i++) {
            if (!rowErrors.get(i).isEmpty()) {
                continue;
            }
            String id = outcomes.get(i).parsed().tradeId();
            ExistingTradeRef ref = existing.get(id);
            if (ref != null) {
                rowErrors.get(i).add(new FieldError("trade_id", ErrorCode.DUPLICATE_EXISTING,
                        "Trade ID '" + id + "' was already imported (batch " + ref.batchId() + ", row "
                                + ref.rowNumber() + "). Existing trades are not overwritten.", id));
            }
        }

        // Phase B: duplicates inside this file, in row order (first valid occurrence wins).
        for (int i = 0; i < n; i++) {
            if (!rowErrors.get(i).isEmpty()) {
                continue;
            }
            String id = outcomes.get(i).parsed().tradeId();
            Integer firstRow = claimedIds.putIfAbsent(id, chunk.get(i).rowNumber());
            if (firstRow != null) {
                rowErrors.get(i).add(new FieldError("trade_id", ErrorCode.DUPLICATE_IN_BATCH,
                        "Trade ID '" + id + "' already appears at row " + firstRow + " of this file.", id));
            }
        }

        List<Trade> tradeEntities = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            RawTradeRecord raw = chunk.get(i);
            ParsedTrade p = outcomes.get(i).parsed();
            boolean ok = rowErrors.get(i).isEmpty();
            tradeEntities.add(new Trade(batchId, raw.rowNumber(), p.tradeId(), p.instrument(), p.timestamp(),
                    p.side(), p.price(), p.quantity(), p.currency(), p.traderId(), raw.rawRecord(),
                    ok ? ValidationStatus.VALID : ValidationStatus.INVALID,
                    p.timestamp() != null ? p.timestamp() : uploadedAt));
        }
        trades.saveAll(tradeEntities);

        List<TradeValidationError> errorEntities = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Trade t = tradeEntities.get(i);
            int row = chunk.get(i).rowNumber();
            boolean hasDuplicate = false;
            for (FieldError e : rowErrors.get(i)) {
                errorEntities.add(new TradeValidationError(batchId, t.getId(), row, e.field(), e.code(),
                        e.message(), e.rejectedValue()));
                hasDuplicate |= e.code().isDuplicate();
            }
            counters.total++;
            if (rowErrors.get(i).isEmpty()) {
                counters.valid++;
            } else {
                counters.invalid++;
            }
            if (hasDuplicate) {
                counters.duplicateRows++;
            }
        }
        errors.saveAll(errorEntities);
        counters.errorCount += errorEntities.size();

        em.flush();
        em.clear();
    }
}
