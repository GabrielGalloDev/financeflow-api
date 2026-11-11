package com.gabriel.financeflow.account.entity;

import com.gabriel.financeflow.shared.entity.BaseEntity;
import com.gabriel.financeflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(length = 100)
    private String institution;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal initialBalance;

    private Instant archivedAt;

    public Account(User user, String name, AccountType type, String institution, BigDecimal initialBalance) {
        this.user = user;
        this.name = name;
        this.type = type;
        this.institution = institution;
        this.initialBalance = initialBalance;
    }

    public void update(String name, AccountType type, String institution, BigDecimal initialBalance) {
        this.name = name;
        this.type = type;
        this.institution = institution;
        this.initialBalance = initialBalance;
    }

    public void archive(Instant now) {
        if (this.archivedAt == null) {
            this.archivedAt = now;
        }
    }

    public void unarchive() {
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return this.archivedAt != null;
    }
}
