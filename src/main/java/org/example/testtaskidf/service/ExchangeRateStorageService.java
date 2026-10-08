package org.example.testtaskidf.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.example.testtaskidf.dto.ExchangeRatePayload;
import org.example.testtaskidf.repository.ExchangeRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates and commits the entire snapshot and job completion atomically. */
@Service
public class ExchangeRateStorageService {
    private final ExchangeRateRepository repository;
    private final Clock clock;

    public ExchangeRateStorageService(ExchangeRateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void save(LocalDate date, ExchangeRatePayload payload) {
        if (payload == null || !"USD".equals(payload.base()) || payload.timestamp() == null
                || payload.rates().isEmpty() || payload.rates().get("USD") == null
                || payload.rates().get("USD").compareTo(java.math.BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("invalid_provider_snapshot");
        }
        var publishedAt = Instant.ofEpochSecond(payload.timestamp());
        if (publishedAt.atOffset(ZoneOffset.UTC).toLocalDate().isAfter(date)
                || !date.isBefore(clock.instant().atOffset(ZoneOffset.UTC).toLocalDate())
                || payload.rates().entrySet().stream().anyMatch(rate -> !rate.getKey().matches("[A-Z]{3}")
                        || rate.getValue().signum() <= 0)) {
            throw new IllegalArgumentException("invalid_provider_snapshot");
        }
        repository.saveSnapshot(date, publishedAt, clock.instant(), payload.rates());
    }
}
