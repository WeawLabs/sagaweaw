package io.sagaweaw.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compiled, immutable flow of a Saga.
 *
 * Produced by SagaBuilder after full validation of the definition.
 * Reused by the engine for every execution of this Saga — never mutated.
 */
public record SagaFlow<C extends SagaContext>(
        String sagaName,
        List<SagaStep<C>> steps,
        SagaFlowHooks<C> hooks
) {

    public SagaFlow {
        if (sagaName == null || sagaName.isBlank())
            throw new IllegalArgumentException("sagaName cannot be blank");
        if (steps == null || steps.isEmpty())
            throw new IllegalArgumentException("Saga '%s' has no steps defined".formatted(sagaName));

        // Validate using the constructor parameters — this.steps is null here (compact constructor)
        validateNoDuplicateNames(steps, sagaName);
        validatePivotPosition(steps, sagaName);
        validateRetriableAfterPivot(steps, sagaName);

        steps = List.copyOf(steps);
        hooks = hooks != null ? hooks : SagaFlowHooks.empty();
    }

    // -- Validation (static — must not access this.steps) -----------------

    private static <C extends SagaContext> void validateNoDuplicateNames(
            List<SagaStep<C>> steps, String sagaName) {
        long unique = steps.stream().map(SagaStep::name).distinct().count();
        if (unique != steps.size()) {
            throw new SagaFlowDefinitionException(
                "Saga '%s' has duplicate step names — each step name must be unique.".formatted(sagaName));
        }
    }

    private static <C extends SagaContext> void validatePivotPosition(
            List<SagaStep<C>> steps, String sagaName) {
        long pivotCount = steps.stream().filter(SagaStep::isPivot).count();
        if (pivotCount > 1) {
            throw new SagaFlowDefinitionException(
                "Saga '%s' has %d PIVOT steps — only one point of no return is allowed."
                    .formatted(sagaName, pivotCount));
        }
    }

    private static <C extends SagaContext> void validateRetriableAfterPivot(
            List<SagaStep<C>> steps, String sagaName) {
        boolean foundPivot = false;
        for (SagaStep<C> step : steps) {
            if (step.isPivot())     { foundPivot = true; continue; }
            if (!foundPivot && step.isRetriable()) {
                throw new SagaFlowDefinitionException(
                    ("Saga '%s': RETRIABLE step '%s' appears before the PIVOT. "
                    + "RETRIABLE steps must run after the point of no return.")
                        .formatted(sagaName, step.name()));
            }
        }
    }

    // -- Engine API -------------------------------------------------------

    /** COMPENSABLE steps in reverse execution order — used by CompensationExecutor */
    public List<SagaStep<C>> compensableStepsInReverseOrder() {
        var list = new ArrayList<SagaStep<C>>();
        for (SagaStep<C> step : steps) {
            if (step.canBeCompensated()) list.add(step);
        }
        Collections.reverse(list);
        return Collections.unmodifiableList(list);
    }

    public int totalSteps() { return steps.size(); }

    public boolean hasPivot() { return steps.stream().anyMatch(SagaStep::isPivot); }

    public static class SagaFlowDefinitionException extends RuntimeException {
        public SagaFlowDefinitionException(String message) {
            super(message);
        }
    }
}
