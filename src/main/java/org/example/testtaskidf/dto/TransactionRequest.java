package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.example.testtaskidf.config.validation.CurrencyCode;

public record TransactionRequest(
        @JsonProperty("account_from") @NotNull @Pattern(regexp = "[0-9]{10}") String accountFrom,
        @JsonProperty("account_to") @NotNull @Pattern(regexp = "[0-9]{10}") String accountTo,
        @JsonProperty("currency_shortname") @NotNull @CurrencyCode String currencyShortname,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal sum,
        @JsonProperty("expense_category") @NotNull @Pattern(regexp = "product|service") String expenseCategory,
        @NotNull OffsetDateTime datetime) {
}
