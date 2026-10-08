package org.example.testtaskidf.util;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeRateUtilsTests {
    @ParameterizedTest
    @CsvSource({"1,8,0.13", "10000.45,500,20.00", "1,1,1.00", "0.01,1000,0.00"})
    void convertsWithHalfUpToCents(String amount, String rate, String expected) {
        assertThat(ExchangeRateUtils.toUsd(new BigDecimal(amount), new BigDecimal(rate)))
                .isEqualTo(new BigDecimal(expected));
    }

    @Test
    void usesUtcDateAtMoscowMidnightAndOnlyClosedDays() {
        var now = Instant.parse("2026-10-08T06:00:00Z");
        assertThat(ExchangeRateUtils.targetDate(OffsetDateTime.parse("2026-10-08T01:00:00+03:00").toInstant(), now))
                .isEqualTo(LocalDate.parse("2026-10-07"));
        assertThat(ExchangeRateUtils.targetDate(Instant.parse("2026-10-08T12:00:00Z"), now))
                .isEqualTo(LocalDate.parse("2026-10-07"));
        assertThat(ExchangeRateUtils.targetDate(Instant.parse("2022-01-01T12:00:00Z"), now))
                .isEqualTo(LocalDate.parse("2022-01-01"));
    }

    @Test
    void supportsRetryAfterSecondsHttpDatesAndInvalidHeaders() {
        var now = Instant.parse("2026-10-08T06:00:00Z");
        assertThat(ExchangeRateUtils.retryAfter("120", now)).isEqualTo(Duration.ofMinutes(2));
        assertThat(ExchangeRateUtils.retryAfter("Thu, 8 Oct 2026 06:02:00 GMT", now))
                .isEqualTo(Duration.ofMinutes(2));
        assertThat(ExchangeRateUtils.retryAfter("Thu, 8 Oct 2026 05:59:00 GMT", now))
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(ExchangeRateUtils.retryAfter(null, now)).isEqualTo(Duration.ofHours(1));
        assertThat(ExchangeRateUtils.retryAfter("invalid", now)).isEqualTo(Duration.ofHours(1));
    }
}
