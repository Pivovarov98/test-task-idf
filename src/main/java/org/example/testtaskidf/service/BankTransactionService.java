package org.example.testtaskidf.service;

import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.dto.TransactionResponse;
import org.example.testtaskidf.service.mapping.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists reception, cache-only conversion and reservation atomically under the account/category lock.
 * Account registration commits separately. Receipt never performs a synchronous external rate request.
 */
@Service
public class BankTransactionService {
    private final TransactionService transactions;
    private final CurrencyConversionService conversions;
    private final TransactionMapper mapper;
    private final OperationLifecycleService lifecycle;

    public BankTransactionService(TransactionService transactions, CurrencyConversionService conversions,
            TransactionMapper mapper, OperationLifecycleService lifecycle) {
        this.transactions = transactions;
        this.conversions = conversions;
        this.mapper = mapper;
        this.lifecycle = lifecycle;
    }

    /**
     * Receives a validated bank operation and reserves its fixed USD amount when conversion and order permit.
     * Each call creates a distinct record; the acknowledgment leaves bank completion in PROCESSING.
     *
     * @param request validated transaction data with creation time and offset
     * @return saved transaction with conversion and lifecycle state
     */
    @Transactional
    public TransactionResponse receive(TransactionRequest request) {
        lifecycle.checkCreation(request);
        var response = transactions.receive(request);
        var conversion = conversions.convert(response.id(), response.currencyShortname(), response.sum(),
                response.datetime().toInstant());
        return mapper.withOperation(mapper.withConversion(response, conversion), lifecycle.created(response.id()));
    }
}
