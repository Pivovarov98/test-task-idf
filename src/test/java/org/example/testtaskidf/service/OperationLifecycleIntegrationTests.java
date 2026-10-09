package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.example.testtaskidf.PostgresTestConfiguration;
import org.example.testtaskidf.dto.BankNotificationRequest;
import org.example.testtaskidf.dto.ExchangeRatePayload;
import org.example.testtaskidf.dto.ExpenseLimitRequest;
import org.example.testtaskidf.dto.OperationState;
import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.dto.TransactionResponse;
import org.example.testtaskidf.repository.OperationRepository;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, OperationLifecycleIntegrationTests.TimeConfiguration.class})
class OperationLifecycleIntegrationTests {
    private static final String ACCOUNT = "5432100000";
    private static final Instant START = Instant.parse("2026-10-08T06:00:00Z");
    @Autowired
    private BankTransactionService bank;
    @Autowired
    private OperationLifecycleService lifecycle;
    @Autowired
    private ClientLimitService limits;
    @Autowired
    private ExchangeRateStorageService storage;
    @Autowired
    private CurrencyConversionService conversions;
    @Autowired
    private OperationRepository operations;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MutableClock clock;
    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM expense_limits");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM exchange_rates");
        jdbc.update("DELETE FROM exchange_rate_snapshots");
        jdbc.update("DELETE FROM exchange_rate_jobs");
        clock.set(START);
    }

    @Test
    void reservesImmediatelyButPublishesFlagOnlyOnSuccessWithoutDoubleCharge() {
        var first = receive("500");
        var second = receive("600");
        assertThat(second.operation().reservationActive()).isTrue();
        assertThat(second.operation().limitExceeded()).isFalse();
        assertThat(second.operation().limitCheckStatus()).isEqualTo("PENDING");
        assertThat(finish(first, "SUCCEEDED").limitExceeded()).isFalse();
        assertThat(finish(second, "SUCCEEDED").limitExceeded()).isTrue();
        assertThat(occupied()).isEqualByComparingTo("1100");
        assertThat(finish(second, "SUCCEEDED")).isEqualTo(lifecycle.state(second.id()));
        assertThat(occupied()).isEqualByComparingTo("1100");
    }

    @Test
    void failureThatCausedCrossingReleasesReserveAndHasFalseFlag() {
        finish(receive("500"), "SUCCEEDED");
        var second = receive("600");
        var failed = finish(second, "FAILED");
        assertThat(failed.limitExceeded()).isFalse();
        assertThat(failed.reservationActive()).isFalse();
        assertThat(failed.reservedUsd()).isEqualByComparingTo("600");
        assertThat(occupied()).isEqualByComparingTo("500");
        assertThat(finish(receive("500"), "SUCCEEDED").limitExceeded()).isFalse();
    }

    @Test
    void failureCreatedAfterLimitWasAlreadyExceededKeepsTrueFlag() {
        finish(receive("1100"), "SUCCEEDED");
        var third = receive("100");
        assertThat(finish(third, "FAILED").limitExceeded()).isTrue();
        assertThat(occupied()).isEqualByComparingTo("1100");
        finish(third, "FAILED");
        assertThat(occupied()).isEqualByComparingTo("1100");
    }

    @Test
    void newLimitDoesNotResetSpentAmountsOrRewriteHistoricalFlags() {
        finish(receive("500"), "SUCCEEDED");
        var old = receive("600");
        clock.set(START.plusSeconds(60));
        limits.create(new ExpenseLimitRequest(ACCOUNT, "product", new BigDecimal("2000")));
        assertThat(finish(old, "SUCCEEDED").limitExceeded()).isTrue();
        assertThat(finish(receive("100"), "SUCCEEDED").limitExceeded()).isFalse();
        assertThat(occupied()).isEqualByComparingTo("1200");
        assertThat(jdbc.queryForObject("SELECT applied_limit_usd FROM transactions WHERE id = ?",
                BigDecimal.class, old.id())).isEqualByComparingTo("1000");
    }

    @Test
    void reducedLimitCountsAlreadySpentAmounts() {
        finish(receive("600"), "SUCCEEDED");
        clock.set(START.plusSeconds(60));
        limits.create(new ExpenseLimitRequest(ACCOUNT, "product", new BigDecimal("400")));
        assertThat(finish(receive("100"), "SUCCEEDED").limitExceeded()).isTrue();
    }

    @Test
    void exactLimitIsNotExceededButNextPositiveAmountIs() {
        assertThat(finish(receive("1000"), "SUCCEEDED").limitExceeded()).isFalse();
        assertThat(finish(receive("0.01"), "SUCCEEDED").limitExceeded()).isTrue();
    }

    @Test
    void zeroLimitDoesNotChangePastFlagAndPositiveOperationExceedsIt() {
        var old = receive("1");
        finish(old, "SUCCEEDED");
        clock.set(START.plusSeconds(60));
        limits.create(new ExpenseLimitRequest(ACCOUNT, "product", BigDecimal.ZERO));
        assertThat(lifecycle.state(old.id()).limitExceeded()).isFalse();
        assertThat(finish(receive("0.01"), "SUCCEEDED").limitExceeded()).isTrue();
    }

    @Test
    void amountRoundedToZeroDoesNotExceedZeroLimit() {
        limits.create(new ExpenseLimitRequest(ACCOUNT, "product", BigDecimal.ZERO));
        saveRate("1000");
        var operation = bank.receive(request(ACCOUNT, "product", "KZT", "0.01", START));
        assertThat(operation.amountUsd()).isEqualByComparingTo("0");
        assertThat(finish(operation, "SUCCEEDED").limitExceeded()).isFalse();
    }

    @Test
    void usesMoscowMonthOfCreationEvenWhenCompletedInNextMonth() {
        var september = bank.receive(request(ACCOUNT, "product", "USD", "1000",
                Instant.parse("2026-09-30T20:59:59Z")));
        var october = bank.receive(request(ACCOUNT, "product", "USD", "600",
                Instant.parse("2026-09-30T21:00:00Z")));
        clock.set(Instant.parse("2026-11-01T06:00:00Z"));
        assertThat(finish(september, "SUCCEEDED").limitExceeded()).isFalse();
        assertThat(finish(october, "SUCCEEDED").limitExceeded()).isFalse();
    }

    @Test
    void pendingConversionBlocksLaterReservationThenPreservesOrderAndFixedRate() {
        var first = bank.receive(request(ACCOUNT, "product", "KZT", "550000", START));
        var second = receive("100");
        assertThat(second.operation().reservedUsd()).isNull();
        assertThat(finish(first, "SUCCEEDED").limitCheckStatus()).isEqualTo("PENDING");
        saveRate("500");
        conversions.convert(first.id(), "KZT", new BigDecimal("550000"), START);
        lifecycle.reconcile(first.id());
        assertThat(lifecycle.state(first.id()).limitExceeded()).isTrue();
        assertThat(finish(second, "SUCCEEDED").limitExceeded()).isTrue();
        clock.set(START.plusSeconds(86400));
        finish(first, "SUCCEEDED");
        assertThat(lifecycle.state(first.id()).reservedUsd()).isEqualByComparingTo("1100");
    }

    @Test
    void failingOperationWithoutRateUnblocksFollowingUsdOperation() {
        var missing = bank.receive(request(ACCOUNT, "product", "KZT", "500", START));
        var usd = receive("100");
        assertThat(usd.operation().reservedUsd()).isNull();
        assertThat(finish(missing, "FAILED").limitExceeded()).isFalse();
        assertThat(lifecycle.state(usd.id()).reservedUsd()).isEqualByComparingTo("100");
    }

    @Test
    void threeHoursOfErrorsReleaseExactlyOnceAndLateSuccessRestoresOriginalExpense() {
        var operation = receive("1100");
        lifecycle.configureStub(operation.id(), "ERROR");
        lifecycle.poll(operation.id());
        clock.set(START.plusSeconds(10799));
        lifecycle.poll(operation.id());
        assertThat(lifecycle.state(operation.id()).operationStatus()).isEqualTo("PROCESSING");
        clock.set(START.plusSeconds(10800));
        lifecycle.poll(operation.id());
        assertThat(lifecycle.state(operation.id()).operationStatus()).isEqualTo("TIMED_OUT");
        assertThat(lifecycle.state(operation.id()).limitExceeded()).isFalse();
        assertThat(occupied()).isZero();
        lifecycle.poll(operation.id());
        assertThat(occupied()).isZero();
        var other = receive("100");
        finish(other, "SUCCEEDED");
        assertThat(finish(operation, "SUCCEEDED").limitExceeded()).isTrue();
        assertThat(occupied()).isEqualByComparingTo("1200");
        assertThat(lifecycle.state(other.id()).limitExceeded()).isFalse();
        finish(operation, "SUCCEEDED");
        assertThat(occupied()).isEqualByComparingTo("1200");
    }

    @Test
    void processingReplyResetsErrorTimerAndNeverTimesOutByItself() {
        var operation = receive("600");
        lifecycle.configureStub(operation.id(), "ERROR");
        lifecycle.poll(operation.id());
        clock.set(START.plusSeconds(7200));
        lifecycle.configureStub(operation.id(), "PROCESSING");
        lifecycle.poll(operation.id());
        assertThat(jdbc.queryForObject("SELECT bank_unavailable_since FROM transactions WHERE id = ?",
                OffsetDateTime.class, operation.id())).isNull();
        clock.set(START.plusSeconds(20000));
        lifecycle.poll(operation.id());
        assertThat(lifecycle.state(operation.id()).operationStatus()).isEqualTo("PROCESSING");
        lifecycle.configureStub(operation.id(), "ERROR");
        lifecycle.poll(operation.id());
        clock.set(START.plusSeconds(30000));
        lifecycle.poll(operation.id());
        assertThat(lifecycle.state(operation.id()).operationStatus()).isEqualTo("PROCESSING");
        clock.set(START.plusSeconds(30800));
        lifecycle.poll(operation.id());
        assertThat(lifecycle.state(operation.id()).operationStatus()).isEqualTo("TIMED_OUT");
    }

    @Test
    void lateFailureAfterTimeoutDoesNotReleaseOtherReservations() {
        var operation = receive("600");
        lifecycle.configureStub(operation.id(), "ERROR");
        lifecycle.poll(operation.id());
        clock.set(START.plusSeconds(10800));
        lifecycle.poll(operation.id());
        receive("100");
        finish(operation, "FAILED");
        assertThat(occupied()).isEqualByComparingTo("100");
    }

    @Test
    void stubFinalResponsesFinishOperation() {
        var success = receive("1100");
        lifecycle.configureStub(success.id(), "SUCCEEDED");
        lifecycle.poll(success.id());
        assertThat(lifecycle.state(success.id()).limitExceeded()).isTrue();
        var failure = receive("100");
        lifecycle.configureStub(failure.id(), "FAILED");
        lifecycle.poll(failure.id());
        assertThat(lifecycle.state(failure.id()).operationStatus()).isEqualTo("FAILED");
        assertThat(occupied()).isEqualByComparingTo("1100");
    }

    @Test
    void rejectsBackdatedReceiptAndConflictingFinalStatusWithoutChangingData() {
        var operation = receive("600");
        assertThatThrownBy(() -> bank.receive(request(ACCOUNT, "product", "USD", "100",
                START.minusSeconds(1)))).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(409));
        finish(operation, "SUCCEEDED");
        assertThatThrownBy(() -> finish(operation, "FAILED")).isInstanceOf(ResponseStatusException.class);
        assertThat(occupied()).isEqualByComparingTo("600");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Long.class)).isEqualTo(1L);
    }

    @Test
    void simultaneousReceiptsAndNotificationsNeverUseSameRemainingLimit() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return receive("600");
            });
            var second = executor.submit(() -> {
                start.await();
                return receive("600");
            });
            start.countDown();
            var one = first.get(10, TimeUnit.SECONDS);
            var two = second.get(10, TimeUnit.SECONDS);
            var doneOne = executor.submit(() -> finish(one, "SUCCEEDED"));
            var doneTwo = executor.submit(() -> finish(two, "SUCCEEDED"));
            assertThat(java.util.List.of(doneOne.get(10, TimeUnit.SECONDS).limitExceeded(),
                    doneTwo.get(10, TimeUnit.SECONDS).limitExceeded())).containsExactlyInAnyOrder(false, true);
            assertThat(occupied()).isEqualByComparingTo("1200");
        }
    }

    @Test
    void accountCategoryLockDoesNotBlockOtherCategoriesOrAccounts() throws Exception {
        try (var executor = Executors.newFixedThreadPool(3)) {
            var locked = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                operations.lock(ACCOUNT, ExpenseCategoryUtils.fromCode("product"));
                locked.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var category = executor.submit(() -> bank.receive(request(ACCOUNT, "service", "USD", "600", START)));
                var account = executor.submit(() -> bank.receive(
                        request("5432100001", "product", "USD", "600", START)));
                assertThat(category.get(5, TimeUnit.SECONDS).operation().reservedUsd()).isEqualByComparingTo("600");
                assertThat(account.get(5, TimeUnit.SECONDS).operation().reservedUsd()).isEqualByComparingTo("600");
                assertThatThrownBy(() -> limits.create(new ExpenseLimitRequest(ACCOUNT, "product",
                        new BigDecimal("2000"))))
                        .isInstanceOf(org.example.testtaskidf.exception.LimitConflictException.class);
            } finally {
                release.countDown();
            }
            holder.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void notificationApiFinalizesAndStatusApiShowsPersistedState() throws Exception {
        var operation = receive("1100");
        mvc.perform(post("/api/v1/bank/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content(notification(operation.id(), "SUCCEEDED", START.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limit_exceeded").value(true))
                .andExpect(jsonPath("$.operation_status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/bank/transactions/{id}/status", operation.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reserved_usd").value(1100));
        mvc.perform(put("/api/v1/bank/stub/transactions/{id}", operation.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ERROR\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void notificationApiReturnsProblemDetailsForInvalidUnknownAndConflictingOperations() throws Exception {
        var operation = receive("600");
        for (var payload : java.util.List.of("{}", "{invalid}", notification(operation.id(), "PROCESSING",
                START.toString()), notification(operation.id(), "SUCCEEDED", START.minusSeconds(1).toString()))) {
            mvc.perform(post("/api/v1/bank/notifications").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }
        mvc.perform(post("/api/v1/bank/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content(notification(UUID.randomUUID(), "SUCCEEDED", START.toString())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        finish(operation, "SUCCEEDED");
        mvc.perform(post("/api/v1/bank/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content(notification(operation.id(), "FAILED", START.toString())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(occupied()).isEqualByComparingTo("600");
    }

    @Test
    void timeoutAndSuccessNotificationRacingLeaveExactlyOneFinalExpense() throws Exception {
        var operation = receive("1100");
        lifecycle.configureStub(operation.id(), "ERROR");
        lifecycle.poll(operation.id());
        clock.set(START.plusSeconds(10800));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var timeout = executor.submit(() -> {
                start.await();
                lifecycle.poll(operation.id());
                return true;
            });
            var success = executor.submit(() -> {
                start.await();
                return finish(operation, "SUCCEEDED");
            });
            start.countDown();
            timeout.get(10, TimeUnit.SECONDS);
            success.get(10, TimeUnit.SECONDS);
        }
        assertThat(lifecycle.state(operation.id()).operationStatus()).isEqualTo("SUCCEEDED");
        assertThat(lifecycle.state(operation.id()).limitExceeded()).isTrue();
        assertThat(occupied()).isEqualByComparingTo("1100");
    }

    @Test
    void timeoutKeepsFlagWhenOperationWasCreatedWithAlreadyExceededLimit() {
        finish(receive("1100"), "SUCCEEDED");
        var operation = receive("100");
        lifecycle.configureStub(operation.id(), "ERROR");
        lifecycle.poll(operation.id());
        clock.set(START.plusSeconds(10800));
        lifecycle.poll(operation.id());
        assertThat(lifecycle.state(operation.id()).limitExceeded()).isTrue();
        assertThat(occupied()).isEqualByComparingTo("1100");
    }

    @Test
    void laterPublicationAndCompletionDoNotChangeFrozenCurrencyAmount() {
        saveRate("500");
        var operation = bank.receive(request(ACCOUNT, "product", "KZT", "500000", START));
        assertThat(operation.amountUsd()).isEqualByComparingTo("1000");
        clock.set(START.plusSeconds(86400));
        storage.save(java.time.LocalDate.parse("2026-10-08"), new ExchangeRatePayload("USD",
                Instant.parse("2026-10-08T23:59:59Z").getEpochSecond(),
                Map.of("USD", BigDecimal.ONE, "KZT", new BigDecimal("250"))));
        conversions.convert(operation.id(), "KZT", new BigDecimal("500000"), START);
        var completed = finish(operation, "SUCCEEDED");
        assertThat(completed.reservedUsd()).isEqualByComparingTo("1000");
        assertThat(completed.limitExceeded()).isFalse();
        assertThat(jdbc.queryForObject("SELECT amount_usd FROM transactions WHERE id = ?",
                BigDecimal.class, operation.id())).isEqualByComparingTo("1000");
    }

    @Test
    void bankStubAndStateApiRejectUnknownOperationsAndInvalidStatus() throws Exception {
        var operation = receive("100");
        mvc.perform(put("/api/v1/bank/stub/transactions/{id}", operation.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"TIMED_OUT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(put("/api/v1/bank/stub/transactions/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ERROR\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/v1/bank/transactions/{id}/status", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }
    private String notification(UUID id, String status, String completed) {
        return "{\"transaction_id\":\"" + id + "\",\"status\":\"" + status
                + "\",\"completed_at\":\"" + completed + "\"}";
    }

    private void saveRate(String rate) {
        storage.save(java.time.LocalDate.parse("2026-10-07"), new ExchangeRatePayload("USD",
                Instant.parse("2026-10-07T23:59:59Z").getEpochSecond(),
                Map.of("USD", BigDecimal.ONE, "KZT", new BigDecimal(rate))));
    }

    private TransactionResponse receive(String amount) {
        return bank.receive(request(ACCOUNT, "product", "USD", amount, clock.instant()));
    }

    private TransactionRequest request(String account, String category, String currency, String amount, Instant time) {
        return new TransactionRequest(account, "9999999999", currency, new BigDecimal(amount), category,
                time.atOffset(ZoneOffset.UTC));
    }

    private OperationState finish(TransactionResponse operation, String status) {
        return lifecycle.notify(new BankNotificationRequest(operation.id(), status,
                clock.instant().atOffset(ZoneOffset.UTC)));
    }

    private BigDecimal occupied() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(reserved_usd), 0) FROM transactions
                WHERE account_from = ? AND reservation_active = TRUE
                """, BigDecimal.class, ACCOUNT);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        MutableClock operationTestClock() {
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


