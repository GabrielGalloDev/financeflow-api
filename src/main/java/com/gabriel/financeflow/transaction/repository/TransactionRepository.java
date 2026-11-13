package com.gabriel.financeflow.transaction.repository;

import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** O JpaSpecificationExecutor traz o findAll(Specification, Pageable) que os filtros da 5.6 vão usar. */
public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /** O guarda: só as do usuário, e nunca as excluídas (excluída = 404). */
    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /** As duas pernas de uma transferência. */
    List<Transaction> findAllByTransferGroupIdAndDeletedAtIsNull(UUID transferGroupId);

    /**
     * Soma das movimentações de uma conta: + entradas, - saídas. Só as pagas e não excluídas.
     * COALESCE: sem nenhuma movimentação, o SUM devolve NULL; aqui vira 0.
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN t.type IN :incoming THEN t.amount ELSE -t.amount END), 0)
            FROM Transaction t
            WHERE t.account.id = :accountId
              AND t.status = :paid
              AND t.deletedAt IS NULL
            """)
    BigDecimal sumMovements(@Param("accountId") UUID accountId,
                            @Param("incoming") Collection<TransactionType> incoming,
                            @Param("paid") TransactionStatus paid);

    /** Atalho para o service: o quanto as movimentações pagas mudaram o saldo da conta. */
    default BigDecimal movementsOf(UUID accountId) {
        return sumMovements(accountId, TransactionType.incoming(), TransactionStatus.PAID);
    }
}
