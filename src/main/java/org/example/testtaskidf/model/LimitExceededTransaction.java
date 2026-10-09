package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Successful exceeded operation with its original applied limit; reading never recalculates flags or rates.
 *
 * @param accountFrom source account
 * @param accountTo counterparty account
 * @param currencyShortname original transaction currency
 * @param sum original transaction amount
 * @param expenseCategory product or service
 * @param datetime operation creation instant
 * @param limitSum fixed historical limit amount in USD
 * @param limitDatetime user establishment instant, or creation month's Moscow start for a default
 * @param limitCurrencyShortname always USD
 */
public record LimitExceededTransaction(String accountFrom, String accountTo, String currencyShortname,
        BigDecimal sum, ExpenseCategory expenseCategory, Instant datetime, BigDecimal limitSum,
        Instant limitDatetime, String limitCurrencyShortname) {
}
