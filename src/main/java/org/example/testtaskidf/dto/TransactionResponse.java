package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transaction acknowledged after persistence; acknowledgment does not mean bank completion.
 *
 * @param id server-generated transaction identifier
 * @param accountFrom source account preserving leading zeros
 * @param accountTo destination account preserving leading zeros
 * @param currencyShortname ISO 4217 currency code
 * @param sum original amount
 * @param expenseCategory product or service
 * @param datetime operation creation time; determines the Moscow accounting month and UTC rate date
 * @param receivedAt server receipt time in UTC
 * @param amountUsd fixed converted amount, or null while pending or unsupported
 * @param conversionStatus PENDING, COMPLETED or UNSUPPORTED_CURRENCY
 * @param exchangeRateId saved rate used; null for USD or incomplete conversion
 * @param operation lifecycle and reservation fields flattened into the response JSON
 */
@Schema(description = "Persisted transaction acknowledgment with conversion and flattened lifecycle fields; "
        + "bank completion may still be pending")
public record TransactionResponse(
        @Schema(description = "Generated UUID", example = "a0187ed2-b40a-4cde-86e2-68972c69dd10") UUID id,
        @Schema(example = "0000000321") @JsonProperty("account_from") String accountFrom,
        @Schema(example = "9999999999") @JsonProperty("account_to") String accountTo,
        @Schema(example = "KZT") @JsonProperty("currency_shortname") String currencyShortname,
        @Schema(example = "10000.45") BigDecimal sum,
        @Schema(example = "product", allowableValues = {"product", "service"})
        @JsonProperty("expense_category") String expenseCategory,
        @Schema(description = "Creation time; accounting month uses Moscow, exchange-rate date uses UTC",
                example = "2022-01-30T00:00:00+06:00") OffsetDateTime datetime,
        @Schema(description = "Server receipt time in UTC", example = "2026-10-07T12:00:00Z")
        @JsonProperty("received_at") OffsetDateTime receivedAt,
        @Schema(description = "Fixed USD amount, never recalculated on completion; null while pending or unsupported",
                example = "20.50")
        @JsonProperty("amount_usd") BigDecimal amountUsd,
        @Schema(allowableValues = {"PENDING", "COMPLETED", "UNSUPPORTED_CURRENCY"})
        @JsonProperty("conversion_status") String conversionStatus,
        @Schema(description = "Saved quote used for conversion; absent for USD or pending calculations")
        @JsonProperty("exchange_rate_id") UUID exchangeRateId,
        @com.fasterxml.jackson.annotation.JsonUnwrapped OperationState operation) {
}
