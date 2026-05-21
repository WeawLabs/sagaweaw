package io.sagaweaw.examples.pix;

import io.sagaweaw.core.SagaExecution;
import io.sagaweaw.test.SagaweawIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class PixPaymentSagaTest extends SagaweawIntegrationTest {

    private PixContext context() {
        return new PixContext("tx-001", "acc-origin", "cpf:12345678900", BigDecimal.valueOf(250));
    }

    @Test
    @DisplayName("all steps complete — saga reaches COMPLETED")
    void happyPath() {
        SagaExecution exec = sagaManager.start(PixPaymentSaga.class, context());

        testKit.assertSaga(exec.sagaId())
                .withinSeconds(10)
                .isCompleted()
                .hasCompletedStep("block-balance")
                .hasCompletedStep("validate-dict")
                .hasCompletedStep("transmit-to-bacen")
                .hasCompletedStep("credit-destination")
                .hasCompletedStep("notify-parties");
    }

    @Test
    @DisplayName("BACEN transmission fails — balance is unblocked (compensation)")
    void compensatesBeforePivot() {
        testKit.simulateFailureOn("transmit-to-bacen");

        SagaExecution exec = sagaManager.start(PixPaymentSaga.class, context());

        testKit.assertSaga(exec.sagaId())
                .withinSeconds(30)
                .isCompensated()
                .hasFailedStep("transmit-to-bacen")
                .hasCompensatedStep("validate-dict")
                .hasCompensatedStep("block-balance");
    }

    @Test
    @DisplayName("credit-destination fails twice then succeeds — infinite retry recovers")
    void infiniteRetryAfterPivot() {
        testKit.simulateFailureOnFirstAttempts("credit-destination", 2);

        SagaExecution exec = sagaManager.start(PixPaymentSaga.class, context());

        testKit.assertSaga(exec.sagaId())
                .withinSeconds(60)
                .isCompleted()
                .hasRetryCount("credit-destination", 2);
    }
}
