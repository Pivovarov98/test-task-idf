package org.example.testtaskidf.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.example.testtaskidf.dto.ExpenseLimitRequest;
import org.example.testtaskidf.dto.ExpenseLimitResponse;
import org.example.testtaskidf.exception.LimitConflictException;
import org.example.testtaskidf.repository.ExpenseLimitRepository;
import org.example.testtaskidf.service.mapping.ExpenseLimitMapper;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks and appends a limit under a transaction-scoped, nonblocking PostgreSQL lock. */
@Service
public class ExpenseLimitCreationService {
    private final ExpenseLimitRepository repository;
    private final ExpenseLimitService limits;
    private final ExpenseLimitMapper mapper;
    private final Clock clock;

    public ExpenseLimitCreationService(ExpenseLimitRepository repository, ExpenseLimitService limits,
            ExpenseLimitMapper mapper, Clock clock) {
        this.repository = repository;
        this.limits = limits;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(timeout = 10)
    public ExpenseLimitResponse create(ExpenseLimitRequest request) {
        var category = ExpenseCategoryUtils.fromCode(request.expenseCategory());
        if (!repository.tryLock(request.account(), category)) {
            throw new LimitConflictException("limit_request_in_progress", "A limit request is already in progress");
        }
        if (limits.getCurrentLimit(request.account(), category).amount().compareTo(request.amount()) == 0) {
            throw new LimitConflictException("limit_amount_unchanged", "The requested amount is already active");
        }
        var limit = mapper.toEntity(request, UUID.randomUUID(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        repository.save(limit);
        return mapper.toResponse(limit);
    }
}
