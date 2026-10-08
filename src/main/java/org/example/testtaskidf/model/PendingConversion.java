package org.example.testtaskidf.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Durable conversion work loaded from the original transaction. */
public record PendingConversion(UUID id, String currency, BigDecimal amount, Instant occurredAt) {
}
