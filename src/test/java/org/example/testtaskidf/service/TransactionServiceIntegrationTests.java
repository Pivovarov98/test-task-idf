package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.example.testtaskidf.PostgresTestConfiguration;
import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.model.Transaction;
import org.example.testtaskidf.service.mapping.TransactionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Import({PostgresTestConfiguration.class, TransactionServiceIntegrationTests.FixedTimeConfiguration.class})
class TransactionServiceIntegrationTests {
    @Autowired
    private TransactionService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private TransactionMapper mapper;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM transactions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"product", "service"})
    void commitsTransactionWithAllFields(String category) {
        var request = request(category);
        var response = service.receive(request);
        var row = jdbcTemplate.queryForMap("SELECT * FROM transactions WHERE id = ?", response.id());
        assertThat(row).containsEntry("id", response.id())
                .containsEntry("account_from", request.accountFrom())
                .containsEntry("account_to", request.accountTo())
                .containsEntry("currency_shortname", request.currencyShortname())
                .containsEntry("sum", request.sum()).containsEntry("expense_category", category);
        var datetime = jdbcTemplate.queryForObject(
                "SELECT datetime FROM transactions WHERE id = ?", OffsetDateTime.class, response.id());
        var receivedAt = jdbcTemplate.queryForObject(
                "SELECT received_at FROM transactions WHERE id = ?", OffsetDateTime.class, response.id());
        assertThat(datetime).isEqualTo(request.datetime());
        assertThat(receivedAt).isEqualTo(response.receivedAt());
    }

    @Test
    void rollsBackInsertWhenResponseMappingFails() {
        var before = jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Long.class);
        var failure = new IllegalStateException("Response mapping failed");
        doThrow(failure).when(mapper).toResponse(any(Transaction.class));

        assertThatThrownBy(() -> service.receive(request("product"))).isSameAs(failure);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Long.class)).isEqualTo(before);
    }

    private TransactionRequest request(String category) {
        return new TransactionRequest("0000000123", "9999999999", "KZT", new BigDecimal("10.45"),
                category, OffsetDateTime.parse("2022-01-30T00:00:00+06:00"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTimeConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
