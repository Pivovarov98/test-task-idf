package org.example.testtaskidf.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.example.testtaskidf.model.ExpenseLimitHistoryEntry;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads user history and implicit defaults without creating or changing any limit records. */
@Repository
public class ExpenseLimitHistoryRepository {
    private static final String HISTORY = """
            WITH selected_account AS (
                SELECT account_number, created_at FROM accounts WHERE account_number = ?
            ), first_limits AS (
                SELECT l.account, l.expense_category, MIN(l.established_at) AS first_established_at
                FROM expense_limits l JOIN selected_account a ON a.account_number = l.account
                GROUP BY l.account, l.expense_category
            ), used_months AS (
                SELECT t.account_from AS account, t.expense_category,
                    date_trunc('month', t.datetime AT TIME ZONE INTERVAL '+03:00')
                        AT TIME ZONE INTERVAL '+03:00' AS month_start,
                    MIN(t.datetime) AS first_operation_at
                FROM transactions t JOIN selected_account a ON a.account_number = t.account_from
                GROUP BY t.account_from, t.expense_category,
                    date_trunc('month', t.datetime AT TIME ZONE INTERVAL '+03:00')
            ), default_months AS (
                SELECT a.account_number AS account, c.expense_category,
                    date_trunc('month', a.created_at AT TIME ZONE INTERVAL '+03:00')
                        AT TIME ZONE INTERVAL '+03:00' AS established_at
                FROM selected_account a CROSS JOIN (VALUES ('product'), ('service')) c(expense_category)
                UNION
                SELECT m.account, m.expense_category, m.month_start
                FROM used_months m LEFT JOIN first_limits f
                    ON f.account = m.account AND f.expense_category = m.expense_category
                WHERE f.first_established_at IS NULL OR m.first_operation_at < f.first_established_at
            ), history AS (
                SELECT l.id, l.account, l.expense_category, l.amount, l.currency, l.established_at, l.revision
                FROM expense_limits l JOIN selected_account a ON a.account_number = l.account
                UNION ALL
                SELECT NULL::uuid, d.account, d.expense_category, 1000.00::numeric, 'USD', d.established_at, 0::bigint
                FROM default_months d
            )
            """;
    private final JdbcTemplate jdbc;

    public ExpenseLimitHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns whether the account exists; reading never registers an unknown account. */
    public boolean accountExists(String account) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM accounts WHERE account_number = ?)", Boolean.class, account));
    }

    /** Counts both user and default entries using the same grouped history as page retrieval. */
    public long count(String account) {
        return Objects.requireNonNull(jdbc.queryForObject(
                HISTORY + "SELECT COUNT(*) FROM history", Long.class, account));
    }

    /** Returns a bounded page, ordered by establishment time, user revision and category. */
    public List<ExpenseLimitHistoryEntry> findPage(String account, long offset, int size) {
        return jdbc.query(HISTORY + """
                SELECT id, account, expense_category, amount, currency, established_at FROM history
                ORDER BY established_at DESC, revision DESC, expense_category ASC
                LIMIT ? OFFSET ?
                """, (row, index) -> new ExpenseLimitHistoryEntry(row.getObject("id", UUID.class),
                row.getString("account"), ExpenseCategoryUtils.fromCode(row.getString("expense_category")),
                row.getBigDecimal("amount"), row.getString("currency"),
                row.getObject("established_at", OffsetDateTime.class).toInstant()), account, size, offset);
    }
}

