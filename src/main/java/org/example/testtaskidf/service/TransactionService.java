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

@Service
@Component
public class TransactionService {
    private final TransactionRepository repository;
    private final TransactionMapper mapper;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, TransactionMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public TransactionResponse receive(TransactionRequest request) {
        var transaction = mapper.toEntity(request, UUID.randomUUID(), OffsetDateTime.now(clock));
        repository.save(transaction);
        return mapper.toResponse(transaction);
    }
}
