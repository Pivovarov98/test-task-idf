package org.example.testtaskidf.service.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.testtaskidf.exception.ExchangeRateFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenExchangeRatesClientTests {
    private static final LocalDate DATE = LocalDate.parse("2026-10-07");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void requestsHistoricalSnapshotAndDecodesExactDecimalValues() {
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            assertThat(request.url().getPath()).isEqualTo("/api/historical/2026-10-07.json");
            assertThat(request.url().getQuery()).isEqualTo("app_id=test-app-id");
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body("""
                            {"base":"USD","timestamp":1791417599,"rates":{"USD":1,"KZT":500.123456789012345},
                             "disclaimer":"provider metadata"}
                            """).build());
        });
        var payload = client.historical(DATE);
        assertThat(payload.rates().get("KZT")).isEqualByComparingTo("500.123456789012345");
        assertThat(calls).hasValue(1);
    }

    @Test
    void retries5xxTwiceAndSanitizesFailureWithoutLeakingCredentials() {
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("test-app-id must never appear in the exception").build());
        });
        assertThatThrownBy(() -> client.historical(DATE)).isInstanceOf(ExchangeRateFailure.class)
                .hasMessage("provider_http_503").hasNoCause();
        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetry429AndPreservesRetryAfter() {
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "120").build());
        });
        assertThatThrownBy(() -> client.historical(DATE)).isInstanceOfSatisfying(ExchangeRateFailure.class,
                failure -> assertThat(failure.getRetryAfter()).isEqualTo(Duration.ofMinutes(2)));
        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotRetryInvalidCredentials() {
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
        });
        assertThatThrownBy(() -> client.historical(DATE)).hasMessage("provider_http_401");
        assertThat(calls).hasValue(1);
    }

    @Test
    @Timeout(25)
    void timesOutUnresponsiveProviderAndLimitsTotalAttempts() {
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.never();
        });
        assertThatThrownBy(() -> client.historical(DATE)).isInstanceOf(ExchangeRateFailure.class)
                .hasMessage("provider_transport_or_payload_error");
        assertThat(calls).hasValue(3);
    }

    @Test
    void retriesBrokenConnectionAndSanitizesUnderlyingNetworkError() {
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.error(new java.io.IOException("Connection reset with test-app-id"));
        });
        assertThatThrownBy(() -> client.historical(DATE)).isInstanceOf(ExchangeRateFailure.class)
                .hasMessage("provider_transport_or_payload_error").hasNoCause();
        assertThat(calls).hasValue(3);
    }

    @Test
    void malformedJsonDoesNotProduceAnUsableSnapshot() {
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body("{invalid-json").build());
        });
        assertThatThrownBy(() -> client.historical(DATE)).isInstanceOf(ExchangeRateFailure.class)
                .hasMessage("provider_transport_or_payload_error");
        assertThat(calls).hasValue(3);
    }

    @Test
    void missingAppIdMakesNoRequest() {
        var client = new OpenExchangeRatesClient(WebClient.builder().exchangeFunction(request -> {
            throw new AssertionError("No HTTP call expected");
        }).build(), "", clock);
        assertThatThrownBy(() -> client.historical(DATE)).hasMessage("missing_app_id");
    }

    private OpenExchangeRatesClient client(ExchangeFunction exchange) {
        return new OpenExchangeRatesClient(WebClient.builder().baseUrl("https://example.invalid/api")
                .exchangeFunction(exchange).build(), "test-app-id", clock);
    }
}
