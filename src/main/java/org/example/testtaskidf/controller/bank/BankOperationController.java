package org.example.testtaskidf.controller.bank;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.testtaskidf.dto.BankNotificationRequest;
import org.example.testtaskidf.dto.BankStubRequest;
import org.example.testtaskidf.dto.OperationState;
import org.example.testtaskidf.service.OperationLifecycleService;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bank final notifications and local simulator diagnostics; all endpoints are unauthenticated. */
@RestController
@RequestMapping("/api/v1/bank")
@Tag(name = "Operation lifecycle", description = "Bank completion, reservation state and local polling simulator")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Persisted operation state",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = OperationState.class))),
    @ApiResponse(responseCode = "400", description = "Invalid JSON, UUID, status or completion time",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "Transaction not found",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "503", description = "Database operation failed",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
})
public class BankOperationController {
    private final OperationLifecycleService lifecycle;

    public BankOperationController(OperationLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    /**
     * Finalizes under the account/category lock; the original USD amount and creation month are retained.
     *
     * @param request final bank status and completion timestamp
     * @return persisted state, including an unchanged result for a repeated final status
     */
    @PostMapping("/notifications")
    @Operation(summary = "Notify final bank status; duplicates are idempotent",
            description = "SUCCEEDED retains the original reserve as an expense; FAILED releases it. "
                    + "The same final status is idempotent; replacing SUCCEEDED or FAILED returns 409. "
                    + "TIMED_OUT accepts either final status: late success restores the original USD expense "
                    + "in its creation month; late failure does not release it twice. "
                    + "Completion must not precede creation. Success with pending conversion keeps "
                    + "limit_check_status=PENDING until the USD reserve can be calculated.",
            responses = @ApiResponse(responseCode = "409", description = "Conflicting final status",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))))
    public OperationState notify(@Valid @RequestBody BankNotificationRequest request) {
        return lifecycle.notify(request);
    }

    /**
     * Stores a simulator reply without immediately applying it to the operation.
     *
     * @param id saved transaction UUID
     * @param request reply to use on subsequent worker polls
     * @return current operation state, not the configured simulator reply
     */
    @PutMapping("/stub/transactions/{id}")
    @Operation(summary = "Configure the local bank reply: PROCESSING, SUCCEEDED, FAILED or ERROR",
            description = "Postman simulator; no real bank API is contacted. The worker polls every 15 minutes "
                    + "by default. ERROR starts or continues a period of bank unavailability. After 3 hours of "
                    + "continuous errors, the next failing poll sets TIMED_OUT and releases the reserve. "
                    + "PROCESSING clears the error timer and retains the reserve. Intervals are configurable. "
                    + "After timeout the worker stops polling; submit late final status through /notifications.")
    public OperationState stub(@PathVariable UUID id, @Valid @RequestBody BankStubRequest request) {
        return lifecycle.configureStub(id, request.status());
    }

    /**
     * Reads the persisted lifecycle state without triggering conversion or bank polling.
     *
     * @param id saved transaction UUID
     * @return current operation and reservation state
     */
    @GetMapping("/transactions/{id}/status")
    @Operation(summary = "Inspect one operation state for bank integration and local diagnostics",
            description = "Read-only snapshot of a single operation. Exposes final status, completion time, "
                    + "limit flag and fixed USD reserve. This endpoint does not poll the bank or calculate rates.")
    public OperationState state(@PathVariable UUID id) {
        return lifecycle.state(id);
    }
}
