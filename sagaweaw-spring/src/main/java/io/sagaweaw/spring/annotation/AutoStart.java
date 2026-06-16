package io.sagaweaw.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a saga to be auto-fired on application startup with its {@code sampleContext()}.
 *
 * <p>Only active when {@code sagaweaw.auto-start.enabled=true} is set in
 * {@code application.properties}. That property defaults to {@code false} and
 * must never be set in production environments.
 *
 * <p>The saga class must also implement {@link io.sagaweaw.core.SagaSampler} to
 * provide the sample context. Without it, this annotation is silently ignored.
 *
 * <pre>{@code
 * @Saga("order-payment")
 * @AutoStart
 * @Component
 * public class OrderPaymentSaga implements SagaDefinition<Context>, SagaSampler<Context> {
 *
 *     @Override
 *     public Context sampleContext() {
 *         return new Context("txn-sample-001", "user@example.com", new BigDecimal("150.00"), "user-42");
 *     }
 *
 *     @Override
 *     public SagaFlow<Context> define(SagaBuilder<Context> saga) { ... }
 * }
 * }</pre>
 *
 * @see io.sagaweaw.core.SagaSampler
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoStart {
}
