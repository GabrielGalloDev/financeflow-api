package com.gabriel.financeflow.transaction.repository;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountMovements(UUID accountId, BigDecimal total) {
}
