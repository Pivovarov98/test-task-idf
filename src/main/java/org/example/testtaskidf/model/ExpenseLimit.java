package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable historical monthly limit for one account and expense category. */
public record ExpenseLimit(UUID id, String account, ExpenseCategory expenseCategory,
                           BigDecimal amount, String currency, Instant establishedAt) {
    public ExpenseLimit {
        Objects.requireNonNull(id);
        Objects.requireNonNull(account);
        Objects.requireNonNull(expenseCategory);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(establishedAt);
        if (!account.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("Account must contain exactly 10 digits");
        }
        if (amount.signum() < 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException(
                    "Limit must be nonnegative with at most 17 integer and 2 decimal digits");
        }
        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException("Limit currency must be USD");
        }
    }
}
