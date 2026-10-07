package org.example.testtaskidf.repository;

import java.time.Instant;
import java.util.Optional;

import org.example.testtaskidf.model.ExpenseCategory;
import org.example.testtaskidf.model.ExpenseLimit;

/** Append-only persistence contract for limit history. No update operation is exposed. */
public interface ExpenseLimitRepository {
    boolean tryLock(String account, ExpenseCategory category);

    void save(ExpenseLimit limit);

    Optional<ExpenseLimit> findLatest(String account, ExpenseCategory category, Instant asOf);
}
