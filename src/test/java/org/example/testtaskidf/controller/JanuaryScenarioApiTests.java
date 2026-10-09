package org.example.testtaskidf.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.example.testtaskidf.PostgresTestConfiguration;
import org.example.testtaskidf.dto.BankNotificationRequest;
import org.example.testtaskidf.dto.ExpenseLimitRequest;
import org.example.testtaskidf.dto.ExpenseLimitResponse;
import org.example.testtaskidf.dto.LimitExceededTransactionPageResponse;
import org.example.testtaskidf.dto.OperationState;
import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.dto.TransactionResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "exchange-rates.enabled=true", "exchange-rates.app-id=scenario-test",
    "exchange-rates.worker-delay-ms=100", "bank.worker-enabled=false"
})
@Import({PostgresTestConfiguration.class, JanuaryScenarioApiTests.TimeConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JanuaryScenarioApiTests {
    private static final String ACCOUNT = "0000000123";
    private static final String RATE_PATH = "/historical/2021-12-31.json";
    private static final WireMockServer RATES = new WireMockServer(wireMockConfig().dynamicPort());

    @LocalServerPort
    private int port;
    @Autowired
    private MutableClock clock;

    @DynamicPropertySource
    static void rates(DynamicPropertyRegistry registry) {
        RATES.start();
        RATES.stubFor(get(urlPathEqualTo(RATE_PATH)).withQueryParam("app_id", equalTo("scenario-test"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"base":"USD","timestamp":1640995199,"rates":{"USD":1,"KZT":500,"RUB":75}}
                        """)));
        registry.add("exchange-rates.base-url", RATES::baseUrl);
    }

    @AfterAll
    static void stopRates() {
        RATES.stop();
    }

    @Test
    void firstTableCaseReturnsOnlyJanuaryThirdAndLastThirteenthTransaction() {
        var http = RestClient.builder().baseUrl("http://localhost:" + port).build();
        // The table uses USD. The real startup worker still fetches its daily snapshot from WireMock.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                RATES.verify(getRequestedFor(urlPathEqualTo(RATE_PATH))
                        .withQueryParam("app_id", equalTo("scenario-test"))));

        // A new account starts with the default 1000 USD limit dated January 1.
        transaction(http, "2022-01-02T12:00:00+03:00", "500", false);
        transaction(http, "2022-01-03T12:00:00+03:00", "600", true);
        setLimit(http, "2022-01-10T12:00:00+03:00", "2000");
        transaction(http, "2022-01-11T12:00:00+03:00", "100", false);
        transaction(http, "2022-01-12T12:00:00+03:00", "700", false);
        transaction(http, "2022-01-13T12:00:00+03:00", "100", false); // Exactly 2000 USD.
        transaction(http, "2022-01-13T13:00:00+03:00", "100", true);

        var response = http.get().uri("/api/v1/client/accounts/" + ACCOUNT
                + "/transactions/limit-exceeded?page=0&size=20")
                .retrieve().toEntity(LimitExceededTransactionPageResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var page = response.getBody();
        assertThat(page).isNotNull();
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.content()).hasSize(2);
        assertThat(page.content()).allSatisfy(item -> {
            assertThat(item.accountFrom()).isEqualTo(ACCOUNT);
            assertThat(item.accountTo()).isEqualTo("9999999999");
            assertThat(item.expenseCategory()).isEqualTo("product");
            assertThat(item.currencyShortname()).isEqualTo("USD");
            assertThat(item.limitCurrencyShortname()).isEqualTo("USD");
        });
        var thirteenth = page.content().get(0);
        assertThat(thirteenth.datetime()).isEqualTo(OffsetDateTime.parse("2022-01-13T13:00:00+03:00"));
        assertThat(thirteenth.sum()).isEqualByComparingTo("100");
        assertThat(thirteenth.limitSum()).isEqualByComparingTo("2000");
        assertThat(thirteenth.limitDatetime()).isEqualTo(OffsetDateTime.parse("2022-01-10T12:00:00+03:00"));
        var third = page.content().get(1);
        assertThat(third.datetime()).isEqualTo(OffsetDateTime.parse("2022-01-03T12:00:00+03:00"));
        assertThat(third.sum()).isEqualByComparingTo("600");
        assertThat(third.limitSum()).isEqualByComparingTo("1000");
        assertThat(third.limitDatetime()).isEqualTo(OffsetDateTime.parse("2022-01-01T00:00:00+03:00"));
        assertThat(RATES.findAllUnmatchedRequests()).isEmpty();
    }

    private void setLimit(RestClient http, String datetime, String amount) {
        var time = OffsetDateTime.parse(datetime);
        clock.set(time.toInstant());
        var response = http.post().uri("/api/v1/client/limits").contentType(MediaType.APPLICATION_JSON)
                .body(new ExpenseLimitRequest(ACCOUNT, "product", new BigDecimal(amount)))
                .retrieve().toEntity(ExpenseLimitResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().establishedAt()).isEqualTo(time);
        assertThat(response.getBody().amount()).isEqualByComparingTo(amount);
        assertThat(response.getBody().currency()).isEqualTo("USD");
    }

    private void transaction(RestClient http, String datetime, String amount, boolean exceeded) {
        var time = OffsetDateTime.parse(datetime);
        clock.set(time.toInstant());
        var response = http.post().uri("/api/v1/bank/transactions").contentType(MediaType.APPLICATION_JSON)
                .body(new TransactionRequest(ACCOUNT, "9999999999", "USD", new BigDecimal(amount), "product", time))
                .retrieve().toEntity(TransactionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        var completion = http.post().uri("/api/v1/bank/notifications").contentType(MediaType.APPLICATION_JSON)
                .body(new BankNotificationRequest(response.getBody().id(), "SUCCEEDED", time.plusSeconds(1)))
                .retrieve().toEntity(OperationState.class);
        assertThat(completion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completion.getBody()).isNotNull();
        assertThat(completion.getBody().operationStatus()).isEqualTo("SUCCEEDED");
        assertThat(completion.getBody().limitCheckStatus()).isEqualTo("COMPLETED");
        assertThat(completion.getBody().reservedUsd()).isEqualByComparingTo(amount);
        assertThat(completion.getBody().limitExceeded()).isEqualTo(exceeded);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        MutableClock scenarioClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now =
                new AtomicReference<>(Instant.parse("2022-01-01T09:00:00Z"));

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
