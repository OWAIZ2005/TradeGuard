package com.tradeguard.domain;

import java.util.Optional;

public enum Side {
    BUY, SELL;

    public static Optional<Side> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.trim().toUpperCase()) {
            case "BUY" -> Optional.of(BUY);
            case "SELL" -> Optional.of(SELL);
            default -> Optional.empty();
        };
    }
}
