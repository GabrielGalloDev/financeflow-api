package com.gabriel.financeflow.transaction.service;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.entity.AccountType;
import com.gabriel.financeflow.account.repository.AccountRepository;
import com.gabriel.financeflow.shared.exception.BusinessException;
import com.gabriel.financeflow.shared.exception.ResourceNotFoundException;
import com.gabriel.financeflow.support.MutableClock;
import com.gabriel.financeflow.transaction.dto.TransferRequest;
import com.gabriel.financeflow.transaction.dto.TransferResponse;
import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import com.gabriel.financeflow.transaction.repository.TransactionRepository;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** As regras da transferência. A atomicidade (rollback) fica no TransferRollbackTest. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransferServiceTest {

    /** 14/11 às 02:30 UTC = 13/11 às 23:30 em São Paulo. */
    static final Instant NOW = Instant.parse("2026-11-14T02:30:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private User ana;
    private User bruno;
    private Account nubank;
    private Account carteira;
    private Account contaDoBruno;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("Ana", "ana@email.com", "hash"));
        bruno = userRepository.save(new User("Bruno", "bruno@email.com", "hash"));
        nubank = accountRepository.save(new Account(ana, "Nubank", AccountType.CHECKING, null, new BigDecimal("1000.00")));
        carteira = accountRepository.save(new Account(ana, "Carteira", AccountType.WALLET, null, BigDecimal.ZERO));
        contaDoBruno = accountRepository.save(new Account(bruno, "Conta", AccountType.CHECKING, null, BigDecimal.ZERO));
        loginAs(ana);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsTwoPaidLegsWithTheSameGroupAndNoCategory() {
        TransferResponse response = transfer(nubank, carteira, "500");

        assertThat(transactionRepository.findAllByTransferGroupIdAndDeletedAtIsNull(response.transferGroupId()))
                .hasSize(2)
                .allSatisfy(leg -> {
                    assertThat(leg.getStatus()).isEqualTo(TransactionStatus.PAID);
                    assertThat(leg.getCategory()).isNull();
                    assertThat(leg.getAmount()).isEqualByComparingTo("500.00");
                })
                .extracting(Transaction::getType)
                .containsExactlyInAnyOrder(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN);
    }

    @Test
    void moneyMovesBetweenAccountsWithoutAppearingOrDisappearing() {
        transfer(nubank, carteira, "500");

        assertThat(transactionRepository.movementsOf(nubank.getId())).isEqualByComparingTo("-500.00");
        assertThat(transactionRepository.movementsOf(carteira.getId())).isEqualByComparingTo("500.00");
    }

    @Test
    void responseHasBothLegsAndTheyCanBeReadThroughTransactions() {
        TransferResponse response = transfer(nubank, carteira, "500");

        assertThat(response.fromAccount().name()).isEqualTo("Nubank");
        assertThat(response.toAccount().name()).isEqualTo("Carteira");
        assertThat(transactionService.get(response.outgoingTransactionId()).type()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(transactionService.get(response.incomingTransactionId()).type()).isEqualTo(TransactionType.TRANSFER_IN);
    }

    @Test
    void cannotTransferFromOrToAnotherUsersAccount() {
        assertThatThrownBy(() -> transfer(contaDoBruno, carteira, "10"))
                .isInstanceOf(ResourceNotFoundException.class);
        // Mandar dinheiro para a conta de outra pessoa também não: para a Ana, ela não existe
        assertThatThrownBy(() -> transfer(nubank, contaDoBruno, "10"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void archivedOriginOrDestinationIsRejected() {
        carteira.archive(Instant.now());

        assertThatThrownBy(() -> transfer(nubank, carteira, "10"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Conta arquivada não aceita movimentações");
        assertThatThrownBy(() -> transfer(carteira, nubank, "10"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void futureDateIsRejectedUsingTheUsersTimezone() {
        // Em São Paulo ainda é 13/11
        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(nubank.getId(), carteira.getId(),
                new BigDecimal("10.00"), LocalDate.parse("2026-11-14"), "Saque", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Uma movimentação paga não pode ter data no futuro");
    }

    @Test
    void deletingTheTransferRemovesItFromBothBalances() {
        TransferResponse response = transfer(nubank, carteira, "500");

        transactionService.delete(response.incomingTransactionId());
        transactionRepository.flush();

        assertThat(transactionRepository.movementsOf(nubank.getId())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.movementsOf(carteira.getId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private TransferResponse transfer(Account from, Account to, String amount) {
        return transferService.transfer(new TransferRequest(from.getId(), to.getId(), new BigDecimal(amount),
                LocalDate.parse("2026-11-10"), "  Saque  ", "   "));
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
