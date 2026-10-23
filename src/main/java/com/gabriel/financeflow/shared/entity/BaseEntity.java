package com.gabriel.financeflow.shared.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@Getter
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        return getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
