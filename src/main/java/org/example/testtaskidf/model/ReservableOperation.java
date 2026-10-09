package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable database projection used under the account/category lock. */
public record ReservableOperation(UUID id, String account, ExpenseCategory category, Instant createdAt,
        BigDecimal amountUsd, String conversionStatus, BankOperationStatus status, Instant unavailableSince,
        BigDecimal reservedUsd, Boolean exceeded, Boolean alreadyExceeded) {
}
