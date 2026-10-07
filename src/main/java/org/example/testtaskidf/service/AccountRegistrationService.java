package org.example.testtaskidf.service;

import java.time.Clock;

import org.example.testtaskidf.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registration survives rejection of a subsequent limit request. */
@Service
public class AccountRegistrationService {
    private final AccountRepository repository;
    private final Clock clock;

    public AccountRegistrationService(AccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void register(String account) {
        repository.registerIfAbsent(account, clock.instant());
    }
}
