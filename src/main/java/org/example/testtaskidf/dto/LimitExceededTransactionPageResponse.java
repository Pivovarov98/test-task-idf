package org.example.testtaskidf.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Newest-first page of successful exceeded transactions, without category or date filters.
 *
 * Count and content share one database snapshot. Across separate requests, new data can shift pages.
 * A page beyond the result set has empty content but retains totalElements and totalPages.
 *
 * @param content original transaction fields with the applied historical limit
 * @param page zero-based page number
 * @param size requested size between 1 and 100
 * @param totalElements number of successful transactions with a completed exceeded-limit check
 * @param totalPages available pages; zero when no eligible transactions exist
 */
@Schema(description = "Page of successful exceeded transactions; PROCESSING, FAILED and TIMED_OUT are excluded")
public record LimitExceededTransactionPageResponse(
        @Schema(description = "Entries for this page; empty beyond the result set",
                requiredMode = Schema.RequiredMode.REQUIRED) List<LimitExceededTransactionResponse> content,
        @Schema(description = "Requested zero-based page", minimum = "0", example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(description = "Requested page size", minimum = "1", maximum = "100", example = "20",
                requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(description = "Total entries across all pages", minimum = "0", example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("total_elements") long totalElements,
        @Schema(description = "Total pages; zero when total_elements is zero", minimum = "0", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("total_pages") long totalPages) {
    public LimitExceededTransactionPageResponse {
        content = List.copyOf(content);
    }
}
