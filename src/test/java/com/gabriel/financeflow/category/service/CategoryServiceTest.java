package com.gabriel.financeflow.category.service;

import com.gabriel.financeflow.category.dto.CategoryResponse;
import com.gabriel.financeflow.category.dto.CreateCategoryRequest;
import com.gabriel.financeflow.category.dto.UpdateCategoryRequest;
import com.gabriel.financeflow.category.entity.CategoryType;
import com.gabriel.financeflow.category.repository.CategoryRepository;
import com.gabriel.financeflow.shared.exception.BusinessException;
import com.gabriel.financeflow.shared.exception.ConflictException;
import com.gabriel.financeflow.shared.exception.ResourceNotFoundException;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** As regras do service direto, sem HTTP. O usuário logado é um JWT no SecurityContextHolder. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User ana;
    private User bruno;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana@email.com", "hash"));
        bruno = userRepository.save(new User("Bruno", "bruno@email.com", "hash"));
        loginAs(ana);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changingADefaultCategoryIsBusinessErrorNotInternalError() {
        UUID alimentacao = defaultId("Alimentação");

        // BusinessException (422), e não o IllegalStateException da entidade (500):
        // prova que o service checa o isDefault() antes de chamar a entidade
        assertThatThrownBy(() -> categoryService.rename(alimentacao, new UpdateCategoryRequest("Comida")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Categorias padrão não podem ser alteradas");
        assertThatThrownBy(() -> categoryService.archive(alimentacao)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> categoryService.unarchive(alimentacao)).isInstanceOf(BusinessException.class);
    }

    @Test
    void aDefaultCategoryCanBeRead() {
        CategoryResponse alimentacao = categoryService.get(defaultId("Alimentação"));

        assertThat(alimentacao.name()).isEqualTo("Alimentação");
        assertThat(alimentacao.custom()).isFalse();
    }

    @Test
    void nameOfADefaultCategoryOfTheSameTypeIsConflict() {
        assertThatThrownBy(() -> categoryService.create(new CreateCategoryRequest("alimentação", CategoryType.EXPENSE)))
                .isInstanceOf(ConflictException.class);

        // Tipo diferente: pode
        CategoryResponse income = categoryService.create(new CreateCategoryRequest("Alimentação", CategoryType.INCOME));
        assertThat(income.custom()).isTrue();
    }

    @Test
    void createTrimsTheName() {
        assertThat(categoryService.create(new CreateCategoryRequest("  Academia  ", CategoryType.EXPENSE)).name())
                .isEqualTo("Academia");
    }

    @Test
    void anotherUsersCategoryIsNotFound() {
        UUID academia = create("Academia");

        loginAs(bruno);

        assertThatThrownBy(() -> categoryService.get(academia)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> categoryService.rename(academia, new UpdateCategoryRequest("X")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> categoryService.archive(academia)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void renameIsPersistedAndKeepsTheType() {
        UUID id = create("Academia");

        categoryService.rename(id, new UpdateCategoryRequest("Esportes"));

        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(id)).get().satisfies(category -> {
            assertThat(category.getName()).isEqualTo("Esportes");
            assertThat(category.getType()).isEqualTo(CategoryType.EXPENSE);
        });
    }

    @Test
    void renameKeepingTheSameNameIsNotAConflict() {
        UUID id = create("Academia");

        assertThat(categoryService.rename(id, new UpdateCategoryRequest("academia")).name()).isEqualTo("academia");
    }

    @Test
    void archivedCategoryCannotBeRenamed() {
        UUID id = create("Academia");
        categoryService.archive(id);

        assertThatThrownBy(() -> categoryService.rename(id, new UpdateCategoryRequest("Outro")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Categoria arquivada não pode ser editada");
    }

    @Test
    void unarchiveIsConflictWhenAnotherActiveCategoryHasTheSameName() {
        UUID old = create("Academia");
        categoryService.archive(old);
        create("Academia");

        assertThatThrownBy(() -> categoryService.unarchive(old)).isInstanceOf(ConflictException.class);
    }

    @Test
    void listFiltersByTypeAndHidesArchived() {
        UUID academia = create("Academia");
        categoryService.create(new CreateCategoryRequest("Bônus", CategoryType.INCOME));
        categoryService.archive(academia);

        assertThat(categoryService.list(CategoryType.INCOME, false))
                .allMatch(category -> category.type() == CategoryType.INCOME)
                .extracting(CategoryResponse::name).contains("Bônus");
        assertThat(categoryService.list(null, false)).extracting(CategoryResponse::name).doesNotContain("Academia");
        assertThat(categoryService.list(null, true)).extracting(CategoryResponse::name).contains("Academia");
    }

    private UUID create(String name) {
        return categoryService.create(new CreateCategoryRequest(name, CategoryType.EXPENSE)).id();
    }

    private UUID defaultId(String name) {
        return categoryService.list(null, false).stream()
                .filter(category -> !category.custom() && category.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
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
