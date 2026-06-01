package io.sagaweaw.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a SagaContext field as sensitive.
 *
 * <p>Fields annotated with {@code @SagaMask} are replaced with {@code "[REDACTED]"} before
 * being written to {@code context_json} in the database and before being returned through
 * the observability API. The original value is never stored or transmitted.
 *
 * <p>Use this for any field that contains PII, credentials, tokens, or any data
 * that must not appear in logs, dashboards, or the dead-letter queue.
 *
 * <pre>{@code
 * data class Context(
 *     val orderId: UUID,
 *     val customerId: UUID,
 *     @SagaMask val creditCardToken: String,   // stored as "[REDACTED]"
 *     @SagaMask val email: String,             // stored as "[REDACTED]"
 *     val amount: BigDecimal,
 * ) : KSagaContext()
 * }</pre>
 *
 * <p><strong>Important:</strong> masking is one-way. Compensating steps that need
 * the original value must obtain it from the original source, not from the stored context.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SagaMask {
}
