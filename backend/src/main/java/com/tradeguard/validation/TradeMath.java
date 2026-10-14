package com.tradeguard.validation;

import java.math.BigDecimal;

/** Exact decimal arithmetic for money-like values. Never use float/double here. */
public final class TradeMath {

    private TradeMath() {
    }

    /** Notional value = price x quantity (in the trade's own currency, no conversion). */
    public static BigDecimal notional(BigDecimal price, BigDecimal quantity) {
        if (price == null || quantity == null) {
            return null;
        }
        return price.multiply(quantity);
    }
}
