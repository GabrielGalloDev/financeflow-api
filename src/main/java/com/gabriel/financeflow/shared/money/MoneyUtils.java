package com.gabriel.financeflow.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class MoneyUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private MoneyUtils() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        Objects.requireNonNull(value, "value must not be null");
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        Objects.requireNonNull(part, "part must not be null");
        Objects.requireNonNull(total, "total must not be null");

        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        return part.multiply(HUNDRED).divide(total, SCALE, ROUNDING);
    }
}
