package com.gabriel.financeflow.transaction.spec;

import com.gabriel.financeflow.transaction.dto.TransactionFilter;
import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> matching(UUID userId, TransactionFilter filter) {
        return belongsTo(userId)
                .and(notDeleted())
                .and(dateFrom(filter.from()))
                .and(dateTo(filter.to()))
                .and(account(filter.accountId()))
                .and(category(filter.categoryId()))
                .and(type(filter.type()))
                .and(status(filter.status()))
                .and(amountMin(filter.minAmount()))
                .and(amountMax(filter.maxAmount()));
    }

    public static Specification<Transaction> belongsTo(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Transaction> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Transaction> dateFrom(LocalDate from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    public static Specification<Transaction> dateTo(LocalDate to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("date"), to);
    }

    public static Specification<Transaction> account(UUID accountId) {
        return (root, query, cb) -> accountId == null ? null : cb.equal(root.get("account").get("id"), accountId);
    }

    public static Specification<Transaction> category(UUID categoryId) {
        return (root, query, cb) -> categoryId == null ? null : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Transaction> type(TransactionType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> status(TransactionStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Transaction> amountMin(BigDecimal min) {
        return (root, query, cb) -> min == null ? null : cb.greaterThanOrEqualTo(root.get("amount"), min);
    }

    public static Specification<Transaction> amountMax(BigDecimal max) {
        return (root, query, cb) -> max == null ? null : cb.lessThanOrEqualTo(root.get("amount"), max);
    }
}
