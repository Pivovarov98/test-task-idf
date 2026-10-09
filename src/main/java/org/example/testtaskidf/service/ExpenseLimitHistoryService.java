package org.example.testtaskidf.service;

import org.example.testtaskidf.dto.ExpenseLimitPageResponse;
import org.example.testtaskidf.repository.ExpenseLimitHistoryRepository;
import org.example.testtaskidf.service.mapping.ExpenseLimitMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only account history; repeatable read keeps totals and page content in one database snapshot. */
@Service
public class ExpenseLimitHistoryService {
    private final ExpenseLimitHistoryRepository repository;
    private final ExpenseLimitMapper mapper;

    public ExpenseLimitHistoryService(ExpenseLimitHistoryRepository repository, ExpenseLimitMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Returns all categories and amounts, including zero, without filters or account registration.
     *
     * @param account ten-digit source account
     * @param page zero-based page number
     * @param size page size between 1 and 100
     * @return newest-first page of user and default history
     * @throws ResponseStatusException for invalid arguments (400) or an unknown account (404)
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExpenseLimitPageResponse getAll(String account, int page, int size) {
        if (account == null || !account.matches("[0-9]{10}") || page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account must contain 10 digits; page must be nonnegative and size must be between 1 and 100");
        }
        if (!repository.accountExists(account)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        var total = repository.count(account);
        var content = repository.findPage(account, (long) page * size, size).stream()
                .map(mapper::toResponse).toList();
        return new ExpenseLimitPageResponse(content, page, size, total,
                total / size + (total % size == 0 ? 0 : 1));
    }
}
