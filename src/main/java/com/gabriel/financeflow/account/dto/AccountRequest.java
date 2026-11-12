package com.gabriel.financeflow.account.dto;

import com.gabriel.financeflow.account.entity.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @NotNull(message = "O tipo é obrigatório")
        AccountType type,

        @Size(max = 100, message = "A instituição deve ter no máximo 100 caracteres")
        String institution,

        @NotNull(message = "O saldo inicial é obrigatório")
        @Digits(integer = 17, fraction = 2, message = "O saldo inicial deve ter no máximo 17 dígitos inteiros e 2 casas decimais")
        BigDecimal initialBalance
) {
}
