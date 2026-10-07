package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.example.testtaskidf.PostgresTestConfiguration;
import org.example.testtaskidf.model.ExpenseCategory;
import org.example.testtaskidf.model.ExpenseLimit;
import org.example.testtaskidf.repository.ExpenseLimitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class ExpenseLimitIntegrationTests {
    @Autowired
    private ExpenseLimitRepository repository;

    @Autowired
    private ExpenseLimitService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void registerAccounts() {
        jdbcTemplate.update("""
                INSERT INTO accounts (account_number, created_at)
                VALUES ('0000000321', CURRENT_TIMESTAMP), ('0000000999', CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """);
    }

    @Test
    void selectsHistoricalLimitAndKeepsAccountsAndCategoriesSeparate() {
        var product = new ExpenseCategory.Product();
        var services = new ExpenseCategory.ServiceExpense();
        var old = limit("0000000321", product, "1000.00", "2022-01-01T00:00:00Z");
        var changed = limit("0000000321", product, "2000.00", "2022-01-10T00:00:00Z");
        repository.save(old);
        repository.save(changed);
        repository.save(limit("0000000321", services, "400.00", "2022-01-02T00:00:00Z"));
        repository.save(limit("0000000999", product, "500.00", "2022-01-03T00:00:00Z"));
        assertEquals(old, service.getApplicableLimit("0000000321", product,
                Instant.parse("2022-01-09T00:00:00Z")).configuredLimit().orElseThrow());
        assertEquals(changed, service.getApplicableLimit("0000000321", product,
                Instant.parse("2022-01-10T00:00:00Z")).configuredLimit().orElseThrow());
        assertEquals(new BigDecimal("400.00"), service.getApplicableLimit("0000000321", services,
                Instant.parse("2022-02-01T00:00:00Z")).amount());
        assertEquals(new BigDecimal("2000.00"), service.getApplicableLimit("0000000321", product,
                Instant.parse("2022-02-01T00:00:00Z")).amount());
        assertFalse(service.getApplicableLimit("0000000321", product,
                Instant.parse("2021-12-31T00:00:00Z")).configuredLimit().isPresent());
        assertEquals(4L, jdbcTemplate.queryForObject("SELECT count(*) FROM expense_limits", Long.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "KZT", "usd"})
    void databaseRejectsNonUsdLimit(String currency) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO expense_limits (id, account, expense_category, amount, currency, established_at)
                VALUES (?, '0000000321', 'product', 1000, ?, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), currency));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "NaN"})
    void databaseRejectsInvalidAmount(String amount) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO expense_limits (id, account, expense_category, amount, currency, established_at)
                VALUES (?, '0000000321', 'product', CAST(? AS NUMERIC), 'USD', CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), amount));
    }

    private ExpenseLimit limit(String account, ExpenseCategory category, String amount, String instant) {
        return new ExpenseLimit(UUID.randomUUID(), account, category, new BigDecimal(amount),
                "USD", Instant.parse(instant));
    }
}
