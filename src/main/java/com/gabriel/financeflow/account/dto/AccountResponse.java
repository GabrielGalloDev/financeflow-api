package com.gabriel.financeflow.account.dto;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.entity.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        String institution,
        BigDecimal initialBalance,
        BigDecimal currentBalance,
        boolean archived,
        Instant createdAt
) {
    public static AccountResponse from(Account account, BigDecimal currentBalance) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInstitution(),
                account.getInitialBalance(),
                currentBalance,
                account.isArchived(),
                account.getCreatedAt()
        );
    }
}
