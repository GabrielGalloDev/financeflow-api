package com.gabriel.financeflow.shared.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyUtilsTest {

    @Test
    void normalizeRoundsHalfToEvenDown() {
        BigDecimal result = MoneyUtils.normalize(new BigDecimal("10.125"));

        assertEquals(new BigDecimal("10.12"), result);
    }

    @Test
    void normalizeRoundsHalfToEvenUp() {
        BigDecimal result = MoneyUtils.normalize(new BigDecimal("10.135"));

        assertEquals(new BigDecimal("10.14"), result);
    }

    @Test
    void normalizeCompletesScale() {
        BigDecimal result = MoneyUtils.normalize(new BigDecimal("10"));

        assertEquals(new BigDecimal("10.00"), result);
    }

    @Test
    void normalizeRejectsNull() {
        assertThrows(NullPointerException.class, () -> MoneyUtils.normalize(null));
    }

    @Test
    void percentageOfExactValue() {
        BigDecimal result = MoneyUtils.percentage(new BigDecimal("80"), new BigDecimal("100"));

        assertEquals(new BigDecimal("80.00"), result);
    }

    @Test
    void percentageOfRepeatingDecimalRoundsDown() {
        BigDecimal result = MoneyUtils.percentage(new BigDecimal("1"), new BigDecimal("3"));

        assertEquals(new BigDecimal("33.33"), result);
    }

    @Test
    void percentageOfRepeatingDecimalRoundsUp() {
        BigDecimal result = MoneyUtils.percentage(new BigDecimal("2"), new BigDecimal("3"));

        assertEquals(new BigDecimal("66.67"), result);
    }

    @Test
    void percentageAboveOneHundredIsNotCapped() {
        BigDecimal result = MoneyUtils.percentage(new BigDecimal("150"), new BigDecimal("100"));

        assertEquals(new BigDecimal("150.00"), result);
    }

    @Test
    void percentageOfZeroTotalIsZero() {
        BigDecimal result = MoneyUtils.percentage(new BigDecimal("50"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("0.00"), result);
    }
}
