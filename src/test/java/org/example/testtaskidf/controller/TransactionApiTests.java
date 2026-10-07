package org.example.testtaskidf.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.example.testtaskidf.PostgresTestConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, TransactionApiTests.FixedTimeConfiguration.class})
@Transactional
class TransactionApiTests {
    private static final String URL = "/api/v1/bank/transactions";
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final String PAYLOAD = """
            {"account_from":"0000000321","account_to":"9999999999","currency_shortname":"KZT",
             "sum":10000.45,"expense_category":"product","datetime":"2022-01-30T00:00:00+06:00"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"product", "service"})
    void receivesAndPersistsTransactionUsingClock(String category) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD.replace("product", category)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.account_from").value("0000000321"))
                .andExpect(jsonPath("$.expense_category").value(category))
                .andExpect(jsonPath("$.received_at").value("2026-10-06T12:00:00Z"));

        var row = jdbcTemplate.queryForMap("SELECT * FROM transactions WHERE account_from = '0000000321'");
        assertEquals(category, row.get("expense_category"));
        assertEquals(new BigDecimal("10000.45"), row.get("sum"));
        var receivedAt = jdbcTemplate.queryForObject(
                "SELECT received_at FROM transactions WHERE account_from = '0000000321'", OffsetDateTime.class);
        assertEquals(NOW, receivedAt.toInstant());
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void invalidRequestReturnsProblemDetailWithoutSaving(String payload) throws Exception {
        var before = jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Long.class);
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value(URL));
        assertEquals(before, jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Long.class));
    }

    @Test
    void exposesDocumentedTransactionContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Bank Transactions API"))
                .andExpect(jsonPath("$.paths['/api/v1/bank/transactions'].post.summary")
                        .value("Receive a bank transaction"))
                .andExpect(jsonPath("$.paths['/api/v1/bank/transactions'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bank/transactions'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bank/transactions'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bank/transactions'].post.responses['500']").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionRequest.properties.account_from.pattern")
                        .value("[0-9]{10}"))
                .andExpect(jsonPath("$.components.schemas.TransactionRequest.properties.expense_category.enum[0]")
                        .value("product"))
                .andExpect(jsonPath("$.components.schemas.TransactionRequest.properties.datetime.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.TransactionResponse.properties.received_at").exists());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void validationErrorsUseInputFieldNames() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD.replace("0000000321", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.account_from").exists());
    }

    @Test
    void unsupportedMethodReturnsProblemDetail() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static Stream<String> invalidPayloads() {
        return Stream.of(
                PAYLOAD.replace("0000000321", "123"),
                PAYLOAD.replace("9999999999", "abcdefghij"),
                PAYLOAD.replace("KZT", "ABC"),
                PAYLOAD.replace("product", "products"),
                PAYLOAD.replace("10000.45", "0"),
                PAYLOAD.replace("10000.45", "-1"),
                PAYLOAD.replace("10000.45", "1.001"),
                PAYLOAD.replace("10000.45", "100000000000000000.00"),
                PAYLOAD.replace("10000.45", "null"),
                PAYLOAD.replace("2022-01-30T00:00:00+06:00", "2022-01-30T00:00:00"),
                "{}", "{invalid}");
    }

    @TestConfiguration
    static class FixedTimeConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
