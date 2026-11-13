package com.gabriel.financeflow.transaction.dto;

import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        UUID accountId,

        UUID categoryId,

        TransactionType type,

        TransactionStatus status,

        @PositiveOrZero(message = "O valor mínimo não pode ser negativo")
        BigDecimal minAmount,

        @PositiveOrZero(message = "O valor máximo não pode ser negativo")
        BigDecimal maxAmount
) {

    public static TransactionFilter empty() {
        return new TransactionFilter(null, null, null, null, null, null, null, null);
    }

    @AssertTrue(message = "A data inicial não pode ser depois da final")
    public boolean isValidPeriod() {
        return from == null || to == null || !from.isAfter(to);
    }

    @AssertTrue(message = "O valor mínimo não pode ser maior que o máximo")
    public boolean isValidAmountRange() {
        return minAmount == null || maxAmount == null || minAmount.compareTo(maxAmount) <= 0;
    }
}
