package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable transaction enforcing domain constraints independently of HTTP validation.
 * All fields are required; accounts contain ten digits, currency must be supported by Java,
 * and the positive amount has at most seventeen integer and two fractional digits.
 *
 * @param id transaction identifier
 * @param accountFrom source account
 * @param accountTo destination account
 * @param currencyShortname ISO 4217 currency code
 * @param sum positive monetary amount
 * @param expenseCategory domain category
 * @param datetime occurrence time
 * @param receivedAt server receipt time
 */
public record Transaction(UUID id, String accountFrom, String accountTo, String currencyShortname,
                          BigDecimal sum, ExpenseCategory expenseCategory,
                          OffsetDateTime datetime, OffsetDateTime receivedAt) {
    public Transaction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(accountFrom);
        Objects.requireNonNull(accountTo);
        Objects.requireNonNull(sum);
        Objects.requireNonNull(expenseCategory);
        Objects.requireNonNull(datetime);
        Objects.requireNonNull(receivedAt);
        Currency.getInstance(Objects.requireNonNull(currencyShortname));
        if (!accountFrom.matches("[0-9]{10}") || !accountTo.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("Accounts must contain exactly 10 digits");
        }
        if (sum.signum() <= 0 || sum.scale() > 2 || sum.precision() - sum.scale() > 17) {
            throw new IllegalArgumentException("Amount must be positive with at most 17 integer and 2 decimal digits");
        }
    }
}
