package io.sagaweaw.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for Saga definitions — the primary developer-facing API.
 *
 * The developer never instantiates SagaBuilder directly. The engine creates it
 * and passes it as the argument to SagaDefinition.define():
 *
 * <pre>
 * {@literal @}Saga(name = "order-processing")
 * public class OrderSaga implements SagaDefinition{@literal <}OrderContext{@literal >} {
 *
 *     {@literal @}Override
 *     public SagaFlow{@literal <}OrderContext{@literal >} define(SagaBuilder{@literal <}OrderContext{@literal >} saga) {
 *         return saga
 *             .step("reserve-inventory")
 *                 .invoke(inventoryService::reserve)
 *                 .compensate(inventoryService::release)
 *             .step("charge-payment")
 *                 .invoke(paymentService::charge)
 *                 .compensate(paymentService::refund)
 *                 .retryPolicy(RetryPolicy.exponential(3, Duration.ofSeconds(5)))
 *             .step("create-shipment")
 *                 .invoke(shippingService::schedule)
 *                 .compensate(shippingService::cancel)
 *             .build();
 *     }
 * }
 * </pre>
 *
 * @param <C> the Saga context type — propagated to all steps
 */
public final class SagaBuilder<C extends SagaContext> {

    private final String sagaName;
    private final List<SagaStep.Builder<C>> stepBuilders = new ArrayList<>();

    private Consumer<C> onSuccess     = ctx -> {};
    private Consumer<C> onCompensated = ctx -> {};
    private SagaFlowHooks.TriConsumer<C, String, String> onFailure = (ctx, step, err) -> {};

    // -- Factory (package-private — only the engine creates builders) -----

    public static <C extends SagaContext> SagaBuilder<C> forSaga(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Saga name cannot be null or blank");
        return new SagaBuilder<>(name);
    }

    private SagaBuilder(String name) {
        this.sagaName = name;
    }

    // -- Fluent API -------------------------------------------------------

    public SagaStepBuilder<C> step(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Step name cannot be null or blank");
        return new SagaStepBuilder<>(name, stepBuilders.size(), this);
    }

    public SagaBuilder<C> onSuccess(Consumer<C> callback) {
        this.onSuccess = callback != null ? callback : ctx -> {};
        return this;
    }

    public SagaBuilder<C> onCompensated(Consumer<C> callback) {
        this.onCompensated = callback != null ? callback : ctx -> {};
        return this;
    }

    public SagaBuilder<C> onFailure(SagaFlowHooks.TriConsumer<C, String, String> callback) {
        this.onFailure = callback != null ? callback : (ctx, step, err) -> {};
        return this;
    }

    public SagaFlow<C> build() {
        if (stepBuilders.isEmpty())
            throw new SagaFlow.SagaFlowDefinitionException(
                "Saga '%s' has no steps — define at least one step before calling build()."
                    .formatted(sagaName));

        List<SagaStep<C>> steps = stepBuilders.stream()
                .map(SagaStep.Builder::build)
                .toList();

        return new SagaFlow<>(sagaName, steps, new SagaFlowHooks<>(onSuccess, onCompensated, onFailure));
    }

    private void addStep(SagaStep.Builder<C> stepBuilder) {
        stepBuilders.add(stepBuilder);
    }

    // -- SagaStepBuilder --------------------------------------------------

    /**
     * Builder for a single step within the Saga.
     *
     * Static inner class with its own {@code <C>} — avoids javac generic type
     * inference issues that occur with lambdas in non-static inner classes.
     *
     * The current step is finalized automatically when the developer calls
     * .step(), .onSuccess(), .onCompensated(), .onFailure(), or .build().
     */
    public static final class SagaStepBuilder<C extends SagaContext> {

        private final String           name;
        private final int              order;
        private final SagaBuilder<C>   parent;
        private SagaStepInvoker<C>     invoker;
        private SagaStepCompensator<C> compensator;
        private RetryPolicy            retryPolicy;
        private Duration               timeout;
        private boolean                finalized = false;

        private SagaStepBuilder(String name, int order, SagaBuilder<C> parent) {
            this.name   = name;
            this.order  = order;
            this.parent = parent;
        }

        // -- invoke -------------------------------------------------------

        /**
         * Step action that produces output for the compensation (ADR-007).
         *
         * <pre>
         * .invoke(ctx -> {
         *     String chargeId = gateway.charge(ctx.amount());
         *     return StepOutput.of("chargeId", chargeId);
         * })
         * </pre>
         */
        public SagaStepBuilder<C> invoke(SagaStepInvoker<C> invoker) {
            this.invoker = invoker;
            return this;
        }

        /**
         * Step action that produces no output for the compensation.
         * Java type inference selects this overload when the method reference
         * returns void and does not declare checked exceptions.
         *
         * <pre>
         * .invoke(inventoryService::reserve)
         * </pre>
         */
        public SagaStepBuilder<C> invoke(Consumer<C> action) {
            this.invoker = ctx -> { action.accept(ctx); return StepOutput.EMPTY; };
            return this;
        }

        // -- compensate ---------------------------------------------------

        /**
         * Compensation that uses the output recorded at execution time (ADR-007).
         *
         * <pre>
         * .compensate((ctx, output) -> gateway.refund(output.require("chargeId", String.class)))
         * </pre>
         */
        public SagaStepBuilder<C> compensate(SagaStepCompensator<C> compensator) {
            this.compensator = compensator;
            return this;
        }

        /**
         * Compensation that only needs the original context.
         *
         * <pre>
         * .compensate(inventoryService::release)
         * </pre>
         */
        public SagaStepBuilder<C> compensate(Consumer<C> action) {
            this.compensator = (ctx, output) -> action.accept(ctx);
            return this;
        }

        // -- retry and timeout --------------------------------------------

        public SagaStepBuilder<C> retryPolicy(RetryPolicy policy) {
            this.retryPolicy = policy;
            return this;
        }

        public SagaStepBuilder<C> timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        // -- transitions (finalize current step, open next context) -------

        public SagaStepBuilder<C> step(String name) {
            finalizeStep();
            return parent.step(name);
        }

        public SagaBuilder<C> onSuccess(Consumer<C> callback) {
            finalizeStep();
            return parent.onSuccess(callback);
        }

        public SagaBuilder<C> onCompensated(Consumer<C> callback) {
            finalizeStep();
            return parent.onCompensated(callback);
        }

        public SagaBuilder<C> onFailure(SagaFlowHooks.TriConsumer<C, String, String> callback) {
            finalizeStep();
            return parent.onFailure(callback);
        }

        public SagaFlow<C> build() {
            finalizeStep();
            return parent.build();
        }

        // -- internal -----------------------------------------------------

        private void finalizeStep() {
            if (finalized) return;

            if (invoker == null)
                throw new SagaFlow.SagaFlowDefinitionException(
                    ("Step '%s' in saga '%s' has no invoker — "
                    + "call .invoke() before .step() or .build().")
                        .formatted(name, parent.sagaName));

            parent.addStep(
                SagaStep.<C>builder(name, order)
                    .invoker(invoker)
                    .compensator(compensator)
                    .retryPolicy(retryPolicy)
                    .timeout(timeout)
            );

            finalized = true;
        }
    }
}
