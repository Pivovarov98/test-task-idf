package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transaction returned after a successful save.
 *
 * @param id server-generated transaction identifier
 * @param accountFrom source account preserving leading zeros
 * @param accountTo destination account preserving leading zeros
 * @param currencyShortname ISO 4217 currency code
 * @param sum original amount
 * @param expenseCategory product or service
 * @param datetime original occurrence time and offset
 * @param receivedAt server receipt time in UTC
 */
@Schema(description = "Saved transaction with server-generated identifier and receipt time")
public record TransactionResponse(
        @Schema(description = "Generated UUID", example = "a0187ed2-b40a-4cde-86e2-68972c69dd10") UUID id,
        @Schema(example = "0000000321") @JsonProperty("account_from") String accountFrom,
        @Schema(example = "9999999999") @JsonProperty("account_to") String accountTo,
        @Schema(example = "KZT") @JsonProperty("currency_shortname") String currencyShortname,
        @Schema(example = "10000.45") BigDecimal sum,
        @Schema(example = "product", allowableValues = {"product", "service"})
        @JsonProperty("expense_category") String expenseCategory,
        @Schema(example = "2022-01-30T00:00:00+06:00") OffsetDateTime datetime,
        @Schema(description = "Server receipt time in UTC", example = "2026-10-07T12:00:00Z")
        @JsonProperty("received_at") OffsetDateTime receivedAt) {
}
