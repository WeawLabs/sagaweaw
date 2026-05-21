package io.sagaweaw.spring.annotation;

import org.springframework.stereotype.Component;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Saga definition in Sagaweaw.
 * the SagaRegistry scans all classes annotated with @Saga
 * on Spring startup, compiles SagaFlow via define(), and registers it internally for use by SagaManager.
 * The name must be unique per application—it appears in the dashboard, logs, and observability queries.
 *
 * <pre>
 * {@literal @}Saga(name = "order-processing")
 * public class OrderSaga implements SagaDefinition{@literal <}OrderContext{@literal >} {
 *     {@literal @}Override
 *     public SagaFlow{@literal <}OrderContext{@literal >} define(SagaBuilder{@literal <}OrderContext{@literal >} saga) {
 *         return saga
 *             .step("reserve-inventory")
 *                 .invoke(inventoryService::reserve)
 *                 .compensate(inventoryService::release)
 *             .build();
 *     }
 * }
 * </pre>
 *
 * @see io.sagaweaw.core.SagaDefinition
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Saga {

    /**
     * Unique name of the Saga within the application.
     * Rules:
     * - Only lowercase letters, numbers, and hyphens
     * - Maximum 255 characters (aligned with the database schema)
     * - Description of the business flow — appears on the dashboard
     * Examples: "order-processing", "pix-payment", "user-onboarding"
     */
    String name();
}
