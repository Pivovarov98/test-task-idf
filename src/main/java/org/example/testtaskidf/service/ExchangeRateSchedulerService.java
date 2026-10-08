package org.example.testtaskidf.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.example.testtaskidf.exception.ExchangeRateFailure;
import org.example.testtaskidf.repository.ExchangeRateRepository;
import org.example.testtaskidf.service.client.OpenExchangeRatesClient;
import org.example.testtaskidf.util.ExchangeRateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Runs bounded external work outside DB transactions and resumes durable pending conversions. */
@Service
public class ExchangeRateSchedulerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeRateSchedulerService.class);
    private final ExchangeRateRepository repository;
    private final OpenExchangeRatesClient client;
    private final ExchangeRateStorageService storage;
    private final CurrencyConversionService conversions;
    private final Clock clock;
    private final boolean enabled;
    private volatile boolean providerBlocked;
    private volatile Instant providerPausedUntil = Instant.MIN;

    public ExchangeRateSchedulerService(ExchangeRateRepository repository, OpenExchangeRatesClient client,
            ExchangeRateStorageService storage, CurrencyConversionService conversions, Clock clock,
            @Value("${exchange-rates.enabled}") boolean enabled) {
        this.repository = repository;
        this.client = client;
        this.storage = storage;
        this.conversions = conversions;
        this.clock = clock;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resume() {
        if (enabled && client.isConfigured()) {
            repository.resumeBlocked(clock.instant());
            daily();
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Moscow")
    public void daily() {
        if (enabled) {
            repository.enqueue(ExchangeRateUtils.lastClosedDate(clock.instant()), clock.instant());
        }
    }

    @Scheduled(fixedDelayString = "${exchange-rates.worker-delay-ms}", initialDelay = 10000)
    public void process() {
        if (!enabled) {
            return;
        }
        try {
            for (var transaction : repository.pending(clock.instant())) {
                conversions.convert(transaction.id(), transaction.currency(), transaction.amount(),
                        transaction.occurredAt());
            }
            if (client.isConfigured() && !providerBlocked && !clock.instant().isBefore(providerPausedUntil)) {
                repository.claim(clock.instant()).ifPresent(this::fetch);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Currency worker failed; durable work will be resumed ({})",
                    exception.getClass().getSimpleName());
        }
    }

    private void fetch(LocalDate date) {
        try {
            storage.save(date, client.historical(date));
        } catch (ExchangeRateFailure failure) {
            boolean throttled = !failure.getRetryAfter().isZero();
            boolean blocked = !failure.isRetryable() && !throttled;
            long delaySeconds = Math.min(3600, 30L * (1L << Math.min(7, repository.attempts(date))));
            var delay = throttled ? failure.getRetryAfter() : Duration.ofSeconds(delaySeconds);
            var retryAt = clock.instant().plus(delay);
            repository.failed(date, retryAt, failure.getMessage(), blocked);
            if (throttled) {
                providerPausedUntil = retryAt;
                repository.pauseJobs(retryAt);
            }
            if (blocked) {
                providerBlocked = true;
            }
            LOGGER.warn("Exchange rate job {} deferred: {}", date, failure.getMessage());
        } catch (RuntimeException failure) {
            repository.failed(date, clock.instant().plusSeconds(300), "snapshot_processing_failed", false);
            LOGGER.warn("Exchange rate job {} failed during snapshot processing ({})", date,
                    failure.getClass().getSimpleName());
        }
    }
}
