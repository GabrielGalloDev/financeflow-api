package com.gabriel.financeflow.account.service;

import com.gabriel.financeflow.account.dto.AccountRequest;
import com.gabriel.financeflow.account.dto.AccountResponse;
import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.account.repository.AccountRepository;
import com.gabriel.financeflow.shared.exception.BusinessException;
import com.gabriel.financeflow.shared.exception.ConflictException;
import com.gabriel.financeflow.shared.exception.ResourceNotFoundException;
import com.gabriel.financeflow.shared.money.MoneyUtils;
import com.gabriel.financeflow.shared.security.CurrentUser;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private static final String DUPLICATE_NAME = "Já existe uma conta ativa com este nome";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository,
                          CurrentUser currentUser, Clock clock) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        UUID userId = currentUser.id();
        String name = normalizeName(request.name());

        if (accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedAtIsNull(userId, name)) {
            throw new ConflictException(DUPLICATE_NAME);
        }

        User owner = userRepository.getReferenceById(userId);

        Account account = new Account(
                owner,
                name,
                request.type(),
                normalizeInstitution(request.institution()),
                MoneyUtils.normalize(request.initialBalance())
        );

        return toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(boolean includeArchived) {
        UUID userId = currentUser.id();

        List<Account> accounts = includeArchived
                ? accountRepository.findAllByUserIdOrderByNameAsc(userId)
                : accountRepository.findAllByUserIdAndArchivedAtIsNullOrderByNameAsc(userId);

        return accounts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        return toResponse(findOwnedAccount(id));
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequest request) {
        Account account = findOwnedAccount(id);

        if (account.isArchived()) {
            throw new BusinessException("Conta arquivada não pode ser editada");
        }

        String name = normalizeName(request.name());

        if (accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedAtIsNullAndIdNot(
                currentUser.id(), name, account.getId())) {
            throw new ConflictException(DUPLICATE_NAME);
        }

        account.update(
                name,
                request.type(),
                normalizeInstitution(request.institution()),
                MoneyUtils.normalize(request.initialBalance())
        );

        return toResponse(account);
    }

    @Transactional
    public AccountResponse archive(UUID id) {
        Account account = findOwnedAccount(id);
        account.archive(Instant.now(clock));
        return toResponse(account);
    }

    @Transactional
    public AccountResponse unarchive(UUID id) {
        Account account = findOwnedAccount(id);

        if (accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedAtIsNullAndIdNot(
                currentUser.id(), account.getName(), account.getId())) {
            throw new ConflictException("Já existe outra conta ativa com o mesmo nome");
        }

        account.unarchive();
        return toResponse(account);
    }

    private Account findOwnedAccount(UUID id) {
        return accountRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
    }

    private BigDecimal currentBalanceOf(Account account) {
        return account.getInitialBalance();
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.from(account, currentBalanceOf(account));
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeInstitution(String institution) {
        if (institution == null || institution.isBlank()) {
            return null;
        }
        return institution.trim();
    }
}
