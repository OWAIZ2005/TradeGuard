package com.tradeguard.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TradeMathTest {

    @Test
    void notionalIsExactDecimalProduct() {
        // 0.1 * 3 is 0.30000000000000004 with doubles; BigDecimal must give exactly 0.3.
        assertThat(TradeMath.notional(new BigDecimal("0.1"), new BigDecimal("3"))).isEqualByComparingTo("0.3");
        assertThat(TradeMath.notional(new BigDecimal("5123.25"), new BigDecimal("10")))
                .isEqualByComparingTo("51232.50");
    }

    @Test
    void notionalKeepsFullPrecision() {
        assertThat(TradeMath.notional(new BigDecimal("1.123456"), new BigDecimal("2.000001")).toPlainString())
                .isEqualTo("2.246913123456");
    }

    @Test
    void notionalOfMissingValuesIsNull() {
        assertThat(TradeMath.notional(null, BigDecimal.ONE)).isNull();
        assertThat(TradeMath.notional(BigDecimal.ONE, null)).isNull();
    }
}
