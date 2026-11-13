package com.gabriel.financeflow.transaction.service;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.entity.AccountType;
import com.gabriel.financeflow.account.repository.AccountRepository;
import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.category.entity.CategoryType;
import com.gabriel.financeflow.category.repository.CategoryRepository;
import com.gabriel.financeflow.shared.dto.PageResponse;
import com.gabriel.financeflow.shared.exception.InvalidRequestException;
import com.gabriel.financeflow.transaction.dto.TransactionFilter;
import com.gabriel.financeflow.transaction.dto.TransactionResponse;
import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import com.gabriel.financeflow.transaction.repository.TransactionRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class TransactionSearchTest {

    private static final Pageable BY_DATE_DESC = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "date"));

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

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
    private Account nubank;
    private Account carteira;
    private Account contaDoBruno;
    private Category moradia;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana@email.com", "hash"));
        User bruno = userRepository.save(new User("Bruno", "bruno@email.com", "hash"));
        nubank = accountRepository.save(new Account(ana, "Nubank", AccountType.CHECKING, null, BigDecimal.ZERO));
        carteira = accountRepository.save(new Account(ana, "Carteira", AccountType.WALLET, null, BigDecimal.ZERO));
        contaDoBruno = accountRepository.save(new Account(bruno, "Conta", AccountType.CHECKING, null, BigDecimal.ZERO));

        moradia = defaultCategory("Moradia", CategoryType.EXPENSE);
        Category alimentacao = defaultCategory("Alimentação", CategoryType.EXPENSE);
        Category contas = defaultCategory("Contas e serviços", CategoryType.EXPENSE);
        Category salario = defaultCategory("Salário", CategoryType.INCOME);

        save(ana, nubank, salario, TransactionType.INCOME, "5000.00", "2026-11-05", TransactionStatus.PAID, "Salário");
        save(ana, nubank, moradia, TransactionType.EXPENSE, "1500.00", "2026-11-10", TransactionStatus.PAID, "Aluguel");
        save(ana, carteira, alimentacao, TransactionType.EXPENSE, "300.00", "2026-11-12", TransactionStatus.PAID, "Mercado");
        save(ana, nubank, contas, TransactionType.EXPENSE, "200.00", "2026-11-25", TransactionStatus.PENDING, "Luz");
        save(ana, nubank, moradia, TransactionType.EXPENSE, "999.00", "2026-11-11", TransactionStatus.PAID, "Excluída")
                .softDelete(Instant.now());
        save(bruno, contaDoBruno, moradia, TransactionType.EXPENSE, "800.00", "2026-11-10", TransactionStatus.PAID, "Do Bruno");

        entityManager.flush();
        entityManager.clear();
        loginAs(ana);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void withoutFiltersReturnsOnlyOwnAndNotDeleted() {
        assertThat(descriptions(TransactionFilter.empty(), BY_DATE_DESC))
                .containsExactly("Luz", "Mercado", "Aluguel", "Salário");
    }

    @Test
    void periodIncludesBothEnds() {
        assertThat(descriptions(filter().from("2026-11-10").to("2026-11-12").build(), BY_DATE_DESC))
                .containsExactly("Mercado", "Aluguel");
    }

    @Test
    void filtersByAccount() {
        assertThat(descriptions(filter().account(carteira.getId()).build(), BY_DATE_DESC))
                .containsExactly("Mercado");
    }

    @Test
    void filtersByCategory() {
        assertThat(descriptions(filter().category(moradia.getId()).build(), BY_DATE_DESC))
                .containsExactly("Aluguel");
    }

    @Test
    void filtersByType() {
        assertThat(descriptions(filter().type(TransactionType.INCOME).build(), BY_DATE_DESC))
                .containsExactly("Salário");
    }

    @Test
    void filtersByStatus() {
        assertThat(descriptions(filter().status(TransactionStatus.PENDING).build(), BY_DATE_DESC))
                .containsExactly("Luz");
    }

    @Test
    void amountRangeIncludesBothEnds() {
        assertThat(descriptions(filter().min("300").max("1500").build(), BY_DATE_DESC))
                .containsExactly("Mercado", "Aluguel");
    }

    @Test
    void combinedFilters() {
        TransactionFilter combined = filter()
                .type(TransactionType.EXPENSE)
                .account(nubank.getId())
                .status(TransactionStatus.PAID)
                .build();

        assertThat(descriptions(combined, BY_DATE_DESC)).containsExactly("Aluguel");
    }

    @Test
    void accountOfAnotherUserReturnsNothing() {
        assertThat(descriptions(filter().account(contaDoBruno.getId()).build(), BY_DATE_DESC)).isEmpty();
    }

    @Test
    void paginationSplitsWithoutRepeating() {
        PageResponse<TransactionResponse> first = transactionService.search(TransactionFilter.empty(),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "date")));
        PageResponse<TransactionResponse> second = transactionService.search(TransactionFilter.empty(),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "date")));

        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.content()).extracting(TransactionResponse::description).containsExactly("Luz", "Mercado");
        assertThat(second.content()).extracting(TransactionResponse::description).containsExactly("Aluguel", "Salário");
    }

    @Test
    void sortsByAmountAscending() {
        assertThat(descriptions(TransactionFilter.empty(), PageRequest.of(0, 20, Sort.by("amount"))))
                .containsExactly("Luz", "Mercado", "Aluguel", "Salário");
    }

    @Test
    void sameDateIsTieBrokenByCreationSoPagesAreStable() {
        LocalDate sameDay = LocalDate.parse("2026-11-01");
        Category alimentacao = defaultCategory("Alimentação", CategoryType.EXPENSE);
        for (int i = 1; i <= 6; i++) {
            transactionRepository.save(Transaction.incomeOrExpense(userRepository.getReferenceById(ana.getId()),
                    accountRepository.getReferenceById(nubank.getId()), alimentacao, TransactionType.EXPENSE,
                    TransactionStatus.PAID, new BigDecimal("10.00"), sameDay, "Café " + i, null));
        }
        entityManager.flush();
        entityManager.clear();

        TransactionFilter onlySameDay = filter().from("2026-11-01").to("2026-11-01").build();
        Sort byDate = Sort.by(Sort.Direction.DESC, "date");

        assertThat(transactionService.search(onlySameDay, PageRequest.of(0, 3, byDate)).content())
                .extracting(TransactionResponse::description)
                .doesNotContainAnyElementsOf(transactionService.search(onlySameDay, PageRequest.of(1, 3, byDate)).content()
                        .stream().map(TransactionResponse::description).toList());
    }

    @Test
    void sortingByAFieldOutsideTheAllowedListIsRejected() {
        assertThatThrownBy(() -> transactionService.search(TransactionFilter.empty(),
                PageRequest.of(0, 20, Sort.by("user.passwordHash"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Não é possível ordenar por 'user.passwordHash'. Use: date, amount ou createdAt");
    }

    @Test
    void transfersWithoutCategoryAreNotLostInTheJoin() {
        UUID group = UUID.randomUUID();
        transactionRepository.save(Transaction.transferLeg(userRepository.getReferenceById(ana.getId()),
                accountRepository.getReferenceById(nubank.getId()), TransactionType.TRANSFER_OUT,
                new BigDecimal("50.00"), LocalDate.parse("2026-11-20"), "Saque", null, group));
        entityManager.flush();
        entityManager.clear();

        assertThat(descriptions(TransactionFilter.empty(), BY_DATE_DESC)).contains("Saque");
    }

    @Test
    void aPageUsesAFixedNumberOfQueriesNoMatterHowManyRows() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        transactionService.search(TransactionFilter.empty(), BY_DATE_DESC);

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    private java.util.List<String> descriptions(TransactionFilter filter, Pageable pageable) {
        return transactionService.search(filter, pageable).content().stream()
                .map(TransactionResponse::description)
                .toList();
    }

    private Transaction save(User owner, Account account, Category category, TransactionType type, String amount,
                             String date, TransactionStatus status, String description) {
        return transactionRepository.save(Transaction.incomeOrExpense(owner, account, category, type, status,
                new BigDecimal(amount), LocalDate.parse(date), description, null));
    }

    private Category defaultCategory(String name, CategoryType type) {
        return categoryRepository.findVisible(ana.getId(), type, false).stream()
                .filter(category -> category.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private FilterBuilder filter() {
        return new FilterBuilder();
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

    private static class FilterBuilder {
        private LocalDate from;
        private LocalDate to;
        private UUID accountId;
        private UUID categoryId;
        private TransactionType type;
        private TransactionStatus status;
        private BigDecimal min;
        private BigDecimal max;

        FilterBuilder from(String date) {
            this.from = LocalDate.parse(date);
            return this;
        }

        FilterBuilder to(String date) {
            this.to = LocalDate.parse(date);
            return this;
        }

        FilterBuilder account(UUID id) {
            this.accountId = id;
            return this;
        }

        FilterBuilder category(UUID id) {
            this.categoryId = id;
            return this;
        }

        FilterBuilder type(TransactionType type) {
            this.type = type;
            return this;
        }

        FilterBuilder status(TransactionStatus status) {
            this.status = status;
            return this;
        }

        FilterBuilder min(String amount) {
            this.min = new BigDecimal(amount);
            return this;
        }

        FilterBuilder max(String amount) {
            this.max = new BigDecimal(amount);
            return this;
        }

        TransactionFilter build() {
            return new TransactionFilter(from, to, accountId, categoryId, type, status, min, max);
        }
    }
}
