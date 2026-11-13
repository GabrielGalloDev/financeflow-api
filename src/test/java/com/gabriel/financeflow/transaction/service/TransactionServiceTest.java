package com.gabriel.financeflow.transaction.service;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.entity.AccountType;
import com.gabriel.financeflow.account.repository.AccountRepository;
import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.category.entity.CategoryType;
import com.gabriel.financeflow.category.repository.CategoryRepository;
import com.gabriel.financeflow.shared.exception.BusinessException;
import com.gabriel.financeflow.shared.exception.ResourceNotFoundException;
import com.gabriel.financeflow.support.MutableClock;
import com.gabriel.financeflow.transaction.dto.TransactionRequest;
import com.gabriel.financeflow.transaction.dto.TransactionResponse;
import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import com.gabriel.financeflow.transaction.repository.TransactionRepository;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As regras do TransactionService, sem HTTP. O relógio é fixo para o "hoje" não depender
 * da hora em que o teste roda.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionServiceTest {

    /** 14/11 às 02:30 UTC = 13/11 às 23:30 em São Paulo = 14/11 às 11:30 em Tóquio. */
    static final Instant NOW = Instant.parse("2026-11-14T02:30:00Z");
    static final LocalDate DAY_14 = LocalDate.parse("2026-11-14");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    private User ana;
    private User bruno;
    private Account nubank;
    private Account contaDoBruno;
    private Category moradia;
    private Category salario;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana@email.com", "hash"));   // fuso padrão: America/Sao_Paulo
        bruno = userRepository.save(new User("Bruno", "bruno@email.com", "hash"));
        nubank = accountRepository.save(new Account(ana, "Nubank", AccountType.CHECKING, null, new BigDecimal("1000.00")));
        contaDoBruno = accountRepository.save(new Account(bruno, "Conta", AccountType.CHECKING, null, BigDecimal.ZERO));
        moradia = defaultCategory("Moradia", CategoryType.EXPENSE);
        salario = defaultCategory("Salário", CategoryType.INCOME);
        loginAs(ana);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- create ----------

    @Test
    void createWithoutStatusIsPaidAndNormalizes() {
        TransactionResponse created = transactionService.create(new TransactionRequest(
                nubank.getId(), moradia.getId(), TransactionType.EXPENSE, null,
                new BigDecimal("200"), LocalDate.parse("2026-11-10"), "  Aluguel  ", null));

        assertThat(created.status()).isEqualTo(TransactionStatus.PAID);
        assertThat(created.description()).isEqualTo("Aluguel");
        assertThat(created.amount()).isEqualByComparingTo("200.00");
        assertThat(created.amount().scale()).isEqualTo(2);
        assertThat(created.account().name()).isEqualTo("Nubank");
        assertThat(created.category().name()).isEqualTo("Moradia");
    }

    @Test
    void notesAreOptionalAndBlankBecomesNull() {
        // Antes da correção, notes null dava NullPointerException (500)
        assertThat(create(moradia, TransactionType.EXPENSE, null, null).notes()).isNull();
        assertThat(create(moradia, TransactionType.EXPENSE, null, "   ").notes()).isNull();
        assertThat(create(moradia, TransactionType.EXPENSE, null, "  Novembro ").notes()).isEqualTo("Novembro");
    }

    // ---------- conta ----------

    @Test
    void anotherUsersAccountIsNotFound() {
        assertThatThrownBy(() -> transactionService.create(request(contaDoBruno.getId(), moradia, TransactionType.EXPENSE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conta não encontrada");
    }

    @Test
    void archivedAccountIsRejected() {
        nubank.archive(Instant.now());

        assertThatThrownBy(() -> create(moradia, TransactionType.EXPENSE, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Conta arquivada não aceita movimentações");
    }

    // ---------- categoria ----------

    @Test
    void categoryOfTheWrongTypeIsRejected() {
        assertThatThrownBy(() -> create(salario, TransactionType.EXPENSE, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A categoria 'Salário' não é do tipo EXPENSE");
    }

    @Test
    void anotherUsersCustomCategoryIsNotFound() {
        Category petsDoBruno = categoryRepository.save(new Category(bruno, "Pets", CategoryType.EXPENSE));

        assertThatThrownBy(() -> create(petsDoBruno, TransactionType.EXPENSE, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Categoria não encontrada");
    }

    @Test
    void archivedCategoryIsRejected() {
        Category academia = categoryRepository.save(new Category(ana, "Academia", CategoryType.EXPENSE));
        academia.archive(Instant.now());

        assertThatThrownBy(() -> create(academia, TransactionType.EXPENSE, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Categoria arquivada não aceita movimentações");
    }

    // ---------- data e fuso ⭐ ----------

    @Test
    void paidOnDay14IsFutureInSaoPaulo() {
        // Em São Paulo ainda é 13/11 (23:30)
        assertThatThrownBy(() -> createOn(DAY_14, TransactionStatus.PAID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Uma movimentação paga não pode ter data no futuro");
    }

    @Test
    void paidOnDay14IsTodayInTokyo() {
        // Em Tóquio já é 14/11 (11:30)
        ana.updateProfile(ana.getName(), ana.getEmail(), ana.getCurrency(), "Asia/Tokyo");

        assertThat(createOn(DAY_14, TransactionStatus.PAID).date()).isEqualTo(DAY_14);
    }

    @Test
    void pendingCanBeInTheFuture() {
        assertThat(createOn(LocalDate.parse("2026-11-30"), TransactionStatus.PENDING).status())
                .isEqualTo(TransactionStatus.PENDING);
    }

    // ---------- get e isolamento ----------

    @Test
    void getReturnsOwnAndAnotherUserGetsNotFound() {
        UUID id = create(moradia, TransactionType.EXPENSE, null, null).id();

        assertThat(transactionService.get(id).description()).isEqualTo("Aluguel");

        loginAs(bruno);
        assertThatThrownBy(() -> transactionService.get(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Movimentação não encontrada");
    }

    // ---------- update ----------

    @Test
    void updateIsPersisted() {
        UUID id = create(moradia, TransactionType.EXPENSE, null, null).id();

        transactionService.update(id, new TransactionRequest(nubank.getId(), salario.getId(), TransactionType.INCOME,
                TransactionStatus.PAID, new BigDecimal("50.5"), LocalDate.parse("2026-11-12"), "Bônus", "extra"));

        entityManager.flush();
        entityManager.clear();

        assertThat(transactionRepository.findById(id)).get().satisfies(transaction -> {
            assertThat(transaction.getType()).isEqualTo(TransactionType.INCOME);
            assertThat(transaction.getAmount()).isEqualByComparingTo("50.50");
            assertThat(transaction.getDescription()).isEqualTo("Bônus");
            assertThat(transaction.getCategory().getName()).isEqualTo("Salário");
        });
    }

    @Test
    void updateWithoutStatusKeepsTheCurrentOne() {
        UUID id = createOn(LocalDate.parse("2026-11-30"), TransactionStatus.PENDING).id();

        TransactionResponse updated = transactionService.update(id, new TransactionRequest(nubank.getId(), moradia.getId(),
                TransactionType.EXPENSE, null, new BigDecimal("210.00"), LocalDate.parse("2026-11-30"), "Aluguel", null));

        assertThat(updated.status()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void transferCannotBeEdited() {
        UUID leg = transferLegs().getFirst().getId();

        assertThatThrownBy(() -> transactionService.update(leg, request(nubank.getId(), moradia, TransactionType.EXPENSE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Transferências não podem ser editadas: exclua e crie outra");
    }

    @Test
    void transactionInAnArchivedAccountCannotBeEdited() {
        UUID id = create(moradia, TransactionType.EXPENSE, null, null).id();
        Account carteira = accountRepository.save(new Account(ana, "Carteira", AccountType.WALLET, null, BigDecimal.ZERO));
        nubank.archive(Instant.now());

        // Mesmo movendo para uma conta ativa: a atual está arquivada
        assertThatThrownBy(() -> transactionService.update(id, request(carteira.getId(), moradia, TransactionType.EXPENSE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Conta arquivada não aceita movimentações");
    }

    // ---------- delete ----------

    @Test
    void deleteIsLogicalAndASecondDeleteIsNotFound() {
        UUID id = create(moradia, TransactionType.EXPENSE, null, null).id();

        transactionService.delete(id);

        assertThat(transactionRepository.findById(id)).get().satisfies(transaction ->
                assertThat(transaction.getDeletedAt()).isEqualTo(NOW));
        assertThatThrownBy(() -> transactionService.get(id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> transactionService.delete(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletingOneTransferLegDeletesBoth() {
        List<Transaction> legs = transferLegs();

        transactionService.delete(legs.getFirst().getId());

        assertThat(legs).allSatisfy(leg -> assertThat(leg.isDeleted()).isTrue());
        assertThat(transactionRepository.findAllByTransferGroupIdAndDeletedAtIsNull(legs.getFirst().getTransferGroupId()))
                .isEmpty();
    }

    // ---------- pay ----------

    @Test
    void payingBeforeTheDueDateMovesTheDateToToday() {
        UUID id = createOn(LocalDate.parse("2026-11-30"), TransactionStatus.PENDING).id();

        TransactionResponse paid = transactionService.pay(id);

        // Em São Paulo, "hoje" é 13/11 (23:30)
        assertThat(paid.status()).isEqualTo(TransactionStatus.PAID);
        assertThat(paid.date()).isEqualTo(LocalDate.parse("2026-11-13"));
    }

    @Test
    void payingLateKeepsTheDueDate() {
        UUID id = createOn(LocalDate.parse("2026-11-05"), TransactionStatus.PENDING).id();

        assertThat(transactionService.pay(id).date()).isEqualTo(LocalDate.parse("2026-11-05"));
    }

    @Test
    void payIsIdempotent() {
        UUID id = create(moradia, TransactionType.EXPENSE, TransactionStatus.PAID, null).id();

        assertThat(transactionService.pay(id).status()).isEqualTo(TransactionStatus.PAID);
        assertThat(transactionService.pay(id).date()).isEqualTo(LocalDate.parse("2026-11-10"));
    }

    // ---------- isolamento ----------

    @Test
    void anotherUserCannotUpdateDeleteOrPay() {
        UUID id = createOn(LocalDate.parse("2026-11-30"), TransactionStatus.PENDING).id();

        loginAs(bruno);

        assertThatThrownBy(() -> transactionService.update(id, request(contaDoBruno.getId(), moradia, TransactionType.EXPENSE)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> transactionService.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> transactionService.pay(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- helpers ----------

    /** Ainda não existe o TransferService (5.5): as pernas são criadas direto pelo repository. */
    private List<Transaction> transferLegs() {
        Account carteira = accountRepository.save(new Account(ana, "Carteira", AccountType.WALLET, null, BigDecimal.ZERO));
        UUID group = UUID.randomUUID();
        Transaction out = transactionRepository.save(Transaction.transferLeg(ana, nubank, TransactionType.TRANSFER_OUT,
                new BigDecimal("150.00"), LocalDate.parse("2026-11-10"), "Saque", null, group));
        Transaction in = transactionRepository.save(Transaction.transferLeg(ana, carteira, TransactionType.TRANSFER_IN,
                new BigDecimal("150.00"), LocalDate.parse("2026-11-10"), "Saque", null, group));
        transactionRepository.flush();
        return List.of(out, in);
    }

    private TransactionResponse create(Category category, TransactionType type, TransactionStatus status, String notes) {
        return transactionService.create(new TransactionRequest(nubank.getId(), category.getId(), type, status,
                new BigDecimal("200.00"), LocalDate.parse("2026-11-10"), "Aluguel", notes));
    }

    private TransactionResponse createOn(LocalDate date, TransactionStatus status) {
        return transactionService.create(new TransactionRequest(nubank.getId(), moradia.getId(),
                TransactionType.EXPENSE, status, new BigDecimal("200.00"), date, "Aluguel", null));
    }

    private TransactionRequest request(UUID accountId, Category category, TransactionType type) {
        return new TransactionRequest(accountId, category.getId(), type, null,
                new BigDecimal("200.00"), LocalDate.parse("2026-11-10"), "Aluguel", null);
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
