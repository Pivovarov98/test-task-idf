package org.example.testtaskidf.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Configures the persisted local bank reply consumed by the polling worker.
 *
 * @param status PROCESSING resets the error timer; ERROR starts or continues it;
 *               SUCCEEDED and FAILED finalize a processing operation at the next poll
 */
@Schema(description = "Local bank simulator configuration; does not immediately finalize the operation")
public record BankStubRequest(
        @Schema(description = "Reply for subsequent polls. PROCESSING resets the continuous-error timer; "
                + "ERROR represents an unavailable bank. Final replies apply only while the operation is PROCESSING. "
                + "After TIMED_OUT send a final notification through POST /api/v1/bank/notifications.",
                allowableValues = {"PROCESSING", "SUCCEEDED", "FAILED", "ERROR"}, example = "ERROR")
        @NotNull @Pattern(regexp = "PROCESSING|SUCCEEDED|FAILED|ERROR") String status) {
}
