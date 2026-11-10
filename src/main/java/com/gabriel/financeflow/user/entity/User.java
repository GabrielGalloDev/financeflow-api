package com.gabriel.financeflow.user.entity;

import com.gabriel.financeflow.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
    public static final String DEFAULT_CURRENCY = "BRL";
    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 50)
    private String timezone;

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.currency = DEFAULT_CURRENCY;
        this.timezone = DEFAULT_TIMEZONE;
    }

    public void updateProfile(String name, String email, String currency, String timezone) {
        this.name = name;
        this.email = email;
        this.currency = currency;
        this.timezone = timezone;
    }
}
