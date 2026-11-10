package com.gabriel.financeflow.auth.controller;

import com.gabriel.financeflow.auth.entity.RefreshToken;
import com.gabriel.financeflow.auth.repository.RefreshTokenRepository;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    // ---------- cadastro válido ----------

    @Test
    void registerReturns201WithUserData() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Gabriel"))
                .andExpect(jsonPath("$.email").value("gabriel@email.com"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void responseNeverContainsPassword() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234")
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail("gabriel@email.com").orElseThrow();

        assertThat(saved.getPasswordHash()).isNotEqualTo("senha1234").startsWith("$2");
        assertThat(passwordEncoder.matches("senha1234", saved.getPasswordHash())).isTrue();
    }

    @Test
    void emailIsSavedInLowercaseAndNameIsTrimmed() throws Exception {
        register("  Gabriel  ", "Gabriel@Email.COM", "senha1234")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gabriel"))
                .andExpect(jsonPath("$.email").value("gabriel@email.com"));

        assertThat(userRepository.existsByEmail("gabriel@email.com")).isTrue();
    }

    // ---------- e-mail duplicado ----------

    @Test
    void duplicateEmailReturns409() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234").andExpect(status().isCreated());

        register("Outro", "gabriel@email.com", "outrasenha")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado"));
    }

    @Test
    void duplicateEmailWithDifferentCaseReturns409() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234").andExpect(status().isCreated());

        register("Gabriel", "GABRIEL@Email.com", "senha1234")
                .andExpect(status().isConflict());
    }

    // ---------- validação ----------

    @Test
    void invalidFieldsReturn400WithAllErrors() throws Exception {
        register("", "abc", "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos"))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "email", "password")));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void passwordShorterThan8Returns400() throws Exception {
        register("Gabriel", "gabriel@email.com", "1234567")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void passwordLongerThan72Returns400() throws Exception {
        register("Gabriel", "gabriel@email.com", "a".repeat(73))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void passwordWithExactly8And72CharactersIsAccepted() throws Exception {
        register("Gabriel", "oito@email.com", "a".repeat(8)).andExpect(status().isCreated());
        register("Gabriel", "setentaedois@email.com", "a".repeat(72)).andExpect(status().isCreated());
    }

    @Test
    void missingBodyFieldsReturn400() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    // ---------- login ----------

    @Test
    void loginReturns200WithBearerToken() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234").andExpect(status().isCreated());

        login("gabriel@email.com", "senha1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void loginAcceptsEmailWithDifferentCase() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234").andExpect(status().isCreated());

        login("  GABRIEL@Email.com ", "senha1234")
                .andExpect(status().isOk());
    }

    @Test
    void tokenSubjectIsTheUserIdAndExpiresIn15Minutes() throws Exception {
        String registerBody = register("Gabriel", "gabriel@email.com", "senha1234")
                .andReturn().getResponse().getContentAsString();
        String userId = JsonPath.read(registerBody, "$.id");

        String loginBody = login("gabriel@email.com", "senha1234")
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");

        Jwt jwt = jwtDecoder.decode(accessToken);

        assertThat(jwt.getSubject()).isEqualTo(userId);
        // getIssuer() tenta converter o "iss" em URL: como o nosso é um nome simples, lemos como texto
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("financeflow");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234").andExpect(status().isCreated());

        login("gabriel@email.com", "senhaErrada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));
    }

    @Test
    void unknownEmailReturns401WithSameMessage() throws Exception {
        login("naoexiste@email.com", "senha1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));
    }

    @Test
    void loginWithBlankFieldsReturns400() throws Exception {
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "password")));
    }

    // ---------- refresh token ----------

    @Test
    void databaseStoresOnlyTheHashOfTheRefreshToken() throws Exception {
        String refreshToken = registerAndLoginRefreshToken();

        List<RefreshToken> tokens = refreshTokenRepository.findAll();

        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getTokenHash())
                .hasSize(64)
                .isNotEqualTo(refreshToken);
        assertThat(refreshToken).doesNotContain("+", "/", "=");
    }

    @Test
    void refreshReturnsNewTokenPair() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234").andExpect(status().isCreated());
        String loginBody = login("gabriel@email.com", "senha1234").andReturn().getResponse().getContentAsString();
        String oldAccess = JsonPath.read(loginBody, "$.accessToken");
        String oldRefresh = JsonPath.read(loginBody, "$.refreshToken");

        String refreshBody = refresh(oldRefresh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(refreshBody, "$.refreshToken")).isNotEqualTo(oldRefresh);
        assertThat(JsonPath.<String>read(refreshBody, "$.accessToken")).isNotBlank();
        assertThat(oldAccess).isNotBlank();
    }

    @Test
    void reusingARefreshTokenReturns401() throws Exception {
        String refreshToken = registerAndLoginRefreshToken();

        refresh(refreshToken).andExpect(status().isOk());

        // Rotação: o token já foi consumido no refresh anterior
        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token inválido ou expirado"));
    }

    @Test
    void newRefreshTokenFromRotationWorks() throws Exception {
        String first = registerAndLoginRefreshToken();

        String second = JsonPath.read(
                refresh(first).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        refresh(second).andExpect(status().isOk());
    }

    @Test
    void logoutReturns204AndRevokesTheRefreshToken() throws Exception {
        String refreshToken = registerAndLoginRefreshToken();

        logout(refreshToken).andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithUnknownTokenStillReturns204() throws Exception {
        logout("token-que-nao-existe").andExpect(status().isNoContent());
    }

    @Test
    void refreshWithInventedTokenReturns401() throws Exception {
        refresh("token-inventado")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Refresh token inválido ou expirado"));
    }

    @Test
    void expiredRefreshTokenReturns401() throws Exception {
        String refreshToken = registerAndLoginRefreshToken();
        refreshTokenRepository.flush();

        // "Envelhece" o token direto no banco (desfeito no fim do teste pelo @Transactional)
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = NOW() - INTERVAL '1 day'");
        entityManager.clear();

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithBlankTokenReturns400() throws Exception {
        refresh("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));
    }

    @Test
    void deletingUserDeletesItsRefreshTokens() throws Exception {
        registerAndLoginRefreshToken();
        refreshTokenRepository.flush();

        // Direto no SQL, para provar o ON DELETE CASCADE da migration V2
        jdbcTemplate.update("DELETE FROM users");

        Integer remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class);
        assertThat(remaining).isZero();
    }

    // ---------- helpers ----------

    private String registerAndLoginRefreshToken() throws Exception {
        register("Gabriel", "gabriel@email.com", "senha1234").andExpect(status().isCreated());
        String body = login("gabriel@email.com", "senha1234")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.refreshToken");
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }

    private ResultActions login(String email, String password) throws Exception {
        String json = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);

        return mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions register(String name, String email, String password) throws Exception {
        String json = """
                {"name": "%s", "email": "%s", "password": "%s"}
                """.formatted(name, email, password);

        return mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
