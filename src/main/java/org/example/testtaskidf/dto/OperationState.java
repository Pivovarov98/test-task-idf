package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Persisted lifecycle state, independent of conversion_status; flattened in a transaction response.
 * Successful reservations remain active as expenses. Failed and timed-out reservations are released.
 *
 * @param operationStatus PROCESSING, SUCCEEDED, FAILED or TIMED_OUT; ERROR is only a stub reply
 * @param completedAt bank completion time, or local timeout time; null while processing
 * @param limitExceeded false while checking is pending; success uses the flag fixed at reservation,
 *                      failure or timeout uses whether the limit was already exceeded before reservation
 * @param limitCheckStatus PENDING until final status and, for success, a calculated USD reserve are available
 * @param reservedUsd fixed USD amount; null before reservation and retained for audit after release
 * @param reservationActive whether the fixed amount occupies the creation month's limit
 */
@Schema(description = "Operation lifecycle and reservation in its creation month, using Europe/Moscow (UTC+03:00)")
public record OperationState(
        @Schema(description = "Persisted operation status; TIMED_OUT permits a later bank final notification",
                allowableValues = {"PROCESSING", "SUCCEEDED", "FAILED", "TIMED_OUT"}, example = "PROCESSING")
        @JsonProperty("operation_status") String operationStatus,
        @Schema(description = "Bank completion time, or local timeout time; null while processing",
                type = "string", format = "date-time", example = "2026-10-08T10:00:00+03:00")
        @JsonProperty("completed_at") OffsetDateTime completedAt,
        @Schema(description = "False while pending. On success: occupied USD plus this reserve exceeds the "
                + "historical limit. On failure or timeout: true only if it was already exceeded before this reserve. "
                + "Exact equality does not exceed a limit; later limit changes do not rewrite the flag.",
                example = "false")
        @JsonProperty("limit_exceeded") boolean limitExceeded,
        @Schema(description = "Success with unavailable conversion remains PENDING until reservation is calculated",
                allowableValues = {"PENDING", "COMPLETED"}, example = "PENDING")
        @JsonProperty("limit_check_status") String limitCheckStatus,
        @Schema(description = "Fixed USD reserve; null until calculated. Retained after release; never reconverted",
                example = "600.00")
        @JsonProperty("reserved_usd") BigDecimal reservedUsd,
        @Schema(description = "True for a held processing reserve or a successful expense; false after release",
                example = "true")
        @JsonProperty("reservation_active") boolean reservationActive) {
}
