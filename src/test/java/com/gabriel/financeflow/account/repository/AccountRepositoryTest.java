package com.gabriel.financeflow.account.repository;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.entity.AccountType;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private User ana;
    private User bruno;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana@email.com", "hash"));
        bruno = userRepository.save(new User("Bruno", "bruno@email.com", "hash"));
    }

    @Test
    void saveFillsIdAuditAndKeepsTypeAndMoney() {
        Account saved = accountRepository.saveAndFlush(
                new Account(ana, "Cartão", AccountType.CREDIT_CARD, "Nubank", new BigDecimal("-1200.50")));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getType()).isEqualTo(AccountType.CREDIT_CARD);
        assertThat(saved.getInitialBalance()).isEqualByComparingTo("-1200.50");
        assertThat(saved.isArchived()).isFalse();
    }

    @Test
    void findByIdAndUserIdOnlyFindsTheOwnersAccount() {
        Account anasAccount = save(ana, "Nubank");

        assertThat(accountRepository.findByIdAndUserId(anasAccount.getId(), ana.getId())).isPresent();
        assertThat(accountRepository.findByIdAndUserId(anasAccount.getId(), bruno.getId())).isEmpty();
    }

    @Test
    void listsAreOrderedByNameAndRespectArchive() {
        save(ana, "Poupança");
        save(ana, "Carteira");
        Account archived = save(ana, "Antiga");
        archived.archive(Instant.now());
        save(bruno, "Conta do Bruno");
        accountRepository.flush();

        assertThat(accountRepository.findAllByUserIdAndArchivedAtIsNullOrderByNameAsc(ana.getId()))
                .extracting(Account::getName)
                .containsExactly("Carteira", "Poupança");

        assertThat(accountRepository.findAllByUserIdOrderByNameAsc(ana.getId()))
                .extracting(Account::getName)
                .containsExactly("Antiga", "Carteira", "Poupança");
    }

    @Test
    void duplicateNameCheckIgnoresCaseArchivedAccountsAndOtherUsers() {
        Account nubank = save(ana, "Nubank");

        assertThat(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedAtIsNull(ana.getId(), "NUBANK")).isTrue();
        assertThat(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedAtIsNull(bruno.getId(), "Nubank")).isFalse();
        // Na edição, a própria conta não conta como conflito
        assertThat(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedAtIsNullAndIdNot(
                ana.getId(), "nubank", nubank.getId())).isFalse();

        nubank.archive(Instant.now());
        accountRepository.flush();

        assertThat(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedAtIsNull(ana.getId(), "Nubank")).isFalse();
    }

    @Test
    void databaseRejectsDuplicateActiveNameEvenIfJavaForgetsToCheck() {
        save(ana, "Nubank");

        // A segunda barreira: o índice único parcial da V3
        assertThatThrownBy(() -> save(ana, "nubank"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void archiveIsIdempotentAndKeepsTheFirstDate() {
        Account account = save(ana, "Nubank");
        Instant first = Instant.parse("2026-01-01T10:00:00Z");

        account.archive(first);
        account.archive(Instant.parse("2026-02-01T10:00:00Z"));

        assertThat(account.isArchived()).isTrue();
        assertThat(account.getArchivedAt()).isEqualTo(first);

        account.unarchive();
        assertThat(account.isArchived()).isFalse();
    }

    private Account save(User owner, String name) {
        return accountRepository.saveAndFlush(
                new Account(owner, name, AccountType.CHECKING, null, new BigDecimal("100.00")));
    }
}
