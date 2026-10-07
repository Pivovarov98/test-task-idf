package org.example.testtaskidf.repository;

import org.example.testtaskidf.model.Transaction;

public interface TransactionRepository {
    void save(Transaction transaction);
}
