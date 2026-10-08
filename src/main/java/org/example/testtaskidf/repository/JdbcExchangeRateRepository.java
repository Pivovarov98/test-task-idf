package org.example.testtaskidf.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.example.testtaskidf.model.PendingConversion;
import org.example.testtaskidf.model.StoredExchangeRate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL cache, durable jobs and guarded conversion updates. */
@Repository
public class JdbcExchangeRateRepository implements ExchangeRateRepository {
    private final JdbcTemplate jdbc;

    public JdbcExchangeRateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasSnapshot(LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM exchange_rate_snapshots WHERE requested_date = ?)", Boolean.class, date));
    }

    public Optional<StoredExchangeRate> findRate(LocalDate date, String currency) {
        return jdbc.query("SELECT id, units_per_usd FROM exchange_rates WHERE requested_date = ? AND base_currency = ?",
                (row, index) -> new StoredExchangeRate(row.getObject("id", UUID.class),
                        row.getBigDecimal("units_per_usd")), date, currency).stream().findFirst();
    }

    public void enqueue(LocalDate date, Instant now) {
        jdbc.update("""
                INSERT INTO exchange_rate_jobs (requested_date, next_attempt_at)
                SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM exchange_rate_snapshots WHERE requested_date = ?)
                ON CONFLICT (requested_date) DO NOTHING
                """, date, now.atOffset(java.time.ZoneOffset.UTC), date);
    }

    public Optional<LocalDate> claim(Instant now) {
        return jdbc.query("""
                UPDATE exchange_rate_jobs SET lease_until = ?, attempts = attempts + 1
                WHERE requested_date = (
                    SELECT requested_date FROM exchange_rate_jobs
                    WHERE status = 'PENDING' AND next_attempt_at <= ? AND (lease_until IS NULL OR lease_until < ?)
                    ORDER BY next_attempt_at, requested_date DESC LIMIT 1 FOR UPDATE SKIP LOCKED
                ) RETURNING requested_date
                """, (row, index) -> row.getObject("requested_date", LocalDate.class),
                now.plusSeconds(60).atOffset(java.time.ZoneOffset.UTC), now.atOffset(java.time.ZoneOffset.UTC),
                now.atOffset(java.time.ZoneOffset.UTC)).stream().findFirst();
    }

    public void saveSnapshot(LocalDate requestedDate, Instant publishedAt, Instant fetchedAt,
            Map<String, BigDecimal> rates) {
        jdbc.update("""
                INSERT INTO exchange_rate_snapshots (requested_date, rate_date, published_at, fetched_at, provider)
                VALUES (?, ?, ?, ?, 'OPEN_EXCHANGE_RATES') ON CONFLICT DO NOTHING
                """, requestedDate, publishedAt.atOffset(java.time.ZoneOffset.UTC).toLocalDate(),
                publishedAt.atOffset(java.time.ZoneOffset.UTC), fetchedAt.atOffset(java.time.ZoneOffset.UTC));
        jdbc.batchUpdate("""
                INSERT INTO exchange_rates (id, requested_date, base_currency, units_per_usd)
                VALUES (?, ?, ?, ?) ON CONFLICT (requested_date, base_currency) DO NOTHING
                """, rates.entrySet(), rates.size(), (statement, rate) -> {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, requestedDate);
                    statement.setString(3, rate.getKey());
                    statement.setBigDecimal(4, rate.getValue());
                });
        jdbc.update("UPDATE exchange_rate_jobs SET status = 'DONE', lease_until = NULL WHERE requested_date = ?",
                requestedDate);
    }

    public void failed(LocalDate date, Instant retryAt, String code, boolean blocked) {
        jdbc.update("""
                UPDATE exchange_rate_jobs SET next_attempt_at = ?, lease_until = NULL, last_error = ?, status = ?
                WHERE requested_date = ?
                """, retryAt.atOffset(java.time.ZoneOffset.UTC), code, blocked ? "BLOCKED" : "PENDING", date);
    }

    public int attempts(LocalDate date) {
        var count = jdbc.queryForObject("SELECT attempts FROM exchange_rate_jobs WHERE requested_date = ?",
                Integer.class, date);
        return count == null ? 0 : count;
    }

    public void resumeBlocked(Instant now) {
        jdbc.update("UPDATE exchange_rate_jobs SET status = 'PENDING', next_attempt_at = ? WHERE status = 'BLOCKED'",
                now.atOffset(java.time.ZoneOffset.UTC));
    }

    public List<PendingConversion> pending(Instant now) {
        return jdbc.query("""
                SELECT id, currency_shortname, sum, datetime FROM transactions WHERE conversion_status = 'PENDING'
                AND conversion_next_attempt_at <= ? ORDER BY conversion_next_attempt_at, received_at, id LIMIT 100
                """, (row, index) -> new PendingConversion(row.getObject("id", UUID.class),
                row.getString("currency_shortname"), row.getBigDecimal("sum"),
                row.getObject("datetime", java.time.OffsetDateTime.class).toInstant()),
                now.atOffset(java.time.ZoneOffset.UTC));
    }

    public void defer(UUID id, Instant nextAttemptAt) {
        jdbc.update("""
                UPDATE transactions SET conversion_next_attempt_at = ? WHERE id = ? AND conversion_status = 'PENDING'
                """,
                nextAttemptAt.atOffset(java.time.ZoneOffset.UTC), id);
    }

    public void pauseJobs(Instant until) {
        jdbc.update("""
                UPDATE exchange_rate_jobs SET next_attempt_at = GREATEST(next_attempt_at, ?)
                WHERE status = 'PENDING'
                """, until.atOffset(java.time.ZoneOffset.UTC));
    }

    public void complete(UUID id, BigDecimal amount, UUID rateId) {
        jdbc.update("""
                UPDATE transactions SET amount_usd = ?, exchange_rate_id = ?, conversion_status = 'COMPLETED'
                WHERE id = ? AND conversion_status = 'PENDING'
                """, amount, rateId, id);
    }

    public void unsupported(UUID id) {
        jdbc.update("""
                UPDATE transactions SET conversion_status = 'UNSUPPORTED_CURRENCY'
                WHERE id = ? AND conversion_status = 'PENDING'
                """, id);
    }
}
