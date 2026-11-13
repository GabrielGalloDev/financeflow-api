package com.gabriel.financeflow.transaction.service;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.repository.AccountRepository;
import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.category.repository.CategoryRepository;
import com.gabriel.financeflow.shared.exception.BusinessException;
import com.gabriel.financeflow.shared.exception.ResourceNotFoundException;
import com.gabriel.financeflow.shared.security.CurrentUser;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Component
class TransactionRules {
    private static final String ACCOUNT_NOT_FOUND = "Conta não encontrada";
    private static final String CATEGORY_NOT_FOUND = "Categoria não encontrada";
    private static final String ARCHIVED_ACCOUNT = "Conta arquivada não aceita movimentações";

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    TransactionRules(AccountRepository accountRepository, CategoryRepository categoryRepository,
                     UserRepository userRepository, CurrentUser currentUser, Clock clock) {
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    Account findUsableAccount(UUID accountId) {
        Account account = accountRepository.findByIdAndUserId(accountId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND));

        assertAccountActive(account);

        return account;
    }

    void assertAccountActive(Account account) {
        if (account.isArchived()) {
            throw new BusinessException(ARCHIVED_ACCOUNT);
        }
    }

    Category findUsableCategory(UUID categoryId, TransactionType type) {
        Category category = categoryRepository.findVisibleById(categoryId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException(CATEGORY_NOT_FOUND));

        if (category.isArchived()) {
            throw new BusinessException("Categoria arquivada não aceita movimentações");
        }

        if (!type.matches(category.getType())) {
            throw new BusinessException("A categoria '" + category.getName() + "' não é do tipo " + type);
        }

        return category;
    }

    void assertDateAllowed(TransactionStatus status, LocalDate date) {
        if (status == TransactionStatus.PAID && date.isAfter(todayForCurrentUser())) {
            throw new BusinessException("Uma movimentação paga não pode ter data no futuro");
        }
    }

    LocalDate todayForCurrentUser() {
        User user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        return LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
    }

    String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }
}
