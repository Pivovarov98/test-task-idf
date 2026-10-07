package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.example.testtaskidf.config.validation.CurrencyCode;

/**
 * Incoming bank transaction; identifiers and receipt timestamps are assigned by the server.
 *
 * @param accountFrom source account, exactly ten digits including leading zeros
 * @param accountTo destination account, exactly ten digits including leading zeros
 * @param currencyShortname ISO 4217 currency accepted by the Java runtime
 * @param sum positive amount with at most seventeen integer and two fractional digits
 * @param expenseCategory category code: product or service
 * @param datetime occurrence time with an explicit UTC offset
 */
@Schema(description = "Incoming transaction; all fields are required")
public record TransactionRequest(
        @Schema(description = "Source account; leading zeros are preserved", example = "0000000321")
        @JsonProperty("account_from") @NotNull @Pattern(regexp = "[0-9]{10}") String accountFrom,
        @Schema(description = "Destination account", example = "9999999999")
        @JsonProperty("account_to") @NotNull @Pattern(regexp = "[0-9]{10}") String accountTo,
        @Schema(description = "Case-sensitive ISO 4217 currency code supported by Java", example = "KZT",
                minLength = 3, maxLength = 3, pattern = "[A-Z]{3}")
        @JsonProperty("currency_shortname") @NotNull @CurrencyCode String currencyShortname,
        @Schema(description = "Positive amount; at most 17 integer and 2 fractional digits", example = "10000.45",
                minimum = "0", exclusiveMinimum = true, maximum = "99999999999999999.99", multipleOf = 0.01)
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal sum,
        @Schema(description = "Expense category", example = "product", allowableValues = {"product", "service"})
        @JsonProperty("expense_category") @NotNull @Pattern(regexp = "product|service") String expenseCategory,
        @Schema(description = "Occurrence time; timezone offset is mandatory",
                example = "2022-01-30T00:00:00+06:00", type = "string", format = "date-time")
        @NotNull OffsetDateTime datetime) {
}
