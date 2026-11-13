package com.gabriel.financeflow.transaction.dto;

import com.gabriel.financeflow.transaction.entity.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** As validações do DTO, sem Spring: o Validator é criado na mão (rápido, milissegundos). */
class TransactionRequestValidationTest {

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
    void validRequestHasNoErrors() {
        assertThat(validator.validate(request(TransactionType.EXPENSE, "200.00"))).isEmpty();
    }

    @Test
    void transferTypesAreRejectedByTheAssertTrue() {
        Set<ConstraintViolation<TransactionRequest>> errors = validator.validate(request(TransactionType.TRANSFER_IN, "10.00"));

        assertThat(errors).extracting(error -> error.getPropertyPath().toString()).containsExactly("incomeOrExpense");
        assertThat(errors).extracting(ConstraintViolation::getMessage)
                .containsExactly("Use INCOME ou EXPENSE; para transferências, use POST /api/v1/transfers");
    }

    @Test
    void nullTypeGivesOnlyOneError() {
        // O @AssertTrue deixa o null para o @NotNull: um erro só, e não dois
        assertThat(validator.validate(request(null, "10.00")))
                .extracting(error -> error.getPropertyPath().toString())
                .containsExactly("type");
    }

    @Test
    void zeroNegativeAndThreeDecimalsAreRejected() {
        assertThat(fieldsWithError(request(TransactionType.EXPENSE, "0"))).containsExactly("amount");
        assertThat(fieldsWithError(request(TransactionType.EXPENSE, "-5.00"))).containsExactly("amount");
        assertThat(fieldsWithError(request(TransactionType.EXPENSE, "10.555"))).containsExactly("amount");
    }

    @Test
    void statusIsOptional() {
        TransactionRequest withoutStatus = new TransactionRequest(UUID.randomUUID(), UUID.randomUUID(),
                TransactionType.INCOME, null, new BigDecimal("1.00"), LocalDate.now(), "Freela", null);

        assertThat(validator.validate(withoutStatus)).isEmpty();
    }

    private Set<String> fieldsWithError(TransactionRequest request) {
        return validator.validate(request).stream()
                .map(error -> error.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private TransactionRequest request(TransactionType type, String amount) {
        return new TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), type, null,
                new BigDecimal(amount), LocalDate.parse("2026-11-10"), "Aluguel", null);
    }
}
