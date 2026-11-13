package com.gabriel.financeflow.transaction.service;

import com.gabriel.financeflow.account.entity.Account;
import com.gabriel.financeflow.shared.money.MoneyUtils;
import com.gabriel.financeflow.shared.security.CurrentUser;
import com.gabriel.financeflow.transaction.dto.TransferRequest;
import com.gabriel.financeflow.transaction.dto.TransferResponse;
import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import com.gabriel.financeflow.transaction.repository.TransactionRepository;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class TransferService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final TransactionRules rules;

    public TransferService(TransactionRepository transactionRepository, UserRepository userRepository,
                           CurrentUser currentUser, TransactionRules rules) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.rules = rules;
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        Account from = rules.findUsableAccount(request.fromAccountId());
        Account to = rules.findUsableAccount(request.toAccountId());
        rules.assertDateAllowed(TransactionStatus.PAID, request.date());

        UUID group = UUID.randomUUID();
        User owner = userRepository.getReferenceById(currentUser.id());
        BigDecimal amount = MoneyUtils.normalize(request.amount());
        LocalDate date = request.date();
        String description = request.description().trim();
        String notes = rules.normalizeNotes(request.notes());

        Transaction outgoing = transactionRepository.save(Transaction.transferLeg(
                owner, from, TransactionType.TRANSFER_OUT, amount, date, description, notes, group));

        Transaction incoming = transactionRepository.save(Transaction.transferLeg(
                owner, to, TransactionType.TRANSFER_IN, amount, date, description, notes, group));

        return TransferResponse.from(outgoing, incoming);
    }
}
