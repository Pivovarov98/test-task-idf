package org.example.testtaskidf.config;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.example.testtaskidf.PostgresTestConfiguration;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TransactionMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void registerSourceAccount() {
        jdbcTemplate.update("""
                INSERT INTO accounts (account_number, created_at) VALUES ('0000000123', CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"product", "service"})
    void storesBothCategoriesWithExactAmountAndAccount(String category) {
        var occurredAt = OffsetDateTime.parse("2022-01-30T00:00:00+06:00");
        var id = jdbcTemplate.queryForObject("""
                INSERT INTO transactions
                    (account_from, account_to, currency_shortname, sum, expense_category, datetime)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, "0000000123", "9999999999", "KZT",
                new BigDecimal("10000.45"), category, occurredAt);

        var row = jdbcTemplate.queryForMap("SELECT * FROM transactions WHERE id = ?", id);
        assertNotNull(id);
        assertEquals("0000000123", row.get("account_from"));
        assertEquals(category, row.get("expense_category"));
        assertEquals(new BigDecimal("10000.45"), row.get("sum"));
        assertNotNull(row.get("received_at"));
        var storedTime = jdbcTemplate.queryForObject(
                "SELECT datetime FROM transactions WHERE id = ?", OffsetDateTime.class, id);
        assertEquals(occurredAt.toInstant(), storedTime.toInstant());
    }

    @ParameterizedTest
    @ValueSource(strings = {"products", "services", "other"})
    void rejectsUnsupportedCategories(String category) {
        assertThrows(DataIntegrityViolationException.class,
                () -> insert("0000000123", "KZT", "10.00", category));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00", "NaN"})
    void rejectsInvalidAmounts(String amount) {
        assertThrows(DataIntegrityViolationException.class,
                () -> insert("0000000123", "KZT", amount, "product"));
    }

    @Test
    void rejectsAccountWithWrongLength() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insert("123", "KZT", "10.00", "product"));
    }

    @Test
    void rejectsMalformedCurrencyCode() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insert("0000000123", "kzt", "10.00", "product"));
    }

    private void insert(String account, String currency, String amount, String category) {
        jdbcTemplate.update("""
                INSERT INTO transactions
                    (account_from, account_to, currency_shortname, sum, expense_category, datetime)
                VALUES (?, '9999999999', ?, CAST(? AS NUMERIC), ?, CURRENT_TIMESTAMP)
                """, account, currency, amount, category);
    }
}
