package org.example.testtaskidf.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable response envelope for account limit history, ordered newest first.
 *
 * Count and content share one database snapshot. Across separate requests, new data can shift pages.
 * A page beyond the result set has empty content but retains totalElements and totalPages.
 *
 * @param content history entries for the requested page
 * @param page zero-based page number
 * @param size requested page size, between 1 and 100
 * @param totalElements number of user and implicit default limits
 * @param totalPages number of available pages; zero for an empty history
 */
@Schema(description = "Paged history of user and default USD limits for both categories")
public record ExpenseLimitPageResponse(
        @Schema(description = "Entries for this page; empty beyond the result set",
                requiredMode = Schema.RequiredMode.REQUIRED) List<ExpenseLimitResponse> content,
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
    public ExpenseLimitPageResponse {
        content = List.copyOf(content);
    }
}
