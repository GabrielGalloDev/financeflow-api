package com.gabriel.financeflow.auth.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que senhas e tokens nunca aparecem no toString, que é o que vai para os logs. */
class SensitiveDtoToStringTest {

    @Test
    void registerRequestHidesPassword() {
        String text = new RegisterRequest("Gabriel", "gabriel@email.com", "senhaSecreta123").toString();

        assertThat(text).doesNotContain("senhaSecreta123").contains("gabriel@email.com", "password=***");
    }

    @Test
    void loginRequestHidesPassword() {
        String text = new LoginRequest("gabriel@email.com", "senhaSecreta123").toString();

        assertThat(text).doesNotContain("senhaSecreta123").contains("password=***");
    }

    @Test
    void refreshRequestHidesToken() {
        String text = new RefreshRequest("refresh-token-secreto").toString();

        assertThat(text).doesNotContain("refresh-token-secreto");
    }

    @Test
    void tokenResponseHidesBothTokens() {
        String text = TokenResponse.bearer("access-secreto", "refresh-secreto", 900).toString();

        assertThat(text).doesNotContain("access-secreto", "refresh-secreto").contains("Bearer", "900");
    }
}
