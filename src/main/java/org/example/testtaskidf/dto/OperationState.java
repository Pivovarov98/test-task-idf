package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Public operation state, distinct from the currency conversion state. */
public record OperationState(@JsonProperty("operation_status") String operationStatus,
        @JsonProperty("completed_at") OffsetDateTime completedAt,
        @JsonProperty("limit_exceeded") boolean limitExceeded,
        @JsonProperty("limit_check_status") String limitCheckStatus,
        @JsonProperty("reserved_usd") BigDecimal reservedUsd,
        @JsonProperty("reservation_active") boolean reservationActive) {
}
