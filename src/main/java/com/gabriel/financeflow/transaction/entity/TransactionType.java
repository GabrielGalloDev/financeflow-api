package com.gabriel.financeflow.transaction.entity;

import com.gabriel.financeflow.category.entity.CategoryType;

import java.util.EnumSet;
import java.util.Set;

public enum TransactionType {
    INCOME(1),
    EXPENSE(-1),
    TRANSFER_IN(1),
    TRANSFER_OUT(-1);

    private final int signum;

    TransactionType(int signum) {
        this.signum = signum;
    }

    public int signum() {
        return signum;
    }

    public boolean isTransfer() {
        return this == TRANSFER_IN || this == TRANSFER_OUT;
    }

    public static Set<TransactionType> incoming() {
        return EnumSet.of(INCOME, TRANSFER_IN);
    }

    public boolean matches(CategoryType categoryType) {
        return (this == INCOME && categoryType == CategoryType.INCOME)
                || (this == EXPENSE && categoryType == CategoryType.EXPENSE);
    }
}
