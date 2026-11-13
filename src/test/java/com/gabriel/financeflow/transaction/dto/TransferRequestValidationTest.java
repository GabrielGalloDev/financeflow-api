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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** As validações do TransferRequest, sem Spring. */
class TransferRequestValidationTest {

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
    void differentAccountsAreValid() {
        assertThat(validator.validate(request(UUID.randomUUID(), UUID.randomUUID(), "500.00"))).isEmpty();
    }

    @Test
    void sameAccountIsRejectedEvenWithDifferentUuidObjects() {
        UUID id = UUID.randomUUID();
        // Dois objetos diferentes com o mesmo valor, como acontece ao ler o JSON: o == não pegaria
        UUID sameValueOtherObject = UUID.fromString(id.toString());

        assertThat(validator.validate(request(id, sameValueOtherObject, "500.00")))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("A conta de origem e a de destino precisam ser diferentes");
    }

    @Test
    void missingAccountGivesOnlyTheNotNullError() {
        assertThat(validator.validate(request(null, UUID.randomUUID(), "500.00")))
                .extracting(error -> error.getPropertyPath().toString())
                .containsExactly("fromAccountId");
    }

    @Test
    void zeroAmountIsRejected() {
        assertThat(validator.validate(request(UUID.randomUUID(), UUID.randomUUID(), "0")))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("O valor deve ser maior que zero");
    }

    private TransferRequest request(UUID from, UUID to, String amount) {
        return new TransferRequest(from, to, new BigDecimal(amount), LocalDate.parse("2026-11-10"), "Saque", null);
    }
}
