package io.sagaweaw.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaFlowTest {

    record TestContext(String id) implements SagaContext {}

    private SagaStep<TestContext> compensable(String name, int order) {
        return SagaStep.<TestContext>builder(name, order)
                .invoker(ctx -> StepOutput.EMPTY)
                .compensator((ctx, out) -> {})
                .build();
    }

    private SagaStep<TestContext> pivot(String name, int order) {
        return SagaStep.<TestContext>builder(name, order)
                .invoker(ctx -> StepOutput.EMPTY)
                .build();
    }

    private SagaStep<TestContext> retriable(String name, int order) {
        return SagaStep.<TestContext>builder(name, order)
                .invoker(ctx -> StepOutput.EMPTY)
                .retryPolicy(RetryPolicy.infinite(Duration.ofSeconds(1)))
                .build();
    }

    @Nested
    class Construction {

        @Test
        void valid_flow_is_created() {
            var flow = new SagaFlow<>("order", List.of(compensable("step-1", 1)), SagaFlowHooks.empty());
            assertThat(flow.totalSteps()).isEqualTo(1);
        }

        @Test
        void steps_are_immutable_after_construction() {
            var mutableList = new java.util.ArrayList<SagaStep<TestContext>>();
            mutableList.add(compensable("step-1", 1));
            var flow = new SagaFlow<>("order", mutableList, SagaFlowHooks.empty());
            mutableList.add(compensable("step-2", 2));
            assertThat(flow.totalSteps()).isEqualTo(1);
        }

        @Test
        void null_hooks_defaults_to_empty() {
            var flow = new SagaFlow<>("order", List.of(compensable("step-1", 1)), null);
            assertThat(flow.hooks()).isNotNull();
        }
    }

    @Nested
    class Validation {

        @Test
        void duplicate_step_names_throws() {
            assertThatThrownBy(() ->
                new SagaFlow<>("order",
                    List.of(compensable("pay", 1), compensable("pay", 2)),
                    SagaFlowHooks.empty())
            ).isInstanceOf(SagaFlow.SagaFlowDefinitionException.class)
             .hasMessageContaining("duplicate step names");
        }

        @Test
        void two_pivot_steps_throws() {
            assertThatThrownBy(() ->
                new SagaFlow<>("order",
                    List.of(pivot("pivot-1", 1), pivot("pivot-2", 2)),
                    SagaFlowHooks.empty())
            ).isInstanceOf(SagaFlow.SagaFlowDefinitionException.class)
             .hasMessageContaining("PIVOT");
        }

        @Test
        void retriable_before_pivot_throws() {
            assertThatThrownBy(() ->
                new SagaFlow<>("order",
                    List.of(retriable("credit", 1), pivot("transmit", 2)),
                    SagaFlowHooks.empty())
            ).isInstanceOf(SagaFlow.SagaFlowDefinitionException.class)
             .hasMessageContaining("before the PIVOT");
        }

        @Test
        void retriable_after_pivot_is_valid() {
            var flow = new SagaFlow<>("pix",
                List.of(compensable("block", 1), pivot("transmit", 2), retriable("credit", 3)),
                SagaFlowHooks.empty());
            assertThat(flow.totalSteps()).isEqualTo(3);
        }

        @Test
        void blank_name_throws() {
            assertThatThrownBy(() ->
                new SagaFlow<>(" ", List.of(compensable("step", 1)), SagaFlowHooks.empty())
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void empty_steps_throws() {
            assertThatThrownBy(() ->
                new SagaFlow<>("order", List.of(), SagaFlowHooks.empty())
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class CompensableStepsInReverseOrder {

        @Test
        void returns_only_compensable_steps_in_reverse() {
            var block   = compensable("block", 1);
            var transmit = pivot("transmit", 2);
            var credit  = retriable("credit", 3);
            var flow = new SagaFlow<>("pix", List.of(block, transmit, credit), SagaFlowHooks.empty());

            var result = flow.compensableStepsInReverseOrder();
            assertThat(result).containsExactly(block);
        }

        @Test
        void multiple_compensable_steps_reversed() {
            var s1 = compensable("reserve", 1);
            var s2 = compensable("charge", 2);
            var s3 = compensable("ship", 3);
            var flow = new SagaFlow<>("order", List.of(s1, s2, s3), SagaFlowHooks.empty());

            var result = flow.compensableStepsInReverseOrder();
            assertThat(result).containsExactly(s3, s2, s1);
        }
    }
}
