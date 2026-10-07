package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.util.Optional;

/** Applied amount in USD; empty configuredLimit means the implicit default, without a fictitious date or ID. */
public record ResolvedExpenseLimit(BigDecimal amount, String currency, Optional<ExpenseLimit> configuredLimit) {
}
