package org.example.testtaskidf.model;

/** Persisted operation states and simulator-only ERROR reply. */
public enum BankOperationStatus {
    /** Awaiting final bank status; converted USD amounts can already be reserved. */
    PROCESSING,
    /** Confirmed bank success; the original reserve counts as expense. */
    SUCCEEDED,
    /** Confirmed bank failure; any held reserve is released. */
    FAILED,
    /** Local continuous-error timeout; reserve released, but late bank completion is accepted. */
    TIMED_OUT,
    /** Simulator-only unavailability reply; never persisted as operation_status. */
    ERROR
}
