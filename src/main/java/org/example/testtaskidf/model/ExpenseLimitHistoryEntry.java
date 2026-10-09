package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only history entry, including implicit defaults without a persisted UUID.
 *
 * @param id persisted user limit UUID, or null for a default
 * @param account source account
 * @param expenseCategory product or service
 * @param amount monthly USD limit, including zero
 * @param currency always USD
 * @param establishedAt user establishment instant, or Moscow month start for a default
 */
public record ExpenseLimitHistoryEntry(UUID id, String account, ExpenseCategory expenseCategory,
        BigDecimal amount, String currency, Instant establishedAt) {
}
