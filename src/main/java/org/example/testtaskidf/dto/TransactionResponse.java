package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransactionResponse(UUID id,
                                  @JsonProperty("account_from") String accountFrom,
                                  @JsonProperty("account_to") String accountTo,
                                  @JsonProperty("currency_shortname") String currencyShortname,
                                  BigDecimal sum,
                                  @JsonProperty("expense_category") String expenseCategory,
                                  OffsetDateTime datetime,
                                  @JsonProperty("received_at") OffsetDateTime receivedAt) {
}
