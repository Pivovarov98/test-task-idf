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

/** Storage contract for daily quotes, durable loading and transaction conversion. */
public interface ExchangeRateRepository {
    boolean hasSnapshot(LocalDate date);

    Optional<StoredExchangeRate> findRate(LocalDate date, String currency);
    Optional<StoredExchangeRate> findPreviousRate(LocalDate date, String currency);
    LocalDate conversionDate(UUID id);

    void enqueue(LocalDate date, Instant now);

    Optional<LocalDate> claim(Instant now);

    void saveSnapshot(LocalDate date, Instant publishedAt, Instant fetchedAt, Map<String, BigDecimal> rates);

    void failed(LocalDate date, Instant retryAt, String code, boolean blocked);

    int attempts(LocalDate date);

    void resumeBlocked(Instant now);

    List<PendingConversion> pending(Instant now);

    void defer(UUID id, Instant nextAttemptAt);

    void pauseJobs(Instant until);

    void complete(UUID id, BigDecimal amount, UUID rateId);

    void unsupported(UUID id);
}
