package org.example.testtaskidf.service.mapping;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.model.ExpenseCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTests {
    private final TransactionMapper mapper = Mappers.getMapper(TransactionMapper.class);

    @ParameterizedTest
    @ValueSource(strings = {"product", "service"})
    void mapsEveryFieldInBothDirections(String category) {
        var request = new TransactionRequest("0000000123", "9999999999", "KZT", new BigDecimal("10000.45"),
                category, OffsetDateTime.parse("2022-01-30T00:00:00+06:00"));
        var id = UUID.randomUUID();
        var receivedAt = OffsetDateTime.parse("2026-10-07T12:00:00Z");

        var transaction = mapper.toEntity(request, id, receivedAt);

        assertThat(transaction.id()).isEqualTo(id);
        assertThat(transaction.receivedAt()).isEqualTo(receivedAt);
        assertThat(transaction).usingRecursiveComparison().ignoringFields("id", "receivedAt", "expenseCategory")
                .isEqualTo(request);
        assertThat(transaction.expenseCategory()).isInstanceOf(category.equals("product")
                ? ExpenseCategory.Product.class : ExpenseCategory.ServiceExpense.class);
        var response = mapper.toResponse(transaction);
        assertThat(response).usingRecursiveComparison().ignoringFields("id", "receivedAt").isEqualTo(request);
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.receivedAt()).isEqualTo(receivedAt);
    }
}
