package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.example.testtaskidf.PostgresTestConfiguration;
import org.example.testtaskidf.dto.BankNotificationRequest;
import org.example.testtaskidf.dto.ExchangeRatePayload;
import org.example.testtaskidf.dto.ExpenseLimitRequest;
import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.dto.TransactionResponse;
import org.example.testtaskidf.repository.ExpenseLimitHistoryRepository;
import org.example.testtaskidf.repository.LimitExceededTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, ClientQueriesIntegrationTests.TimeConfiguration.class})
class ClientQueriesIntegrationTests {
    private static final String ACCOUNT = "1234500000";
    private static final String BASE = "/api/v1/client/accounts/" + ACCOUNT;
    private static final String LIMITS = BASE + "/limits";
    private static final String EXCEEDED = BASE + "/transactions/limit-exceeded";
    private static final Instant START = Instant.parse("2021-12-31T21:00:00Z");
    @Autowired
    private AccountRegistrationService accounts;
    @Autowired
    private ClientLimitService limits;
    @Autowired
    private ExpenseLimitHistoryService history;
    @Autowired
    private LimitExceededTransactionService exceeded;
    @Autowired
    private BankTransactionService bank;
    @Autowired
    private OperationLifecycleService lifecycle;
    @Autowired
    private ExchangeRateStorageService storage;
    @Autowired
    private CurrencyConversionService conversions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private MutableClock clock;

