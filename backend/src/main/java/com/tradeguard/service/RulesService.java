package com.tradeguard.service;

import com.tradeguard.domain.ValidationRule;
import com.tradeguard.repository.ValidationRuleRepository;
import com.tradeguard.validation.RuleSet;
import com.tradeguard.web.dto.RulesDto;
import com.tradeguard.web.dto.RulesUpdateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the configurable validation rules. Every import reads the current rules when it starts, so a
 * change only affects imports that begin after it is saved; already-imported rows are never re-validated.
 */
@Service
public class RulesService {

    public static final int HARD_UPLOAD_LIMIT_MB = 50;
    static final String MAX_UPLOAD = "MAX_UPLOAD_SIZE_MB";
    static final String CURRENCIES = "ALLOWED_CURRENCIES";
    static final String INSTRUMENTS = "ALLOWED_INSTRUMENTS";
    static final String MAX_QTY = "MAX_TRADE_QUANTITY";

    private static final Logger log = LoggerFactory.getLogger(RulesService.class);

    private final ValidationRuleRepository repository;

    public RulesService(ValidationRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RuleSet current() {
        return toRuleSet(load());
    }

    @Transactional(readOnly = true)
    public RulesDto get() {
        Map<String, ValidationRule> rules = load();
        RuleSet set = toRuleSet(rules);
        Instant updated = rules.values().stream().map(ValidationRule::getUpdatedAt)
                .max(Instant::compareTo).orElse(null);
        return new RulesDto(set.maxUploadSizeMb(), HARD_UPLOAD_LIMIT_MB, sorted(set.allowedCurrencies()),
                sorted(set.allowedInstruments()), set.maxTradeQuantity(), updated);
    }

    @Transactional
    public RulesDto update(RulesUpdateRequest request) {
        Map<String, ValidationRule> rules = load();
        set(rules, MAX_UPLOAD, String.valueOf(request.maxUploadSizeMb()));
        set(rules, CURRENCIES, String.join(",", normalise(request.allowedCurrencies())));
        set(rules, INSTRUMENTS, String.join(",", normalise(request.allowedInstruments())));
        set(rules, MAX_QTY, request.maxTradeQuantity().stripTrailingZeros().toPlainString());
        repository.saveAll(rules.values());
        repository.flush();
        log.info("Validation rules updated");
        return get();
    }

    private void set(Map<String, ValidationRule> rules, String key, String value) {
        ValidationRule rule = rules.get(key);
        if (rule == null) {
            throw new IllegalStateException("Rule " + key + " is missing from validation_rules");
        }
        rule.setValue(value);
    }

    private Map<String, ValidationRule> load() {
        return repository.findAll().stream().collect(Collectors.toMap(ValidationRule::getKey, Function.identity()));
    }

    private RuleSet toRuleSet(Map<String, ValidationRule> rules) {
        RuleSet defaults = RuleSet.defaults();
        return new RuleSet(
                intValue(rules, MAX_UPLOAD, defaults.maxUploadSizeMb()),
                setValue(rules, CURRENCIES, defaults.allowedCurrencies()),
                setValue(rules, INSTRUMENTS, defaults.allowedInstruments()),
                decimalValue(rules, MAX_QTY, defaults.maxTradeQuantity()));
    }

    private int intValue(Map<String, ValidationRule> rules, String key, int fallback) {
        ValidationRule r = rules.get(key);
        try {
            return r == null ? fallback : Integer.parseInt(r.getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private BigDecimal decimalValue(Map<String, ValidationRule> rules, String key, BigDecimal fallback) {
        ValidationRule r = rules.get(key);
        try {
            return r == null ? fallback : new BigDecimal(r.getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Set<String> setValue(Map<String, ValidationRule> rules, String key, Set<String> fallback) {
        ValidationRule r = rules.get(key);
        if (r == null || r.getValue().isBlank()) {
            return fallback;
        }
        return Arrays.stream(r.getValue().split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> normalise(List<String> values) {
        return values.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
    }

    private List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }
}
