package org.example.testtaskidf.repository;

import org.example.testtaskidf.model.Transaction;

/** Persistence contract for incoming transactions. */
public interface TransactionRepository {
    /**
     * Inserts a transaction using metadata already assigned by the service.
     *
     * @param transaction validated transaction to insert
     * @throws org.springframework.dao.DataAccessException if the insert fails
     */
    void save(Transaction transaction);
}
