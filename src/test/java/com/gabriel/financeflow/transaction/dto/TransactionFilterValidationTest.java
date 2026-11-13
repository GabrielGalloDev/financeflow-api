package com.gabriel.financeflow.transaction.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionFilterValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void emptyFilterIsValid() {
        assertThat(validator.validate(TransactionFilter.empty())).isEmpty();
    }

    @Test
    void sameDayPeriodIsValid() {
        LocalDate day = LocalDate.parse("2026-11-10");

        assertThat(validator.validate(filter(day, day, null, null))).isEmpty();
    }

    @Test
    void invertedPeriodIsRejected() {
        assertThat(validator.validate(filter(LocalDate.parse("2026-11-30"), LocalDate.parse("2026-11-01"), null, null)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("A data inicial não pode ser depois da final");
    }

    @Test
    void invertedAmountRangeIsRejected() {
        assertThat(validator.validate(filter(null, null, new BigDecimal("500"), new BigDecimal("100"))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("O valor mínimo não pode ser maior que o máximo");
    }

    @Test
    void negativeAmountIsRejected() {
        assertThat(validator.validate(filter(null, null, new BigDecimal("-1"), null)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("O valor mínimo não pode ser negativo");
    }

    private TransactionFilter filter(LocalDate from, LocalDate to, BigDecimal min, BigDecimal max) {
        return new TransactionFilter(from, to, null, null, null, null, min, max);
    }
}
