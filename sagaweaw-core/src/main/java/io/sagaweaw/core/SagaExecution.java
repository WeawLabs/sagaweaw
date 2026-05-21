package io.sagaweaw.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight reference to a started Saga execution.
 *
 * Returned by SagaEngine.start() — contains just enough to track the saga
 * without loading the full SagaInstance. The caller uses sagaId to query
 * the engine for detailed status via SagaEngine.findById().
 *
 * @param sagaId      unique identifier for this execution (UUID)
 * @param sagaName    name of the saga definition (e.g., "pix-payment")
 * @param startedAt   when the engine accepted the saga for execution
 * @param idempotent  true when the engine found an existing execution for the given
 *                    IdempotencyKey and returned it instead of creating a new one (ADR-003).
 *                    Callers should NOT re-publish downstream events when this is true.
 */
public record SagaExecution(
        String  sagaId,
        String  sagaName,
        Instant startedAt,
        boolean idempotent
) {
    public SagaExecution {
        Objects.requireNonNull(sagaId,    "sagaId cannot be null");
        Objects.requireNonNull(sagaName,  "sagaName cannot be null");
        Objects.requireNonNull(startedAt, "startedAt cannot be null");
    }

    /** Creates a reference for a brand-new saga execution. */
    public static SagaExecution newExecution(String sagaId, String sagaName, Instant startedAt) {
        return new SagaExecution(sagaId, sagaName, startedAt, false);
    }

    /** Creates a reference when idempotency deduplication found an existing execution (ADR-003). */
    public static SagaExecution existingExecution(String sagaId, String sagaName, Instant startedAt) {
        return new SagaExecution(sagaId, sagaName, startedAt, true);
    }
}
