package org.example.testtaskidf.service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.example.testtaskidf.dto.BankNotificationRequest;
import org.example.testtaskidf.dto.OperationState;
import org.example.testtaskidf.dto.TransactionRequest;
import org.example.testtaskidf.model.BankOperationStatus;
import org.example.testtaskidf.model.ReservableOperation;
import org.example.testtaskidf.repository.OperationRepository;
import org.example.testtaskidf.util.BusinessTimeUtils;
import org.example.testtaskidf.util.ExpenseCategoryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reserves fixed USD amounts in creation order within the account/category/month in Europe/Moscow.
 * The historical limit is resolved at creation; equality is not an exceedance. New limits do not
 * reset occupied amounts or rewrite flags. Reservation and finalization share the PostgreSQL
 * transaction-scoped account/category lock with limit creation. Earlier unconverted operations
 * block later reservations in the same month.
 *
 * <p>Success retains the reserve as expense. Failure and timeout release it once. Late success
 * after timeout restores the original amount in the original month without rewriting other flags.
 */
@Service
public class OperationLifecycleService {
    private final OperationRepository repository;
    private final ExpenseLimitService limits;
    private final Clock clock;
    private final Duration timeout;
    private final Duration pollInterval;

    public OperationLifecycleService(OperationRepository repository, ExpenseLimitService limits, Clock clock,
            @Value("${bank.unavailability-timeout}") Duration timeout,
            @Value("${bank.poll-interval}") Duration pollInterval) {
        this.repository = repository;
        this.limits = limits;
        this.clock = clock;
        this.timeout = timeout;
        this.pollInterval = pollInterval;
    }

    /** Rejects nonpositive polling intervals and error timeouts at application startup. */
    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        if (timeout.isNegative() || timeout.isZero() || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("Bank timeout and poll interval must be positive");
        }
    }

    /**
     * Reads the persisted state without polling the bank or recomputing a reservation.
     *
     * @param id saved transaction identifier
     * @return lifecycle state
     * @throws ResponseStatusException if the transaction does not exist (404)
     */
    @Transactional(readOnly = true)
    public OperationState state(UUID id) {
        if (repository.find(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        return repository.state(id);
    }

    /**
     * Locks the account/category and rejects creation before a calculated reserve in the same month.
     * The enclosing reception transaction retains the lock through insertion and reservation.
     *
     * @param request validated incoming transaction
     * @throws ResponseStatusException for a backdated operation that changes reservation order (409)
     */
    @Transactional
    public void checkCreation(TransactionRequest request) {
        var category = ExpenseCategoryUtils.fromCode(request.expenseCategory());
        repository.lock(request.accountFrom(), category);
        var time = request.datetime().toInstant();
        if (repository.isLate(request.accountFrom(), request.expenseCategory(), time,
                BusinessTimeUtils.monthContaining(time))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operation precedes an already calculated reserve");
        }
    }

    /**
     * Schedules the first poll and reserves converted operations in creation order within the month.
     *
     * @param id newly persisted transaction identifier
     * @return state after any currently possible reservations
     * @throws ResponseStatusException if the transaction does not exist (404)
     */
    @Transactional
    public OperationState created(UUID id) {
        var operation = locked(id);
        repository.poll(id, clock.instant().plus(pollInterval), null);
        reserveMonth(operation);
        return repository.state(id);
    }

    /**
     * Retries month reservations after conversion becomes available; existing reserves remain unchanged.
     *
     * @param id transaction identifying the account/category/month to reconcile
     * @throws ResponseStatusException if the transaction does not exist (404)
     */
    @Transactional
    public void reconcile(UUID id) {
        reserveMonth(locked(id));
    }

    private void reserveMonth(ReservableOperation operation) {
        var month = BusinessTimeUtils.monthContaining(operation.createdAt());
        for (var id : repository.unreserved(operation, month)) {
            var pending = repository.find(id).orElseThrow();
            if (!"COMPLETED".equals(pending.conversionStatus())) {
                break;
            }
            var applied = limits.getApplicableLimit(pending.account(), pending.category(), pending.createdAt());
            var occupied = repository.occupied(pending, month);
            repository.reserve(id, pending.amountUsd(), applied.amount(),
                    applied.configuredLimit().map(limit -> limit.id()).orElse(null),
                    occupied.add(pending.amountUsd()).compareTo(applied.amount()) > 0,
                    occupied.compareTo(applied.amount()) > 0);
        }
    }

    /**
     * Applies a validated final notification. Same-status duplicates retain the original completion time.
     * PROCESSING and TIMED_OUT accept final statuses; other conflicting final statuses are rejected.
     * Success uses exceedance fixed at reservation; failure uses whether the limit was already exceeded
     * before reservation. Success without a USD amount remains pending until reconciliation.
     *
     * @param request bank final status and completion time
     * @return persisted final or pending-check state
     * @throws ResponseStatusException for an unknown operation (404), conflicting final status (409),
     *                                or completion before creation (400)
     */
    @Transactional
    public OperationState notify(BankNotificationRequest request) {
        var operation = locked(request.transactionId());
        var status = BankOperationStatus.valueOf(request.status());
        if (operation.status() == status) {
            return repository.state(operation.id());
        }
        if (operation.status() != BankOperationStatus.PROCESSING
                && operation.status() != BankOperationStatus.TIMED_OUT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Final operation status cannot be replaced");
        }
        if (request.completedAt().toInstant().isBefore(operation.createdAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completion precedes operation creation");
        }
        repository.finish(operation, status, request.completedAt().toInstant());
        reserveMonth(repository.find(operation.id()).orElseThrow());
        return repository.state(operation.id());
    }

    /**
     * Stores a local simulator reply for later polls without immediately changing lifecycle status.
     *
     * @param id saved transaction identifier
     * @param status validated PROCESSING, SUCCEEDED, FAILED or ERROR reply
     * @return current lifecycle state
     * @throws ResponseStatusException if the transaction does not exist (404)
     */
    @Transactional
    public OperationState configureStub(UUID id, String status) {
        locked(id);
        repository.stub(id, BankOperationStatus.valueOf(status));
        return repository.state(id);
    }

    /**
     * Polls the local simulator for a PROCESSING operation under the account/category lock.
     * The first ERROR starts the unavailability period; further errors preserve its start. PROCESSING
     * clears it. At the configured timeout a failing poll releases the reserve and sets TIMED_OUT.
     * Final bank replies use Clock as completion time. Finalized operations are ignored.
     *
     * @param id saved transaction identifier
     * @throws ResponseStatusException if the transaction does not exist (404)
     */
    @Transactional
    public void poll(UUID id) {
        var operation = locked(id);
        if (operation.status() != BankOperationStatus.PROCESSING) {
            return;
        }
        var now = clock.instant();
        var reply = repository.stub(id);
        switch (reply) {
            case ERROR -> {
                var since = operation.unavailableSince() == null ? now : operation.unavailableSince();
                if (!now.isBefore(since.plus(timeout))) {
                    repository.finish(operation, BankOperationStatus.TIMED_OUT, now);
                    reserveMonth(operation);
                } else {
                    repository.poll(id, now.plus(pollInterval), since);
                }
            }
            case PROCESSING -> repository.poll(id, now.plus(pollInterval), null);
            case SUCCEEDED, FAILED -> {
                repository.finish(operation, reply, now);
                reserveMonth(repository.find(id).orElseThrow());
            }
            case TIMED_OUT -> throw new IllegalStateException("Invalid bank stub status");
        }
    }

    private ReservableOperation locked(UUID id) {
        var operation = repository.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        repository.lock(operation);
        return repository.find(id).orElseThrow();
    }
}
