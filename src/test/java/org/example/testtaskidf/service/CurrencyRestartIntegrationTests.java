package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.example.testtaskidf.TestTaskIdfApplication;
import org.example.testtaskidf.dto.ExchangeRatePayload;
import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.repository.ExchangeRateRepository;
import org.example.testtaskidf.service.client.OpenExchangeRatesClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CurrencyRestartIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-10-07");

    @Test
    void pendingReceiptAndAbandonedLeaseSurviveFullApplicationContextRestart() {
        try (var postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            UUID id;
            try (var first = application(postgres)) {
                var response = first.getBean(BankTransactionService.class).receive(new TransactionRequest(
                        "6543210000", "9999999999", "KZT", new BigDecimal("1000"), "product",
                        OffsetDateTime.parse("2026-10-08T01:00:00+03:00")));
                id = response.id();
                assertThat(response.conversionStatus()).isEqualTo("PENDING");
                var lifecycle = first.getBean(OperationLifecycleService.class);
                lifecycle.configureStub(id, "ERROR");
                lifecycle.poll(id);
                assertThat(first.getBean(ExchangeRateRepository.class).claim(NOW)).contains(DATE);
            }
            try (var second = application(postgres)) {
                var jdbc = second.getBean(JdbcTemplate.class);
                var repository = second.getBean(ExchangeRateRepository.class);
                assertThat(jdbc.queryForObject("SELECT conversion_status FROM transactions WHERE id = ?",
                        String.class, id)).isEqualTo("PENDING");
                assertThat(jdbc.queryForObject("SELECT bank_unavailable_since FROM transactions WHERE id = ?",
                        OffsetDateTime.class, id).toInstant()).isEqualTo(NOW);
                assertThat(repository.claim(NOW.plusSeconds(59))).isEmpty();
                assertThat(repository.claim(NOW.plusSeconds(61))).contains(DATE);
                second.getBean(ExchangeRateStorageService.class).save(DATE, new ExchangeRatePayload("USD",
                        Instant.parse("2026-10-07T23:59:59Z").getEpochSecond(),
                        Map.of("USD", BigDecimal.ONE, "KZT", new BigDecimal("500"))));
                var worker = new ExchangeRateSchedulerService(repository, mock(OpenExchangeRatesClient.class),
                        second.getBean(ExchangeRateStorageService.class),
                        second.getBean(CurrencyConversionService.class),
                        Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC), true);
                worker.process();
                assertThat(jdbc.queryForObject("SELECT amount_usd FROM transactions WHERE id = ?",
                        BigDecimal.class, id)).isEqualByComparingTo("2.00");
                assertThat(jdbc.queryForObject("SELECT conversion_status FROM transactions WHERE id = ?",
                        String.class, id)).isEqualTo("COMPLETED");
                var lifecycle = second.getBean(OperationLifecycleService.class);
                lifecycle.reconcile(id);
                assertThat(lifecycle.state(id).reservedUsd()).isEqualByComparingTo("2.00");
                var afterTimeout = new OperationLifecycleService(
                        second.getBean(org.example.testtaskidf.repository.OperationRepository.class),
                        second.getBean(ExpenseLimitService.class),
                        Clock.fixed(NOW.plusSeconds(10800), ZoneOffset.UTC),
                        java.time.Duration.ofHours(3), java.time.Duration.ofMinutes(15));
                new org.springframework.transaction.support.TransactionTemplate(
                        second.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                        .executeWithoutResult(tx -> afterTimeout.poll(id));
                assertThat(lifecycle.state(id).operationStatus()).isEqualTo("TIMED_OUT");
                assertThat(lifecycle.state(id).reservationActive()).isFalse();
                lifecycle.notify(new org.example.testtaskidf.dto.BankNotificationRequest(id, "SUCCEEDED",
                        NOW.plusSeconds(10801).atOffset(ZoneOffset.UTC)));
                assertThat(lifecycle.state(id).reservedUsd()).isEqualByComparingTo("2.00");
                assertThat(lifecycle.state(id).reservationActive()).isTrue();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Long.class)).isEqualTo(1L);
            }
        }
    }

    private ConfigurableApplicationContext application(PostgreSQLContainer postgres) {
        return new SpringApplicationBuilder(TestTaskIdfApplication.class, FixedTimeConfiguration.class)
                .web(WebApplicationType.NONE).profiles("test").run(
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--exchange-rates.enabled=false", "--exchange-rates.app-id=");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTimeConfiguration {
        @Bean
        @Primary
        Clock restartTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
