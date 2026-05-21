package io.sagaweaw.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaStepTest {

    record TestContext(String orderId) implements SagaContext {}

    private SagaStep.Builder<TestContext> base(String name) {
        return SagaStep.<TestContext>builder(name, 1)
                .invoker(ctx -> StepOutput.EMPTY);
    }

    @Nested
    class TypeInference {

        @Test
        void compensator_and_finite_retry_infers_COMPENSABLE() {
            var step = base("pay")
                    .compensator((ctx, out) -> {})
                    .retryPolicy(RetryPolicy.fixed(3, Duration.ofSeconds(1)))
                    .build();
            assertThat(step.type()).isEqualTo(SagaStep.StepType.COMPENSABLE);
            assertThat(step.canBeCompensated()).isTrue();
        }

        @Test
        void no_compensator_and_finite_retry_infers_PIVOT() {
            var step = base("transmit").build();
            assertThat(step.type()).isEqualTo(SagaStep.StepType.PIVOT);
            assertThat(step.isPivot()).isTrue();
        }

        @Test
        void infinite_retry_infers_RETRIABLE_regardless_of_compensator() {
            var step = base("credit")
                    .retryPolicy(RetryPolicy.infinite(Duration.ofSeconds(5)))
                    .build();
            assertThat(step.type()).isEqualTo(SagaStep.StepType.RETRIABLE);
            assertThat(step.isRetriable()).isTrue();
        }
    }

    @Nested
    class Validation {

        @Test
        void infinite_retry_with_compensator_throws() {
            assertThatThrownBy(() ->
                base("bad-step")
                    .compensator((ctx, out) -> {})
                    .retryPolicy(RetryPolicy.infinite(Duration.ofSeconds(1)))
                    .build()
            ).isInstanceOf(SagaStep.SagaStepDefinitionException.class)
             .hasMessageContaining("infinite retry AND a compensator");
        }

        @Test
        void null_invoker_throws() {
            assertThatThrownBy(() ->
                SagaStep.<TestContext>builder("step", 1).build()
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void blank_name_throws() {
            assertThatThrownBy(() -> SagaStep.<TestContext>builder("  ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Defaults {

        @Test
        void timeout_defaults_to_30_seconds() {
            var step = base("step").build();
            assertThat(step.timeout()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        void retry_policy_defaults_to_three_attempts() {
            var step = base("step").build();
            assertThat(step.shouldRetry(1)).isTrue();
            assertThat(step.shouldRetry(3)).isFalse();
        }
    }

    @Nested
    class Compensate {

        @Test
        void compensating_pivot_throws() throws Exception {
            var step = base("pivot").build();
            assertThatThrownBy(() -> step.compensate(new TestContext("x"), StepOutput.EMPTY))
                .isInstanceOf(SagaStep.SagaStepDefinitionException.class)
                .hasMessageContaining("COMPENSABLE");
        }

        @Test
        void compensating_compensable_step_calls_compensator() throws Exception {
            var called = new boolean[]{false};
            var step = base("pay")
                    .compensator((ctx, out) -> called[0] = true)
                    .build();
            step.compensate(new TestContext("x"), StepOutput.EMPTY);
            assertThat(called[0]).isTrue();
        }
    }
}
