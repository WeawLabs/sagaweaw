package io.sagaweaw.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a Saga at a point in time.
 *
 * Returned by SagaEngine.findById() — represents the full state of a saga
 * including each step's individual state. Used internally by the engine and
 * exposed by the dashboard REST API as an observability DTO.
 *
 * @param id            UUID of this saga execution
 * @param name          saga definition name (e.g., "order-payment")
 * @param status        current saga state machine state
 * @param contextJson   business context serialized as JSON (deserialization belongs in sagaweaw-spring)
 * @param steps         ordered list of step snapshots
 * @param createdAt     when the saga was submitted to the engine
 * @param updatedAt     when any state transition last occurred
 * @param completedAt   when the saga reached a terminal state; null if still running
 * @param version       monotonically increasing version for optimistic locking
 */
public record SagaInstance(
        String             id,
        String             name,
        SagaStatus         status,
        String             contextJson,
        List<StepInstance> steps,
        Instant            createdAt,
        Instant            updatedAt,
        Instant            completedAt,
        int                version
) {
    public SagaInstance {
        Objects.requireNonNull(id,     "SagaInstance id cannot be null");
        Objects.requireNonNull(name,   "SagaInstance name cannot be null");
        Objects.requireNonNull(status, "SagaInstance status cannot be null");
        steps = steps != null ? List.copyOf(steps) : List.of();
    }

    public boolean isTerminal()    { return status.isTerminal(); }
    public boolean isRunning()     { return status instanceof SagaStatus.Executing; }
    public boolean isCompensated() { return status instanceof SagaStatus.Compensated; }

    /** Finds a step by name — primarily used in SagaTestKit assertions. */
    public StepInstance getStep(String stepName) {
        return steps.stream()
                .filter(s -> s.name().equals(stepName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Step '%s' not found in saga '%s'".formatted(stepName, name)));
    }

    /** Total duration in ms; 0 while the saga is still running. */
    public long durationMs() {
        if (completedAt == null) return 0;
        return completedAt.toEpochMilli() - createdAt.toEpochMilli();
    }
}
