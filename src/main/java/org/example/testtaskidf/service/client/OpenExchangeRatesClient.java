package org.example.testtaskidf.service.client;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

import org.example.testtaskidf.dto.ExchangeRatePayload;
import org.example.testtaskidf.exception.ExchangeRateFailure;
import org.example.testtaskidf.util.ExchangeRateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/** Requests complete UTC-day snapshots with two retries for network failures and 5xx only. */
@Component
public class OpenExchangeRatesClient {
    private final WebClient client;
    private final String appId;
    private final Clock clock;

    public OpenExchangeRatesClient(WebClient exchangeRateWebClient,
            @Value("${exchange-rates.app-id}") String appId, Clock clock) {
        this.client = exchangeRateWebClient.mutate().build();
        this.appId = appId;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return !appId.isBlank();
    }

    public ExchangeRatePayload historical(LocalDate date) {
        if (!isConfigured()) {
            throw new ExchangeRateFailure("missing_app_id", false, Duration.ZERO);
        }
        return client.get().uri(builder -> builder.path("/historical/{date}.json")
                        .queryParam("app_id", appId).build(date))
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(ExchangeRatePayload.class);
                    }
                    int status = response.statusCode().value();
                    var wait = status == 429 ? ExchangeRateUtils.retryAfter(
                            response.headers().asHttpHeaders().getFirst("Retry-After"), clock.instant())
                            : Duration.ZERO;
                    return response.releaseBody().then(Mono.error(new ExchangeRateFailure(
                            "provider_http_" + status, status >= 500, wait)));
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(error -> !(error instanceof ExchangeRateFailure),
                        error -> new ExchangeRateFailure("provider_transport_or_payload_error", true, Duration.ZERO))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(250))
                        .filter(error -> ((ExchangeRateFailure) error).isRetryable())
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .block(Duration.ofSeconds(20));
    }
}
