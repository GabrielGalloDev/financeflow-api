package com.gabriel.financeflow.transaction.service;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.category.entity.Category;
import com.gabriel.financeflow.shared.dto.PageResponse;
import com.gabriel.financeflow.shared.exception.BusinessException;
import com.gabriel.financeflow.shared.exception.InvalidRequestException;
import com.gabriel.financeflow.shared.exception.ResourceNotFoundException;
import com.gabriel.financeflow.shared.money.MoneyUtils;
import com.gabriel.financeflow.shared.security.CurrentUser;
import com.gabriel.financeflow.transaction.dto.TransactionFilter;
import com.gabriel.financeflow.transaction.dto.TransactionRequest;
import com.gabriel.financeflow.transaction.dto.TransactionResponse;
import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.repository.TransactionRepository;
import com.gabriel.financeflow.transaction.spec.TransactionSpecifications;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TransactionService {

    private static final String NOT_FOUND = "Movimentação não encontrada";
    private static final Set<String> SORTABLE = Set.of("date", "amount", "createdAt");

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final TransactionRules rules;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository,
                              CurrentUser currentUser, Clock clock, TransactionRules rules) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.clock = clock;
        this.rules = rules;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        TransactionStatus status = request.status() == null ? TransactionStatus.PAID : request.status();

        Account account = rules.findUsableAccount(request.accountId());
        Category category = rules.findUsableCategory(request.categoryId(), request.type());
        rules.assertDateAllowed(status, request.date());

        User owner = userRepository.getReferenceById(currentUser.id());

        Transaction transaction = Transaction.incomeOrExpense(
                owner,
                account,
                category,
                request.type(),
                status,
                MoneyUtils.normalize(request.amount()),
                request.date(),
                request.description().trim(),
                rules.normalizeNotes(request.notes())
        );

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id) {
        return TransactionResponse.from(findOwnedTransaction(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(TransactionFilter filter, Pageable pageable) {
        Specification<Transaction> spec = TransactionSpecifications.matching(currentUser.id(), filter);
        Page<Transaction> page = transactionRepository.findAll(spec, safePageable(pageable));
        return PageResponse.from(page, TransactionResponse::from);
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest request) {
        Transaction transaction = findOwnedTransaction(id);

        if (transaction.isTransfer()) {
            throw new BusinessException("Transferências não podem ser editadas: exclua e crie outra");
        }

        rules.assertAccountActive(transaction.getAccount());

        TransactionStatus status = request.status() == null ? transaction.getStatus() : request.status();

        Account account = rules.findUsableAccount(request.accountId());
        Category category = rules.findUsableCategory(request.categoryId(), request.type());
        rules.assertDateAllowed(status, request.date());

        transaction.update(
                account,
                category,
                request.type(),
                status,
                MoneyUtils.normalize(request.amount()),
                request.date(),
                request.description().trim(),
                rules.normalizeNotes(request.notes())
        );

        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(UUID id) {
        Transaction transaction = findOwnedTransaction(id);
        Instant now = Instant.now(clock);

        List<Transaction> toDelete = transaction.isTransfer()
                ? transactionRepository.findAllByTransferGroupIdAndDeletedAtIsNull(transaction.getTransferGroupId())
                : List.of(transaction);

        toDelete.forEach(item -> rules.assertAccountActive(item.getAccount()));
        toDelete.forEach(item -> item.softDelete(now));
    }

    @Transactional
    public TransactionResponse pay(UUID id) {
        Transaction transaction = findOwnedTransaction(id);

        if (transaction.getStatus() == TransactionStatus.PAID) {
            return TransactionResponse.from(transaction);
        }

        rules.assertAccountActive(transaction.getAccount());

        transaction.markAsPaid(rules.todayForCurrentUser());

        return TransactionResponse.from(transaction);
    }

    private Pageable safePageable(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidRequestException("Não é possível ordenar por '" + order.getProperty()
                        + "'. Use: date, amount ou createdAt");
            }
        }

        Sort sort = pageable.getSort()
                .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                .and(Sort.by("id"));

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private Transaction findOwnedTransaction(UUID id) {
        return transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}
