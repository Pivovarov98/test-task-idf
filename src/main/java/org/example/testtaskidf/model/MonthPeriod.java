package org.example.testtaskidf.model;

import java.time.Instant;

/** Half-open time interval used to select transactions in a calendar month. */
public record MonthPeriod(Instant startInclusive, Instant endExclusive) {
}