    @MockitoSpyBean
    private ExpenseLimitHistoryRepository historyRepository;
    @MockitoSpyBean
    private LimitExceededTransactionRepository exceededRepository;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM expense_limits");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM exchange_rates");
        jdbc.update("DELETE FROM exchange_rate_snapshots");
        jdbc.update("DELETE FROM exchange_rate_jobs");
        clock.set(START);
        accounts.register(ACCOUNT);
    }

    @Test
    void newAccountHasBothDefaultLimitsAtMoscowMonthStartWithoutPersistingHistory() throws Exception {
        mvc.perform(get(LIMITS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elements").value(2)).andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.content[0].expense_category").value("product"))
                .andExpect(jsonPath("$.content[1].expense_category").value("service"))
                .andExpect(jsonPath("$.content[0].id").isEmpty())
                .andExpect(jsonPath("$.content[0].amount").value(1000))
                .andExpect(jsonPath("$.content[0].currency").value("USD"))
                .andExpect(jsonPath("$.content[0].established_at").value("2022-01-01T00:00:00+03:00"));
        history.getAll(ACCOUNT, 0, 20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_limits", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class)).isEqualTo(1L);
    }

    @Test
    void includesUserHistoryAndZeroLimitsAndBreaksEqualTimeTiesByRevision() {
        on("2022-01-10");
        var first = limits.create(new ExpenseLimitRequest(ACCOUNT, "product", new BigDecimal("2000")));
        var second = limits.create(new ExpenseLimitRequest(ACCOUNT, "product", BigDecimal.ZERO));
        var result = history.getAll(ACCOUNT, 0, 20);
        assertThat(result.totalElements()).isEqualTo(4);
        assertThat(result.content()).extracting(item -> item.id())
                .containsExactly(second.id(), first.id(), null, null);
        assertThat(result.content().getFirst().amount()).isEqualByComparingTo("0");
        assertThat(result.content()).allSatisfy(item -> assertThat(item.currency()).isEqualTo("USD"));
    }

    @Test
    void defaultsAppearOnlyForUsedMonthsAndDeduplicateRegistrationMonth() {
        success("2022-01-02", "100", "product");
        success("2022-01-03", "100", "product");
        success("2022-03-02", "100", "product");
        var result = history.getAll(ACCOUNT, 0, 20);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.content()).extracting(item -> item.establishedAt().toLocalDate().toString())
                .containsExactly("2022-03-01", "2022-01-01", "2022-01-01");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_limits", Long.class)).isZero();
    }

    @Test
    void establishedLimitCarriesAcrossMonthsWithoutReplacingItWithDefault() {
        on("2022-01-10");
        limits.create(new ExpenseLimitRequest(ACCOUNT, "product", new BigDecimal("2000")));
        success("2022-03-02", "100", "product");
        success("2022-03-02", "100", "service");
        var result = history.getAll(ACCOUNT, 0, 20);
        assertThat(result.totalElements()).isEqualTo(4);
        assertThat(result.content().getFirst().expenseCategory()).isEqualTo("service");
        assertThat(result.content().stream().filter(item -> item.expenseCategory().equals("product")
                && item.establishedAt().getMonthValue() == 3)).isEmpty();
    }

    @Test
    void monthBoundaryUsesMoscowRatherThanUtcForDefaultHistory() {
        clock.set(Instant.parse("2022-01-31T21:00:00Z"));
        var request = request(ACCOUNT, "product", "USD", "100", clock.instant());
        finish(bank.receive(request), "SUCCEEDED");
        var result = history.getAll(ACCOUNT, 0, 20);
        assertThat(result.content().getFirst().establishedAt())
                .isEqualTo(OffsetDateTime.parse("2022-02-01T00:00:00+03:00"));
        assertThat(result.totalElements()).isEqualTo(3);
    }

    @Test
    void historyPaginationIncludesDefaultsAndReturnsEmptyBeyondLastPage() {
        on("2022-01-10");
        limits.create(new ExpenseLimitRequest(ACCOUNT, "service", new BigDecimal("1500")));
        var first = history.getAll(ACCOUNT, 0, 2);
        var second = history.getAll(ACCOUNT, 1, 2);
        var empty = history.getAll(ACCOUNT, 2, 2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).hasSize(2);
        assertThat(second.content()).hasSize(1);
        assertThat(empty.content()).isEmpty();
        assertThat(empty.totalElements()).isEqualTo(3);
        assertThat(history.getAll(ACCOUNT, Integer.MAX_VALUE, 100).content()).isEmpty();
    }

    @Test
    void januaryExampleReturnsOnlyThirdAndLastThirteenthOperationsWithOriginalLimits() {
        success("2022-01-02", "500", "product");
        success("2022-01-03", "600", "product");
        on("2022-01-10");
        limits.create(new ExpenseLimitRequest(ACCOUNT, "product", new BigDecimal("2000")));
        success("2022-01-11", "100", "product");
        success("2022-01-12", "700", "product");
        success("2022-01-13", "100", "product");
        success("2022-01-13", "100", "product");
        var result = exceeded.getAll(ACCOUNT, 0, 20);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting(item -> item.datetime().getDayOfMonth()).containsExactly(13, 3);
        assertThat(result.content().getFirst().limitSum()).isEqualByComparingTo("2000");
        assertThat(result.content().getFirst().limitDatetime()).isEqualTo(time("2022-01-10"));
        assertThat(result.content().get(1).limitSum()).isEqualByComparingTo("1000");
        assertThat(result.content().get(1).limitDatetime())
                .isEqualTo(OffsetDateTime.parse("2022-01-01T00:00:00+03:00"));
        on("2022-01-20");
        limits.create(new ExpenseLimitRequest(ACCOUNT, "product", new BigDecimal("3000")));
        assertThat(exceeded.getAll(ACCOUNT, 0, 20)).isEqualTo(result);
    }

    @Test
    void februaryExampleReturnsEleventhAndTwelfthWithReducedHistoricalLimit() {
        success("2022-02-02", "500", "service");
        success("2022-02-03", "100", "service");
        on("2022-02-10");
        limits.create(new ExpenseLimitRequest(ACCOUNT, "service", new BigDecimal("400")));
        success("2022-02-11", "100", "service");
        success("2022-02-12", "100", "service");
        var result = exceeded.getAll(ACCOUNT, 0, 20);
        assertThat(result.content()).extracting(item -> item.datetime().getDayOfMonth()).containsExactly(12, 11);
        assertThat(result.content()).allSatisfy(item -> {
            assertThat(item.limitSum()).isEqualByComparingTo("400");
            assertThat(item.limitDatetime()).isEqualTo(time("2022-02-10"));
            assertThat(item.limitCurrencyShortname()).isEqualTo("USD");
        });
    }

    @Test
    void failedTimedOutProcessingAndUnconvertedSuccessAreExcludedDespiteTechnicalFlags() {
        success("2022-01-02", "1100", "product");
        var failed = operation("2022-01-03", "10", "product");
        finish(failed, "FAILED");
        assertThat(lifecycle.state(failed.id()).limitExceeded()).isTrue();
        var timeout = operation("2022-01-04", "10", "product");
        lifecycle.configureStub(timeout.id(), "ERROR");
        lifecycle.poll(timeout.id());
        clock.set(clock.instant().plusSeconds(10800));
        lifecycle.poll(timeout.id());
        assertThat(lifecycle.state(timeout.id()).limitExceeded()).isTrue();
        operation("2022-01-05", "10", "product");
        on("2022-01-06");
        var pending = bank.receive(request(ACCOUNT, "service", "KZT", "600000", clock.instant()));
        finish(pending, "SUCCEEDED");
        assertThat(lifecycle.state(pending.id()).limitCheckStatus()).isEqualTo("PENDING");
        var result = exceeded.getAll(ACCOUNT, 0, 20);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().datetime().getDayOfMonth()).isEqualTo(2);
    }

    @Test
    void lateSuccessAfterTimeoutAppearsWithOriginalMonthAndDefaultDate() {
        var operation = operation("2022-01-02", "1100", "product");
        lifecycle.configureStub(operation.id(), "ERROR");
        lifecycle.poll(operation.id());
        clock.set(clock.instant().plusSeconds(10800));
        lifecycle.poll(operation.id());
        assertThat(exceeded.getAll(ACCOUNT, 0, 20).content()).isEmpty();
        on("2022-02-10");
        finish(operation, "SUCCEEDED");
        var result = exceeded.getAll(ACCOUNT, 0, 20);
        assertThat(result.content().getFirst().datetime()).isEqualTo(time("2022-01-02"));
        assertThat(result.content().getFirst().limitDatetime())
                .isEqualTo(OffsetDateTime.parse("2022-01-01T00:00:00+03:00"));
        finish(operation, "SUCCEEDED");
        assertThat(exceeded.getAll(ACCOUNT, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void zeroLimitAppearsInExceededResponseAndExactEqualityDoesNot() {
        success("2022-01-02", "1000", "product");
        assertThat(exceeded.getAll(ACCOUNT, 0, 20).content()).isEmpty();
        on("2022-01-03");
        limits.create(new ExpenseLimitRequest(ACCOUNT, "service", BigDecimal.ZERO));
        success("2022-01-04", "0.01", "service");
        assertThat(exceeded.getAll(ACCOUNT, 0, 20).content().getFirst().limitSum()).isEqualByComparingTo("0");
    }

    @Test
    void convertedSuccessKeepsOriginalCurrencyAndAmountAndReadDoesNotFetchOrReconvert() {
        on("2022-01-03");
        var operation = bank.receive(request(ACCOUNT, "product", "KZT", "550000", clock.instant()));
        finish(operation, "SUCCEEDED");
        assertThat(exceeded.getAll(ACCOUNT, 0, 20).content()).isEmpty();
        storage.save(java.time.LocalDate.parse("2021-12-31"), new ExchangeRatePayload("USD",
                Instant.parse("2021-12-31T23:59:59Z").getEpochSecond(),
                Map.of("USD", BigDecimal.ONE, "KZT", new BigDecimal("500"))));
        conversions.convert(operation.id(), "KZT", operation.sum(), operation.datetime().toInstant());
        lifecycle.reconcile(operation.id());
        var jobs = jdbc.queryForObject("SELECT count(*) FROM exchange_rate_jobs", Long.class);
        var result = exceeded.getAll(ACCOUNT, 0, 20);
        assertThat(result.content().getFirst().currencyShortname()).isEqualTo("KZT");
        assertThat(result.content().getFirst().sum()).isEqualByComparingTo("550000");
        assertThat(result.content().getFirst().limitSum()).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rate_jobs", Long.class)).isEqualTo(jobs);
        assertThat(jdbc.queryForObject("SELECT amount_usd FROM transactions WHERE id = ?",
                BigDecimal.class, operation.id())).isEqualByComparingTo("1100");
    }

    @Test
    void exceededPaginationUsesCreationSequenceForEqualTimesAndEmptyPageRetainsTotals() {
        success("2022-01-02", "1100", "product");
        success("2022-01-02", "20", "product");
        success("2022-01-02", "30", "product");
        assertThat(exceeded.getAll(ACCOUNT, 0, 1).content().getFirst().sum()).isEqualByComparingTo("30");
        assertThat(exceeded.getAll(ACCOUNT, 1, 1).content().getFirst().sum()).isEqualByComparingTo("20");
        var empty = exceeded.getAll(ACCOUNT, 3, 1);
        assertThat(empty.content()).isEmpty();
        assertThat(empty.totalElements()).isEqualTo(3);
        assertThat(empty.totalPages()).isEqualTo(3);
        assertThat(exceeded.getAll(ACCOUNT, Integer.MAX_VALUE, 100).content()).isEmpty();
    }

    @Test
    void bothQueriesIsolateAccountButIncludeBothExpenseCategories() {
        success("2022-01-02", "1100", "product");
        success("2022-01-02", "1200", "service");
        var other = bank.receive(request("1234500001", "product", "USD", "1300", clock.instant()));
        finish(other, "SUCCEEDED");
        limits.create(new ExpenseLimitRequest("1234500001", "service", new BigDecimal("2500")));
        assertThat(exceeded.getAll(ACCOUNT, 0, 20).content()).hasSize(2)
                .allSatisfy(item -> assertThat(item.accountFrom()).isEqualTo(ACCOUNT));
        assertThat(history.getAll(ACCOUNT, 0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    void exceededApiReturnsExactlyNineFieldsAndPageEnvelope() throws Exception {
        success("2022-01-03", "1100", "product");
        mvc.perform(get(EXCEEDED)).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0]", org.hamcrest.Matchers.aMapWithSize(9)))
                .andExpect(jsonPath("$.content[0].account_from").value(ACCOUNT))
                .andExpect(jsonPath("$.content[0].account_to").value("9999999999"))
                .andExpect(jsonPath("$.content[0].currency_shortname").value("USD"))
                .andExpect(jsonPath("$.content[0].sum").value(1100))
                .andExpect(jsonPath("$.content[0].expense_category").value("product"))
                .andExpect(jsonPath("$.content[0].datetime").value("2022-01-03T12:00:00+03:00"))
                .andExpect(jsonPath("$.content[0].limit_sum").value(1000))
                .andExpect(jsonPath("$.content[0].limit_datetime").value("2022-01-01T00:00:00+03:00"))
                .andExpect(jsonPath("$.content[0].limit_currency_shortname").value("USD"));
    }

    @Test
    void knownAccountWithoutExceedancesReturnsEmptyPage() throws Exception {
        mvc.perform(get(EXCEEDED)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.total_elements").value(0))
                .andExpect(jsonPath("$.total_pages").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/limits", "/transactions/limit-exceeded"})
    void unknownAccountReturnsNotFoundWithoutRegistration(String suffix) throws Exception {
        mvc.perform(get("/api/v1/client/accounts/0000000000" + suffix))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class)).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/limits", "/transactions/limit-exceeded"})
    void malformedAccountReturnsBadRequest(String suffix) throws Exception {
        mvc.perform(get("/api/v1/client/accounts/123" + suffix)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest
    @MethodSource("invalidPagination")
    void invalidPaginationReturnsProblemDetail(String suffix, String parameter, String value) throws Exception {
        mvc.perform(get(BASE + suffix).param(parameter, value)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/limits", "/transactions/limit-exceeded"})
    void maximumPageSizeIsAccepted(String suffix) throws Exception {
        mvc.perform(get(BASE + suffix).param("size", "100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void swaggerDocumentsBothClientQueriesAndHistoricalLimitFields() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/client/accounts/{account}/limits']"
                        + ".get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/client/accounts/{account}/transactions/limit-exceeded']"
                        + ".get.responses['400'].content['application/problem+json']").exists())
                .andExpect(jsonPath("$.components.schemas.LimitExceededTransactionResponse.properties",
                        org.hamcrest.Matchers.aMapWithSize(9)))
                .andExpect(jsonPath("$.components.schemas.LimitExceededTransactionResponse"
                        + ".properties.limit_currency_shortname.enum[0]").value("USD"))
                .andExpect(jsonPath("$.components.schemas.ExpenseLimitPageResponse.required")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "content", "page", "size", "total_elements", "total_pages")))
                .andExpect(jsonPath("$.components.schemas.LimitExceededTransactionPageResponse.properties.size.maximum")
                        .value(100))
                .andExpect(jsonPath("$.components.schemas.ExpenseLimitResponse.properties.id.type")
                        .value(org.hamcrest.Matchers.hasItem("null")))
                .andExpect(jsonPath("$.paths['/api/v1/client/accounts/{account}/limits'].get.responses['200']"
                        + ".content['application/json'].examples['New account defaults'].value.total_elements")
                        .value(2))
                .andExpect(jsonPath("$.paths['/api/v1/client/accounts/{account}/transactions/limit-exceeded']"
                        + ".get.responses['200'].content['application/json'].examples['January example']"
                        + ".value.content[0].limit_sum").value(2000))
                .andExpect(jsonPath("$.paths['/api/v1/client/accounts/{account}/transactions/limit-exceeded']"
                        + ".get.responses['200'].content['application/json'].examples['No exceedances']"
                        + ".value.total_pages").value(0));
    }

    @Test
    void historyCountAndContentShareSnapshotWhenAnotherRequestEstablishesLimit() throws Exception {
        var counted = new CountDownLatch(1);
        var written = new CountDownLatch(1);
        doAnswer(invocation -> {
            var total = invocation.callRealMethod();
            counted.countDown();
            assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
            return total;
        }).when(historyRepository).count(ACCOUNT);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reader = executor.submit(() -> history.getAll(ACCOUNT, 0, 20));
            try {
                assertThat(counted.await(5, TimeUnit.SECONDS)).isTrue();
                limits.create(new ExpenseLimitRequest(ACCOUNT, "product", new BigDecimal("1500")));
            } finally {
                written.countDown();
            }
            var snapshot = reader.get(5, TimeUnit.SECONDS);
            assertThat(snapshot.totalElements()).isEqualTo(2);
            assertThat(snapshot.content()).hasSize(2).allSatisfy(item -> assertThat(item.id()).isNull());
        }
        assertThat(history.getAll(ACCOUNT, 0, 20).totalElements()).isEqualTo(3);
    }

    @Test
    void exceededCountAndContentShareSnapshotWhenAnotherRequestFinalizesOperation() throws Exception {
        success("2022-01-02", "1100", "product");
        var pending = operation("2022-01-03", "100", "product");
        var counted = new CountDownLatch(1);
        var written = new CountDownLatch(1);
        doAnswer(invocation -> {
            var total = invocation.callRealMethod();
            counted.countDown();
            assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
            return total;
        }).when(exceededRepository).count(ACCOUNT);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reader = executor.submit(() -> exceeded.getAll(ACCOUNT, 0, 20));
            try {
                assertThat(counted.await(5, TimeUnit.SECONDS)).isTrue();
                finish(pending, "SUCCEEDED");
            } finally {
                written.countDown();
            }
            var snapshot = reader.get(5, TimeUnit.SECONDS);
            assertThat(snapshot.totalElements()).isEqualTo(1);
            assertThat(snapshot.content()).hasSize(1);
            assertThat(snapshot.content().getFirst().datetime().getDayOfMonth()).isEqualTo(2);
        }
        assertThat(exceeded.getAll(ACCOUNT, 0, 20).totalElements()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/limits", "/transactions/limit-exceeded"})
    void databaseReadFailureReturnsServiceUnavailableProblemDetail(String suffix) throws Exception {
        var error = new org.springframework.dao.DataAccessResourceFailureException("temporary outage");
        if (suffix.equals("/limits")) {
            doThrow(error).when(historyRepository).count(ACCOUNT);
        } else {
            doThrow(error).when(exceededRepository).count(ACCOUNT);
        }
        mvc.perform(get(BASE + suffix)).andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.instance").value(BASE + suffix));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class)).isEqualTo(1L);
    }
    private static Stream<Arguments> invalidPagination() {
        return Stream.of("/limits", "/transactions/limit-exceeded").flatMap(suffix -> Stream.of(
                Arguments.of(suffix, "page", "-1"), Arguments.of(suffix, "size", "0"),
                Arguments.of(suffix, "size", "101"), Arguments.of(suffix, "page", "abc"),
                Arguments.of(suffix, "page", "2147483648"), Arguments.of(suffix, "size", "-1")));
    }

    private void on(String date) {
        clock.set(time(date).toInstant());
    }

    private OffsetDateTime time(String date) {
        return OffsetDateTime.parse(date + "T12:00:00+03:00");
    }

    private TransactionResponse operation(String date, String amount, String category) {
        on(date);
        return bank.receive(request(ACCOUNT, category, "USD", amount, clock.instant()));
    }

    private void success(String date, String amount, String category) {
        finish(operation(date, amount, category), "SUCCEEDED");
    }

    private TransactionRequest request(String account, String category, String currency, String amount, Instant time) {
        return new TransactionRequest(account, "9999999999", currency, new BigDecimal(amount), category,
                time.atOffset(ZoneOffset.UTC));
    }

    private void finish(TransactionResponse operation, String status) {
        lifecycle.notify(new BankNotificationRequest(operation.id(), status,
                clock.instant().plusSeconds(1).atOffset(ZoneOffset.UTC)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        MutableClock clientQueriesClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(START);

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
