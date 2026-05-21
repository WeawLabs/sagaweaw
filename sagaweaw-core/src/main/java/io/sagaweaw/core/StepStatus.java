package io.sagaweaw.core;

import java.time.Instant;

/**
 * All possible states a Step can be in during Saga execution.
 *
 * Sealed interface — compiler enforces exhaustive pattern matching.
 *
 * Happy path:    PENDING → EXECUTING → COMPLETED
 * Compensation:  EXECUTING → FAILED → COMPENSATING → COMPENSATED
 * Skip:          PENDING → SKIPPED  (step never ran — a prior step failed first)
 *
 * COMPLETED, COMPENSATED, and SKIPPED are terminal — no further transitions.
 * PIVOT steps never reach COMPENSATING; they have no compensation defined (ADR-006).
 */
public sealed interface StepStatus
        permits StepStatus.Pending,
                StepStatus.Executing,
                StepStatus.Completed,
                StepStatus.Failed,
                StepStatus.Compensating,
                StepStatus.Compensated,
                StepStatus.Skipped {

    record Pending() implements StepStatus {}

    record Executing(Instant startedAt, int attempt) implements StepStatus {}

    record Completed(Instant at, long durationMs, String output) implements StepStatus {}

    /**
     * exhausted=true: RetryPolicy has no attempts left — engine will start compensating
     * all previously completed steps in reverse order.
     * exhausted=false: step is eligible for retry per its RetryPolicy.
     */
    record Failed(
            Instant at,
            int totalAttempts,
            String errorMessage,
            String errorTrace,
            boolean exhausted
    ) implements StepStatus {}

    // Only COMPENSABLE steps reach this state — PIVOT and RETRIABLE never do (ADR-006)
    record Compensating(Instant startedAt) implements StepStatus {}

    record Compensated(Instant at, long durationMs) implements StepStatus {}

    record Skipped() implements StepStatus {}

    // -- Dashboard color indicator ----------------------------------------

    enum StatusIndicator { GREEN, YELLOW, RED }

    default StatusIndicator indicator() {
        return switch (this) {
            case Completed c    -> StatusIndicator.GREEN;
            case Failed f       -> StatusIndicator.RED;
            case Pending p      -> StatusIndicator.YELLOW;
            case Executing e    -> StatusIndicator.YELLOW;
            case Compensating c -> StatusIndicator.YELLOW;
            case Compensated c  -> StatusIndicator.YELLOW;
            case Skipped s      -> StatusIndicator.YELLOW;
        };
    }

    // -- State queries ----------------------------------------------------

    default boolean isTerminal() {
        return this instanceof Completed
                || this instanceof Compensated
                || this instanceof Skipped;
    }

    // Only COMPLETED steps are compensable — per ADR-007, compensation uses the output
    // captured at completion time, never the live context
    default boolean needsCompensation() {
        return this instanceof Completed;
    }

    default boolean isFailed() {
        return this instanceof Failed;
    }

    // True only when all retry attempts are exhausted and compensation must begin
    default boolean isExhausted() {
        return this instanceof Failed f && f.exhausted();
    }

    default String persistenceName() {
        return switch (this) {
            case Pending p      -> "PENDING";
            case Executing e    -> "EXECUTING";
            case Completed c    -> "COMPLETED";
            case Failed f       -> "FAILED";
            case Compensating c -> "COMPENSATING";
            case Compensated c  -> "COMPENSATED";
            case Skipped s      -> "SKIPPED";
        };
    }

    static StepStatus fromPersistenceName(String name) {
        return switch (name) {
            case "PENDING"      -> new Pending();
            case "EXECUTING"    -> new Executing(null, 0);
            case "COMPLETED"    -> new Completed(null, 0L, null);
            case "FAILED"       -> new Failed(null, 0, null, null, false);
            case "COMPENSATING" -> new Compensating(null);
            case "COMPENSATED"  -> new Compensated(null, 0L);
            case "SKIPPED"      -> new Skipped();
            default             -> throw new IllegalArgumentException(
                    "Unknown StepStatus persistence name: '%s'".formatted(name));
        };
    }
}
