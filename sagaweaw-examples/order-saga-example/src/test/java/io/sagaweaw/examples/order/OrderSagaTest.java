package io.sagaweaw.examples.order;

import io.sagaweaw.core.SagaExecution;
import io.sagaweaw.test.SagaweawIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class OrderSagaTest extends SagaweawIntegrationTest {

    private OrderContext context() {
        return new OrderContext("order-1", "customer-1", "item-42", 3, BigDecimal.valueOf(150));
    }

    @Test
    @DisplayName("all steps complete — saga reaches COMPLETED")
    void happyPath() {
        SagaExecution exec = sagaManager.start(OrderSaga.class, context());

        testKit.assertSaga(exec.sagaId())
                .withinSeconds(10)
                .isCompleted()
                .hasCompletedStep("reserve-inventory")
                .hasCompletedStep("charge-payment")
                .hasCompletedStep("create-shipment");
    }

    @Test
    @DisplayName("payment fails — inventory is released in reverse order")
    void compensatesWhenPaymentFails() {
        testKit.simulateFailureOn("charge-payment");

        SagaExecution exec = sagaManager.start(OrderSaga.class, context());

        testKit.assertSaga(exec.sagaId())
                .withinSeconds(30)
                .isCompensated()
                .hasFailedStep("charge-payment")
                .hasCompensatedStep("reserve-inventory")
                .hasSkippedStep("create-shipment");
    }

    @Test
    @DisplayName("payment fails twice then succeeds — retry recovers the saga")
    void retryRecovery() {
        testKit.simulateFailureOnFirstAttempts("charge-payment", 2);

        SagaExecution exec = sagaManager.start(OrderSaga.class, context());

        testKit.assertSaga(exec.sagaId())
                .withinSeconds(60)
                .isCompleted()
                .hasRetryCount("charge-payment", 2);
    }

    @Test
    @DisplayName("same idempotency key — returns existing saga without creating another")
    void idempotency() {
        var key = io.sagaweaw.core.IdempotencyKey.of("order-processing", "order-1");

        SagaExecution first  = sagaManager.start(OrderSaga.class, context(), key);
        SagaExecution second = sagaManager.start(OrderSaga.class, context(), key);

        org.assertj.core.api.Assertions.assertThat(second.sagaId())
                .isEqualTo(first.sagaId());

        org.assertj.core.api.Assertions.assertThat(second.idempotent()).isTrue();
    }

    @Test
    @DisplayName("compensation runs in strict reverse order")
    void compensationOrder() {
        testKit.simulateFailureOn("create-shipment");

        SagaExecution exec = sagaManager.start(OrderSaga.class, context());

        testKit.assertSaga(exec.sagaId())
                .withinSeconds(30)
                .isCompensated()
                .compensationExecutedInReverseOrder(
                        "charge-payment",
                        "reserve-inventory"
                );
    }
}
