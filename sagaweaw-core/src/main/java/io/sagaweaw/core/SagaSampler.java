package io.sagaweaw.core;

/**
 * Optional interface for saga definitions that want to provide a sample context
 * for local development and testing.
 *
 * <p>When a saga implements this interface:
 * <ul>
 *   <li>The {@code @AutoStart} runner fires {@link #sampleContext()} automatically
 *       on startup when {@code sagaweaw.auto-start.enabled=true}</li>
 *   <li>The {@code GET /api/sagas/registry} endpoint includes the sample JSON
 *       so the trigger form is pre-filled</li>
 * </ul>
 *
 * <p>The sample context does not need to reference real database entities.
 * Its only purpose is to exercise the saga's step logic during development.
 *
 * @param <C> the context type of the saga
 */
public interface SagaSampler<C extends SagaContext> {

    /**
     * Returns a representative context for local testing.
     * Values can be hardcoded — this is never called in production
     * unless {@code sagaweaw.auto-start.enabled=true} is explicitly set.
     */
    C sampleContext();
}
