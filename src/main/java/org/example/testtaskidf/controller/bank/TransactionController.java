package org.example.testtaskidf.controller.bank;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.dto.TransactionResponse;
import org.example.testtaskidf.service.BankTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Receives bank transactions through the versioned HTTP API. */
@RestController
@Tag(name = "Transactions", description = "Receive and persist bank transactions")
@RequestMapping("/api/v1/bank/transactions")
public class TransactionController {

    private final BankTransactionService service;
    public TransactionController(BankTransactionService service) {
        this.service = service;
    }

    /**
     * Validates and persists an operation, converts from cached rates and reserves available USD amounts.
     * HTTP 201 acknowledges storage; the bank must later supply a final notification.
     *
     * @param request transaction supplied by the bank
     * @return saved transaction with conversion and lifecycle state
     */
    @Operation(summary = "Receive a bank transaction",
            description = "Creates a new transaction on each call. The server generates id and received_at. "
                    + "Rates use the creation date in UTC, capped to the last closed weekday at receipt. The last available cached close is used if the target date is missing. No usable rate leaves conversion_status=PENDING; "
                    + "background processing completes the USD amount. No external HTTP call is made during receipt. "
                    + "Converted amounts reserve the creation month in Europe/Moscow (UTC+03:00), unless an earlier unconverted operation blocks reservation. Amounts are fixed once calculated. limit_exceeded remains false while checking is pending and is finalized on completion or timeout. "
                    + "Late operations before calculated reserves are rejected with 409. "
                    + "The operation is not idempotent; repeated requests create separate records.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Transaction persisted; bank completion can still be pending",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = TransactionResponse.class))),
                @ApiResponse(responseCode = "400", description = "Invalid JSON, field format or transaction data",
                        content = @Content(mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(responseCode = "409", description = "Operation precedes a calculated reserve",
                        content = @Content(mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(responseCode = "503", description = "Transaction could not be saved",
                        content = @Content(mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(responseCode = "500", description = "Unexpected server error",
                        content = @Content(mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse receive(@Valid @RequestBody TransactionRequest request) {
        return service.receive(request);
    }
}

