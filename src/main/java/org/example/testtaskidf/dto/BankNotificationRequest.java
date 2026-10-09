package org.example.testtaskidf.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BankNotificationRequest(@JsonProperty("transaction_id") @NotNull UUID transactionId,
        @NotNull @Pattern(regexp = "SUCCEEDED|FAILED") String status,
        @JsonProperty("completed_at") @NotNull OffsetDateTime completedAt) {
}
