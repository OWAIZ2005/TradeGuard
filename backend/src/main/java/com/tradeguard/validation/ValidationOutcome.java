package com.tradeguard.validation;

import java.util.List;

public record ValidationOutcome(ParsedTrade parsed, List<FieldError> errors) {
    public boolean valid() {
        return errors.isEmpty();
    }
}
