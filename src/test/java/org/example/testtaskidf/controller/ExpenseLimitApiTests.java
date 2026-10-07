package org.example.testtaskidf.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.example.testtaskidf.PostgresTestConfiguration;
import org.example.testtaskidf.model.ExpenseCategory;
import org.example.testtaskidf.service.ExpenseLimitService;
import org.example.testtaskidf.util.AccountLockUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, ExpenseLimitApiTests.FixedTimeConfiguration.class})
class ExpenseLimitApiTests {
    private static final String URL = "/api/v1/client/limits";
    private static final String ACCOUNT = "8765432100";
    private static final String OTHER_ACCOUNT = "8765432101";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ExpenseLimitService limits;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM expense_limits WHERE account IN (?, ?)", ACCOUNT, OTHER_ACCOUNT);
        jdbc.update("DELETE FROM accounts WHERE account_number IN (?, ?)", ACCOUNT, OTHER_ACCOUNT);
    }

    @Test
    void registersNewAccountEvenWhenDefaultAmountIsRejected() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload(ACCOUNT, "product", "1000")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("limit_amount_unchanged"))
                .andExpect(jsonPath("$.instance").value(URL));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts WHERE account_number = ?",
                Long.class, ACCOUNT)).isEqualTo(1L);
        assertThat(limits.getCurrentLimit(ACCOUNT, new ExpenseCategory.ServiceExpense()).amount())
                .isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_limits WHERE account = ?",
                Long.class, ACCOUNT)).isZero();
    }

    @Test
    void appendsHistoryAllowsZeroAndRejectsNumericallyEqualAmount() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload(ACCOUNT, "product", "1500")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.established_at").value("2026-10-08T15:00:00+03:00"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload(ACCOUNT, "product", "1500.00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("limit_amount_unchanged"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload(ACCOUNT, "product", "0")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(0));
        assertThat(limits.getCurrentLimit(ACCOUNT, new ExpenseCategory.Product()).amount())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_limits WHERE account = ?",
                Long.class, ACCOUNT)).isEqualTo(2L);
        assertThatThrownBy(() -> jdbc.update("UPDATE expense_limits SET amount = 2000 WHERE account = ?", ACCOUNT))
                .isInstanceOf(DataAccessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"established_at\":\"2020-01-01T00:00:00Z\"", "\"currency\":\"USD\"",
            "\"unexpected\":true"})
    void rejectsFieldsOwnedByServer(String field) throws Exception {
        var body = payload(ACCOUNT, "product", "1500").replace("}", "," + field + "}");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts WHERE account_number = ?",
                Long.class, ACCOUNT)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0.001", "100000000000000000", "null"})
    void validatesAmountBeforeAccountRegistration(String amount) throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload(ACCOUNT, "product", amount)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.amount").exists());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsOccupiedLockAndReleasesItAfterCommitOrRollback(boolean rollback) throws Exception {
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class,
                        AccountLockUtils.key(ACCOUNT, new ExpenseCategory.Product()))).isTrue();
                acquired.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test lock release timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                if (rollback) {
                    status.setRollbackOnly();
                }
                return null;
            }));
            try {
                assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
                mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                                .content(payload(ACCOUNT, "product", "1500")))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("limit_request_in_progress"));
                mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                                .content(payload(ACCOUNT, "service", "500")))
                        .andExpect(status().isCreated());
                mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                                .content(payload(OTHER_ACCOUNT, "product", "500")))
                        .andExpect(status().isCreated());
            } finally {
                release.countDown();
            }
            holder.get(5, TimeUnit.SECONDS);
        }
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload(ACCOUNT, "product", "1500")))
                .andExpect(status().isCreated());
    }

    private String payload(String account, String category, String amount) {
        return "{\"account\":\"" + account + "\",\"expense_category\":\"" + category + "\",\"amount\":" + amount + "}";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTimeConfiguration {
        @Bean
        @Primary
        Clock fixedLimitClock() {
            return Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
