package org.example.testtaskidf.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.example.testtaskidf.exception.ExchangeRateFailure;
import org.example.testtaskidf.repository.ExchangeRateRepository;
import org.example.testtaskidf.service.client.OpenExchangeRatesClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateSchedulerServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-10-07");
    @Mock
    private ExchangeRateRepository repository;
    @Mock
    private OpenExchangeRatesClient client;
    @Mock
    private ExchangeRateStorageService storage;
    @Mock
    private CurrencyConversionService conversions;

    @Test
    void startupResumesBlockedJobsAndCatchesUpLastClosedDay() {
        when(client.isConfigured()).thenReturn(true);
        worker().resume();
        verify(repository).resumeBlocked(NOW);
        verify(repository).enqueue(DATE, NOW);
    }

    @Test
    void throttlingPausesFurtherRequestsUntilRetryAfter() {
        when(client.isConfigured()).thenReturn(true);
        when(repository.claim(NOW)).thenReturn(Optional.of(DATE));
        when(client.historical(DATE)).thenThrow(new ExchangeRateFailure("provider_http_429", false,
                Duration.ofMinutes(2)));
        var worker = worker();
        worker.process();
        worker.process();
        verify(client, times(1)).historical(DATE);
        verify(repository).failed(DATE, NOW.plusSeconds(120), "provider_http_429", false);
        verify(repository).pauseJobs(NOW.plusSeconds(120));
    }

    @Test
    void permanentFailureBlocksProviderUntilRestart() {
        when(client.isConfigured()).thenReturn(true);
        when(repository.claim(NOW)).thenReturn(Optional.of(DATE));
        when(client.historical(DATE)).thenThrow(new ExchangeRateFailure("provider_http_401", false, Duration.ZERO));
        var worker = worker();
        worker.process();
        worker.process();
        verify(client, times(1)).historical(DATE);
        verify(repository).failed(DATE, NOW.plusSeconds(30), "provider_http_401", true);
    }

    @Test
    void transientFailureUsesBoundedExponentialBackoff() {
        when(client.isConfigured()).thenReturn(true);
        when(repository.claim(NOW)).thenReturn(Optional.of(DATE));
        when(repository.attempts(DATE)).thenReturn(100);
        when(client.historical(DATE)).thenThrow(new ExchangeRateFailure("provider_http_503", true, Duration.ZERO));
        worker().process();
        verify(repository).failed(DATE, NOW.plusSeconds(3600), "provider_http_503", false);
    }

    @Test
    void missingKeyDoesNotClaimExternalWork() {
        worker().process();
        verify(repository, never()).claim(NOW);
    }

    private ExchangeRateSchedulerService worker() {
        return new ExchangeRateSchedulerService(repository, client, storage, conversions,
                Clock.fixed(NOW, ZoneOffset.UTC), true);
    }
}
