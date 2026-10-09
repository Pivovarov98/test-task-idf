package org.example.testtaskidf.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BankStubRequest(@NotNull @Pattern(regexp = "PROCESSING|SUCCEEDED|FAILED|ERROR") String status) {
}
