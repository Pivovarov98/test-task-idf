package org.example.testtaskidf.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.example.testtaskidf.model.ExpenseCategory;
import org.example.testtaskidf.model.Transaction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class JdbcTransactionRepositoryTests {
    @ParameterizedTest
    @ValueSource(strings = {"product", "service"})
    void bindsAllTransactionFieldsInColumnOrder(String category) {
        var jdbcTemplate = mock(JdbcTemplate.class);
        ExpenseCategory expenseCategory = category.equals("product")
                ? new ExpenseCategory.Product() : new ExpenseCategory.ServiceExpense();
        var transaction = new Transaction(UUID.randomUUID(), "0000000123", "9999999999", "KZT",
                new BigDecimal("10.45"), expenseCategory, OffsetDateTime.parse("2022-01-30T00:00:00+06:00"),
                OffsetDateTime.parse("2026-10-07T12:00:00Z"));

        new JdbcTransactionRepository(jdbcTemplate).save(transaction);

        verify(jdbcTemplate).update(contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"), eq(transaction.id()),
                eq(transaction.accountFrom()), eq(transaction.accountTo()), eq(transaction.currencyShortname()),
                eq(transaction.sum()), eq(category), eq(transaction.datetime()), eq(transaction.receivedAt()),
                eq(transaction.receivedAt()), eq(java.time.LocalDate.parse("2022-01-28")));
        verifyNoMoreInteractions(jdbcTemplate);
    }
}
