package com.gabriel.financeflow.user.controller;

import com.gabriel.financeflow.auth.repository.RefreshTokenRepository;
import com.gabriel.financeflow.auth.service.LoginAttemptService;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerTest {

    private static final String ME_URL = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void resetLoginAttempts() {
        // Estado em memória compartilhado entre testes: o @Transactional não o desfaz
        loginAttemptService.clear();
    }

    @Test
    void fullFlowRegisterLoginAndGetMe() throws Exception {
        String token = registerAndLogin("Gabriel", "gabriel@email.com", "senha1234");

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Gabriel"))
                .andExpect(jsonPath("$.email").value("gabriel@email.com"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void eachTokenReturnsItsOwnUser() throws Exception {
        String tokenAna = registerAndLogin("Ana", "ana@email.com", "senha1234");
        String tokenBruno = registerAndLogin("Bruno", "bruno@email.com", "senha1234");

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAna))
                .andExpect(jsonPath("$.email").value("ana@email.com"));
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBruno))
                .andExpect(jsonPath("$.email").value("bruno@email.com"));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void withInventedTokenReturns401() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer token-inventado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenOfDeletedUserReturns404() throws Exception {
        String token = registerAndLogin("Gabriel", "gabriel@email.com", "senha1234");

        // O token continua válido até expirar, mas a conta não existe mais.
        // Os refresh tokens saem primeiro: o Hibernate não deixa apagar um User que ainda está
        // referenciado por entidades carregadas na memória (o CASCADE do banco não alcança essa memória).
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.flush();

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Usuário não encontrado"));
    }

    // ---------- PUT /users/me ----------

    @Test
    void updateReturns200WithNewData() throws Exception {
        String token = registerAndLogin("Gabriel", "gabriel@email.com", "senha1234");

        updateMe(token, "Gabriel Gallo", "novo@email.com", "USD", "America/New_York")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gabriel Gallo"))
                .andExpect(jsonPath("$.email").value("novo@email.com"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));

        // O GET depois confirma que a alteração foi gravada
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.email").value("novo@email.com"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void updateNormalizesEmailNameAndCurrency() throws Exception {
        String token = registerAndLogin("Gabriel", "gabriel@email.com", "senha1234");

        // E-mail sem espaços nas pontas: com eles, o @Email recusaria com 400 antes da normalização
        updateMe(token, "  Gabriel  ", "Novo@Email.COM", "usd", "Asia/Tokyo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gabriel"))
                .andExpect(jsonPath("$.email").value("novo@email.com"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void keepingYourOwnEmailIsNotAConflict() throws Exception {
        String token = registerAndLogin("Gabriel", "gabriel@email.com", "senha1234");

        updateMe(token, "Gabriel", "GABRIEL@email.com", "BRL", "America/Sao_Paulo")
                .andExpect(status().isOk());
    }

    @Test
    void emailOfAnotherUserReturns409() throws Exception {
        registerAndLogin("Ana", "ana@email.com", "senha1234");
        String tokenBruno = registerAndLogin("Bruno", "bruno@email.com", "senha1234");

        updateMe(tokenBruno, "Bruno", "ana@email.com", "BRL", "America/Sao_Paulo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado"));
    }

    @Test
    void invalidCurrencyAndTimezoneReturn400() throws Exception {
        String token = registerAndLogin("Gabriel", "gabriel@email.com", "senha1234");

        updateMe(token, "Gabriel", "gabriel@email.com", "XYZ", "+03:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("currency", "timezone")))
                .andExpect(jsonPath("$.errors[?(@.field == 'currency')].message")
                        .value("Moeda inválida: use um código ISO 4217, como BRL ou USD"));
    }

    @Test
    void emptyBodyReturns400WithFourErrors() throws Exception {
        String token = registerAndLogin("Gabriel", "gabriel@email.com", "senha1234");

        mockMvc.perform(put(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                // Um erro por campo: o @ValidCurrency/@ValidTimezone deixam o null para o @NotBlank
                .andExpect(jsonPath("$.errors", hasSize(4)));
    }

    @Test
    void updateWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put(ME_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "email": "x@email.com", "currency": "BRL", "timezone": "UTC"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateChangesUpdatedAt() throws Exception {
        String token = registerAndLogin("Gabriel", "gabriel@email.com", "senha1234");

        updateMe(token, "Gabriel Gallo", "gabriel@email.com", "BRL", "America/Sao_Paulo")
                .andExpect(status().isOk());

        // O teste nunca faz commit: o flush força o UPDATE (e o @LastModifiedDate) agora
        userRepository.flush();
        User user = userRepository.findByEmail("gabriel@email.com").orElseThrow();

        assertThat(user.getUpdatedAt()).isAfter(user.getCreatedAt());
    }

    @Test
    void loginWorksWithTheNewEmailAndNotWithTheOld() throws Exception {
        String token = registerAndLogin("Gabriel", "velho@email.com", "senha1234");

        updateMe(token, "Gabriel", "novo@email.com", "BRL", "America/Sao_Paulo")
                .andExpect(status().isOk());

        login("novo@email.com", "senha1234").andExpect(status().isOk());
        login("velho@email.com", "senha1234").andExpect(status().isUnauthorized());
    }

    @Test
    void tokenKeepsWorkingAfterEmailChange() throws Exception {
        String token = registerAndLogin("Gabriel", "gabriel@email.com", "senha1234");

        updateMe(token, "Gabriel", "novo@email.com", "BRL", "America/Sao_Paulo")
                .andExpect(status().isOk());

        // O "sub" do JWT é o id, que não muda: o usuário continua logado
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("novo@email.com"));
    }

    // ---------- helpers ----------

    private ResultActions updateMe(String token, String name, String email, String currency, String timezone)
            throws Exception {
        return mockMvc.perform(put(ME_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "email": "%s", "currency": "%s", "timezone": "%s"}
                        """.formatted(name, email, currency, timezone)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    /** Cadastra, faz login e devolve o access token. Útil nas próximas fases também. */
    private String registerAndLogin(String name, String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "email": "%s", "password": "%s"}
                                """.formatted(name, email, password)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.accessToken");
    }
}
