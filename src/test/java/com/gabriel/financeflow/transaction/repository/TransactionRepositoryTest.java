package com.gabriel.financeflow.transaction.repository;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.entity.AccountType;
import com.gabriel.financeflow.account.repository.AccountRepository;
import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.category.entity.CategoryType;
import com.gabriel.financeflow.category.repository.CategoryRepository;
import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionRepositoryTest {

    private static final LocalDate DAY = LocalDate.parse("2026-11-10");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private User ana;
    private User bruno;
    private Account nubank;
    private Account carteira;
    private Account contaDoBruno;
    private Category moradia;
    private Category freelance;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana@email.com", "hash"));
        bruno = userRepository.save(new User("Bruno", "bruno@email.com", "hash"));
        nubank = accountRepository.save(new Account(ana, "Nubank", AccountType.CHECKING, null, new BigDecimal("1000.00")));
        carteira = accountRepository.save(new Account(ana, "Carteira", AccountType.WALLET, null, BigDecimal.ZERO));
        contaDoBruno = accountRepository.save(new Account(bruno, "Conta", AccountType.CHECKING, null, BigDecimal.ZERO));
        moradia = defaultCategory("Moradia", CategoryType.EXPENSE);
        freelance = defaultCategory("Freelance", CategoryType.INCOME);
    }

    // ---------- mapeamento ----------

    @Test
    void savesAndReadsBackAllFields() {
        Transaction saved = transactionRepository.saveAndFlush(Transaction.incomeOrExpense(
                ana, nubank, moradia, TransactionType.EXPENSE, TransactionStatus.PAID,
                new BigDecimal("200.00"), DAY, "Aluguel", "Novembro"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PAID);
        assertThat(saved.getAmount()).isEqualByComparingTo("200.00");
        assertThat(saved.getDate()).isEqualTo(DAY);
        assertThat(saved.getTransferGroupId()).isNull();
        assertThat(saved.isDeleted()).isFalse();
    }

    // ---------- guarda ----------

    @Test
    void guardFindsOnlyOwnAndNotDeleted() {
        Transaction aluguel = expense("200.00", TransactionStatus.PAID);

        assertThat(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(aluguel.getId(), ana.getId())).isPresent();
        assertThat(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(aluguel.getId(), bruno.getId())).isEmpty();

        aluguel.softDelete(Instant.now());
        transactionRepository.flush();

        assertThat(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(aluguel.getId(), ana.getId())).isEmpty();
    }

    // ---------- transferência ----------

    @Test
    void transferLegsAreFoundByGroup() {
        UUID group = transfer("150.00");

        assertThat(transactionRepository.findAllByTransferGroupIdAndDeletedAtIsNull(group))
                .hasSize(2)
                .extracting(Transaction::getType)
                .containsExactlyInAnyOrder(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN);
    }

    // ---------- saldo ⭐ ----------

    @Test
    void movementsCountOnlyPaidAndNotDeleted() {
        Transaction aluguel = expense("200.00", TransactionStatus.PAID);
        transfer("150.00");
        Transaction freela = income("300.00", TransactionStatus.PENDING);

        // 1000 de saldo inicial é da Account; aqui só as movimentações: -200 - 150 (a pendente não entra)
        assertThat(transactionRepository.movementsOf(nubank.getId())).isEqualByComparingTo("-350.00");
        assertThat(transactionRepository.movementsOf(carteira.getId())).isEqualByComparingTo("150.00");

        aluguel.softDelete(Instant.now());
        transactionRepository.flush();
        assertThat(transactionRepository.movementsOf(nubank.getId())).isEqualByComparingTo("-150.00");

        freela.markAsPaid();
        transactionRepository.flush();
        assertThat(transactionRepository.movementsOf(nubank.getId())).isEqualByComparingTo("150.00");
    }

    @Test
    void accountWithoutMovementsIsZeroNotNull() {
        // Sem o COALESCE, o SUM devolveria null aqui
        assertThat(transactionRepository.movementsOf(carteira.getId()))
                .isNotNull()
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void movementsDoNotLeakBetweenAccounts() {
        expense("200.00", TransactionStatus.PAID);

        assertThat(transactionRepository.movementsOf(carteira.getId())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.movementsOf(contaDoBruno.getId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------- barreiras do banco ----------

    @Test
    void databaseRejectsTransactionOfOneUserInAnotherUsersAccount() {
        // A FK composta (account_id, user_id) da V5: a Ana não pode lançar na conta do Bruno
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(Transaction.incomeOrExpense(
                ana, contaDoBruno, moradia, TransactionType.EXPENSE, TransactionStatus.PAID,
                new BigDecimal("10.00"), DAY, "Invasão", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsZeroAmount() {
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(Transaction.incomeOrExpense(
                ana, nubank, moradia, TransactionType.EXPENSE, TransactionStatus.PAID,
                BigDecimal.ZERO, DAY, "Zero", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- entidade ----------

    @Test
    void factoriesRefuseTheWrongKindOfType() {
        assertThatThrownBy(() -> Transaction.incomeOrExpense(ana, nubank, null, TransactionType.TRANSFER_IN,
                TransactionStatus.PAID, BigDecimal.TEN, DAY, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Transaction.transferLeg(ana, nubank, TransactionType.EXPENSE,
                BigDecimal.TEN, DAY, "x", null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transferLegIsAlwaysPaidAndCannotBeEdited() {
        UUID group = transfer("150.00");
        Transaction leg = transactionRepository.findAllByTransferGroupIdAndDeletedAtIsNull(group).getFirst();

        assertThat(leg.getStatus()).isEqualTo(TransactionStatus.PAID);
        assertThat(leg.getCategory()).isNull();
        assertThatThrownBy(() -> leg.update(nubank, moradia, TransactionType.EXPENSE, TransactionStatus.PAID,
                BigDecimal.TEN, DAY, "x", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void softDeleteIsIdempotentAndSignedAmountHasTheRightSign() {
        Transaction aluguel = expense("200.00", TransactionStatus.PAID);
        Instant first = Instant.parse("2026-11-10T10:00:00Z");

        aluguel.softDelete(first);
        aluguel.softDelete(Instant.parse("2026-12-01T10:00:00Z"));

        assertThat(aluguel.getDeletedAt()).isEqualTo(first);
        assertThat(aluguel.signedAmount()).isEqualByComparingTo("-200.00");
        assertThat(income("300.00", TransactionStatus.PAID).signedAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void typeMatchesOnlyTheSameCategoryType() {
        assertThat(TransactionType.EXPENSE.matches(CategoryType.EXPENSE)).isTrue();
        assertThat(TransactionType.EXPENSE.matches(CategoryType.INCOME)).isFalse();
        assertThat(TransactionType.INCOME.matches(CategoryType.INCOME)).isTrue();
        assertThat(TransactionType.TRANSFER_IN.matches(CategoryType.INCOME)).isFalse();
    }

    // ---------- helpers ----------

    private Transaction expense(String amount, TransactionStatus status) {
        return transactionRepository.saveAndFlush(Transaction.incomeOrExpense(
                ana, nubank, moradia, TransactionType.EXPENSE, status, new BigDecimal(amount), DAY, "Aluguel", null));
    }

    private Transaction income(String amount, TransactionStatus status) {
        return transactionRepository.saveAndFlush(Transaction.incomeOrExpense(
                ana, nubank, freelance, TransactionType.INCOME, status, new BigDecimal(amount), DAY, "Freela", null));
    }

    /** Nubank → Carteira. */
    private UUID transfer(String amount) {
        UUID group = UUID.randomUUID();
        transactionRepository.save(Transaction.transferLeg(
                ana, nubank, TransactionType.TRANSFER_OUT, new BigDecimal(amount), DAY, "Saque", null, group));
        transactionRepository.save(Transaction.transferLeg(
                ana, carteira, TransactionType.TRANSFER_IN, new BigDecimal(amount), DAY, "Saque", null, group));
        transactionRepository.flush();
        return group;
    }

    private Category defaultCategory(String name, CategoryType type) {
        return categoryRepository.findVisible(ana.getId(), type, false).stream()
                .filter(category -> category.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
