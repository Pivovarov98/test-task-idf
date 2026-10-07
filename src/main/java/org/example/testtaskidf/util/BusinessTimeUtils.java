package org.example.testtaskidf.util;

import java.time.Instant;
import java.time.ZoneOffset;

import org.example.testtaskidf.model.MonthPeriod;

/** Calendar calculations in the agreed Moscow business time, fixed UTC+03:00. */
public final class BusinessTimeUtils {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(3);

    private BusinessTimeUtils() {
    }

    /** Returns month boundaries in UTC instants, with the beginning included and the next month excluded. */
    public static MonthPeriod monthContaining(Instant instant) {
        var start = instant.atOffset(BUSINESS_OFFSET).toLocalDate().withDayOfMonth(1)
                .atStartOfDay().atOffset(BUSINESS_OFFSET);
        return new MonthPeriod(start.toInstant(), start.plusMonths(1).toInstant());
    }
}
