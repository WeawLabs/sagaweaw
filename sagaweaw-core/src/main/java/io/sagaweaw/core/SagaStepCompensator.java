package io.sagaweaw.core;

/**
 * The action the engine calls to undo a step when the Saga must compensate.
 *
 * <pre>
 * .compensate(inventoryService::release)
 * .compensate((ctx, output) -> paymentGateway.refund(output.require("chargeId", String.class)))
 * </pre>
 *
 * Compensations MUST be idempotent — the engine may call them more than once
 * if a failure occurs during the compensation phase itself.
 */
@FunctionalInterface
public interface SagaStepCompensator<C extends SagaContext> {

    void compensate(C context, StepOutput stepOutput) throws Exception;
}
