package com.gabriel.financeflow.transaction.service;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.entity.AccountType;
import com.gabriel.financeflow.account.repository.AccountRepository;
import com.gabriel.financeflow.transaction.dto.TransferRequest;
import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.repository.TransactionRepository;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * ⭐ A atomicidade da transferência: se a segunda perna falhar, a primeira NÃO pode ficar gravada.
 *
 * SEM @Transactional de propósito: num teste @Transactional, o service entraria na transação do
 * TESTE (que só é desfeita no fim), e o rollback do service não aconteceria de verdade. Pior: o
 * teste passaria mesmo se o TransferService estivesse SEM o @Transactional.
 * A consequência é limpar os dados na mão (@AfterEach) e usar e-mails únicos.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferRollbackTest {

    /** O repository de verdade, "embrulhado": igual ao original, exceto no que o teste mandar. */
    @MockitoSpyBean
    private TransactionRepository transactionRepository;

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User ana;
    private Account nubank;
    private Account carteira;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana-" + UUID.randomUUID() + "@email.com", "hash"));
        nubank = accountRepository.save(new Account(ana, "Nubank", AccountType.CHECKING, null, new BigDecimal("1000.00")));
        carteira = accountRepository.save(new Account(ana, "Carteira", AccountType.WALLET, null, BigDecimal.ZERO));
        loginAs(ana);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        // O ON DELETE CASCADE leva as contas e as movimentações (se alguma tivesse ficado)
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", ana.getId());
    }

    @Test
    void ifTheSecondLegFailsTheFirstIsRolledBack() {
        // O repository é uma INTERFACE (a implementação é gerada pelo Spring Data): o doCallRealMethod()
        // tentaria chamar o método abstrato da interface. A "resposta padrão" do espião é a que delega
        // ao objeto real, então a 1ª chamada usa ela, e a 2ª (a perna de entrada) explode.
        Answer<?> delegateToRealRepository = mockingDetails(transactionRepository)
                .getMockCreationSettings()
                .getDefaultAnswer();
        AtomicInteger saves = new AtomicInteger();

        doAnswer(invocation -> {
            if (saves.incrementAndGet() == 2) {
                throw new RuntimeException("falha simulada na segunda perna");
            }
            return delegateToRealRepository.answer(invocation);
        }).when(transactionRepository).save(any(Transaction.class));

        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(nubank.getId(), carteira.getId(),
                new BigDecimal("500.00"), LocalDate.parse("2026-11-10"), "Saque", null)))
                .hasMessage("falha simulada na segunda perna");

        // A prova, direto no banco: nenhuma perna ficou gravada (a de saída foi desfeita)
        Integer legs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?", Integer.class, ana.getId());
        assertThat(legs).isZero();
    }

    @Test
    void withoutFailureBothLegsAreCommitted() {
        // O controle: o mesmo cenário sem a falha grava as duas pernas (prova que o teste acima não passa "à toa")
        transferService.transfer(new TransferRequest(nubank.getId(), carteira.getId(),
                new BigDecimal("500.00"), LocalDate.parse("2026-11-10"), "Saque", null));

        Integer legs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?", Integer.class, ana.getId());
        assertThat(legs).isEqualTo(2);
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
