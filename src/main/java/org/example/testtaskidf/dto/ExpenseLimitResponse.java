package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** User-established or implicit default USD limit; history defaults have no persisted UUID. */
public record ExpenseLimitResponse(
        @Schema(description = "User limit UUID; null for an implicit default") UUID id,
        @Schema(example = "0000000123") String account,
        @Schema(allowableValues = {"product", "service"})
        @JsonProperty("expense_category") String expenseCategory,
        @Schema(description = "Monthly USD limit; zero is allowed", example = "1000.00") BigDecimal amount,
        @Schema(allowableValues = "USD", example = "USD") String currency,
        @Schema(description = "Server establishment time, or Moscow month start for a default",
                type = "string", format = "date-time", example = "2026-10-01T00:00:00+03:00")
        @JsonProperty("established_at") OffsetDateTime establishedAt) {
}
