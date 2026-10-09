package org.example.testtaskidf.config;

import java.sql.Connection;
import javax.sql.DataSource;

import org.example.testtaskidf.PostgresTestConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class AccountUpgradeMigrationTests {
    @Autowired
    private DataSource dataSource;

    @Test
    void conversionMigrationPreservesOriginalTransactionsAndBackfillsUsdOnly() throws Exception {
        var schema = "currency_upgrade_test";
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("4").load().migrate();
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("SET LOCAL search_path TO " + schema);
            statement.execute("""
                    INSERT INTO accounts (account_number, created_at) VALUES ('0000000001', CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO transactions
                        (account_from, account_to, currency_shortname, sum, expense_category, datetime)
                    VALUES ('0000000001', '9999999999', 'USD', 10.50, 'product', '2022-01-01T00:00:00Z'),
                           ('0000000001', '9999999999', 'KZT', 5000, 'service', '2022-01-01T00:00:00Z')
                    """);
            connection.commit();
        }
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT * FROM "
                        + schema + ".transactions ORDER BY currency_shortname")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("currency_shortname")).isEqualTo("KZT");
            assertThat(rows.getBigDecimal("sum")).isEqualByComparingTo("5000");
            assertThat(rows.getBigDecimal("amount_usd")).isNull();
            assertThat(rows.getString("conversion_status")).isEqualTo("PENDING");
            assertThat(rows.getString("operation_status")).isEqualTo("PROCESSING");
            assertThat(rows.getBoolean("limit_exceeded")).isFalse();
            assertThat(rows.getBoolean("reservation_active")).isFalse();
            assertThat(rows.getBigDecimal("reserved_usd")).isNull();
            assertThat(rows.getDate("conversion_rate_date").toLocalDate())
                    .isEqualTo(java.time.LocalDate.parse("2021-12-31"));
            var sequence = rows.getLong("operation_sequence");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBigDecimal("amount_usd")).isEqualByComparingTo("10.50");
            assertThat(rows.getString("conversion_status")).isEqualTo("COMPLETED");
            assertThat(rows.getLong("operation_sequence")).isNotEqualTo(sequence);
            assertThat(rows.getString("limit_check_status")).isEqualTo("PENDING");
            assertThat(rows.next()).isFalse();
        }
    }

    @Test
    void backfillsExistingSourceAndLimitAccountsWithoutRegisteringCounterparty() throws Exception {
        var schema = "account_upgrade_test";
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("3").load().migrate();
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("SET LOCAL search_path TO " + schema);
            statement.execute("""
                    INSERT INTO transactions (account_from, account_to, currency_shortname, sum,
                        expense_category, datetime, received_at)
                    VALUES ('0000000001', '9999999999', 'USD', 10, 'product',
                        '2022-01-01T00:00:00Z', '2022-01-01T00:00:00Z')
                    """);
            statement.execute("""
                    INSERT INTO expense_limits (id, account, expense_category, amount, established_at)
                    VALUES (gen_random_uuid(), '0000000002', 'service', 500, '2022-01-02T00:00:00Z')
                    """);
            connection.commit();
        }
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            try (var accounts = statement.executeQuery(
                    "SELECT account_number FROM " + schema + ".accounts ORDER BY account_number")) {
                assertThat(accounts.next()).isTrue();
                assertThat(accounts.getString(1)).isEqualTo("0000000001");
                assertThat(accounts.next()).isTrue();
                assertThat(accounts.getString(1)).isEqualTo("0000000002");
                assertThat(accounts.next()).isFalse();
            }
        }
    }
}
