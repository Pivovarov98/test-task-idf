package org.example.testtaskidf.repository;

import java.time.Instant;

/** Registry of source accounts managed by this service. */
public interface AccountRepository {
    void registerIfAbsent(String account, Instant createdAt);

    /** Checks registration without creating an account. */
    boolean exists(String account);
}
