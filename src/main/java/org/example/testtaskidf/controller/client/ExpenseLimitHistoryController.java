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
import org.example.testtaskidf.dto.ExpenseLimitPageResponse;
import org.example.testtaskidf.service.ExpenseLimitHistoryService;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Client API for reading the full limit history of an existing source account. */
@RestController
@RequestMapping("/api/v1/client/accounts/{account}/limits")
@Tag(name = "Limits", description = "Establish limits and read account history in USD")
public class ExpenseLimitHistoryController {
    private final ExpenseLimitHistoryService service;

    public ExpenseLimitHistoryController(ExpenseLimitHistoryService service) {
        this.service = service;
    }

    /**
     * Reads both categories, including user-established zero amounts and implicit defaults.
     * Defaults have no persisted UUID; their timestamps are month starts at fixed UTC+03:00.
     * This read neither establishes limits nor registers unknown accounts.
     *
     * @param account existing ten-digit source account
     * @param page zero-based page number
     * @param size page size between 1 and 100, default 20
     * @return newest-first history page; beyond the last page content is empty and totals are retained
     */
    @GetMapping
    @Operation(summary = "Get all account limits",
            description = "Returns user limits and default 1000 USD limits for product and service, including zeros. "
                    + "Default timestamps are Moscow month starts; their id is null. Defaults are included for "
                    + "the registration month and operation months where no user limit was yet active. "
                    + "No rows are created on reading, no unused intervening months are generated, and user limits "
                    + "carry into subsequent months. Sort: established_at DESC, revision DESC, expense_category ASC. "
                    + "Pages start at zero; default size is 20, maximum 100. A page beyond the history is empty. "
                    + "Totals and content share one database snapshot. Separate page requests may shift after writes. "
                    + "GET never registers accounts; all errors use RFC 9457 application/problem+json.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Account history page",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ExpenseLimitPageResponse.class),
                                examples = @ExampleObject(name = "New account defaults", value = """
                                        {"content":[
                                          {"id":null,"account":"0000000123","expense_category":"product",
                                           "amount":1000.00,"currency":"USD",
                                           "established_at":"2022-01-01T00:00:00+03:00"},
                                          {"id":null,"account":"0000000123","expense_category":"service",
                                           "amount":1000.00,"currency":"USD",
                                           "established_at":"2022-01-01T00:00:00+03:00"}],
                                         "page":0,"size":20,"total_elements":2,"total_pages":1}
                                        """))),
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
    public ExpenseLimitPageResponse getAll(
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
