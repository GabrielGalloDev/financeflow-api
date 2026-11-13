package com.gabriel.financeflow.category.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A camada HTTP das categorias: status, JSON, a proteção das categorias padrão e o isolamento
 * entre usuários, com tokens de verdade (cadastro + login).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryControllerTest {

    private static final String CATEGORIES_URL = "/api/v1/categories";
    private static final int DEFAULTS = 13;
    private static final int DEFAULT_INCOMES = 4;

    @Autowired
    private MockMvc mockMvc;

    // ======================================================================
    // Listar
    // ======================================================================
    @Nested
    class List_ {

        @Test
        void newUserSeesTheThirteenDefaultCategories() throws Exception {
            String token = registerAndLogin("ana");

            list(token, null, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(DEFAULTS)))
                    .andExpect(jsonPath("$[*].custom", everyItem(is(false))))
                    .andExpect(jsonPath("$[*].archived", everyItem(is(false))))
                    .andExpect(jsonPath("$[*].name", hasItem("Alimentação")));
        }

        @Test
        void createdCategoryAppearsAsCustom() throws Exception {
            String token = registerAndLogin("ana");
            create(token, "Academia", "EXPENSE");

            list(token, null, false)
                    .andExpect(jsonPath("$", hasSize(DEFAULTS + 1)))
                    .andExpect(jsonPath("$[?(@.name == 'Academia')].custom", hasItem(true)));
        }

        @Test
        void typeFilterReturnsOnlyThatType() throws Exception {
            String token = registerAndLogin("ana");
            create(token, "Bônus", "INCOME");
            create(token, "Academia", "EXPENSE");

            list(token, "INCOME", false)
                    .andExpect(jsonPath("$", hasSize(DEFAULT_INCOMES + 1)))
                    .andExpect(jsonPath("$[*].type", everyItem(is("INCOME"))))
                    .andExpect(jsonPath("$[*].name", hasItem("Bônus")));
        }

        @Test
        void expensesComeBeforeIncomes() throws Exception {
            String token = registerAndLogin("ana");

            String body = list(token, null, false).andReturn().getResponse().getContentAsString();
            List<String> types = JsonPath.read(body, "$[*].type");

            assertThat(types.getFirst()).isEqualTo("EXPENSE");
            assertThat(types.getLast()).isEqualTo("INCOME");
        }

        @Test
        void unknownTypeFilterReturns400WithReadableMessage() throws Exception {
            String token = registerAndLogin("ana");

            list(token, "POUPANCA", false)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Parâmetro 'type' com valor inválido"));
        }
    }

    // ======================================================================
    // Criar
    // ======================================================================
    @Nested
    class Create {

        @Test
        void returns201WithLocationPointingToTheNewCategory() throws Exception {
            String token = registerAndLogin("ana");

            MockHttpServletResponse response = create(token, "Academia", "EXPENSE")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("Academia"))
                    .andExpect(jsonPath("$.type").value("EXPENSE"))
                    .andExpect(jsonPath("$.custom").value(true))
                    .andExpect(jsonPath("$.archived").value(false))
                    .andReturn().getResponse();

            String id = JsonPath.read(response.getContentAsString(), "$.id");
            String location = response.getHeader(HttpHeaders.LOCATION);
            assertThat(location).endsWith(CATEGORIES_URL + "/" + id);

            mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Academia"));
        }

        @Test
        void nameIsTrimmed() throws Exception {
            String token = registerAndLogin("ana");

            create(token, "  Academia  ", "EXPENSE")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Academia"));
        }

        @Test
        void sameNameWithDifferentTypesIsAllowed() throws Exception {
            String token = registerAndLogin("ana");

            create(token, "Viagem", "EXPENSE").andExpect(status().isCreated());
            create(token, "Viagem", "INCOME").andExpect(status().isCreated());
        }

        @Test
        void nameOfADefaultCategoryOfTheSameTypeReturns409() throws Exception {
            String token = registerAndLogin("ana");

            // A regra que só o Java garante: o banco tem as padrão e as personalizadas em índices separados
            create(token, "alimentação", "EXPENSE")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Já existe uma categoria com este nome e tipo"));
        }

        @Test
        void nameOfADefaultCategoryWithAnotherTypeIsAllowed() throws Exception {
            String token = registerAndLogin("ana");

            create(token, "Alimentação", "INCOME").andExpect(status().isCreated());
        }

        @Test
        void duplicateActiveCustomNameIgnoringCaseReturns409() throws Exception {
            String token = registerAndLogin("ana");
            create(token, "Academia", "EXPENSE");

            create(token, "ACADEMIA", "EXPENSE").andExpect(status().isConflict());
        }

        @Test
        void sameNameAsAnArchivedCategoryIsAllowed() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(create(token, "Academia", "EXPENSE"));
            archive(token, id);

            create(token, "Academia", "EXPENSE").andExpect(status().isCreated());
        }
    }

    // ======================================================================
    // Validação
    // ======================================================================
    @Nested
    class Validation {

        @Test
        void emptyBodyReturns400WithNameAndTypeErrors() throws Exception {
            String token = registerAndLogin("ana");

            mockMvc.perform(post(CATEGORIES_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasSize(2)))
                    .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "type")));
        }

        @Test
        void nameLongerThan50Returns400() throws Exception {
            String token = registerAndLogin("ana");

            create(token, "a".repeat(51), "EXPENSE")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        void transferIsNotACategoryType() throws Exception {
            String token = registerAndLogin("ana");

            create(token, "Transferência", "TRANSFER")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail")
                            .value("Corpo da requisição inválido: confira o formato do JSON e os valores dos campos"));
        }

        @Test
        void unknownIdReturns404AndInvalidIdReturns400() throws Exception {
            String token = registerAndLogin("ana");

            mockMvc.perform(get(CATEGORIES_URL + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Categoria não encontrada"));

            mockMvc.perform(get(CATEGORIES_URL + "/abc").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Parâmetro 'id' com valor inválido"));
        }
    }

    // ======================================================================
    // Renomear e arquivar
    // ======================================================================
    @Nested
    class RenameAndArchive {

        @Test
        void renameChangesOnlyTheNameAndGetShowsIt() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(create(token, "Academia", "EXPENSE"));

            rename(token, id, "Esportes")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Esportes"))
                    .andExpect(jsonPath("$.type").value("EXPENSE"));

            mockMvc.perform(get(CATEGORIES_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(jsonPath("$.name").value("Esportes"));
        }

        @Test
        void typeSentInPutIsIgnored() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(create(token, "Academia", "EXPENSE"));

            // O UpdateCategoryRequest não tem "type": o Jackson ignora o campo, e o tipo não muda
            mockMvc.perform(put(CATEGORIES_URL + "/" + id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Academia", "type": "INCOME"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("EXPENSE"));
        }

        @Test
        void renameKeepingTheSameNameIsNotAConflict() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(create(token, "Academia", "EXPENSE"));

            rename(token, id, "academia").andExpect(status().isOk());
        }

        @Test
        void renameToTheNameOfAnotherCategoryReturns409() throws Exception {
            String token = registerAndLogin("ana");
            create(token, "Academia", "EXPENSE");
            String pets = idFrom(create(token, "Pets", "EXPENSE"));

            rename(token, pets, "Academia").andExpect(status().isConflict());
            rename(token, pets, "Lazer").andExpect(status().isConflict());
        }

        @Test
        void renameArchivedCategoryReturns422() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(create(token, "Academia", "EXPENSE"));
            archive(token, id);

            rename(token, id, "Outro")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("Categoria arquivada não pode ser editada"));
        }

        @Test
        void archiveHidesAndUnarchiveBringsBackIdempotently() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(create(token, "Academia", "EXPENSE"));

            archive(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
            archive(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
            list(token, null, false).andExpect(jsonPath("$[*].name", not(hasItem("Academia"))));
            list(token, null, true).andExpect(jsonPath("$[*].name", hasItem("Academia")));

            unarchive(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
            unarchive(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
            list(token, null, false).andExpect(jsonPath("$[*].name", hasItem("Academia")));
        }

        @Test
        void unarchiveWhenAnotherActiveCategoryHasTheSameNameReturns409() throws Exception {
            String token = registerAndLogin("ana");
            String old = idFrom(create(token, "Academia", "EXPENSE"));
            archive(token, old);
            create(token, "Academia", "EXPENSE");

            unarchive(token, old).andExpect(status().isConflict());
        }
    }

    // ======================================================================
    // Proteção das categorias padrão ⭐
    // ======================================================================
    @Nested
    class DefaultCategories {

        @Test
        void defaultCategoryCanBeReadByAnyUser() throws Exception {
            String tokenAna = registerAndLogin("ana");
            String tokenBruno = registerAndLogin("bruno");
            String alimentacao = defaultId(tokenAna, "Alimentação");

            mockMvc.perform(get(CATEGORIES_URL + "/" + alimentacao).header(HttpHeaders.AUTHORIZATION, bearer(tokenBruno)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.custom").value(false));
        }

        @Test
        void defaultCategoryCannotBeRenamedArchivedOrUnarchived() throws Exception {
            String token = registerAndLogin("ana");
            String lazer = defaultId(token, "Lazer");

            // 422, e não 404 (o usuário vê a categoria na lista) nem 500 (o service checa antes da entidade)
            rename(token, lazer, "Diversão")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("Categorias padrão não podem ser alteradas"));
            archive(token, lazer).andExpect(status().isUnprocessableContent());
            unarchive(token, lazer).andExpect(status().isUnprocessableContent());

            // E nada mudou
            mockMvc.perform(get(CATEGORIES_URL + "/" + lazer).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(jsonPath("$.name").value("Lazer"))
                    .andExpect(jsonPath("$.archived").value(false));
        }
    }

    // ======================================================================
    // Isolamento entre usuários ⭐
    // ======================================================================
    @Nested
    class Isolation {

        @Test
        void anotherUserCannotReadRenameOrArchiveACustomCategory() throws Exception {
            String tokenAna = registerAndLogin("ana");
            String tokenBruno = registerAndLogin("bruno");
            String academia = idFrom(create(tokenAna, "Academia", "EXPENSE"));

            mockMvc.perform(get(CATEGORIES_URL + "/" + academia).header(HttpHeaders.AUTHORIZATION, bearer(tokenBruno)))
                    .andExpect(status().isNotFound());
            rename(tokenBruno, academia, "Hackeada").andExpect(status().isNotFound());
            archive(tokenBruno, academia).andExpect(status().isNotFound());
            unarchive(tokenBruno, academia).andExpect(status().isNotFound());

            mockMvc.perform(get(CATEGORIES_URL + "/" + academia).header(HttpHeaders.AUTHORIZATION, bearer(tokenAna)))
                    .andExpect(jsonPath("$.name").value("Academia"))
                    .andExpect(jsonPath("$.archived").value(false));
        }

        @Test
        void eachUserSeesDefaultsPlusOnlyTheirOwn() throws Exception {
            String tokenAna = registerAndLogin("ana");
            String tokenBruno = registerAndLogin("bruno");
            create(tokenAna, "Academia", "EXPENSE");
            create(tokenBruno, "Pets", "EXPENSE");

            list(tokenAna, null, true)
                    .andExpect(jsonPath("$", hasSize(DEFAULTS + 1)))
                    .andExpect(jsonPath("$[*].name", hasItem("Academia")))
                    .andExpect(jsonPath("$[*].name", not(hasItem("Pets"))));
            list(tokenBruno, null, true)
                    .andExpect(jsonPath("$", hasSize(DEFAULTS + 1)))
                    .andExpect(jsonPath("$[*].name", hasItem("Pets")))
                    .andExpect(jsonPath("$[*].name", not(hasItem("Academia"))));
        }

        @Test
        void sameCustomNameIsAllowedForDifferentUsers() throws Exception {
            String tokenAna = registerAndLogin("ana");
            String tokenBruno = registerAndLogin("bruno");

            create(tokenAna, "Academia", "EXPENSE").andExpect(status().isCreated());
            create(tokenBruno, "Academia", "EXPENSE").andExpect(status().isCreated());
        }

        @Test
        void responseNeverExposesTheOwner() throws Exception {
            String token = registerAndLogin("ana");

            create(token, "Academia", "EXPENSE")
                    .andExpect(jsonPath("$.user").doesNotExist())
                    .andExpect(jsonPath("$.userId").doesNotExist());
        }

        @Test
        void everyRouteRequiresAToken() throws Exception {
            String id = UUID.randomUUID().toString();

            mockMvc.perform(get(CATEGORIES_URL)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(CATEGORIES_URL + "/" + id)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(CATEGORIES_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(put(CATEGORIES_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(patch(CATEGORIES_URL + "/" + id + "/archive")).andExpect(status().isUnauthorized());
            mockMvc.perform(patch(CATEGORIES_URL + "/" + id + "/unarchive")).andExpect(status().isUnauthorized());
        }
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private ResultActions list(String token, String type, boolean includeArchived) throws Exception {
        var request = get(CATEGORIES_URL)
                .param("includeArchived", String.valueOf(includeArchived))
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (type != null) {
            request.param("type", type);
        }
        return mockMvc.perform(request);
    }

    private ResultActions create(String token, String name, String type) throws Exception {
        return mockMvc.perform(post(CATEGORIES_URL)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "type": "%s"}
                        """.formatted(name, type)));
    }

    private ResultActions rename(String token, String id, String name) throws Exception {
        return mockMvc.perform(put(CATEGORIES_URL + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s"}
                        """.formatted(name)));
    }

    private ResultActions archive(String token, String id) throws Exception {
        return mockMvc.perform(patch(CATEGORIES_URL + "/" + id + "/archive")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions unarchive(String token, String id) throws Exception {
        return mockMvc.perform(patch(CATEGORIES_URL + "/" + id + "/unarchive")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String idFrom(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** O id de uma categoria padrão, pelo nome, buscado na própria API. */
    private String defaultId(String token, String name) throws Exception {
        String body = list(token, null, false).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.name == '" + name + "' && @.custom == false)].id");
        return ids.getFirst();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    /** Cadastra e faz login; devolve o access token. */
    private String registerAndLogin(String name) throws Exception {
        String email = name + "@email.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "email": "%s", "password": "senha1234"}
                                """.formatted(name, email)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "senha1234"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.accessToken");
    }
}
