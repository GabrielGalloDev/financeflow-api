package com.gabriel.financeflow.transaction.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerTest {

    private static final String TRANSACTIONS = "/api/v1/transactions";
    private static final String TRANSFERS = "/api/v1/transfers";
    private static final String ACCOUNTS = "/api/v1/accounts";

    @Autowired
    private MockMvc mockMvc;

    private String ana;
    private String bruno;
    private String nubank;
    private String carteira;
    private String contaDoBruno;
    private String moradia;
    private String alimentacao;
    private String salario;

    @BeforeEach
    void setUp() throws Exception {
        ana = registerAndLogin("ana");
        bruno = registerAndLogin("bruno");
        nubank = createAccount(ana, "Nubank", "1000");
        carteira = createAccount(ana, "Carteira", "0");
        contaDoBruno = createAccount(bruno, "Conta", "0");
        moradia = categoryId(ana, "EXPENSE", "Moradia");
        alimentacao = categoryId(ana, "EXPENSE", "Alimentação");
        salario = categoryId(ana, "INCOME", "Salário");
    }

    @Nested
    class Create {

        @Test
        void returns201WithLocationAndBody() throws Exception {
            MockHttpServletResponse response = transaction(ana, nubank, moradia, "EXPENSE", null, "200", "2026-11-10", "Aluguel")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("EXPENSE"))
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.date").value("2026-11-10"))
                    .andExpect(jsonPath("$.account.name").value("Nubank"))
                    .andExpect(jsonPath("$.category.name").value("Moradia"))
                    .andExpect(jsonPath("$.transferGroupId").doesNotExist())
                    .andExpect(content().string(containsString("\"amount\":200.00")))
                    .andReturn().getResponse();

            String id = JsonPath.read(response.getContentAsString(), "$.id");
            assertThat(response.getHeader(HttpHeaders.LOCATION)).endsWith(TRANSACTIONS + "/" + id);
        }

        @Test
        void pendingCanBeInTheFuture() throws Exception {
            transaction(ana, nubank, moradia, "EXPENSE", "PENDING", "200", "2999-01-01", "Conta futura")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        void paidInTheFutureReturns422() throws Exception {
            transaction(ana, nubank, moradia, "EXPENSE", "PAID", "200", "2999-01-01", "Futuro")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("Uma movimentação paga não pode ter data no futuro"));
        }

        @Test
        void categoryOfTheWrongTypeReturns422() throws Exception {
            transaction(ana, nubank, salario, "EXPENSE", null, "200", "2026-11-10", "x")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("A categoria 'Salário' não é do tipo EXPENSE"));
        }

        @Test
        void archivedAccountReturns422() throws Exception {
            mockMvc.perform(patch(ACCOUNTS + "/" + carteira + "/archive").header(HttpHeaders.AUTHORIZATION, bearer(ana)));

            transaction(ana, carteira, moradia, "EXPENSE", null, "10", "2026-11-10", "x")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("Conta arquivada não aceita movimentações"));
        }
    }

    @Nested
    class Validation {

        @Test
        void emptyBodyReturns400WithRequiredFields() throws Exception {
            mockMvc.perform(post(TRANSACTIONS)
                            .header(HttpHeaders.AUTHORIZATION, bearer(ana))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field",
                            containsInAnyOrder("accountId", "categoryId", "type", "amount", "date", "description")));
        }

        @Test
        void zeroNegativeAndThreeDecimalsReturn400() throws Exception {
            for (String amount : List.of("0", "-5", "10.555")) {
                transaction(ana, nubank, moradia, "EXPENSE", null, amount, "2026-11-10", "x")
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors[0].field").value("amount"));
            }
        }

        @Test
        void transferTypeIsRejectedInThisRoute() throws Exception {
            transaction(ana, nubank, moradia, "TRANSFER_IN", null, "10", "2026-11-10", "x")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("incomeOrExpense"));
        }

        @Test
        void invalidDateFormatReturns400() throws Exception {
            transaction(ana, nubank, moradia, "EXPENSE", null, "10", "10/11/2026", "x")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void unknownIdReturns404() throws Exception {
            mockMvc.perform(get(TRANSACTIONS + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Movimentação não encontrada"));
        }
    }

    @Nested
    class Search {

        @BeforeEach
        void extract() throws Exception {
            transaction(ana, nubank, salario, "INCOME", null, "5000", "2026-11-05", "Salário");
            transaction(ana, nubank, moradia, "EXPENSE", null, "1500", "2026-11-10", "Aluguel");
            transaction(ana, carteira, alimentacao, "EXPENSE", null, "300", "2026-11-12", "Mercado");
            transaction(ana, nubank, moradia, "EXPENSE", "PENDING", "200", "2026-11-25", "Condomínio");
            transaction(bruno, contaDoBruno, moradia, "EXPENSE", null, "800", "2026-11-10", "Do Bruno");
        }

        @Test
        void defaultIsNewestFirstAndOnlyOwn() throws Exception {
            search(ana, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.content[*].description").value(
                            org.hamcrest.Matchers.contains("Condomínio", "Mercado", "Aluguel", "Salário")));
        }

        @Test
        void filtersFromTheUrl() throws Exception {
            search(ana, "?type=EXPENSE&status=PAID&accountId=" + nubank)
                    .andExpect(jsonPath("$.content[*].description").value(org.hamcrest.Matchers.contains("Aluguel")));
            search(ana, "?from=2026-11-10&to=2026-11-12")
                    .andExpect(jsonPath("$.totalElements").value(2));
            search(ana, "?minAmount=300&maxAmount=1500")
                    .andExpect(jsonPath("$.totalElements").value(2));
            search(ana, "?categoryId=" + alimentacao)
                    .andExpect(jsonPath("$.content[*].description").value(org.hamcrest.Matchers.contains("Mercado")));
        }

        @Test
        void paginationAndSortFromTheUrl() throws Exception {
            search(ana, "?size=1&page=1&sort=amount,asc")
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].description").value("Mercado"))
                    .andExpect(jsonPath("$.totalPages").value(4));
        }

        @Test
        void pageSizeIsCappedAt100() throws Exception {
            search(ana, "?size=1000").andExpect(jsonPath("$.size").value(100));
        }

        @Test
        void invalidParametersReturn400WithoutInternalDetails() throws Exception {
            search(ana, "?type=POUPANCA")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("type"))
                    .andExpect(jsonPath("$.errors[0].message").value("Valor inválido"));
            search(ana, "?from=2026-11-30&to=2026-11-01")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("A data inicial não pode ser depois da final"));
            search(ana, "?sort=description")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Não é possível ordenar por 'description'. Use: date, amount ou createdAt"));
        }

        @Test
        void filteringByAnotherUsersAccountReturnsNothing() throws Exception {
            search(ana, "?accountId=" + contaDoBruno).andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    class UpdateDeletePay {

        @Test
        void updateChangesTheTransaction() throws Exception {
            String id = idFrom(transaction(ana, nubank, moradia, "EXPENSE", null, "200", "2026-11-10", "Aluguel"));

            mockMvc.perform(put(TRANSACTIONS + "/" + id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(ana))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(carteira, alimentacao, "EXPENSE", null, "50.5", "2026-11-11", "Mercado")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account.name").value("Carteira"))
                    .andExpect(jsonPath("$.category.name").value("Alimentação"))
                    .andExpect(content().string(containsString("\"amount\":50.50")));

            mockMvc.perform(get(TRANSACTIONS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(jsonPath("$.description").value("Mercado"));
        }

        @Test
        void deleteReturns204AndThenNotFound() throws Exception {
            String id = idFrom(transaction(ana, nubank, moradia, "EXPENSE", null, "200", "2026-11-10", "Aluguel"));

            mockMvc.perform(delete(TRANSACTIONS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get(TRANSACTIONS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete(TRANSACTIONS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void payTurnsPendingIntoPaidAndIsIdempotent() throws Exception {
            String id = idFrom(transaction(ana, nubank, moradia, "EXPENSE", "PENDING", "200", "2026-11-01", "Luz"));

            pay(ana, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
            pay(ana, id).andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-11-01"));
        }
    }

    @Nested
    class Transfers {

        @Test
        void returns201WithBothLegs() throws Exception {
            String body = transfer(ana, nubank, carteira, "150")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.transferGroupId").isNotEmpty())
                    .andExpect(jsonPath("$.fromAccount.name").value("Nubank"))
                    .andExpect(jsonPath("$.toAccount.name").value("Carteira"))
                    .andExpect(content().string(containsString("\"amount\":150.00")))
                    .andReturn().getResponse().getContentAsString();

            String outgoing = JsonPath.read(body, "$.outgoingTransactionId");
            String incoming = JsonPath.read(body, "$.incomingTransactionId");
            mockMvc.perform(get(TRANSACTIONS + "/" + outgoing).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                    .andExpect(jsonPath("$.category").doesNotExist());
            mockMvc.perform(get(TRANSACTIONS + "/" + incoming).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(jsonPath("$.type").value("TRANSFER_IN"));
        }

        @Test
        void sameAccountReturns400() throws Exception {
            transfer(ana, nubank, nubank, "10")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("differentAccounts"));
        }

        @Test
        void transferLegCannotBeEdited() throws Exception {
            String leg = JsonPath.read(transfer(ana, nubank, carteira, "150").andReturn().getResponse().getContentAsString(),
                    "$.incomingTransactionId");

            mockMvc.perform(put(TRANSACTIONS + "/" + leg)
                            .header(HttpHeaders.AUTHORIZATION, bearer(ana))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(nubank, moradia, "EXPENSE", null, "10", "2026-11-10", "x")))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        void cannotSendMoneyToAnotherUsersAccount() throws Exception {
            transfer(ana, nubank, contaDoBruno, "10").andExpect(status().isNotFound());
            transfer(ana, contaDoBruno, nubank, "10").andExpect(status().isNotFound());
        }
    }

    @Nested
    class Balance {

        @Test
        void accountsShowTheRealBalance() throws Exception {
            transaction(ana, nubank, moradia, "EXPENSE", null, "200", "2026-11-10", "Aluguel");
            transaction(ana, nubank, salario, "INCOME", "PENDING", "300", "2026-11-10", "Freela");
            transfer(ana, nubank, carteira, "150");

            assertBalances("650.00", "150.00");
        }

        @Test
        void deletingATransferLegRestoresBothBalances() throws Exception {
            String leg = JsonPath.read(transfer(ana, nubank, carteira, "150").andReturn().getResponse().getContentAsString(),
                    "$.outgoingTransactionId");

            mockMvc.perform(delete(TRANSACTIONS + "/" + leg).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isNoContent());

            assertBalances("1000.00", "0.00");
        }

        @Test
        void payingAPendingIncomeChangesTheBalance() throws Exception {
            String id = idFrom(transaction(ana, nubank, salario, "INCOME", "PENDING", "300", "2026-11-10", "Freela"));

            pay(ana, id);

            assertBalances("1300.00", "0.00");
        }

        private void assertBalances(String nubankBalance, String carteiraBalance) throws Exception {
            mockMvc.perform(get(ACCOUNTS).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(jsonPath("$[0].name").value("Carteira"))
                    .andExpect(content().string(containsString("\"currentBalance\":" + carteiraBalance)))
                    .andExpect(jsonPath("$[1].name").value("Nubank"))
                    .andExpect(content().string(containsString("\"currentBalance\":" + nubankBalance)));
        }
    }

    @Nested
    class Isolation {

        @Test
        void anotherUserCannotReadUpdateDeleteOrPay() throws Exception {
            String id = idFrom(transaction(ana, nubank, moradia, "EXPENSE", "PENDING", "200", "2026-11-10", "Aluguel"));

            mockMvc.perform(get(TRANSACTIONS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put(TRANSACTIONS + "/" + id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(bruno))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(contaDoBruno, moradia, "EXPENSE", null, "1", "2026-11-10", "x")))
                    .andExpect(status().isNotFound());
            pay(bruno, id).andExpect(status().isNotFound());
            mockMvc.perform(delete(TRANSACTIONS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get(TRANSACTIONS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        void cannotCreateInAnotherUsersAccount() throws Exception {
            transaction(ana, contaDoBruno, moradia, "EXPENSE", null, "10", "2026-11-10", "x")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conta não encontrada"));
        }

        @Test
        void responseNeverExposesTheOwner() throws Exception {
            transaction(ana, nubank, moradia, "EXPENSE", null, "10", "2026-11-10", "x")
                    .andExpect(jsonPath("$.user").doesNotExist())
                    .andExpect(jsonPath("$.userId").doesNotExist());
        }

        @Test
        void everyRouteRequiresAToken() throws Exception {
            String id = UUID.randomUUID().toString();
            List<MockHttpServletRequestBuilder> requests = List.of(
                    get(TRANSACTIONS),
                    get(TRANSACTIONS + "/" + id),
                    post(TRANSACTIONS).contentType(MediaType.APPLICATION_JSON).content("{}"),
                    put(TRANSACTIONS + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"),
                    delete(TRANSACTIONS + "/" + id),
                    patch(TRANSACTIONS + "/" + id + "/pay"),
                    post(TRANSFERS).contentType(MediaType.APPLICATION_JSON).content("{}"));

            for (MockHttpServletRequestBuilder request : requests) {
                mockMvc.perform(request).andExpect(status().isUnauthorized());
            }
        }
    }

    private ResultActions transaction(String token, String accountId, String categoryId, String type, String status,
                                      String amount, String date, String description) throws Exception {
        return mockMvc.perform(post(TRANSACTIONS)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(accountId, categoryId, type, status, amount, date, description)));
    }

    private String transactionJson(String accountId, String categoryId, String type, String status,
                                   String amount, String date, String description) {
        String statusJson = status == null ? "null" : "\"" + status + "\"";
        return """
                {"accountId": "%s", "categoryId": "%s", "type": "%s", "status": %s,
                 "amount": %s, "date": "%s", "description": "%s"}
                """.formatted(accountId, categoryId, type, statusJson, amount, date, description);
    }

    private ResultActions transfer(String token, String from, String to, String amount) throws Exception {
        return mockMvc.perform(post(TRANSFERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fromAccountId": "%s", "toAccountId": "%s", "amount": %s,
                         "date": "2026-11-10", "description": "Saque"}
                        """.formatted(from, to, amount)));
    }

    private ResultActions search(String token, String query) throws Exception {
        return mockMvc.perform(get(TRANSACTIONS + query).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions pay(String token, String id) throws Exception {
        return mockMvc.perform(patch(TRANSACTIONS + "/" + id + "/pay").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String createAccount(String token, String name, String initialBalance) throws Exception {
        return idFrom(mockMvc.perform(post(ACCOUNTS)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "type": "CHECKING", "initialBalance": %s}
                        """.formatted(name, initialBalance))));
    }

    private String categoryId(String token, String type, String name) throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories?type=" + type).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    private String idFrom(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

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
