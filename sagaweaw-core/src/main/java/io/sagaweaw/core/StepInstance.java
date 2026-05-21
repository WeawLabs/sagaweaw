package io.sagaweaw.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of a single step within a running or completed SagaInstance.
 *
 * Created by the engine at each state transition and returned by
 * SagaEngine.findById() — used by the dashboard for observability.
 *
 * @param name          step name as declared in the SagaFlow
 * @param order         execution order (for reverse compensation — ADR-007)
 * @param status        current state machine state
 * @param attempt       total number of execution attempts made
 * @param maxAttempts   maximum allowed attempts (0 = unlimited); enables "attempt X/Y" display
 * @param inputPayload  serialized input at execution time (JSONB in DB)
 * @param outputPayload serialized output used by compensator (ADR-007); null if not yet completed
 * @param lastError     most recent error message; null if step has not failed
 * @param errorTrace    full stack trace of the last failure; null if step has not failed
 * @param nextRetryAt   when the next retry is scheduled; null if no retry pending
 * @param executedAt    when the first execution attempt started; null if still pending
 * @param completedAt   when the step reached a terminal or compensated state; null otherwise
 * @param durationMs    execution duration in ms; 0 while in progress
 */
public record StepInstance(
        String     name,
        int        order,
        StepStatus status,
        int        attempt,
        int        maxAttempts,
        String     inputPayload,
        String     outputPayload,
        String     lastError,
        String     errorTrace,
        Instant    nextRetryAt,
        Instant    executedAt,
        Instant    completedAt,
        long       durationMs
) {
    public StepInstance {
        Objects.requireNonNull(name,   "StepInstance name cannot be null");
        Objects.requireNonNull(status, "StepInstance status cannot be null");
    }

    public boolean isCompleted()   { return status instanceof StepStatus.Completed; }
    public boolean isFailed()      { return status instanceof StepStatus.Failed f && f.exhausted(); }
    public boolean isCompensated() { return status instanceof StepStatus.Compensated; }
    public boolean wasSkipped()    { return status instanceof StepStatus.Skipped; }

    public StepStatus.StatusIndicator indicator() { return status.indicator(); }
}
