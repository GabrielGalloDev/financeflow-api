package com.gabriel.financeflow.account.repository;

import com.gabriel.financeflow.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    List<Account> findAllByUserIdAndArchivedAtIsNullOrderByNameAsc(UUID userId);

    List<Account> findAllByUserIdOrderByNameAsc(UUID userId);

    boolean existsByUserIdAndNameIgnoreCaseAndArchivedAtIsNull(UUID userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndArchivedAtIsNullAndIdNot(UUID userId, String name, UUID id);
}
