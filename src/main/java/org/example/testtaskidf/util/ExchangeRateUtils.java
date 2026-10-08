package org.example.testtaskidf.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Time and monetary calculations shared by reception and background processing. */
public final class ExchangeRateUtils {
    private ExchangeRateUtils() {
    }

    public static LocalDate lastClosedDate(Instant now) {
        return now.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(1);
    }

    public static LocalDate targetDate(Instant occurredAt, Instant now) {
        var date = occurredAt.atOffset(ZoneOffset.UTC).toLocalDate();
        var closed = lastClosedDate(now);
        return date.isAfter(closed) ? closed : date;
    }

    public static BigDecimal toUsd(BigDecimal amount, BigDecimal unitsPerUsd) {
        return amount.divide(unitsPerUsd, 2, RoundingMode.HALF_UP);
    }

    public static Duration retryAfter(String value, Instant now) {
        if (value == null) {
            return Duration.ofHours(1);
        }
        try {
            return Duration.ofSeconds(Math.max(1, Long.parseLong(value)));
        } catch (NumberFormatException exception) {
            try {
                var date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                var duration = Duration.between(now, date);
                return duration.isNegative() || duration.isZero() ? Duration.ofSeconds(1) : duration;
            } catch (java.time.format.DateTimeParseException invalidHeader) {
                return Duration.ofHours(1);
            }
        }
    }
}
