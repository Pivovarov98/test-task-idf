package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * User-established or implicit default USD limit; reading does not persist generated history defaults.
 *
 * @param id generated user limit identifier, or null for an implicit default in history
 * @param account ten-digit source account, including leading zeros
 * @param expenseCategory product or service
 * @param amount monthly USD limit, including zero
 * @param currency always USD
 * @param establishedAt server establishment time for user limits, or fixed UTC+03:00 month start for defaults
 */
public record ExpenseLimitResponse(
        @Schema(description = "User limit UUID; null for an implicit default in history",
                types = {"string", "null"}, format = "uuid") UUID id,
        @Schema(description = "Source account; leading zeros are preserved", pattern = "[0-9]{10}",
                example = "0000000123") String account,
        @Schema(allowableValues = {"product", "service"})
        @JsonProperty("expense_category") String expenseCategory,
        @Schema(description = "Monthly USD limit; zero is allowed", minimum = "0", example = "1000.00")
        BigDecimal amount,
        @Schema(allowableValues = "USD", example = "USD") String currency,
        @Schema(description = "Server establishment time, or Moscow month start for a default",
                type = "string", format = "date-time", example = "2026-10-01T00:00:00+03:00")
        @JsonProperty("established_at") OffsetDateTime establishedAt) {
}
