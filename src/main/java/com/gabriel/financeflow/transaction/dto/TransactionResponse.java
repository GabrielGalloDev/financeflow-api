package com.gabriel.financeflow.transaction.dto;

import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        LocalDate date,
        String description,
        String notes,
        Ref account,
        Ref category,
        UUID transferGroupId,
        Instant createdAt
) {
    public record Ref(UUID id, String name) {
    }

    public static TransactionResponse from(Transaction transaction) {
        Category category = transaction.getCategory();

        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getDate(),
                transaction.getDescription(),
                transaction.getNotes(),
                new Ref(transaction.getAccount().getId(), transaction.getAccount().getName()),
                category == null ? null : new Ref(category.getId(), category.getName()),
                transaction.getTransferGroupId(),
                transaction.getCreatedAt()
        );
    }
}
