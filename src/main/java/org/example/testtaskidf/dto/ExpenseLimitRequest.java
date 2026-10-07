package org.example.testtaskidf.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Client supplies the account, category and amount; currency and time belong to the server. */
public record ExpenseLimitRequest(
        @Schema(description = "Source account; registered automatically if unknown", example = "0000000123")
        @NotNull @Pattern(regexp = "[0-9]{10}") String account,
        @Schema(description = "Expense category", example = "product", allowableValues = {"product", "service"})
        @JsonProperty("expense_category") @NotNull @Pattern(regexp = "product|service") String expenseCategory,
        @Schema(description = "New monthly limit in USD; zero is allowed", example = "1500.00",
                minimum = "0", maximum = "99999999999999999.99", multipleOf = 0.01)
        @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
