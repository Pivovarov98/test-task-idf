package org.example.testtaskidf.controller.bank;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.dto.TransactionResponse;
import org.example.testtaskidf.service.TransactionService;
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

    private final TransactionService service;
    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /**
     * Validates and persists an incoming transaction.
     *
     * @param request transaction supplied by the bank
     * @return saved transaction with a generated identifier and receipt time
     */
    @Operation(summary = "Receive a bank transaction",
            description = "Creates a new transaction on each call. The server generates id and received_at. "
                    + "The operation is not idempotent; repeated requests create separate records.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Transaction saved",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = TransactionResponse.class))),
                @ApiResponse(responseCode = "400", description = "Invalid JSON, field format or transaction data",
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
