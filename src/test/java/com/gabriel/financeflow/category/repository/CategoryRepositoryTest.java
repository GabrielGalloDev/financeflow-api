package com.gabriel.financeflow.category.repository;

import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.category.entity.CategoryType;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryRepositoryTest {

    private static final int DEFAULT_EXPENSES = 9;
    private static final int DEFAULT_INCOMES = 4;
    private static final int DEFAULTS = DEFAULT_EXPENSES + DEFAULT_INCOMES;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User ana;
    private User bruno;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana@email.com", "hash"));
        bruno = userRepository.save(new User("Bruno", "bruno@email.com", "hash"));
    }

    // ---------- seed ----------

    @Test
    void seedCreatesThirteenDefaultCategories() {
        List<Category> visible = categoryRepository.findVisible(ana.getId(), null, false);

        // Se a consulta fizesse JOIN com users, as padrão (user nulo) sumiriam daqui
        assertThat(visible).hasSize(DEFAULTS).allMatch(Category::isDefault);
        assertThat(categoryRepository.findVisible(ana.getId(), CategoryType.EXPENSE, false)).hasSize(DEFAULT_EXPENSES);
        assertThat(categoryRepository.findVisible(ana.getId(), CategoryType.INCOME, false)).hasSize(DEFAULT_INCOMES);
    }

    @Test
    void seedKeepsAccentsFromUtf8() {
        assertThat(categoryRepository.findVisible(ana.getId(), CategoryType.EXPENSE, false))
                .extracting(Category::getName)
                .contains("Alimentação", "Saúde", "Educação", "Contas e serviços");
    }

    // ---------- visibilidade ----------

    @Test
    void userSeesDefaultsAndOwnCategoriesButNotOthers() {
        save(ana, "Academia", CategoryType.EXPENSE);
        save(bruno, "Pets", CategoryType.EXPENSE);

        assertThat(categoryRepository.findVisible(ana.getId(), null, false))
                .hasSize(DEFAULTS + 1)
                .extracting(Category::getName)
                .contains("Academia")
                .doesNotContain("Pets");
    }

    @Test
    void findVisibleByIdRespectsOwnership() {
        Category academia = save(ana, "Academia", CategoryType.EXPENSE);
        Category alimentacao = defaultCategory("Alimentação");

        assertThat(categoryRepository.findVisibleById(academia.getId(), ana.getId())).isPresent();
        assertThat(categoryRepository.findVisibleById(academia.getId(), bruno.getId())).isEmpty();
        // As padrão são de todos
        assertThat(categoryRepository.findVisibleById(alimentacao.getId(), bruno.getId())).isPresent();
    }

    @Test
    void findByIdAndUserIdOnlyFindsOwnCategories() {
        Category academia = save(ana, "Academia", CategoryType.EXPENSE);
        Category alimentacao = defaultCategory("Alimentação");

        assertThat(categoryRepository.findByIdAndUserId(academia.getId(), ana.getId())).isPresent();
        assertThat(categoryRepository.findByIdAndUserId(academia.getId(), bruno.getId())).isEmpty();
        // Uma padrão não é de ninguém
        assertThat(categoryRepository.findByIdAndUserId(alimentacao.getId(), ana.getId())).isEmpty();
    }

    // ---------- filtros e ordem ----------

    @Test
    void typeFilterReturnsOnlyThatType() {
        save(ana, "Bônus", CategoryType.INCOME);
        save(ana, "Academia", CategoryType.EXPENSE);

        assertThat(categoryRepository.findVisible(ana.getId(), CategoryType.INCOME, false))
                .hasSize(DEFAULT_INCOMES + 1)
                .allMatch(category -> category.getType() == CategoryType.INCOME);
    }

    @Test
    void archivedCategoriesOnlyWithIncludeArchived() {
        Category academia = save(ana, "Academia", CategoryType.EXPENSE);
        academia.archive(Instant.now());
        categoryRepository.flush();

        assertThat(categoryRepository.findVisible(ana.getId(), null, false))
                .extracting(Category::getName).doesNotContain("Academia");
        assertThat(categoryRepository.findVisible(ana.getId(), null, true))
                .extracting(Category::getName).contains("Academia");
    }

    @Test
    void expensesComeFirstAndNamesAreOrderedIgnoringCase() {
        save(ana, "viagem", CategoryType.EXPENSE);

        List<Category> visible = categoryRepository.findVisible(ana.getId(), null, false);

        // EXPENSE vem antes de INCOME (ordem alfabética do tipo)
        assertThat(visible.getFirst().getType()).isEqualTo(CategoryType.EXPENSE);
        assertThat(visible.getLast().getType()).isEqualTo(CategoryType.INCOME);

        // "viagem" (minúsculo) fica entre "Transporte" e o fim das despesas, e não depois de tudo
        List<String> expenses = visible.stream()
                .filter(category -> category.getType() == CategoryType.EXPENSE)
                .map(Category::getName)
                .toList();
        assertThat(expenses.indexOf("viagem")).isEqualTo(expenses.indexOf("Transporte") + 1);
    }

    // ---------- checagens de nome ----------

    @Test
    void defaultNameCheckIgnoresCaseAndConsidersType() {
        assertThat(categoryRepository.existsByUserIsNullAndTypeAndNameIgnoreCase(CategoryType.EXPENSE, "ALIMENTAÇÃO")).isTrue();
        assertThat(categoryRepository.existsByUserIsNullAndTypeAndNameIgnoreCase(CategoryType.INCOME, "Alimentação")).isFalse();
    }

    @Test
    void ownNameCheckIgnoresCaseArchivedOthersAndItself() {
        Category academia = save(ana, "Academia", CategoryType.EXPENSE);

        assertThat(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedAtIsNull(
                ana.getId(), CategoryType.EXPENSE, "academia")).isTrue();
        assertThat(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedAtIsNull(
                bruno.getId(), CategoryType.EXPENSE, "Academia")).isFalse();
        assertThat(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedAtIsNullAndIdNot(
                ana.getId(), CategoryType.EXPENSE, "Academia", academia.getId())).isFalse();

        academia.archive(Instant.now());
        categoryRepository.flush();

        assertThat(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedAtIsNull(
                ana.getId(), CategoryType.EXPENSE, "Academia")).isFalse();
    }

    // ---------- as barreiras do banco ----------

    @Test
    void databaseRejectsDuplicateActiveCustomName() {
        save(ana, "Academia", CategoryType.EXPENSE);

        assertThatThrownBy(() -> save(ana, "ACADEMIA", CategoryType.EXPENSE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsArchivingADefaultCategory() {
        // Direto no SQL: a entidade já se recusaria (assertCustom), então o teste vai por baixo dela
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE categories SET archived_at = NOW() WHERE user_id IS NULL AND name = 'Lazer'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void entityRefusesToChangeADefaultCategory() {
        Category lazer = defaultCategory("Lazer");

        assertThatThrownBy(() -> lazer.rename("Diversão")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> lazer.archive(Instant.now())).isInstanceOf(IllegalStateException.class);
    }

    // ---------- helpers ----------

    private Category save(User owner, String name, CategoryType type) {
        return categoryRepository.saveAndFlush(new Category(owner, name, type));
    }

    private Category defaultCategory(String name) {
        return categoryRepository.findVisible(ana.getId(), null, false).stream()
                .filter(category -> category.isDefault() && category.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
