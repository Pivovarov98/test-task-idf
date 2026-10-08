package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Exact provider quote expressed as source currency units per USD. */
public record StoredExchangeRate(UUID id, BigDecimal unitsPerUsd) {
}
