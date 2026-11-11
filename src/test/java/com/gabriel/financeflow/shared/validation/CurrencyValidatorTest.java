package com.gabriel.financeflow.shared.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyValidatorTest {

    private final CurrencyValidator validator = new CurrencyValidator();

    @ParameterizedTest
    @ValueSource(strings = {"BRL", "USD", "EUR", "JPY", "brl", " usd "})
    void acceptsExistingIsoCodes(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"XYZ", "REAL", "R$", "", "   "})
    void rejectsUnknownCodes(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
