package org.example.testtaskidf.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.example.testtaskidf.dto.OperationState;
import org.example.testtaskidf.model.BankOperationStatus;
import org.example.testtaskidf.model.ReservableOperation;
import org.example.testtaskidf.model.MonthPeriod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized PostgreSQL operations; caller owns transaction and account/category lock. */
@Repository
public class OperationRepository {
    private final JdbcTemplate jdbc;

    public OperationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReservableOperation> find(UUID id) {
        return jdbc.query("SELECT * FROM transactions WHERE id = ?", (row, index) -> new ReservableOperation(
                row.getObject("id", UUID.class), row.getString("account_from"),
                org.example.testtaskidf.util.ExpenseCategoryUtils.fromCode(row.getString("expense_category")),
                row.getObject("datetime", java.time.OffsetDateTime.class).toInstant(), row.getBigDecimal("amount_usd"),
                row.getString("conversion_status"), BankOperationStatus.valueOf(row.getString("operation_status")),
                row.getObject("bank_unavailable_since", java.time.OffsetDateTime.class) == null ? null
                        : row.getObject("bank_unavailable_since", java.time.OffsetDateTime.class).toInstant(),
                row.getBigDecimal("reserved_usd"), (Boolean) row.getObject("exceeded_at_reservation"),
                (Boolean) row.getObject("already_exceeded_before")), id).stream().findFirst();
    }

    public void lock(ReservableOperation operation) {
        lock(operation.account(), operation.category());
    }

    public void lock(String account, org.example.testtaskidf.model.ExpenseCategory category) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class,
                org.example.testtaskidf.util.AccountLockUtils.key(account, category));
    }

    public boolean isLate(String account, String category, Instant createdAt, MonthPeriod month) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM transactions WHERE account_from = ? AND expense_category = ?
                    AND datetime > ? AND datetime >= ? AND datetime < ? AND reserved_usd IS NOT NULL)
                """, Boolean.class, account, category, createdAt.atOffset(java.time.ZoneOffset.UTC),
                month.startInclusive().atOffset(java.time.ZoneOffset.UTC),
                month.endExclusive().atOffset(java.time.ZoneOffset.UTC)));
    }

    public List<UUID> unreserved(ReservableOperation operation, MonthPeriod month) {
        return jdbc.query("""
                SELECT id FROM transactions WHERE account_from = ? AND expense_category = ?
                    AND datetime >= ? AND datetime < ? AND reserved_usd IS NULL
                    AND operation_status IN ('PROCESSING', 'SUCCEEDED') ORDER BY datetime, operation_sequence
                """, (row, index) -> row.getObject("id", UUID.class), operation.account(),
                org.example.testtaskidf.util.ExpenseCategoryUtils.toCode(operation.category()),
                month.startInclusive().atOffset(java.time.ZoneOffset.UTC),
                month.endExclusive().atOffset(java.time.ZoneOffset.UTC));
    }

    public BigDecimal occupied(ReservableOperation operation, MonthPeriod month) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(reserved_usd), 0) FROM transactions WHERE account_from = ? AND expense_category = ?
                    AND datetime >= ? AND datetime < ? AND reservation_active = TRUE
                """, BigDecimal.class, operation.account(),
                org.example.testtaskidf.util.ExpenseCategoryUtils.toCode(operation.category()),
                month.startInclusive().atOffset(java.time.ZoneOffset.UTC),
                month.endExclusive().atOffset(java.time.ZoneOffset.UTC));
    }

    public void reserve(UUID id, BigDecimal amount, BigDecimal limit, UUID limitId, boolean exceeded, boolean before) {
        jdbc.update("""
                UPDATE transactions SET reserved_usd = ?, reservation_active = TRUE, applied_limit_usd = ?,
                    applied_limit_id = ?, exceeded_at_reservation = ?, already_exceeded_before = ?,
                    limit_exceeded = CASE WHEN operation_status = 'SUCCEEDED' THEN ? ELSE FALSE END,
                    limit_check_status = CASE WHEN operation_status = 'SUCCEEDED'
                        THEN 'COMPLETED' ELSE 'PENDING' END
                WHERE id = ? AND reserved_usd IS NULL
                """, amount, limit, limitId, exceeded, before, exceeded, id);
    }

    public void finish(ReservableOperation operation, BankOperationStatus status, Instant completedAt) {
        boolean succeeded = status == BankOperationStatus.SUCCEEDED;
        boolean flag = succeeded ? Boolean.TRUE.equals(operation.exceeded())
                : Boolean.TRUE.equals(operation.alreadyExceeded());
        jdbc.update("""
                UPDATE transactions SET operation_status = ?, completed_at = ?, limit_exceeded = ?,
                    limit_check_status = ?, reservation_active = ?, bank_unavailable_since = NULL
                WHERE id = ?
                """, status.name(), completedAt.atOffset(java.time.ZoneOffset.UTC), flag,
                succeeded && operation.reservedUsd() == null ? "PENDING" : "COMPLETED",
                succeeded && operation.reservedUsd() != null, operation.id());
    }

    public OperationState state(UUID id) {
        return jdbc.queryForObject("SELECT * FROM transactions WHERE id = ?", (row, index) -> new OperationState(
                row.getString("operation_status"), row.getObject("completed_at", java.time.OffsetDateTime.class),
                row.getBoolean("limit_exceeded"), row.getString("limit_check_status"),
                row.getBigDecimal("reserved_usd"),
                row.getBoolean("reservation_active")), id);
    }

    public void stub(UUID id, BankOperationStatus status) {
        jdbc.update("UPDATE transactions SET bank_stub_status = ? WHERE id = ?", status.name(), id);
    }

    public BankOperationStatus stub(UUID id) {
        return BankOperationStatus.valueOf(jdbc.queryForObject(
                "SELECT bank_stub_status FROM transactions WHERE id = ?", String.class, id));
    }

    public List<UUID> due(Instant now) {
        return jdbc.query("""
                SELECT id FROM transactions WHERE operation_status = 'PROCESSING' AND next_bank_poll_at <= ?
                ORDER BY next_bank_poll_at LIMIT 100
                """, (row, index) -> row.getObject("id", UUID.class), now.atOffset(java.time.ZoneOffset.UTC));
    }

    public void poll(UUID id, Instant next, Instant unavailableSince) {
        jdbc.update("""
                UPDATE transactions SET next_bank_poll_at = ?, bank_unavailable_since = ? WHERE id = ?
                """, next.atOffset(java.time.ZoneOffset.UTC),
                unavailableSince == null ? null : unavailableSince.atOffset(java.time.ZoneOffset.UTC), id);
    }

    public List<UUID> needsReservation() {
        return jdbc.query("""
                SELECT id FROM transactions WHERE reserved_usd IS NULL AND conversion_status = 'COMPLETED'
                    AND operation_status IN ('PROCESSING', 'SUCCEEDED') ORDER BY datetime, operation_sequence LIMIT 100
                """, (row, index) -> row.getObject("id", UUID.class));
    }
}
