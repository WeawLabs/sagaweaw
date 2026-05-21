package io.sagaweaw.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable definition of a single step within a Saga.
 *
 * Built once at application startup by the SagaBuilder and reused for every
 * execution of the Saga. Thread-safe by design — no field changes after construction.
 *
 * StepType is inferred automatically — the developer never declares it (ADR-006):
 *   has compensator + finite retry → COMPENSABLE
 *   no compensator  + finite retry → PIVOT
 *   infinite retry                 → RETRIABLE (compensator not allowed)
 */
public final class SagaStep<C extends SagaContext> {

    private final String name;
    private final int order;
    private final SagaStepInvoker<C> invoker;
    private final SagaStepCompensator<C> compensator;
    private final RetryPolicy retryPolicy;
    private final Duration timeout;
    private final StepType type;

    private SagaStep(Builder<C> builder) {
        this.name        = Objects.requireNonNull(builder.name, "step name cannot be null");
        this.order       = builder.order;
        this.invoker     = Objects.requireNonNull(builder.invoker,
                               "step '%s' must have an invoker".formatted(builder.name));
        this.compensator = builder.compensator;
        this.retryPolicy = builder.retryPolicy != null ? builder.retryPolicy : RetryPolicy.defaultPolicy();
        this.timeout     = builder.timeout     != null ? builder.timeout     : Duration.ofSeconds(30);
        this.type        = inferType();
        validateConstraints();
    }

    private StepType inferType() {
        if (retryPolicy.isInfinite()) return StepType.RETRIABLE;
        if (compensator == null)      return StepType.PIVOT;
        return StepType.COMPENSABLE;
    }

    private void validateConstraints() {
        // RETRIABLE steps run after the PIVOT and must always succeed — compensation is meaningless
        if (retryPolicy.isInfinite() && compensator != null) {
            throw new SagaStepDefinitionException(
                ("Step '%s': infinite retry AND a compensator is a contradiction. "
                + "RETRIABLE steps must always succeed — they cannot be compensated. "
                + "Remove the compensator or use a finite retry policy.")
                    .formatted(name));
        }
    }

    // -- Execution API (called by the engine, not by the developer) -------

    public StepOutput invoke(C context) throws Exception {
        StepOutput output = invoker.execute(context);
        return output != null ? output : StepOutput.EMPTY;
    }

    public void compensate(C context, StepOutput originalOutput) throws Exception {
        if (compensator == null) {
            throw new SagaStepDefinitionException(
                "Attempted to compensate step '%s' of type %s — only COMPENSABLE steps can be compensated."
                    .formatted(name, type));
        }
        compensator.compensate(context, originalOutput);
    }

    // -- Query API (engine asks before acting) ----------------------------

    public boolean canBeCompensated()              { return type == StepType.COMPENSABLE; }
    public boolean isPivot()                       { return type == StepType.PIVOT; }
    public boolean isRetriable()                   { return type == StepType.RETRIABLE; }
    public boolean shouldRetry(int attempt)        { return retryPolicy.shouldRetry(attempt); }
    public Duration delayBeforeRetry(int attempt)  { return retryPolicy.nextDelay(attempt); }

    // -- Getters ----------------------------------------------------------

    public String      name()        { return name; }
    public int         order()       { return order; }
    public StepType    type()        { return type; }
    public RetryPolicy retryPolicy() { return retryPolicy; }
    public Duration    timeout()     { return timeout; }

    @Override
    public String toString() {
        return "SagaStep{name='%s', order=%d, type=%s, hasCompensator=%b}"
            .formatted(name, order, type, compensator != null);
    }

    // -- StepType ---------------------------------------------------------

    public enum StepType {
        /** Has compensation and finite retry. Can be undone. */
        COMPENSABLE,
        /** No compensation, finite retry. Point of no return. */
        PIVOT,
        /** Infinite retry, no compensation. Runs after PIVOT — must always succeed. */
        RETRIABLE
    }

    public static class SagaStepDefinitionException extends RuntimeException {
        public SagaStepDefinitionException(String message) {
            super(message);
        }
    }

    // -- Builder (created by SagaBuilder, not by the developer) ----------

    static <C extends SagaContext> Builder<C> builder(String name, int order) {
        return new Builder<>(name, order);
    }

    static final class Builder<C extends SagaContext> {
        private final String name;
        private final int order;
        private SagaStepInvoker<C> invoker;
        private SagaStepCompensator<C> compensator;
        private RetryPolicy retryPolicy;
        private Duration timeout;

        private Builder(String name, int order) {
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Step name cannot be null or blank");
            this.name  = name;
            this.order = order;
        }

        Builder<C> invoker(SagaStepInvoker<C> invoker)            { this.invoker      = invoker;      return this; }
        Builder<C> compensator(SagaStepCompensator<C> compensator) { this.compensator  = compensator;  return this; }
        Builder<C> retryPolicy(RetryPolicy policy)                  { this.retryPolicy  = policy;       return this; }
        Builder<C> timeout(Duration timeout)                        { this.timeout      = timeout;      return this; }

        SagaStep<C> build() { return new SagaStep<>(this); }
    }
}
