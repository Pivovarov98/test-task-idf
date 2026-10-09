package org.example.testtaskidf.controller.client;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.example.testtaskidf.dto.LimitExceededTransactionPageResponse;
import org.example.testtaskidf.service.LimitExceededTransactionService;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Client API for reading successful exceeded transactions with their original historical limits. */
@RestController
@RequestMapping("/api/v1/client/accounts/{account}/transactions/limit-exceeded")
@Tag(name = "Exceeded transactions", description = "Successful operations exceeding their historical USD limit")
public class LimitExceededTransactionController {
    private final LimitExceededTransactionService service;

    public LimitExceededTransactionController(LimitExceededTransactionService service) {
        this.service = service;
    }

    /**
     * Reads both categories with original transaction fields and the limit exceeded at reservation.
     * Only SUCCEEDED operations with a completed check and true flag are eligible. Timestamps are
     * presented at fixed UTC+03:00. Reading does not poll the bank or recalculate rates or flags.
     *
     * @param account existing ten-digit source account
     * @param page zero-based page number
     * @param size page size between 1 and 100, default 20
     * @return newest-first page; an account with no eligible operations gets empty content and zero totals
     */
    @GetMapping
    @Operation(summary = "Get successful transactions exceeding their limit",
            description = "Returns only SUCCEEDED operations with a COMPLETED limit check and limit_exceeded=true. "
                    + "FAILED, TIMED_OUT and pending operations are excluded even if their technical flag is true. "
                    + "Each item contains the six original input fields and limit_sum, limit_datetime, "
                    + "limit_currency_shortname. The original applied limit is returned, never the current limit. "
                    + "Default 1000 USD limits use the creation month's start at UTC+03:00. "
                    + "Timestamps are presented at UTC+03:00; currency conversion is not repeated. "
                    + "Late success can add a transaction to the list without rewriting previous flags. "
                    + "Sort: datetime DESC, operation_sequence DESC. No category or date filters. "
                    + "Pages start at zero; default size is 20, maximum 100. Beyond the result set, content is empty. "
                    + "Totals and content share one database snapshot. Separate page requests may shift after writes. "
                    + "GET never registers accounts; all errors use RFC 9457 application/problem+json.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Page of exceedances; empty content when none exist",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = LimitExceededTransactionPageResponse.class),
                                examples = {
                                    @ExampleObject(name = "January example", value = """
                                            {"content":[
                                              {"account_from":"0000000123","account_to":"9999999999",
                                               "currency_shortname":"USD","sum":100.00,"expense_category":"product",
                                               "datetime":"2022-01-13T12:00:00+03:00","limit_sum":2000.00,
                                               "limit_datetime":"2022-01-10T12:00:00+03:00",
                                               "limit_currency_shortname":"USD"},
                                              {"account_from":"0000000123","account_to":"9999999999",
                                               "currency_shortname":"USD","sum":600.00,"expense_category":"product",
                                               "datetime":"2022-01-03T12:00:00+03:00","limit_sum":1000.00,
                                               "limit_datetime":"2022-01-01T00:00:00+03:00",
                                               "limit_currency_shortname":"USD"}],
                                             "page":0,"size":20,"total_elements":2,"total_pages":1}
                                            """),
                                    @ExampleObject(name = "No exceedances", value = """
                                            {"content":[],"page":0,"size":20,"total_elements":0,"total_pages":0}
                                            """)
                                })),
                @ApiResponse(responseCode = "400", description = "Invalid account or pagination parameters",
                        content = @Content(mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(responseCode = "404", description = "Account not found; no account is created",
                        content = @Content(mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(responseCode = "503", description = "Database unavailable",
                        content = @Content(mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(responseCode = "500", description = "Unexpected server error",
                        content = @Content(mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class)))
            })
    public LimitExceededTransactionPageResponse getAll(
            @Parameter(description = "Existing source account", example = "0000000123",
                    schema = @Schema(pattern = "[0-9]{10}", minLength = 10, maxLength = 10))
            @PathVariable @Pattern(regexp = "[0-9]{10}") String account,
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(minimum = "0", maximum = "2147483647", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Number of entries per page, from 1 to 100",
                    schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.getAll(account, page, size);
    }
}
