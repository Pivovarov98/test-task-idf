package org.example.testtaskidf.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.dto.TransactionResponse;
import org.example.testtaskidf.repository.TransactionRepository;
import org.example.testtaskidf.service.mapping.TransactionMapper;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assigns server metadata and saves each incoming transaction atomically. */
@Service
@Component
public class TransactionService {
    private final TransactionRepository repository;
    private final TransactionMapper mapper;
    private final Clock clock;
    private final AccountRegistrationService accounts;

    public TransactionService(TransactionRepository repository, TransactionMapper mapper, Clock clock,
            AccountRegistrationService accounts) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
        this.accounts = accounts;
    }

    /**
     * Maps and saves a new transaction, then creates its response within the same database transaction.
     * Runtime failures, including response mapping failures, roll back the insert.
     * Each invocation generates a new UUID; repeated requests are not deduplicated.
     *
     * @param request incoming transaction
     * @return response with a new UUID and receipt time from the injected clock
     * @throws IllegalArgumentException if the transaction violates domain constraints
     * @throws org.springframework.dao.DataAccessException if persistence fails
     */
    @Transactional
    public TransactionResponse receive(TransactionRequest request) {
        var transaction = mapper.toEntity(request, UUID.randomUUID(), OffsetDateTime.now(clock));
        accounts.register(transaction.accountFrom());
        repository.save(transaction);
        return mapper.toResponse(transaction);
    }
}
