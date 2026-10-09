package org.example.testtaskidf.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable response envelope for account limit history, ordered newest first.
 *
 * @param content history entries for the requested page
 * @param page zero-based page number
 * @param size requested page size, between 1 and 100
 * @param totalElements number of user and implicit default limits
 * @param totalPages number of available pages; zero for an empty history
 */
@Schema(description = "Paged history of user and default USD limits for both categories")
public record ExpenseLimitPageResponse(List<ExpenseLimitResponse> content,
        @Schema(example = "0") int page, @Schema(example = "20") int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") long totalPages) {
    public ExpenseLimitPageResponse {
        content = List.copyOf(content);
    }
}
