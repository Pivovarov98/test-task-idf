package org.example.testtaskidf.service.mapping;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.dto.TransactionResponse;
import org.example.testtaskidf.model.Transaction;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Context;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = ExpenseCategoryUtils.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TransactionMapper {
    @Mapping(target = "id", expression = "java(id)")
    @Mapping(target = "receivedAt", expression = "java(receivedAt)")
    Transaction toEntity(TransactionRequest request, @Context UUID id, @Context OffsetDateTime receivedAt);

    TransactionResponse toResponse(Transaction transaction);
}
