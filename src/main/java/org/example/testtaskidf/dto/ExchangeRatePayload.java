package org.example.testtaskidf.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** OER returns units of each currency per one USD; additional provider fields are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeRatePayload(String base, Long timestamp, Map<String, BigDecimal> rates) {
    public ExchangeRatePayload {
        rates = rates == null ? Map.of() : Map.copyOf(rates);
    }
}
