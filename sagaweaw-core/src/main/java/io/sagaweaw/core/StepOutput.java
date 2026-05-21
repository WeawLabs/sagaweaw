package io.sagaweaw.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Output produced by a step during its execution.
 *
 * Stored in saga_steps.payload (JSONB) and passed to the step's compensation
 * lambda as the second argument: .compensate((ctx, output) -> ...)
 *
 * This guarantees compensation uses data from the ACTUAL execution, never the
 * live context — preventing asymmetric compensation (ADR-007).
 *
 * <pre>
 * .step("charge-payment")
 *     .invoke(ctx -> {
 *         String chargeId = paymentGateway.charge(ctx.amount());
 *         return StepOutput.of("chargeId", chargeId);
 *     })
 *     .compensate((ctx, output) -> {
 *         paymentGateway.refund(output.require("chargeId", String.class));
 *     })
 * </pre>
 */
public record StepOutput(Map<String, Object> values) {

    public StepOutput {
        values = Map.copyOf(values); // defensive copy — record is immutable by contract
    }

    public static final StepOutput EMPTY = new StepOutput(Map.of());

    public static StepOutput of(String key, Object value) {
        return new StepOutput(Map.of(key, value));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the value cast to the requested type, or throws if the key is absent.
     * Use in compensations where the value is mandatory.
     */
    public <T> T require(String key, Class<T> type) {
        Object value = values.get(key);
        if (value == null) {
            throw new StepOutputMissingKeyException(
                ("StepOutput missing required key '%s'. Available keys: %s. "
                + "Ensure the invoke() lambda writes this key before the compensate() lambda reads it.")
                    .formatted(key, values.keySet())
            );
        }
        return type.cast(value);
    }

    /** Returns the value cast to the requested type, or empty if the key is absent. */
    public <T> Optional<T> get(String key, Class<T> type) {
        return Optional.ofNullable(type.cast(values.get(key)));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public static class Builder {
        private final Map<String, Object> values = new HashMap<>();

        public Builder put(String key, Object value) {
            values.put(key, value);
            return this;
        }

        public StepOutput build() {
            return new StepOutput(values); // constructor copies
        }
    }

    // Thrown when a compensation reads a key that invoke() never wrote
    public static class StepOutputMissingKeyException extends RuntimeException {
        StepOutputMissingKeyException(String message) {
            super(message);
        }
    }
}
