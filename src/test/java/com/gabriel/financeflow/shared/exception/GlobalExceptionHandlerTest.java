package com.gabriel.financeflow.shared.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void resourceNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/fake/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Conta não encontrada"));
    }

    @Test
    void businessExceptionReturns422() throws Exception {
        mockMvc.perform(get("/fake/business"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Conta arquivada não aceita movimentações"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/fake/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Erro interno inesperado"));
    }

    @Test
    void conflictExceptionReturns409WithItsMessage() throws Exception {
        mockMvc.perform(get("/fake/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado"));
    }

    @Test
    void invalidCredentialsReturns401WithItsMessage() throws Exception {
        mockMvc.perform(get("/fake/invalid-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));
    }

    @Test
    void invalidRefreshTokenReturns401WithItsMessage() throws Exception {
        mockMvc.perform(get("/fake/invalid-refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Refresh token inválido ou expirado"));
    }

    @Test
    void tooManyRequestsReturns429WithRetryAfterHeader() throws Exception {
        mockMvc.perform(get("/fake/too-many-requests"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Muitas tentativas de login. Tente novamente mais tarde."));
    }

    @Test
    void accessDeniedReturns403InsteadOf500() throws Exception {
        mockMvc.perform(get("/fake/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Acesso negado"));
    }

    @Test
    void malformedJsonReturns400WithoutInternalDetails() throws Exception {
        mockMvc.perform(post("/fake/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value("Corpo da requisição inválido: confira o formato do JSON e os valores dos campos"));
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/fake/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "invalid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "email")));
    }

    @Test
    void invalidUuidInPathReturns400() throws Exception {
        mockMvc.perform(get("/fake/items/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                // O nome do parâmetro aparece; o nome da classe Java (java.util.UUID) não
                .andExpect(jsonPath("$.detail").value("Parâmetro 'id' com valor inválido"));
    }

    @RestController
    static class FakeController {

        @GetMapping("/fake/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Conta não encontrada");
        }

        @GetMapping("/fake/business")
        void business() {
            throw new BusinessException("Conta arquivada não aceita movimentações");
        }

        @GetMapping("/fake/unexpected")
        void unexpected() {
            throw new IllegalStateException("SELECT * FROM users -- detalhe interno");
        }

        @GetMapping("/fake/conflict")
        void conflict() {
            throw new ConflictException("E-mail já cadastrado");
        }

        @GetMapping("/fake/invalid-credentials")
        void invalidCredentials() {
            throw new InvalidCredentialsException("E-mail ou senha inválidos");
        }

        @GetMapping("/fake/invalid-refresh-token")
        void invalidRefreshToken() {
            throw new InvalidRefreshTokenException("Refresh token inválido ou expirado");
        }

        @GetMapping("/fake/too-many-requests")
        void tooManyRequests() {
            throw new TooManyRequestsException("Muitas tentativas de login. Tente novamente mais tarde.", 900);
        }

        @GetMapping("/fake/forbidden")
        void forbidden() {
            throw new AccessDeniedException("sem permissão");
        }

        @PostMapping("/fake/validation")
        void validation(@Valid @RequestBody FakeRequest request) {
        }

        @GetMapping("/fake/items/{id}")
        void item(@PathVariable UUID id) {
        }
    }

    record FakeRequest(@NotBlank String name, @Email String email) {
    }
}
