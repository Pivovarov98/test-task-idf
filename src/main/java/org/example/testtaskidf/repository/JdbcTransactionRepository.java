package org.example.testtaskidf.repository;

import org.example.testtaskidf.model.Transaction;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Inserts transactions into PostgreSQL using bound JDBC parameters. */
@Repository
public class JdbcTransactionRepository implements TransactionRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** {@inheritDoc} */
    @Override
    public void save(Transaction transaction) {
        jdbcTemplate.update("""
                INSERT INTO transactions
                    (id, account_from, account_to, currency_shortname, sum, expense_category, datetime, received_at,
                    conversion_next_attempt_at, conversion_rate_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, transaction.id(), transaction.accountFrom(), transaction.accountTo(),
                transaction.currencyShortname(), transaction.sum(),
                ExpenseCategoryUtils.toCode(transaction.expenseCategory()),
                transaction.datetime(), transaction.receivedAt(), transaction.receivedAt(),
                org.example.testtaskidf.util.ExchangeRateUtils.targetDate(transaction.datetime().toInstant(),
                        transaction.receivedAt().toInstant()));
    }
}
