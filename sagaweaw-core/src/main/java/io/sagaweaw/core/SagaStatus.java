package io.sagaweaw.core;

import java.time.Instant;

/**
 * All possible states of a Saga and their valid transitions.
 *
 * Sealed interface forces the compiler to verify exhaustive handling
 * in every switch expression — no invalid state can exist at runtime.
 *
 * Valid transitions:
 *   STARTED      → EXECUTING
 *   EXECUTING    → COMPLETED | COMPENSATING
 *   COMPENSATING → COMPENSATED | FAILED
 *
 * Terminal states (no further transitions): COMPLETED, COMPENSATED, FAILED
 */
public sealed interface SagaStatus
        permits SagaStatus.Started,
                SagaStatus.Executing,
                SagaStatus.Completed,
                SagaStatus.Compensating,
                SagaStatus.Compensated,
                SagaStatus.Failed {

    record Started(Instant at) implements SagaStatus {
        public Started() { this(Instant.now()); }
    }

    record Executing(String currentStep, Instant startedAt) implements SagaStatus {
        public Executing(String currentStep) { this(currentStep, Instant.now()); }
    }

    record Completed(Instant at, long durationMs) implements SagaStatus {}

    record Compensating(String failedStep, String errorMessage, Instant at)
            implements SagaStatus {
        public Compensating(String failedStep, String errorMessage) {
            this(failedStep, errorMessage, Instant.now());
        }
    }

    record Compensated(String originalFailedStep, Instant at) implements SagaStatus {
        public Compensated(String originalFailedStep) { this(originalFailedStep, Instant.now()); }
    }

    record Failed(String reason, Instant at) implements SagaStatus {
        public Failed(String reason) { this(reason, Instant.now()); }
    }

    default boolean isTerminal() {
        return this instanceof Completed
            || this instanceof Compensated
            || this instanceof Failed;
    }

    default boolean isCompensating() {
        return this instanceof Compensating;
    }

    default boolean isSuccessful() {
        return this instanceof Completed;
    }

    /**
     * Returns the string value stored in the {@code sagas.status} column.
     */
    default String persistenceName() {
        if (this instanceof Started)     return "STARTED";
        if (this instanceof Executing)   return "EXECUTING";
        if (this instanceof Completed)   return "COMPLETED";
        if (this instanceof Compensating) return "COMPENSATING";
        if (this instanceof Compensated) return "COMPENSATED";
        if (this instanceof Failed)      return "FAILED";
        throw new IllegalStateException("Unknown SagaStatus: " + getClass().getSimpleName());
    }

    /**
     * Reconstructs a SagaStatus from its persisted string.
     * Timestamps and step names are set to sentinel values — the JPA
     * AttributeConverter in sagaweaw-spring overwrites them with real data.
     */
    static SagaStatus fromPersistenceName(String name) {
        return switch (name) {
            case "STARTED"       -> new Started(Instant.EPOCH);
            case "EXECUTING"     -> new Executing("", Instant.EPOCH);
            case "COMPLETED"     -> new Completed(Instant.EPOCH, 0);
            case "COMPENSATING"  -> new Compensating("", "", Instant.EPOCH);
            case "COMPENSATED"   -> new Compensated("", Instant.EPOCH);
            case "FAILED"        -> new Failed("", Instant.EPOCH);
            default              -> throw new IllegalArgumentException(
                "Unknown SagaStatus persistence name: '%s'. Valid values: STARTED, EXECUTING, COMPLETED, COMPENSATING, COMPENSATED, FAILED."
                    .formatted(name));
        };
    }
}
