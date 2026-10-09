package org.example.testtaskidf.service;

import java.time.Clock;

import org.example.testtaskidf.repository.OperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Durable operations are recovered from PostgreSQL after every restart. */
@Service
public class OperationSchedulerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationSchedulerService.class);
    private final OperationRepository repository;
    private final OperationLifecycleService lifecycle;
    private final Clock clock;
    private final boolean enabled;

    public OperationSchedulerService(OperationRepository repository, OperationLifecycleService lifecycle, Clock clock,
            @Value("${bank.worker-enabled}") boolean enabled) {
        this.repository = repository;
        this.lifecycle = lifecycle;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${bank.worker-delay-ms}", initialDelay = 10000)
    public void process() {
        if (!enabled) {
            return;
        }
        try {
            for (var id : repository.needsReservation()) {
                lifecycle.reconcile(id);
            }
            for (var id : repository.due(clock.instant())) {
                lifecycle.poll(id);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Operation worker deferred: {}", failure.getClass().getSimpleName());
        }
    }
}
