package org.example.testtaskidf.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Newest-first page of successful exceeded transactions, without category or date filters.
 *
 * @param content original transaction fields with the applied historical limit
 * @param page zero-based page number
 * @param size requested size between 1 and 100
 * @param totalElements number of successful transactions with a completed exceeded-limit check
 * @param totalPages available pages; zero when no eligible transactions exist
 */
@Schema(description = "Page of successful exceeded transactions; PROCESSING, FAILED and TIMED_OUT are excluded")
public record LimitExceededTransactionPageResponse(List<LimitExceededTransactionResponse> content,
        @Schema(example = "0") int page, @Schema(example = "20") int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") long totalPages) {
    public LimitExceededTransactionPageResponse {
        content = List.copyOf(content);
    }
}
