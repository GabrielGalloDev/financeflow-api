package com.gabriel.financeflow.user.dto;

import com.gabriel.financeflow.shared.validation.ValidCurrency;
import com.gabriel.financeflow.shared.validation.ValidTimezone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O e-mail é inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        String email,

        @NotBlank(message = "A moeda é obrigatória")
        @ValidCurrency
        String currency,

        @NotBlank(message = "O fuso horário é obrigatório")
        @Size(max = 50, message = "O fuso horário deve ter no máximo 50 caracteres")
        @ValidTimezone
        String timezone
) {
}
