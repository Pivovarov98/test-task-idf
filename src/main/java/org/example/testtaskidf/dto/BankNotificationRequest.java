package org.example.testtaskidf.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Final status supplied by the bank, including late completion after a local timeout.
 *
 * @param transactionId identifier returned by transaction reception
 * @param status SUCCEEDED or FAILED; repeating the same final status is idempotent
 * @param completedAt bank completion time with an offset; must not precede operation creation
 */
@Schema(description = "Bank final notification; can finalize PROCESSING or TIMED_OUT operations")
public record BankNotificationRequest(
        @Schema(description = "UUID returned by POST /api/v1/bank/transactions",
                example = "a0187ed2-b40a-4cde-86e2-68972c69dd10")
        @JsonProperty("transaction_id") @NotNull UUID transactionId,
        @Schema(description = "Final bank status", allowableValues = {"SUCCEEDED", "FAILED"}, example = "SUCCEEDED")
        @NotNull @Pattern(regexp = "SUCCEEDED|FAILED") String status,
        @Schema(description = "Bank completion time with offset, at or after the operation's datetime",
                type = "string", format = "date-time", example = "2026-10-08T10:00:00+03:00")
        @JsonProperty("completed_at") @NotNull OffsetDateTime completedAt) {
}
