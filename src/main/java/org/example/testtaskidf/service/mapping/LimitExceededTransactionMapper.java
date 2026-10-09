package org.example.testtaskidf.service.mapping;

import org.example.testtaskidf.dto.LimitExceededTransactionResponse;
import org.example.testtaskidf.model.LimitExceededTransaction;
import org.example.testtaskidf.util.BusinessTimeUtils;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Maps task 6 projections to the exact nine-field contract, with Moscow timestamp presentation. */
@Mapper(componentModel = "spring", uses = {ExpenseCategoryUtils.class, BusinessTimeUtils.class},
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface LimitExceededTransactionMapper {
    /**
     * Maps saved values without recalculation or changing historical limit association.
     *
     * @param transaction persisted query projection
     * @return original fields plus historical limit data
     */
    LimitExceededTransactionResponse toResponse(LimitExceededTransaction transaction);
}
