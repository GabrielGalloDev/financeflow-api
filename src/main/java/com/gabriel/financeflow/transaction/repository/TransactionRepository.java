package com.gabriel.financeflow.transaction.repository;

import com.gabriel.financeflow.transaction.entity.Transaction;
import com.gabriel.financeflow.transaction.entity.TransactionStatus;
import com.gabriel.financeflow.transaction.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<Transaction> findAllByTransferGroupIdAndDeletedAtIsNull(UUID transferGroupId);

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

    default BigDecimal movementsOf(UUID accountId) {
        return sumMovements(accountId, TransactionType.incoming(), TransactionStatus.PAID);
    }

    @Query("""
            SELECT new com.gabriel.financeflow.transaction.repository.AccountMovements(
                t.account.id,
                SUM(CASE WHEN t.type IN :incoming THEN t.amount ELSE -t.amount END))
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.status = :paid
              AND t.deletedAt IS NULL
            GROUP BY t.account.id
            """)
    List<AccountMovements> sumMovementsByAccount(@Param("userId") UUID userId,
                                                 @Param("incoming") Collection<TransactionType> incoming,
                                                 @Param("paid") TransactionStatus paid);

    default Map<UUID, BigDecimal> movementsByAccountOf(UUID userId) {
        return sumMovementsByAccount(userId, TransactionType.incoming(), TransactionStatus.PAID).stream()
                .collect(Collectors.toMap(AccountMovements::accountId, AccountMovements::total));
    }

    @Override
    @EntityGraph(attributePaths = {"account", "category"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);
}
