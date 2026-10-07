package org.example.testtaskidf.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.example.testtaskidf.model.ExpenseCategory;
import org.example.testtaskidf.model.ResolvedExpenseLimit;
import org.example.testtaskidf.repository.ExpenseLimitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves monthly limits independently for accounts and categories, using 1000 USD when none is set. */
@Service
@Transactional(readOnly = true)
public class ExpenseLimitService {
    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("1000.00");
    private final ExpenseLimitRepository repository;
    private final Clock clock;

    public ExpenseLimitService(ExpenseLimitRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Resolves the currently active limit using the injectable clock. */
    public ResolvedExpenseLimit getCurrentLimit(String account, ExpenseCategory category) {
        return getApplicableLimit(account, category, clock.instant());
    }

    /** Resolves the last limit established at or before the supplied instant, including previous months. */
    public ResolvedExpenseLimit getApplicableLimit(String account, ExpenseCategory category, Instant asOf) {
        Objects.requireNonNull(account);
        Objects.requireNonNull(category);
        Objects.requireNonNull(asOf);
        if (!account.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("Account must contain exactly 10 digits");
        }
        var configured = repository.findLatest(account, category, asOf);
        return new ResolvedExpenseLimit(configured.map(limit -> limit.amount()).orElse(DEFAULT_AMOUNT),
                "USD", configured);
    }
}
