package io.sagaweaw.core;

import java.util.Optional;

/**
 * Central contract of the Saga engine — the State Machine of Sagaweaw.
 *
 * This is a pure Java interface with zero framework dependencies.
 * The concrete implementation (SpringSagaEngine) lives in sagaweaw-spring
 * and adds JPA persistence, transactions, and Spring event publishing.
 *
 * Responsibilities:
 *   - Create Saga instances from a compiled SagaFlow
 *   - Advance steps in sequence, respecting the State Machine
 *   - Detect exhausted steps and trigger reverse compensation
 *   - Guarantee idempotency via IdempotencyKey
 *
 * Developers do not interact with SagaEngine directly.
 * The public entry point is SagaManager (sagaweaw-spring).
 */
public interface SagaEngine {

    // ================================================================
    // PUBLIC API — called by SagaManager in sagaweaw-spring
    // ================================================================

    /**
     * Starts a new Saga from the compiled flow and business context.
     *
     * @param flow    compiled flow produced by SagaDefinition.define()
     * @param context business data that travels between steps
     * @return lightweight reference to the started execution
     */
    <C extends SagaContext> SagaExecution start(SagaFlow<C> flow, C context);

    /**
     * Starts a new Saga with idempotency guarantee.
     * If a Saga with the given key already exists, returns the existing execution
     * without creating a duplicate — safe to call on retry (ADR-003).
     *
     * @param flow           compiled flow
     * @param context        business context
     * @param idempotencyKey unique key for deduplication (e.g., "order-payment-42")
     * @return existing execution if the key was already seen, or a newly started one
     */
    <C extends SagaContext> SagaExecution start(SagaFlow<C> flow, C context, IdempotencyKey idempotencyKey);

    /**
     * Reprocesses a Saga from the Dead Letter Queue.
     * Restarts from the failed step using the original serialized context.
     *
     * @param sagaId ID of the saga in the Dead Letter Queue
     * @return new execution reference with the same context
     * @throws SagaNotFoundException if no saga with that ID exists
     */
    SagaExecution reprocess(String sagaId);

    /**
     * Returns the current full snapshot of a Saga by ID.
     *
     * @param sagaId UUID of the saga execution
     * @return the saga snapshot, or empty if not found
     */
    Optional<SagaInstance> findById(String sagaId);

    // ================================================================
    // INTERNAL CONTRACTS — implemented by SpringSagaEngine,
    // called by RetryScheduler and CompensationExecutor within the engine
    // ================================================================

    /**
     * Advances the State Machine to execute a specific step.
     * Called by RetryScheduler when a step is eligible for a new attempt.
     *
     * @param sagaId   UUID of the saga to advance
     * @param stepName name of the step to execute
     * @param attempt  current attempt number (1-based)
     * @throws SagaNotFoundException if the saga does not exist
     */
    void executeStep(String sagaId, String stepName, int attempt);

    /**
     * Begins reverse compensation starting from the failed step.
     * Executes all COMPENSABLE steps in reverse order (ADR-007).
     *
     * @param sagaId      UUID of the saga to compensate
     * @param failedStep  name of the step that exhausted its retry budget
     * @throws SagaNotFoundException if the saga does not exist
     */
    void startCompensation(String sagaId, String failedStep);

    /**
     * Applies a State Machine transition to the given saga.
     * Validates that the transition is allowed before persisting.
     *
     * @param sagaId    UUID of the saga
     * @param newStatus desired target state
     * @throws InvalidSagaTransitionException if the transition is not allowed
     * @throws SagaNotFoundException          if the saga does not exist
     */
    void transition(String sagaId, SagaStatus newStatus);

    // ================================================================
    // EXCEPTIONS
    // ================================================================

    /** Thrown when an invalid state transition is attempted. */
    class InvalidSagaTransitionException extends RuntimeException {

        private final String     sagaId;
        private final SagaStatus currentStatus;
        private final SagaStatus attemptedStatus;

        public InvalidSagaTransitionException(
                String sagaId,
                SagaStatus current,
                SagaStatus attempted) {
            super("Cannot transition saga '%s' from %s to %s"
                    .formatted(sagaId,
                               current.persistenceName(),
                               attempted.persistenceName()));
            this.sagaId          = sagaId;
            this.currentStatus   = current;
            this.attemptedStatus = attempted;
        }

        public String     sagaId()          { return sagaId; }
        public SagaStatus currentStatus()   { return currentStatus; }
        public SagaStatus attemptedStatus() { return attemptedStatus; }
    }

    /** Thrown when no saga with the given ID exists. */
    class SagaNotFoundException extends RuntimeException {
        public SagaNotFoundException(String sagaId) {
            super("Saga not found: '%s'".formatted(sagaId));
        }
    }
}
