package com.gabriel.financeflow.transaction.entity;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.shared.entity.BaseEntity;
import com.gabriel.financeflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends BaseEntity {
    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 140)
    private String description;

    @Column(length = 500)
    private String notes;

    private UUID transferGroupId;

    private Instant deletedAt;

    private Transaction(User user, Account account, Category category, TransactionType type,
                        TransactionStatus status, BigDecimal amount, LocalDate date,
                        String description, String notes, UUID transferGroupId) {
        this.user = user;
        this.account = account;
        this.category = category;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.notes = notes;
        this.transferGroupId = transferGroupId;
    }

    public static Transaction incomeOrExpense(User user, Account account, Category category,
                                              TransactionType type, TransactionStatus status,
                                              BigDecimal amount, LocalDate date,
                                              String description, String notes) {
        if (type.isTransfer()) {
            throw new IllegalArgumentException("Use transferLeg para transferências");
        }

        return new Transaction(user, account, category, type, status, amount, date, description, notes, null);
    }

    public static Transaction transferLeg(User user, Account account, TransactionType type,
                                          BigDecimal amount, LocalDate date,
                                          String description, String notes, UUID transferGroupId) {
        if (!type.isTransfer()) {
            throw new IllegalArgumentException("Use incomeOrExpense para receitas e despesas");
        }

        return new Transaction(user, account, null, type, TransactionStatus.PAID,
                amount, date, description, notes, transferGroupId);
    }

    public void update(Account account, Category category, TransactionType type, TransactionStatus status,
                       BigDecimal amount, LocalDate date, String description, String notes) {
        if (isTransfer()) {
            throw new IllegalStateException("Transferências não podem ser editadas");
        }

        if (type.isTransfer()) {
            throw new IllegalArgumentException("Não pode virar transferência");
        }

        this.account = account;
        this.category = category;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.notes = notes;
    }

    public void markAsPaid(LocalDate today) {
        if (status == TransactionStatus.PAID) {
            return;
        }

        this.status = TransactionStatus.PAID;

        if (date.isAfter(today)) {
            this.date = today;
        }
    }

    public void softDelete(Instant now) {
        if (deletedAt == null) {
            this.deletedAt = now;
        }
    }

    public boolean isTransfer() {
        return type.isTransfer();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public BigDecimal signedAmount() {
        return type.signum() > 0 ? amount : amount.negate();
    }
}
