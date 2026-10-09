package org.example.testtaskidf.service;

import org.example.testtaskidf.dto.LimitExceededTransactionPageResponse;
import org.example.testtaskidf.repository.AccountRepository;
import org.example.testtaskidf.repository.LimitExceededTransactionRepository;
import org.example.testtaskidf.service.mapping.LimitExceededTransactionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Snapshot-consistent read of successful exceedances; does not poll the bank or fetch exchange rates. */
@Service
public class LimitExceededTransactionService {
    private final LimitExceededTransactionRepository repository;
    private final AccountRepository accounts;
    private final LimitExceededTransactionMapper mapper;

    public LimitExceededTransactionService(LimitExceededTransactionRepository repository,
            AccountRepository accounts, LimitExceededTransactionMapper mapper) {
        this.repository = repository;
        this.accounts = accounts;
        this.mapper = mapper;
    }

    /**
     * Returns successful exceeded operations with the fixed historical limit, including late bank success.
     *
     * The account check, count and page are read within one REPEATABLE_READ snapshot. No writes
     * or external API requests occur. A page beyond the result set retains totals with empty content.
     *
     * @param account existing ten-digit source account
     * @param page zero-based page number
     * @param size page size between 1 and 100
     * @return page of the exact task 6 response fields
     * @throws ResponseStatusException for invalid arguments (400) or an unknown account (404)
     * @throws org.springframework.dao.DataAccessException if a database read fails
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LimitExceededTransactionPageResponse getAll(String account, int page, int size) {
        if (account == null || !account.matches("[0-9]{10}") || page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account must contain 10 digits; page must be nonnegative and size must be between 1 and 100");
        }
        if (!accounts.exists(account)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        var total = repository.count(account);
        var content = repository.findPage(account, (long) page * size, size).stream()
                .map(mapper::toResponse).toList();
        return new LimitExceededTransactionPageResponse(content, page, size, total,
                total / size + (total % size == 0 ? 0 : 1));
    }
}
