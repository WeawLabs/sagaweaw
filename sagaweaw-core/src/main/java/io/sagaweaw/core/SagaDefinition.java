package io.sagaweaw.core;

import java.time.Duration;
import java.util.Optional;

/**
 * Contract that every Saga class must implement.
 *
 * Generic on the context type — the compiler guarantees the correct context
 * is used across all steps and compensations.
 *
 * <pre>
 * {@literal @}Saga(name = "order-processing")
 * public class OrderSaga implements SagaDefinition&lt;OrderContext&gt; {
 *
 *     {@literal @}Override
 *     public SagaFlow&lt;OrderContext&gt; define(SagaBuilder&lt;OrderContext&gt; saga) {
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
 */
public interface SagaDefinition<C extends SagaContext> {

    /**
     * Defines the complete flow of this Saga.
     *
     * Called once at application startup. The resulting SagaFlow is immutable
     * and reused for every execution of this Saga.
     */
    SagaFlow<C> define(SagaBuilder<C> saga);

    /**
     * Optional global timeout for the entire Saga.
     * If the Saga does not complete within this duration, it is marked FAILED
     * and compensation is triggered for all steps already executed.
     *
     * Default: no global timeout — each step has its own individual timeout.
     */
    default Optional<Duration> sagaTimeout() {
        return Optional.empty();
    }

    /**
     * Maximum number of steps compensated concurrently.
     * Default: 1 — sequential compensation in reverse order.
     * Override only when compensation steps are provably independent.
     */
    default int compensationConcurrency() {
        return 1;
    }

    // -- Lifecycle hooks (class-level alternative to SagaBuilder.onSuccess/etc.) -----

    /** Called once after all steps complete successfully. */
    default void onSuccess(C context) {}

    /** Called once after all compensations complete successfully. */
    default void onCompensated(C context) {}

    /** Called when the saga is permanently failed (retries exhausted, compensation may be ongoing). */
    default void onFailure(C context, String failedStep, String errorMessage) {}
}
