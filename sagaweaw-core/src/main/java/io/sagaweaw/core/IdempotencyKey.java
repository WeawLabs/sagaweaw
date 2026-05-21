package io.sagaweaw.core;

/**
 * Value object that uniquely identifies a Saga start request.
 *
 * When the engine receives a start() call with an IdempotencyKey that matches
 * an existing saga, it returns the existing execution instead of creating a new
 * one — making the operation safe to retry (ADR-003: at-least-once delivery).
 *
 * Recommended format: "<saga-type>-<business-id>", e.g. "order-payment-42".
 * The caller is responsible for choosing a key that is unique per business event.
 *
 * Maximum length: 255 characters — matches the sagas.idempotency_key column (VARCHAR 255).
 */
public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 255;

    public IdempotencyKey {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("IdempotencyKey cannot be null or blank");
        if (value.length() > MAX_LENGTH)
            throw new IllegalArgumentException(
                ("IdempotencyKey exceeds the maximum of %d characters (was %d). "
                + "Use a shorter key or hash a longer identifier — e.g. UUID.nameUUIDFromBytes(longKey.getBytes()).")
                    .formatted(MAX_LENGTH, value.length()));
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    /** Convenience factory — concatenates saga name and business ID with a dash separator. */
    public static IdempotencyKey of(String sagaName, String businessId) {
        return new IdempotencyKey(sagaName + "-" + businessId);
    }

    @Override
    public String toString() {
        return value;
    }
}
