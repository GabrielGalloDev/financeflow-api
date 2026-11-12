package com.gabriel.financeflow.category.repository;

import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.category.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    @Query("""
            SELECT c FROM Category c
            WHERE (c.user IS NULL OR c.user.id = :userId)
              AND (:type IS NULL OR c.type = :type)
              AND (:includeArchived = true OR c.archivedAt IS NULL)
            ORDER BY c.type, LOWER(c.name)
            """)
    List<Category> findVisible(@Param("userId") UUID userId,
                               @Param("type") CategoryType type,
                               @Param("includeArchived") boolean includeArchived);

    @Query("""
            SELECT c FROM Category c
            WHERE c.id = :id
              AND (c.user IS NULL OR c.user.id = :userId)
            """)
    Optional<Category> findVisibleById(@Param("id") UUID id,
                                       @Param("userId") UUID userId);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIsNullAndTypeAndNameIgnoreCase(CategoryType type, String name);

    boolean existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedAtIsNull(UUID userId, CategoryType type, String name);

    boolean existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedAtIsNullAndIdNot(
            UUID userId, CategoryType type, String name, UUID id);
}
