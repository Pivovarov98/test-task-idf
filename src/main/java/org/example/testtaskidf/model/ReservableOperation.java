package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable database projection, re-read after acquiring the account/category lock.
 *
 * @param id transaction identifier
 * @param account source account
 * @param category product or service
 * @param createdAt original creation instant, determining the historical limit and Moscow month
 * @param amountUsd calculated amount, null until conversion completes
 * @param conversionStatus PENDING, COMPLETED or UNSUPPORTED_CURRENCY
 * @param status persisted operation status
 * @param unavailableSince first uninterrupted bank error instant, null when the bank is responding
 * @param reservedUsd fixed USD amount, retained after release
 * @param exceeded whether occupied amount plus this reserve exceeded the limit, null before reservation
 * @param alreadyExceeded whether the limit was exceeded before this reserve, null before reservation
 */
public record ReservableOperation(UUID id, String account, ExpenseCategory category, Instant createdAt,
        BigDecimal amountUsd, String conversionStatus, BankOperationStatus status, Instant unavailableSince,
        BigDecimal reservedUsd, Boolean exceeded, Boolean alreadyExceeded) {
}
