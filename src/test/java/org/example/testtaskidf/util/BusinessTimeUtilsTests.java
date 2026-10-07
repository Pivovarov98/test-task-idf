package org.example.testtaskidf.util;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessTimeUtilsTests {
    @Test
    void monthChangesAtNinePmUtcInMoscow() {
        var before = BusinessTimeUtils.monthContaining(Instant.parse("2026-01-31T20:59:59Z"));
        var after = BusinessTimeUtils.monthContaining(Instant.parse("2026-01-31T21:00:00Z"));
        assertEquals(Instant.parse("2025-12-31T21:00:00Z"), before.startInclusive());
        assertEquals(Instant.parse("2026-01-31T21:00:00Z"), before.endExclusive());
        assertEquals(before.endExclusive(), after.startInclusive());
        assertEquals(Instant.parse("2026-02-28T21:00:00Z"), after.endExclusive());
    }

    @Test
    void handlesLeapYearFebruary() {
        var month = BusinessTimeUtils.monthContaining(Instant.parse("2024-02-15T00:00:00Z"));
        assertEquals(Instant.parse("2024-01-31T21:00:00Z"), month.startInclusive());
        assertEquals(Instant.parse("2024-02-29T21:00:00Z"), month.endExclusive());
    }
}
