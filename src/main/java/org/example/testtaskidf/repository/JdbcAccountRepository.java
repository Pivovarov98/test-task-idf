package org.example.testtaskidf.repository;

import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Concurrent registration relies on the account primary key. */
@Repository
public class JdbcAccountRepository implements AccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void registerIfAbsent(String account, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO accounts (account_number, created_at) VALUES (?, ?)
                ON CONFLICT (account_number) DO NOTHING
                """, account, createdAt.atOffset(ZoneOffset.UTC));
    }

    @Override
    public boolean exists(String account) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM accounts WHERE account_number = ?)", Boolean.class, account));
    }
}
