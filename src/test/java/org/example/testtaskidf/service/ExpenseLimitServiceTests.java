package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.example.testtaskidf.model.ExpenseLimit;
import org.example.testtaskidf.repository.ExpenseLimitRepository;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseLimitServiceTests {
    private final Instant now = Instant.parse("2026-10-07T12:00:00Z");
    private final ExpenseLimitRepository repository = mock(ExpenseLimitRepository.class);
    private final ExpenseLimitService service = new ExpenseLimitService(repository, Clock.fixed(now, ZoneOffset.UTC));

    @ParameterizedTest
    @ValueSource(strings = {"product", "service"})
    void defaultsIndependentlyForBothCategories(String code) {
        var category = ExpenseCategoryUtils.fromCode(code);
        when(repository.findLatest("0000000123", category, now)).thenReturn(Optional.empty());
        var resolved = service.getCurrentLimit("0000000123", category);
        assertEquals(new BigDecimal("1000.00"), resolved.amount());
        assertEquals("USD", resolved.currency());
        assertFalse(resolved.configuredLimit().isPresent());
        verify(repository).findLatest("0000000123", category, now);
    }

    @Test
    void keepsZeroAsConfiguredLimitInsteadOfUsingDefault() {
        var category = ExpenseCategoryUtils.fromCode("product");
        var limit = new ExpenseLimit(UUID.randomUUID(), "0000000123", category,
                new BigDecimal("0.00"), "USD", now);
        when(repository.findLatest("0000000123", category, now)).thenReturn(Optional.of(limit));
        var resolved = service.getCurrentLimit("0000000123", category);
        assertEquals(new BigDecimal("0.00"), resolved.amount());
        assertEquals(Optional.of(limit), resolved.configuredLimit());
    }

    @Test
    void rejectsMalformedAccount() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getCurrentLimit("123", ExpenseCategoryUtils.fromCode("product")));
    }
}
