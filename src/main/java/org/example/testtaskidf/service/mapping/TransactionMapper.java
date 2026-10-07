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

/** MapStruct mapping between API records and the validated transaction domain model. */
@Mapper(componentModel = "spring", uses = ExpenseCategoryUtils.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TransactionMapper {
    /**
     * Creates a domain transaction with metadata assigned by the service.
     *
     * @param request source request, or null
     * @param id generated identifier
     * @param receivedAt server receipt time
     * @return validated transaction, or null when request is null
     * @throws IllegalArgumentException for invalid transaction fields or category codes
     */
    @Mapping(target = "id", expression = "java(id)")
    @Mapping(target = "receivedAt", expression = "java(receivedAt)")
    Transaction toEntity(TransactionRequest request, @Context UUID id, @Context OffsetDateTime receivedAt);

    /**
     * Maps persisted transaction data back to API field values.
     *
     * @param transaction source transaction, or null
     * @return response, or null when transaction is null
     */
    TransactionResponse toResponse(Transaction transaction);
}
