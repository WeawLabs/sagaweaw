package io.sagaweaw.core;

/**
 * The action the engine calls to execute a step's business logic.
 *
 * <pre>
 * .invoke(paymentService::charge)
 * .invoke(ctx -> StepOutput.of("chargeId", gateway.charge(ctx.amount()).id()))
 * </pre>
 *
 * Return StepOutput.EMPTY when the compensation does not need data from this step.
 * Return StepOutput.of(...) when the compensation needs data produced here (ADR-007).
 */
@FunctionalInterface
public interface SagaStepInvoker<C extends SagaContext> {

    StepOutput execute(C context) throws Exception;
}
