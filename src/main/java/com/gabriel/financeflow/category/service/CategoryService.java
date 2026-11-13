package com.gabriel.financeflow.category.service;

import com.gabriel.financeflow.category.dto.CategoryResponse;
import com.gabriel.financeflow.category.dto.CreateCategoryRequest;
import com.gabriel.financeflow.category.dto.UpdateCategoryRequest;
import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.category.entity.CategoryType;
import com.gabriel.financeflow.category.repository.CategoryRepository;
import com.gabriel.financeflow.shared.exception.BusinessException;
import com.gabriel.financeflow.shared.exception.ConflictException;
import com.gabriel.financeflow.shared.exception.ResourceNotFoundException;
import com.gabriel.financeflow.shared.security.CurrentUser;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private static final String NOT_FOUND = "Categoria não encontrada";
    private static final String DUPLICATE_NAME = "Já existe uma categoria com este nome e tipo";

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository,
                           CurrentUser currentUser, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(CategoryType type, boolean includeArchived) {
        return categoryRepository.findVisible(currentUser.id(), type, includeArchived).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        Category category = categoryRepository.findVisibleById(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));

        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = normalizeName(request.name());

        assertNameAvailable(request.type(), name, null);

        User owner = userRepository.getReferenceById(currentUser.id());

        return CategoryResponse.from(categoryRepository.save(new Category(owner, name, request.type())));
    }

    @Transactional
    public CategoryResponse rename(UUID id, UpdateCategoryRequest request) {
        Category category = findOwnedCategory(id);

        if (category.isArchived()) {
            throw new BusinessException("Categoria arquivada não pode ser editada");
        }

        String name = normalizeName(request.name());

        assertNameAvailable(category.getType(), name, category.getId());

        category.rename(name);

        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse archive(UUID id) {
        Category category = findOwnedCategory(id);
        category.archive(Instant.now(clock));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse unarchive(UUID id) {
        Category category = findOwnedCategory(id);

        if (category.isArchived()) {
            assertNameAvailable(category.getType(), category.getName(), category.getId());
        }

        category.unarchive();
        return CategoryResponse.from(category);
    }

    private Category findOwnedCategory(UUID id) {
        Category category = categoryRepository.findVisibleById(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));

        if (category.isDefault()) {
            throw new BusinessException("Categorias padrão não podem ser alteradas");
        }

        return category;
    }

    private void assertNameAvailable(CategoryType type, String name, UUID ignoreId) {
        UUID userId = currentUser.id();

        if (categoryRepository.existsByUserIsNullAndTypeAndNameIgnoreCase(type, name)) {
            throw new ConflictException(DUPLICATE_NAME);
        }

        boolean ownDuplicate = ignoreId == null
                ? categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedAtIsNull(userId, type, name)
                : categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedAtIsNullAndIdNot(
                        userId, type, name, ignoreId);

        if (ownDuplicate) {
            throw new ConflictException(DUPLICATE_NAME);
        }
    }

    private String normalizeName(String name) {
        return name.trim();
    }
}
