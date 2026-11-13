package com.gabriel.financeflow.transaction.dto;

import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionRequest(
        @NotNull(message = "A conta é obrigatória")
        UUID accountId,

        @NotNull(message = "A categoria é obrigatória")
        UUID categoryId,

        @NotNull(message = "O tipo é obrigatório")
        TransactionType type,

        TransactionStatus status,

        @NotNull(message = "O valor é obrigatório")
        @Positive(message = "O valor deve ser maior que zero")
        @Digits(integer = 17, fraction = 2, message = "O valor deve ter no máximo 17 dígitos inteiros e 2 casas decimais")
        BigDecimal amount,

        @NotNull(message = "A data é obrigatória")
        LocalDate date,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 140, message = "A descrição deve ter no máximo 140 caracteres")
        String description,

        @Size(max = 500, message = "As observações devem ter no máximo 500 caracteres")
        String notes
) {
    @AssertTrue(message = "Use INCOME ou EXPENSE; para transferências, use POST /api/v1/transfers")
    public boolean isIncomeOrExpense() {
        return type == null || !type.isTransfer();
    }
}
