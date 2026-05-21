package io.sagaweaw.test.annotation;

import io.sagaweaw.core.RetryPolicy;
import io.sagaweaw.core.SagaBuilder;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.spring.SagaManager;
import io.sagaweaw.spring.annotation.Saga;
import io.sagaweaw.core.SagaDefinition;
import io.sagaweaw.core.SagaExecution;
import io.sagaweaw.test.SagaTestKit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SagaweawTest
@Import(SagaweawTestSliceTest.PingSaga.class)
class SagaweawTestSliceTest {

    @Autowired SagaManager sagaManager;
    @Autowired SagaTestKit testKit;

    @BeforeEach
    void reset() {
        testKit.reset();
    }

    @Nested
    class HappyPath {
        @Test
        void saga_completes_without_infrastructure() {
            SagaExecution exec = sagaManager.start(PingSaga.class, new PingContext("hello"));
            testKit.assertSaga(exec.sagaId()).isCompleted();
        }
    }

    @Nested
    class Compensation {
        @Test
        void saga_compensates_on_simulated_failure() {
            testKit.simulateFailureOn("step-b");
            SagaExecution exec = sagaManager.start(PingSaga.class, new PingContext("hello"));
            testKit.assertSaga(exec.sagaId())
                    .isCompensated()
                    .hasCompensatedStep("step-a");
        }
    }

    // ----------------------------------------------------------------
    // Minimal saga used only by this test
    // ----------------------------------------------------------------

    public record PingContext(String value) implements SagaContext {}

    @Saga(name = "ping")
    public static class PingSaga implements SagaDefinition<PingContext> {
        @Override
        public SagaFlow<PingContext> define(SagaBuilder<PingContext> saga) {
            return saga
                    .step("step-a")
                        .invoke(ctx -> {})
                        .compensate(ctx -> {})
                    .step("step-b")
                        .invoke(ctx -> {})
                        .retryPolicy(RetryPolicy.none())
                    .build();
        }
    }
}
