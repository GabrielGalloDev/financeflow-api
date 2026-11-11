package com.gabriel.financeflow.shared.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneValidatorTest {

    private final TimezoneValidator validator = new TimezoneValidator();

    @ParameterizedTest
    @ValueSource(strings = {"America/Sao_Paulo", "America/New_York", "Asia/Tokyo", "Europe/Lisbon", "UTC"})
    void acceptsRegionNames(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Marte/Base", "+03:00", "GMT-3", "Z", "america/sao_paulo", ""})
    void rejectsUnknownNamesAndFixedOffsets(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
