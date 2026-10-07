package org.example.testtaskidf.service;

import org.example.testtaskidf.dto.ExpenseLimitRequest;
import org.example.testtaskidf.dto.ExpenseLimitResponse;
import org.springframework.stereotype.Service;

/** Registers the account before starting the limit transaction. */
@Service
public class ClientLimitService {
    private final AccountRegistrationService accounts;
    private final ExpenseLimitCreationService creation;

    public ClientLimitService(AccountRegistrationService accounts, ExpenseLimitCreationService creation) {
        this.accounts = accounts;
        this.creation = creation;
    }

    public ExpenseLimitResponse create(ExpenseLimitRequest request) {
        accounts.register(request.account());
        return creation.create(request);
    }
}
