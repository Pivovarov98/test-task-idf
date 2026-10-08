package org.example.testtaskidf.exception;

import java.time.Duration;

/** Sanitized failure data: no provider response, URI or App ID is logged. */
public class ExchangeRateFailure extends RuntimeException {
    private final boolean retryable;
    private final Duration retryAfter;

    public ExchangeRateFailure(String code, boolean retryable, Duration retryAfter) {
        super(code);
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
