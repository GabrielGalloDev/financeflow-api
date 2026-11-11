package com.gabriel.financeflow.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // Sem @WithMockUser: o teste também prova que a documentação é pública
    @Test
    void apiDocsExposeInfoAndBearerSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FinanceFlow API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    void authEndpointsDoNotRequireToken() throws Exception {
        // "security": [] = lista vazia, sobrescreve a exigência global (sem cadeado no Swagger)
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security").isEmpty());
    }

    @Test
    void userEndpointsKeepTheGlobalTokenRequirement() throws Exception {
        // Sem "security" próprio: herda a exigência global (com cadeado)
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].put.security").doesNotExist());
    }

    @Test
    void endpointsAreGroupedByTagWithSummaries() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.tags[0]").value("Auth"))
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.tags[0]").value("Users"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.summary").value("Cadastra um novo usuário"))
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].put.summary")
                        .value("Atualiza nome, e-mail, moeda e fuso horário"));
    }
}
