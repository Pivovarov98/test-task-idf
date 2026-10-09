package org.example.testtaskidf.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.example.testtaskidf.model.ExpenseCategory;
import org.example.testtaskidf.model.ExpenseLimit;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.example.testtaskidf.util.AccountLockUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter that stores each limit as a new historical row. */
@Repository
public class JdbcExpenseLimitRepository implements ExpenseLimitRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcExpenseLimitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryLock(String account, ExpenseCategory category) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(?)",
                Boolean.class, AccountLockUtils.key(account, category)));
    }

    @Override
    public void save(ExpenseLimit limit) {
        jdbcTemplate.update("""
                INSERT INTO expense_limits (id, account, expense_category, amount, currency, established_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, limit.id(), limit.account(), ExpenseCategoryUtils.toCode(limit.expenseCategory()),
                limit.amount(), limit.currency(), limit.establishedAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public Optional<ExpenseLimit> findLatest(String account, ExpenseCategory category, Instant asOf) {
        return jdbcTemplate.query("""
                SELECT l.id, l.account, l.expense_category, l.amount, l.currency, l.established_at
                FROM expense_limits l JOIN (
                    SELECT account, expense_category, MAX(established_at) AS established_at
                    FROM expense_limits
                    WHERE account = ? AND expense_category = ? AND established_at <= ?
                    GROUP BY account, expense_category
                ) latest ON latest.account = l.account AND latest.expense_category = l.expense_category
                    AND latest.established_at = l.established_at
                ORDER BY l.revision DESC
                LIMIT 1
                """, (row, index) -> new ExpenseLimit(row.getObject("id", UUID.class), row.getString("account"),
                ExpenseCategoryUtils.fromCode(row.getString("expense_category")), row.getBigDecimal("amount"),
                row.getString("currency"), row.getObject("established_at", OffsetDateTime.class).toInstant()),
                account, ExpenseCategoryUtils.toCode(category), asOf.atOffset(ZoneOffset.UTC))
                .stream().findFirst();
    }
}
