package org.example.testtaskidf.service.mapping;

import java.time.Instant;
import java.util.UUID;

import org.example.testtaskidf.dto.ExpenseLimitRequest;
import org.example.testtaskidf.dto.ExpenseLimitResponse;
import org.example.testtaskidf.model.ExpenseLimit;
import org.example.testtaskidf.model.ExpenseLimitHistoryEntry;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.example.testtaskidf.util.BusinessTimeUtils;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Maps immutable API records to the validated domain model. */
@Mapper(componentModel = "spring", uses = {ExpenseCategoryUtils.class, BusinessTimeUtils.class},
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExpenseLimitMapper {
    @Mapping(target = "id", expression = "java(id)")
    @Mapping(target = "currency", constant = "USD")
    @Mapping(target = "establishedAt", expression = "java(establishedAt)")
    ExpenseLimit toEntity(ExpenseLimitRequest request, @Context UUID id, @Context Instant establishedAt);

    ExpenseLimitResponse toResponse(ExpenseLimit limit);

    /** Maps user or implicit default history, returning timestamps in Moscow business time. */
    ExpenseLimitResponse toResponse(ExpenseLimitHistoryEntry limit);
}
