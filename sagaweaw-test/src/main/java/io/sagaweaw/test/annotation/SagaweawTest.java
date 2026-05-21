package io.sagaweaw.test.annotation;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test slice for Sagaweaw — starts the saga engine with an embedded H2 database.
 * No Testcontainers, no Kafka, no Docker required.
 *
 * Use {@code @Import} to bring in your {@code @Saga} classes and {@code @MockBean}
 * for any external services they depend on.
 *
 * <pre>
 * {@literal @}SagaweawTest
 * {@literal @}Import(OrderSaga.class)
 * class OrderSagaTest {
 *
 *     {@literal @}Autowired SagaManager sagaManager;
 *     {@literal @}Autowired SagaTestKit testKit;
 *     {@literal @}MockBean InventoryService inventoryService;
 *     {@literal @}MockBean PaymentService paymentService;
 *
 *     {@literal @}Test
 *     void compensates_when_payment_fails() {
 *         testKit.simulateFailureOn("charge-payment");
 *         SagaExecution exec = sagaManager.start(OrderSaga.class, new OrderContext("order-1"));
 *         testKit.assertSaga(exec.sagaId()).isCompensated();
 *     }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@BootstrapWith(SagaweawTestContextBootstrapper.class)
@ExtendWith(SpringExtension.class)
@OverrideAutoConfiguration(enabled = false)
@AutoConfigureSagaweaw
public @interface SagaweawTest {

    /**
     * Extra properties to set on the Spring test {@link org.springframework.core.env.Environment}.
     * Each entry must follow the {@code key=value} format.
     */
    String[] properties() default {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "sagaweaw.flyway.enabled=false"
    };
}
