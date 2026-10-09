package org.example.testtaskidf.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.example.testtaskidf.model.LimitExceededTransaction;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads completed successful exceedances and joins their saved limit IDs, never the current limit. */
@Repository
public class LimitExceededTransactionRepository {
    private static final String ELIGIBLE = """
            FROM transactions WHERE account_from = ? AND operation_status = 'SUCCEEDED'
                AND limit_check_status = 'COMPLETED' AND limit_exceeded = TRUE
            """;
    private final JdbcTemplate jdbc;

    public LimitExceededTransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns a count with exactly the same eligibility predicate as page retrieval. */
    public long count(String account) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) " + ELIGIBLE, Long.class, account));
    }

    /**
     * Paginates before joining limits; applied_limit_usd retains the reservation-time amount.
     * An implicit default uses the operation's Moscow month start, including after late bank success.
     *
     * @param account source account
     * @param offset nonnegative row offset
     * @param size bounded page size
     * @return newest-first page with creation-sequence tie breaking
     */
    public List<LimitExceededTransaction> findPage(String account, long offset, int size) {
        return jdbc.query("WITH eligible AS (SELECT * " + ELIGIBLE + """
                ORDER BY datetime DESC, operation_sequence DESC LIMIT ? OFFSET ?
                )
                SELECT t.account_from, t.account_to, t.currency_shortname, t.sum, t.expense_category, t.datetime,
                    t.applied_limit_usd AS limit_sum,
                    COALESCE(l.established_at,
                        date_trunc('month', t.datetime AT TIME ZONE INTERVAL '+03:00')
                            AT TIME ZONE INTERVAL '+03:00') AS limit_datetime,
                    COALESCE(l.currency, 'USD') AS limit_currency_shortname
                FROM eligible t LEFT JOIN expense_limits l ON l.id = t.applied_limit_id
                ORDER BY t.datetime DESC, t.operation_sequence DESC
                """, (row, index) -> new LimitExceededTransaction(row.getString("account_from"),
                row.getString("account_to"), row.getString("currency_shortname"), row.getBigDecimal("sum"),
                ExpenseCategoryUtils.fromCode(row.getString("expense_category")),
                row.getObject("datetime", OffsetDateTime.class).toInstant(), row.getBigDecimal("limit_sum"),
                row.getObject("limit_datetime", OffsetDateTime.class).toInstant(),
                row.getString("limit_currency_shortname")), account, size, offset);
    }
}
