package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.model.Transaction;
import org.example.testtaskidf.repository.TransactionRepository;
import org.example.testtaskidf.service.mapping.TransactionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTests {
    @Mock
    private TransactionRepository repository;
    @Mock
    private AccountRegistrationService accounts;
    private final Instant now = Instant.parse("2026-10-07T12:00:00Z");

    @Test
    void savesTransactionAndReturnsAllFieldsWithGeneratedIdAndClockTime() {
        var request = request("product");
        var response = service().receive(request);
        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(saved.capture());
        assertThat(response.id()).isEqualTo(saved.getValue().id());
        assertThat(response.id().version()).isEqualTo(4);
        assertThat(response).usingRecursiveComparison()
                .ignoringFields("id", "receivedAt", "amountUsd", "conversionStatus", "exchangeRateId", "operation")
                .isEqualTo(request);
        assertThat(response.receivedAt().toInstant()).isEqualTo(now);
        assertThat(saved.getValue().receivedAt().toInstant()).isEqualTo(now);
        assertThat(saved.getValue().sum()).isEqualTo(request.sum());
    }

    @Test
    void propagatesPersistenceFailure() {
        var failure = new IllegalStateException("Database unavailable");
        doThrow(failure).when(repository).save(any(Transaction.class));
        assertThatThrownBy(() -> service().receive(request("service"))).isSameAs(failure);
    }

    @Test
    void doesNotSaveInvalidCategory() {
        assertThatThrownBy(() -> service().receive(request("other")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    private TransactionService service() {
        return new TransactionService(repository, Mappers.getMapper(TransactionMapper.class),
                Clock.fixed(now, ZoneOffset.UTC), accounts);
    }

    private TransactionRequest request(String category) {
        return new TransactionRequest("0000000123", "9999999999", "KZT", new BigDecimal("10.45"),
                category, OffsetDateTime.parse("2022-01-30T00:00:00+06:00"));
    }
}
