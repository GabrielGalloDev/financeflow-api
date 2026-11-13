package com.gabriel.financeflow.account.service;

import com.gabriel.financeflow.account.dto.AccountResponse;
import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.entity.AccountType;
import com.gabriel.financeflow.account.repository.AccountRepository;
import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.category.entity.CategoryType;
import com.gabriel.financeflow.category.repository.CategoryRepository;
import com.gabriel.financeflow.transaction.dto.TransactionRequest;
import com.gabriel.financeflow.transaction.dto.TransferRequest;
import com.gabriel.financeflow.transaction.dto.TransferResponse;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import com.gabriel.financeflow.transaction.service.TransactionService;
import com.gabriel.financeflow.transaction.service.TransferService;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class AccountBalanceTest {

    private static final LocalDate DAY = LocalDate.parse("2026-11-10");

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User ana;
    private User bruno;
    private Account nubank;
    private Account carteira;
    private Category moradia;
    private Category freelance;
    private UUID freela;
    private UUID transferLeg;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana@email.com", "hash"));
        bruno = userRepository.save(new User("Bruno", "bruno@email.com", "hash"));
        nubank = accountRepository.save(new Account(ana, "Nubank", AccountType.CHECKING, null, new BigDecimal("1000.00")));
        carteira = accountRepository.save(new Account(ana, "Carteira", AccountType.WALLET, null, BigDecimal.ZERO));
        moradia = defaultCategory("Moradia", CategoryType.EXPENSE);
        freelance = defaultCategory("Freelance", CategoryType.INCOME);

        loginAs(ana);
        expense(nubank, "200.00");
        freela = transactionService.create(new TransactionRequest(nubank.getId(), freelance.getId(),
                TransactionType.INCOME, TransactionStatus.PENDING, new BigDecimal("300.00"), DAY, "Freela", null)).id();
        TransferResponse transfer = transferService.transfer(new TransferRequest(nubank.getId(), carteira.getId(),
                new BigDecimal("150.00"), DAY, "Saque", null));
        transferLeg = transfer.outgoingTransactionId();
        transactionService.delete(expense(nubank, "999.00"));
        entityManager.flush();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void balanceOfOneAccountCountsOnlyPaidAndNotDeleted() {
        assertThat(accountService.get(nubank.getId()).currentBalance()).isEqualByComparingTo("650.00");
        assertThat(accountService.get(carteira.getId()).currentBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void balanceHasTwoDecimals() {
        assertThat(accountService.get(nubank.getId()).currentBalance().scale()).isEqualTo(2);
        assertThat(accountService.list(false)).allSatisfy(account ->
                assertThat(account.currentBalance().scale()).isEqualTo(2));
    }

    @Test
    void listShowsTheSameBalancesAsGet() {
        assertThat(accountService.list(false))
                .extracting(AccountResponse::name, AccountResponse::currentBalance)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Carteira", new BigDecimal("150.00")),
                        org.assertj.core.groups.Tuple.tuple("Nubank", new BigDecimal("650.00")));
    }

    @Test
    void accountWithoutMovementsKeepsTheInitialBalance() {
        Account poupanca = accountRepository.save(new Account(ana, "Poupança", AccountType.SAVINGS, null, new BigDecimal("80.00")));

        assertThat(accountService.get(poupanca.getId()).currentBalance()).isEqualByComparingTo("80.00");
        assertThat(accountService.list(false))
                .filteredOn(account -> account.name().equals("Poupança"))
                .extracting(AccountResponse::currentBalance)
                .containsExactly(new BigDecimal("80.00"));
    }

    @Test
    void archivedAccountStillHasItsBalance() {
        accountService.archive(carteira.getId());

        assertThat(accountService.list(true))
                .filteredOn(AccountResponse::archived)
                .extracting(AccountResponse::currentBalance)
                .containsExactly(new BigDecimal("150.00"));
    }

    @Test
    void payingAPendingIncomeIncreasesTheBalance() {
        transactionService.pay(freela);
        entityManager.flush();

        assertThat(accountService.get(nubank.getId()).currentBalance()).isEqualByComparingTo("950.00");
    }

    @Test
    void deletingTheTransferRestoresBothBalances() {
        transactionService.delete(transferLeg);
        entityManager.flush();

        assertThat(accountService.get(nubank.getId()).currentBalance()).isEqualByComparingTo("800.00");
        assertThat(accountService.get(carteira.getId()).currentBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void creditCardWithNegativeInitialBalanceGoesFurtherNegative() {
        Account cartao = accountRepository.save(new Account(ana, "Cartão", AccountType.CREDIT_CARD, null, new BigDecimal("-1200.50")));
        expense(cartao, "100.00");
        entityManager.flush();

        assertThat(accountService.get(cartao.getId()).currentBalance()).isEqualByComparingTo("-1300.50");
    }

    @Test
    void anotherUsersMovementsDoNotAffectTheBalances() {
        loginAs(bruno);
        Account contaDoBruno = accountRepository.save(new Account(bruno, "Conta", AccountType.CHECKING, null, BigDecimal.ZERO));
        expense(contaDoBruno, "5000.00");
        entityManager.flush();

        loginAs(ana);

        assertThat(accountService.get(nubank.getId()).currentBalance()).isEqualByComparingTo("650.00");
        assertThat(accountService.list(false)).extracting(AccountResponse::name).containsExactly("Carteira", "Nubank");
    }

    @Test
    void listUsesAFixedNumberOfQueriesNoMatterHowManyAccounts() {
        for (int i = 1; i <= 5; i++) {
            Account account = accountRepository.save(new Account(ana, "Conta " + i, AccountType.CHECKING, null, BigDecimal.ZERO));
            expense(account, "10.00");
        }
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(accountService.list(false)).hasSize(7);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    private UUID expense(Account account, String amount) {
        return transactionService.create(new TransactionRequest(account.getId(), moradia.getId(),
                TransactionType.EXPENSE, TransactionStatus.PAID, new BigDecimal(amount), DAY, "Despesa", null)).id();
    }

    private Category defaultCategory(String name, CategoryType type) {
        return categoryRepository.findVisible(ana.getId(), type, false).stream()
                .filter(category -> category.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void loginAs(User user) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(user.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
