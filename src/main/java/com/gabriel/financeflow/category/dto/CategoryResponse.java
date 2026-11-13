package com.gabriel.financeflow.category.dto;

import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.category.entity.CategoryType;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType type,
        boolean custom,
        boolean archived
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                !category.isDefault(),
                category.isArchived()
        );
    }
}
