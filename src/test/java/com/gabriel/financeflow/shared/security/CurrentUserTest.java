package com.gabriel.financeflow.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearContext() {
        // O SecurityContextHolder é por thread: limpar evita que um teste vaze para o outro
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsUserIdFromJwtSubject() {
        UUID userId = UUID.randomUUID();
        authenticateWithSubject(userId.toString());

        assertThat(currentUser.id()).isEqualTo(userId);
    }

    @Test
    void throwsWhenNobodyIsAuthenticated() {
        assertThatThrownBy(currentUser::id)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhum usuário autenticado");
    }

    @Test
    void throwsForAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(currentUser::id).isInstanceOf(IllegalStateException.class);
    }

    private void authenticateWithSubject(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
