package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Task 6 contract: six original transaction fields and three fields describing the exceeded limit.
 *
 * @param accountFrom source account
 * @param accountTo counterparty account
 * @param currencyShortname original ISO 4217 currency
 * @param sum original amount, without conversion on reading
 * @param expenseCategory product or service
 * @param datetime creation time presented at UTC+03:00
 * @param limitSum historical limit amount in USD
 * @param limitDatetime user establishment time, or Moscow month start for a default
 * @param limitCurrencyShortname always USD
 */
@Schema(description = "Successful exceeded transaction with the limit fixed at reservation")
public record LimitExceededTransactionResponse(
        @Schema(example = "0000000123") @JsonProperty("account_from") String accountFrom,
        @Schema(example = "9999999999") @JsonProperty("account_to") String accountTo,
        @Schema(example = "USD") @JsonProperty("currency_shortname") String currencyShortname,
        @Schema(description = "Original transaction amount in currency_shortname", example = "600.00") BigDecimal sum,
        @Schema(allowableValues = {"product", "service"})
        @JsonProperty("expense_category") String expenseCategory,
        @Schema(description = "Operation creation time, presented in Moscow time (UTC+03:00)",
                type = "string", format = "date-time", example = "2022-01-03T00:00:00+03:00") OffsetDateTime datetime,
        @Schema(description = "Historical USD limit, including zero; later changes do not replace it",
                example = "1000.00") @JsonProperty("limit_sum") BigDecimal limitSum,
        @Schema(description = "User establishment time, or creation month's start for the default 1000 USD",
                type = "string", format = "date-time", example = "2022-01-01T00:00:00+03:00")
        @JsonProperty("limit_datetime") OffsetDateTime limitDatetime,
        @Schema(allowableValues = "USD", example = "USD")
        @JsonProperty("limit_currency_shortname") String limitCurrencyShortname) {
}
