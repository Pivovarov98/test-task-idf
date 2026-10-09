package org.example.testtaskidf.controller.client;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.testtaskidf.dto.ExpenseLimitRequest;
import org.example.testtaskidf.dto.ExpenseLimitResponse;
import org.example.testtaskidf.service.ClientLimitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client API for establishing a new limit without changing historical limits.
 */
@RestController
@Tag(name = "Limits", description = "Establish limits and read account history in USD")
@RequestMapping("/api/v1/client/limits")
public class ExpenseLimitController {
    private final ClientLimitService service;

    public ExpenseLimitController(ClientLimitService service) {
        this.service = service;
    }

    @Operation(summary = "Establish a new expense limit",
            description = "Registers unknown accounts. Assigns USD currency and current server time. "
                    + "Appends history; identical amounts and concurrent requests for the same category are rejected.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Limit established",
                            content = @Content(schema = @Schema(implementation = ExpenseLimitResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input or server-owned fields supplied",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "409", description = "Amount unchanged or request already in progress",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "503", description = "Database unavailable",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseLimitResponse create(@Valid @RequestBody ExpenseLimitRequest request) {
        return service.create(request);
    }
}
