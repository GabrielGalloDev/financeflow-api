package com.gabriel.financeflow.category.dto;

import com.gabriel.financeflow.category.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 50, message = "O nome deve ter no máximo 50 caracteres")
        String name,

        @NotNull(message = "O tipo é obrigatório")
        CategoryType type
) {
}
