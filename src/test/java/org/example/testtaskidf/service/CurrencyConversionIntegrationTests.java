package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.example.testtaskidf.PostgresTestConfiguration;
import org.example.testtaskidf.dto.ExchangeRatePayload;
import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.exception.ExchangeRateFailure;
import org.example.testtaskidf.repository.ExchangeRateRepository;
import org.example.testtaskidf.service.client.OpenExchangeRatesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, CurrencyConversionIntegrationTests.FixedTimeConfiguration.class})
class CurrencyConversionIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-10-07");
    private static final String ACCOUNT = "7654321000";
    @Autowired
    private BankTransactionService bank;
    @Autowired
    private CurrencyConversionService conversions;
    @Autowired
    private ExchangeRateStorageService storage;
    @Autowired
    private ExchangeRateRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;
    @Autowired
    private MockMvc mvc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM transactions WHERE account_from = ?", ACCOUNT);
        jdbc.update("DELETE FROM accounts WHERE account_number = ?", ACCOUNT);
        jdbc.update("DELETE FROM exchange_rates");
        jdbc.update("DELETE FROM exchange_rate_snapshots");
        jdbc.update("DELETE FROM exchange_rate_jobs");
    }

    @Test
    void cachesExactQuotesConvertsBothCurrenciesAndDoesNotRecalculateCompletedRows() {
        storage.save(DATE, snapshot("2026-10-07T23:59:59Z", "500.123456789012345"));
        var kzt = bank.receive(request("KZT", "10000.45"));
        var rub = bank.receive(request("RUB", "1000.05"));
        assertThat(kzt.amountUsd()).isEqualTo(new BigDecimal("20.00"));
        assertThat(rub.amountUsd()).isEqualTo(new BigDecimal("10.00"));
        assertThat(kzt.conversionStatus()).isEqualTo("COMPLETED");
        assertThat(kzt.exchangeRateId()).isNotNull();
        assertThat(repository.findRate(DATE, "KZT").orElseThrow().unitsPerUsd())
                .isEqualByComparingTo("500.123456789012345");
        conversions.convert(kzt.id(), "KZT", new BigDecimal("20000"), request("KZT", "1").datetime().toInstant());
        assertThat(jdbc.queryForObject("SELECT amount_usd FROM transactions WHERE id = ?",
                BigDecimal.class, kzt.id())).isEqualByComparingTo("20.00");
        assertThat(repository.pending(NOW)).isEmpty();
    }

    @Test
    void usdCompletesImmediatelyWithoutAnyLoadingJob() {
        var response = bank.receive(request("USD", "1"));
        assertThat(response.amountUsd()).isEqualTo(new BigDecimal("1.00"));
        assertThat(response.exchangeRateId()).isNull();
        assertThat(response.conversionStatus()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rate_jobs", Long.class)).isZero();
    }

    @Test
    void outagePreservesTransactionsAndFreshWorkerResumesUsingOneSharedSnapshot() {
        var first = bank.receive(request("KZT", "1000"));
        var second = bank.receive(request("RUB", "1000"));
        assertThat(first.conversionStatus()).isEqualTo("PENDING");
        assertThat(first.amountUsd()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rate_jobs", Long.class)).isEqualTo(1L);
        var client = mock(OpenExchangeRatesClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.historical(DATE)).thenThrow(new ExchangeRateFailure("provider_http_503", true, Duration.ZERO));
        worker(client).process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE conversion_status = 'PENDING'",
                Long.class)).isEqualTo(2L);
        jdbc.update("UPDATE exchange_rate_jobs SET next_attempt_at = ?", NOW.atOffset(ZoneOffset.UTC));
        doReturn(snapshot("2026-10-07T23:59:59Z", "500")).when(client).historical(DATE);
        worker(client).process();
        jdbc.update("UPDATE transactions SET conversion_next_attempt_at = ?", NOW.atOffset(ZoneOffset.UTC));
        worker(client).process();
        verify(client, times(2)).historical(DATE);
        assertThat(jdbc.queryForObject("SELECT amount_usd FROM transactions WHERE id = ?", BigDecimal.class,
                first.id())).isEqualByComparingTo("2.00");
        assertThat(jdbc.queryForObject("SELECT amount_usd FROM transactions WHERE id = ?", BigDecimal.class,
                second.id())).isEqualByComparingTo("10.00");
    }

    @Test
    void previousCloseRetainsItsActualPublicationDate() {
        storage.save(DATE, snapshot("2026-10-06T23:59:59Z", "500"));
        assertThat(bank.receive(request("KZT", "500")).amountUsd()).isEqualTo(new BigDecimal("1.00"));
        assertThat(jdbc.queryForObject("SELECT rate_date FROM exchange_rate_snapshots WHERE requested_date = ?",
                LocalDate.class, DATE)).isEqualTo(DATE.minusDays(1));
    }

    @Test
    void previousSnapshotConvertsImmediatelyAndUnsupportedCurrencyIsDistinct() {
        storage.save(DATE.minusDays(1), snapshot("2026-10-06T23:59:59Z", "500"));
        assertThat(bank.receive(request("KZT", "500")).conversionStatus()).isEqualTo("COMPLETED");
        storage.save(DATE, snapshot("2026-10-07T23:59:59Z", "500"));
        var unsupported = bank.receive(request("EUR", "10"));
        assertThat(unsupported.conversionStatus()).isEqualTo("UNSUPPORTED_CURRENCY");
        assertThat(unsupported.amountUsd()).isNull();
    }

    @Test
    void invalidSnapshotDoesNotLeavePartialCacheOrCompleteJob() {
        repository.enqueue(DATE, NOW);
        assertThatThrownBy(() -> storage.save(DATE, snapshot("2026-10-08T00:00:00Z", "500")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.hasSnapshot(DATE)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM exchange_rate_jobs", String.class)).isEqualTo("PENDING");
    }

    @Test
    void databaseFailureRollsBackWholeSnapshotAndLeavesJobPending() {
        repository.enqueue(DATE, NOW);
        jdbc.execute("ALTER TABLE exchange_rates ADD CONSTRAINT test_reject_rub CHECK (base_currency <> 'RUB')");
        try {
            assertThatThrownBy(() -> storage.save(DATE, snapshot("2026-10-07T23:59:59Z", "500")))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(repository.hasSnapshot(DATE)).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rates", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM exchange_rate_jobs", String.class)).isEqualTo("PENDING");
        } finally {
            jdbc.execute("ALTER TABLE exchange_rates DROP CONSTRAINT test_reject_rub");
        }
    }

    @Test
    void leasesAllowOnlyOneConcurrentClaimAndRecoverAfterExpiration() throws Exception {
        repository.enqueue(DATE, NOW);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> repository.claim(NOW));
            var second = executor.submit(() -> repository.claim(NOW));
            long count = java.util.stream.Stream.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))
                    .filter(java.util.Optional::isPresent).count();
            assertThat(count).isEqualTo(1);
        }
        assertThat(repository.claim(NOW.plusSeconds(59))).isEmpty();
        assertThat(repository.claim(NOW.plusSeconds(61))).contains(DATE);
    }

    @Test
    void bankApiAcknowledgesPendingOperationWhenNoRatesAreAvailable() throws Exception {
        mvc.perform(post("/api/v1/bank/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"account_from":"7654321000","account_to":"9999999999","currency_shortname":"KZT",
                 "sum":500,"expense_category":"product","datetime":"2026-10-08T01:00:00+03:00"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.conversion_status").value("PENDING"))
                .andExpect(jsonPath("$.amount_usd").isEmpty());
        assertThat(jdbc.queryForObject("SELECT requested_date FROM exchange_rate_jobs", LocalDate.class))
                .isEqualTo(DATE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE account_from = ?",
                Long.class, ACCOUNT)).isEqualTo(1L);
    }

    private ExchangeRateSchedulerService worker(OpenExchangeRatesClient client) {
        return new ExchangeRateSchedulerService(repository, client, storage, conversions, clock, true);
    }

    @Test
    void bankApiReturnsProblemDetailAndRollsBackReceiptWhenDatabaseRejectsInsert() throws Exception {
        jdbc.execute("""
                ALTER TABLE transactions ADD CONSTRAINT test_reject_receipt CHECK (account_from <> '7654321000')
                """);
        try {
            mvc.perform(post("/api/v1/bank/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                    {"account_from":"7654321000","account_to":"9999999999","currency_shortname":"KZT",
                     "sum":500,"expense_category":"product","datetime":"2026-10-08T01:00:00+03:00"}
                    """))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.instance").value("/api/v1/bank/transactions"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE account_from = ?",
                    Long.class, ACCOUNT)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rate_jobs", Long.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE transactions DROP CONSTRAINT test_reject_receipt");
        }
    }

    @Test
    void swaggerDocumentsConversionFieldsAndBankApiReturnsCompletedUsd() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.TransactionResponse.properties.amount_usd").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionResponse.properties.exchange_rate_id").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionResponse.properties.conversion_status.enum")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "COMPLETED", "PENDING", "UNSUPPORTED_CURRENCY")));
        mvc.perform(post("/api/v1/bank/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"account_from":"7654321000","account_to":"9999999999","currency_shortname":"USD",
                 "sum":10.50,"expense_category":"service","datetime":"2026-10-08T01:00:00+03:00"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.conversion_status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount_usd").value(10.50));
    }

    private ExchangeRatePayload snapshot(String publishedAt, String kzt) {
        return new ExchangeRatePayload("USD", Instant.parse(publishedAt).getEpochSecond(),
                Map.of("USD", BigDecimal.ONE, "KZT", new BigDecimal(kzt), "RUB", new BigDecimal("100")));
    }

    private TransactionRequest request(String currency, String amount) {
        return new TransactionRequest(ACCOUNT, "9999999999", currency, new BigDecimal(amount), "product",
                OffsetDateTime.parse("2026-10-08T01:00:00+03:00"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTimeConfiguration {
        @Bean
        @Primary
        Clock conversionTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
