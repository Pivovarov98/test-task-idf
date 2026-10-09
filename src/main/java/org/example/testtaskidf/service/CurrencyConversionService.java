package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.example.testtaskidf.model.ConversionResult;
import org.example.testtaskidf.repository.ExchangeRateRepository;
import org.example.testtaskidf.util.ExchangeRateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Receives no external dependencies: missing rates leave durable pending work. */
@Service
public class CurrencyConversionService {
    private final ExchangeRateRepository repository;
    private final Clock clock;

    public CurrencyConversionService(ExchangeRateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ConversionResult convert(UUID id, String currency, BigDecimal amount, Instant occurredAt) {
        if ("USD".equals(currency)) {
            var converted = ExchangeRateUtils.toUsd(amount, BigDecimal.ONE);
            repository.complete(id, converted, null);
            return new ConversionResult(converted, "COMPLETED", null);
        }
        var date = repository.conversionDate(id);
        var rate = repository.findRate(date, currency);
        if (rate.isEmpty() && !repository.hasSnapshot(date)) {
            repository.enqueue(date, clock.instant());
            rate = repository.findPreviousRate(date, currency);
        }
        if (rate.isPresent()) {
            var quote = rate.orElseThrow();
            var converted = ExchangeRateUtils.toUsd(amount, quote.unitsPerUsd());
            repository.complete(id, converted, quote.id());
            return new ConversionResult(converted, "COMPLETED", quote.id());
        }
        if (repository.hasSnapshot(date)) {
            repository.unsupported(id);
            return new ConversionResult(null, "UNSUPPORTED_CURRENCY", null);
        }
        repository.enqueue(date, clock.instant());
        repository.defer(id, clock.instant().plusSeconds(30));
        return new ConversionResult(null, "PENDING", null);
    }
}
