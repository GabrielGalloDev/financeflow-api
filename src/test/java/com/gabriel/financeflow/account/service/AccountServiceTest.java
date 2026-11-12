package com.gabriel.financeflow.account.service;

import com.gabriel.financeflow.account.dto.AccountRequest;
import com.gabriel.financeflow.account.dto.AccountResponse;
import com.gabriel.financeflow.account.entity.AccountType;
import com.gabriel.financeflow.account.repository.AccountRepository;
import com.gabriel.financeflow.shared.exception.BusinessException;
import com.gabriel.financeflow.shared.exception.ConflictException;
import com.gabriel.financeflow.shared.exception.ResourceNotFoundException;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa as regras do service direto, sem HTTP. O "usuário logado" é simulado colocando
 * um JWT no SecurityContextHolder, que é de onde o CurrentUser lê o id.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User ana;
    private User bruno;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana@email.com", "hash"));
        bruno = userRepository.save(new User("Bruno", "bruno@email.com", "hash"));
        loginAs(ana);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNormalizesNameInstitutionAndBalance() {
        AccountResponse created = accountService.create(
                new AccountRequest("  Nubank  ", AccountType.CHECKING, "   ", new BigDecimal("100")));

        assertThat(created.name()).isEqualTo("Nubank");
        assertThat(created.institution()).isNull();
        assertThat(created.initialBalance()).isEqualByComparingTo("100.00");
        assertThat(created.initialBalance().scale()).isEqualTo(2);
        assertThat(created.currentBalance()).isEqualByComparingTo("100.00");
        assertThat(created.archived()).isFalse();
    }

    @Test
    void updateIsPersistedToTheDatabase() {
        UUID id = createAccount("Nubank");

        accountService.update(id, new AccountRequest("Inter", AccountType.SAVINGS, "Banco Inter", new BigDecimal("50.5")));

        // Força o UPDATE e descarta a memória do Hibernate: o que vem agora é o que está no banco
        entityManager.flush();
        entityManager.clear();

        assertThat(accountRepository.findById(id)).get()
                .satisfies(account -> {
                    assertThat(account.getName()).isEqualTo("Inter");
                    assertThat(account.getType()).isEqualTo(AccountType.SAVINGS);
                    assertThat(account.getInitialBalance()).isEqualByComparingTo("50.50");
                });
    }

    @Test
    void anotherUsersAccountIsNotFound() {
        UUID anasAccount = createAccount("Nubank");

        loginAs(bruno);

        assertThatThrownBy(() -> accountService.get(anasAccount)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.update(anasAccount, request("Hackeada")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.archive(anasAccount)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(accountService.list(true)).isEmpty();
    }

    @Test
    void archivedAccountCannotBeEdited() {
        UUID id = createAccount("Nubank");
        accountService.archive(id);

        assertThatThrownBy(() -> accountService.update(id, request("Outro nome")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Conta arquivada não pode ser editada");
    }

    @Test
    void duplicateNameOnCreateAndUpdateIsConflict() {
        createAccount("Nubank");
        UUID inter = createAccount("Inter");

        assertThatThrownBy(() -> accountService.create(request("NUBANK"))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> accountService.update(inter, request("nubank"))).isInstanceOf(ConflictException.class);
        // Manter o próprio nome não é conflito
        assertThat(accountService.update(inter, request("Inter")).name()).isEqualTo("Inter");
    }

    @Test
    void unarchiveIsConflictWhenAnotherActiveAccountHasTheSameName() {
        UUID old = createAccount("Nubank");
        accountService.archive(old);
        createAccount("Nubank");

        assertThatThrownBy(() -> accountService.unarchive(old))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Já existe outra conta ativa com o mesmo nome");
    }

    @Test
    void archiveHidesFromDefaultListAndUnarchiveBringsBack() {
        UUID id = createAccount("Nubank");
        createAccount("Carteira");

        accountService.archive(id);
        assertThat(accountService.list(false)).extracting(AccountResponse::name).containsExactly("Carteira");
        assertThat(accountService.list(true)).extracting(AccountResponse::name).containsExactly("Carteira", "Nubank");

        accountService.unarchive(id);
        assertThat(accountService.list(false)).extracting(AccountResponse::name).containsExactly("Carteira", "Nubank");
    }

    private UUID createAccount(String name) {
        return accountService.create(request(name)).id();
    }

    private AccountRequest request(String name) {
        return new AccountRequest(name, AccountType.CHECKING, null, new BigDecimal("100.00"));
    }

    private void loginAs(User user) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(user.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
