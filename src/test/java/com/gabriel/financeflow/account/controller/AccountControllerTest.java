package com.gabriel.financeflow.account.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a camada HTTP das contas: status, headers, JSON e o isolamento entre usuários
 * com tokens de verdade (cadastro + login), como um cliente real faria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountControllerTest {

    private static final String ACCOUNTS_URL = "/api/v1/accounts";

    @Autowired
    private MockMvc mockMvc;

    // ======================================================================
    // Criar
    // ======================================================================
    @Nested
    class Create {

        @Test
        void returns201WithBodyAndBalancesWithTwoDecimals() throws Exception {
            String token = registerAndLogin("ana");

            createAccount(token, "Nubank", "CHECKING", "\"Nubank\"", "1500")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("Nubank"))
                    .andExpect(jsonPath("$.type").value("CHECKING"))
                    .andExpect(jsonPath("$.institution").value("Nubank"))
                    .andExpect(jsonPath("$.archived").value(false))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    // Escala 2 no JSON: 1500 vira 1500.00 (o MoneyUtils.normalize do service)
                    .andExpect(content().string(containsString("\"initialBalance\":1500.00")))
                    .andExpect(content().string(containsString("\"currentBalance\":1500.00")));
        }

        @Test
        void locationHeaderPointsToTheNewAccount() throws Exception {
            String token = registerAndLogin("ana");

            var response = createAccount(token, "Nubank", "CHECKING", "null", "0")
                    .andReturn().getResponse();
            String id = JsonPath.read(response.getContentAsString(), "$.id");
            String location = response.getHeader(HttpHeaders.LOCATION);

            assertThat(location).endsWith(ACCOUNTS_URL + "/" + id);

            // E o endereço funciona: buscar por ele devolve a conta
            mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id));
        }

        @Test
        void creditCardAcceptsNegativeInitialBalance() throws Exception {
            String token = registerAndLogin("ana");

            createAccount(token, "Cartão", "CREDIT_CARD", "null", "-1200.5")
                    .andExpect(status().isCreated())
                    .andExpect(content().string(containsString("\"initialBalance\":-1200.50")));
        }

        @Test
        void blankInstitutionIsSavedAsNullAndNameIsTrimmed() throws Exception {
            String token = registerAndLogin("ana");

            createAccount(token, "  Carteira  ", "WALLET", "\"   \"", "50")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Carteira"))
                    .andExpect(jsonPath("$.institution").doesNotExist());
        }

        @Test
        void duplicateActiveNameIgnoringCaseReturns409() throws Exception {
            String token = registerAndLogin("ana");
            createAccount(token, "Nubank", "CHECKING", "null", "0").andExpect(status().isCreated());

            createAccount(token, "NUBANK", "SAVINGS", "null", "0")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Já existe uma conta ativa com este nome"));
        }

        @Test
        void sameNameAsAnArchivedAccountIsAllowed() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(createAccount(token, "Nubank", "CHECKING", "null", "0"));
            archive(token, id).andExpect(status().isOk());

            createAccount(token, "Nubank", "CHECKING", "null", "0")
                    .andExpect(status().isCreated());
        }
    }

    // ======================================================================
    // Validação
    // ======================================================================
    @Nested
    class Validation {

        @Test
        void emptyBodyReturns400WithErrorsForRequiredFields() throws Exception {
            String token = registerAndLogin("ana");

            mockMvc.perform(post(ACCOUNTS_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    // A instituição é opcional: só nome, tipo e saldo inicial são exigidos
                    .andExpect(jsonPath("$.errors", hasSize(3)))
                    .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "type", "initialBalance")));
        }

        @Test
        void moreThanTwoDecimalPlacesReturns400() throws Exception {
            String token = registerAndLogin("ana");

            // Sem o @Digits, o NUMERIC(19,2) do banco arredondaria para 10.56 sem avisar
            createAccount(token, "Carteira", "WALLET", "null", "10.555")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("initialBalance"));
        }

        @Test
        void tooManyIntegerDigitsReturns400InsteadOfDatabaseError() throws Exception {
            String token = registerAndLogin("ana");

            // 18 dígitos inteiros: não cabe no NUMERIC(19,2). Sem o @Digits(integer = 17), seria 500
            createAccount(token, "Carteira", "WALLET", "null", "123456789012345678")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void unknownTypeReturns400WithReadableMessage() throws Exception {
            String token = registerAndLogin("ana");

            createAccount(token, "Poupança", "POUPANCA", "null", "0")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail")
                            .value("Corpo da requisição inválido: confira o formato do JSON e os valores dos campos"));
        }

        @Test
        void nameLongerThan100Returns400() throws Exception {
            String token = registerAndLogin("ana");

            createAccount(token, "a".repeat(101), "WALLET", "null", "0")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        void invalidIdInPathReturns400() throws Exception {
            String token = registerAndLogin("ana");

            mockMvc.perform(get(ACCOUNTS_URL + "/nao-e-um-uuid").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void unknownIdReturns404() throws Exception {
            String token = registerAndLogin("ana");

            mockMvc.perform(get(ACCOUNTS_URL + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conta não encontrada"));
        }
    }

    // ======================================================================
    // Listar, buscar, editar, arquivar
    // ======================================================================
    @Nested
    class ReadUpdateArchive {

        @Test
        void listReturnsOnlyActiveAccountsOrderedByName() throws Exception {
            String token = registerAndLogin("ana");
            createAccount(token, "Poupança", "SAVINGS", "null", "0");
            createAccount(token, "Carteira", "WALLET", "null", "0");
            String antiga = idFrom(createAccount(token, "Antiga", "CHECKING", "null", "0"));
            archive(token, antiga);

            list(token, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].name").value("Carteira"))
                    .andExpect(jsonPath("$[1].name").value("Poupança"));

            list(token, true)
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].name").value("Antiga"))
                    .andExpect(jsonPath("$[0].archived").value(true));
        }

        @Test
        void getReturnsTheAccount() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(createAccount(token, "Nubank", "CHECKING", "\"Nubank\"", "1500"));

            mockMvc.perform(get(ACCOUNTS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.name").value("Nubank"));
        }

        @Test
        void updateChangesAllFieldsAndGetShowsTheNewValues() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(createAccount(token, "Nubank", "CHECKING", "\"Nubank\"", "1500"));

            update(token, id, "Inter", "SAVINGS", "\"Banco Inter\"", "200.5")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Inter"))
                    .andExpect(jsonPath("$.type").value("SAVINGS"))
                    .andExpect(jsonPath("$.institution").value("Banco Inter"))
                    .andExpect(content().string(containsString("\"initialBalance\":200.50")));

            mockMvc.perform(get(ACCOUNTS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(jsonPath("$.name").value("Inter"));
        }

        @Test
        void updateKeepingTheSameNameIsNotAConflict() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(createAccount(token, "Nubank", "CHECKING", "null", "0"));

            update(token, id, "nubank", "SAVINGS", "null", "10")
                    .andExpect(status().isOk());
        }

        @Test
        void updateToTheNameOfAnotherActiveAccountReturns409() throws Exception {
            String token = registerAndLogin("ana");
            createAccount(token, "Nubank", "CHECKING", "null", "0");
            String inter = idFrom(createAccount(token, "Inter", "CHECKING", "null", "0"));

            update(token, inter, "Nubank", "CHECKING", "null", "0")
                    .andExpect(status().isConflict());
        }

        @Test
        void updateArchivedAccountReturns422() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(createAccount(token, "Nubank", "CHECKING", "null", "0"));
            archive(token, id);

            update(token, id, "Outro", "CHECKING", "null", "0")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("Conta arquivada não pode ser editada"));
        }

        @Test
        void archiveAndUnarchiveToggleTheFlagAndAreIdempotent() throws Exception {
            String token = registerAndLogin("ana");
            String id = idFrom(createAccount(token, "Nubank", "CHECKING", "null", "0"));

            archive(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
            archive(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));

            unarchive(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
            unarchive(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
        }

        @Test
        void unarchiveWhenAnotherActiveAccountHasTheSameNameReturns409() throws Exception {
            String token = registerAndLogin("ana");
            String old = idFrom(createAccount(token, "Nubank", "CHECKING", "null", "0"));
            archive(token, old);
            createAccount(token, "Nubank", "CHECKING", "null", "0");

            unarchive(token, old)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Já existe outra conta ativa com o mesmo nome"));
        }
    }

    // ======================================================================
    // Isolamento entre usuários ⭐ (pedido explicitamente no enunciado)
    // ======================================================================
    @Nested
    class Isolation {

        @Test
        void anotherUserCannotReadUpdateOrArchiveTheAccount() throws Exception {
            String tokenAna = registerAndLogin("ana");
            String tokenBruno = registerAndLogin("bruno");
            String anasAccount = idFrom(createAccount(tokenAna, "Nubank", "CHECKING", "null", "1500"));

            // Para o Bruno, a conta da Ana simplesmente não existe: 404 em tudo (e não 403)
            mockMvc.perform(get(ACCOUNTS_URL + "/" + anasAccount).header(HttpHeaders.AUTHORIZATION, bearer(tokenBruno)))
                    .andExpect(status().isNotFound());
            update(tokenBruno, anasAccount, "Hackeada", "WALLET", "null", "0")
                    .andExpect(status().isNotFound());
            archive(tokenBruno, anasAccount).andExpect(status().isNotFound());
            unarchive(tokenBruno, anasAccount).andExpect(status().isNotFound());

            // E nada mudou para a Ana
            mockMvc.perform(get(ACCOUNTS_URL + "/" + anasAccount).header(HttpHeaders.AUTHORIZATION, bearer(tokenAna)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Nubank"))
                    .andExpect(jsonPath("$.archived").value(false));
        }

        @Test
        void eachUserListsOnlyTheirOwnAccounts() throws Exception {
            String tokenAna = registerAndLogin("ana");
            String tokenBruno = registerAndLogin("bruno");
            createAccount(tokenAna, "Conta da Ana", "CHECKING", "null", "0");
            createAccount(tokenBruno, "Conta do Bruno", "CHECKING", "null", "0");

            list(tokenAna, true)
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("Conta da Ana"));
            list(tokenBruno, true)
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("Conta do Bruno"));
        }

        @Test
        void sameAccountNameIsAllowedForDifferentUsers() throws Exception {
            String tokenAna = registerAndLogin("ana");
            String tokenBruno = registerAndLogin("bruno");

            createAccount(tokenAna, "Nubank", "CHECKING", "null", "0").andExpect(status().isCreated());
            createAccount(tokenBruno, "Nubank", "CHECKING", "null", "0").andExpect(status().isCreated());
        }

        @Test
        void responseNeverExposesTheOwner() throws Exception {
            String token = registerAndLogin("ana");

            createAccount(token, "Nubank", "CHECKING", "null", "0")
                    .andExpect(jsonPath("$.user").doesNotExist())
                    .andExpect(jsonPath("$.userId").doesNotExist());
        }

        @Test
        void everyRouteRequiresAToken() throws Exception {
            String id = UUID.randomUUID().toString();

            mockMvc.perform(get(ACCOUNTS_URL)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(ACCOUNTS_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(ACCOUNTS_URL + "/" + id)).andExpect(status().isUnauthorized());
            mockMvc.perform(put(ACCOUNTS_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(patch(ACCOUNTS_URL + "/" + id + "/archive")).andExpect(status().isUnauthorized());
            mockMvc.perform(patch(ACCOUNTS_URL + "/" + id + "/unarchive")).andExpect(status().isUnauthorized());
        }
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    /** institutionJson já vem no formato JSON: "\"Nubank\"" ou "null". O saldo vai como número. */
    private ResultActions createAccount(String token, String name, String type, String institutionJson,
                                        String initialBalance) throws Exception {
        return mockMvc.perform(post(ACCOUNTS_URL)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson(name, type, institutionJson, initialBalance)));
    }

    private ResultActions update(String token, String id, String name, String type, String institutionJson,
                                 String initialBalance) throws Exception {
        return mockMvc.perform(put(ACCOUNTS_URL + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson(name, type, institutionJson, initialBalance)));
    }

    private ResultActions archive(String token, String id) throws Exception {
        return mockMvc.perform(patch(ACCOUNTS_URL + "/" + id + "/archive")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions unarchive(String token, String id) throws Exception {
        return mockMvc.perform(patch(ACCOUNTS_URL + "/" + id + "/unarchive")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions list(String token, boolean includeArchived) throws Exception {
        return mockMvc.perform(get(ACCOUNTS_URL)
                .param("includeArchived", String.valueOf(includeArchived))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String accountJson(String name, String type, String institutionJson, String initialBalance) {
        return """
                {"name": "%s", "type": "%s", "institution": %s, "initialBalance": %s}
                """.formatted(name, type, institutionJson, initialBalance);
    }

    private String idFrom(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }


    private String bearer(String token) {
        return "Bearer " + token;
    }

    /** Cadastra e faz login; devolve o access token. O e-mail é derivado do nome. */
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
