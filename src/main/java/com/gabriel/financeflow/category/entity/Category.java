package com.gabriel.financeflow.category.entity;

import com.gabriel.financeflow.shared.entity.BaseEntity;
import com.gabriel.financeflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CategoryType type;

    private Instant archivedAt;

    public Category(User user, String name, CategoryType type) {
        this.user = user;
        this.name = name;
        this.type = type;
    }

    public void rename(String name) {
        assertCustom();
        this.name = name;
    }

    public void archive(Instant now) {
        assertCustom();
        if (this.archivedAt == null) {
            this.archivedAt = now;
        }
    }

    public void unarchive() {
        assertCustom();
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isDefault() {
        return user == null;
    }

    private void assertCustom() {
        if (isDefault()) {
            throw new IllegalStateException("Categorias padrão não podem ser alteradas");
        }
    }
}
