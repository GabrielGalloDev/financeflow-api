package com.gabriel.financeflow.transaction.dto;

import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransferResponse(
        UUID transferGroupId,
        UUID outgoingTransactionId,
        UUID incomingTransactionId,
        TransactionResponse.Ref fromAccount,
        TransactionResponse.Ref toAccount,
        BigDecimal amount,
        LocalDate date,
        String description,
        String notes
) {
    public static TransferResponse from(Transaction outgoing, Transaction incoming) {
        if (outgoing.getType() != TransactionType.TRANSFER_OUT || incoming.getType() != TransactionType.TRANSFER_IN) {
            throw new IllegalArgumentException("Esperado: a perna de saída (TRANSFER_OUT) e a de entrada (TRANSFER_IN), nessa ordem");
        }

        return new TransferResponse(
                outgoing.getTransferGroupId(),
                outgoing.getId(),
                incoming.getId(),
                new TransactionResponse.Ref(outgoing.getAccount().getId(), outgoing.getAccount().getName()),
                new TransactionResponse.Ref(incoming.getAccount().getId(), incoming.getAccount().getName()),
                outgoing.getAmount(),
                outgoing.getDate(),
                outgoing.getDescription(),
                outgoing.getNotes()
        );
    }
}
