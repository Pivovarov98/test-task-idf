package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Result state is persisted even when external data is unavailable. */
public record ConversionResult(BigDecimal amountUsd, String status, UUID exchangeRateId) {
}
