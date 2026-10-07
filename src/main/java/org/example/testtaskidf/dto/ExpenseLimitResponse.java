package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Newly established USD limit and its server timestamp. */
public record ExpenseLimitResponse(UUID id, String account,
        @JsonProperty("expense_category") String expenseCategory, BigDecimal amount, String currency,
        @JsonProperty("established_at") OffsetDateTime establishedAt) {
}
