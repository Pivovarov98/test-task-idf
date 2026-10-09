package org.example.testtaskidf.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.example.testtaskidf.repository.OperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationSchedulerServiceTests {
    private final OperationRepository repository = mock(OperationRepository.class);
    private final OperationLifecycleService lifecycle = mock(OperationLifecycleService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void disabledWorkerDoesNotReadOrProcessOperations() {
        new OperationSchedulerService(repository, lifecycle, clock, false).process();
        verifyNoInteractions(repository, lifecycle);
    }

    @Test
    void reconcilesConvertedOperationsAndPollsDueOperationsUsingClock() {
        var converted = UUID.randomUUID();
        var due = UUID.randomUUID();
        when(repository.needsReservation()).thenReturn(List.of(converted));
        when(repository.due(clock.instant())).thenReturn(List.of(due));
        new OperationSchedulerService(repository, lifecycle, clock, true).process();
        verify(lifecycle).reconcile(converted);
        verify(lifecycle).poll(due);
    }

    @Test
    void failureLeavesDurableOperationForNextWorkerRun() {
        var due = UUID.randomUUID();
        when(repository.due(clock.instant())).thenReturn(List.of(due));
        doThrow(new IllegalStateException("temporary database outage")).doNothing().when(lifecycle).poll(due);
        var worker = new OperationSchedulerService(repository, lifecycle, clock, true);
        assertThatCode(worker::process).doesNotThrowAnyException();
        worker.process();
        verify(lifecycle, org.mockito.Mockito.times(2)).poll(due);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveTimeoutAndPollingInterval(long seconds) {
        var limits = mock(ExpenseLimitService.class);
        var invalid = Duration.ofSeconds(seconds);
        var valid = Duration.ofHours(3);
        assertThatThrownBy(() -> new OperationLifecycleService(repository, limits, clock, invalid, valid)
                .validateConfiguration()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperationLifecycleService(repository, limits, clock, valid, invalid)
                .validateConfiguration()).isInstanceOf(IllegalArgumentException.class);
    }
}
