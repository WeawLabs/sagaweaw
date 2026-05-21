package io.sagaweaw.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaBuilderTest {

    record OrderContext(String orderId) implements SagaContext {}

    // -- Service stubs — method references resolve overloads unambiguously --
    // void methods  → Consumer<C> overload
    // StepOutput    → SagaStepInvoker<C> overload

    private void reserve(OrderContext ctx)    {}
    private void release(OrderContext ctx)    {}
    private void ship(OrderContext ctx)       {}
    private void cancelShip(OrderContext ctx) {}

    private StepOutput charge(OrderContext ctx) {
        return StepOutput.of("chargeId", "ch_" + ctx.orderId());
    }
    private void refund(OrderContext ctx, StepOutput out) {}

    private void blockBalance(OrderContext ctx) {}
    private void unblockBalance(OrderContext ctx) {}
    private void transmit(OrderContext ctx) {}
    private void credit(OrderContext ctx) {}

    private SagaBuilder<OrderContext> builder() {
        return SagaBuilder.forSaga("order-processing");
    }

    // -----------------------------------------------------------------------

    @Nested
    class FlowConstruction {

        @Test
        void builds_the_30_line_example_from_the_roadmap() {
            var flow = SagaBuilder.<OrderContext>forSaga("order-processing")
                .step("reserve-inventory")
                    .invoke(SagaBuilderTest.this::reserve)
                    .compensate(SagaBuilderTest.this::release)
                .step("charge-payment")
                    .invoke(SagaBuilderTest.this::charge)
                    .compensate(SagaBuilderTest.this::refund)
                    .retryPolicy(RetryPolicy.exponential(3, Duration.ofSeconds(5)))
                .step("create-shipment")
                    .invoke(SagaBuilderTest.this::ship)
                    .compensate(SagaBuilderTest.this::cancelShip)
                .build();

            assertThat(flow.sagaName()).isEqualTo("order-processing");
            assertThat(flow.totalSteps()).isEqualTo(3);
            assertThat(flow.steps()).allMatch(SagaStep::canBeCompensated);
        }

        @Test
        void pivot_and_retriable_steps_inferred_correctly() {
            var flow = SagaBuilder.<OrderContext>forSaga("pix-payment")
                .step("block-balance")
                    .invoke(SagaBuilderTest.this::blockBalance)
                    .compensate(SagaBuilderTest.this::unblockBalance)
                .step("transmit-to-bacen")
                    .invoke(SagaBuilderTest.this::transmit)
                    .timeout(Duration.ofSeconds(10))
                .step("credit-destination")
                    .invoke(SagaBuilderTest.this::credit)
                    .retryPolicy(RetryPolicy.infinite(Duration.ofSeconds(5)))
                .build();

            assertThat(flow.steps().get(0).canBeCompensated()).isTrue();
            assertThat(flow.steps().get(1).isPivot()).isTrue();
            assertThat(flow.steps().get(2).isRetriable()).isTrue();
        }

        @Test
        void steps_preserve_declaration_order() {
            var flow = builder()
                .step("first").invoke(SagaBuilderTest.this::reserve).compensate(SagaBuilderTest.this::release)
                .step("second").invoke(SagaBuilderTest.this::reserve).compensate(SagaBuilderTest.this::release)
                .step("third").invoke(SagaBuilderTest.this::reserve).compensate(SagaBuilderTest.this::release)
                .build();

            assertThat(flow.steps()).extracting(SagaStep::name)
                    .containsExactly("first", "second", "third");
        }
    }

    @Nested
    class InvokeOverloads {

        @Test
        void void_method_reference_wraps_to_EMPTY_output() throws Exception {
            var invoked = new boolean[]{false};
            var flow = builder()
                .step("step")
                    .invoke((OrderContext ctx) -> invoked[0] = true)  // block-void → Consumer
                .build();

            var output = flow.steps().get(0).invoke(new OrderContext("x"));
            assertThat(invoked[0]).isTrue();
            assertThat(output.isEmpty()).isTrue();
        }

        @Test
        void stepoutput_method_reference_passes_through() throws Exception {
            var flow = builder()
                .step("step")
                    .invoke(SagaBuilderTest.this::charge)
                .build();

            var output = flow.steps().get(0).invoke(new OrderContext("42"));
            assertThat(output.require("chargeId", String.class)).isEqualTo("ch_42");
        }
    }

    @Nested
    class CompensateOverloads {

        @Test
        void void_method_reference_ignores_step_output() throws Exception {
            var compensated = new boolean[]{false};
            var flow = builder()
                .step("step")
                    .invoke(SagaBuilderTest.this::reserve)
                    .compensate((OrderContext ctx) -> compensated[0] = true)
                .build();

            flow.steps().get(0).compensate(new OrderContext("x"), StepOutput.EMPTY);
            assertThat(compensated[0]).isTrue();
        }

        @Test
        void compensator_with_output_receives_step_output() throws Exception {
            String[] capturedId = {null};
            var flow = builder()
                .step("step")
                    .invoke(SagaBuilderTest.this::charge)
                    .compensate((ctx, out) -> capturedId[0] = out.require("chargeId", String.class))
                .build();

            flow.steps().get(0).compensate(new OrderContext("x"),
                    StepOutput.of("chargeId", "ch_123"));
            assertThat(capturedId[0]).isEqualTo("ch_123");
        }
    }

    @Nested
    class LifecycleHooks {

        @Test
        void hooks_are_stored_in_flow() {
            var called = new boolean[]{false};
            var flow = builder()
                .step("step").invoke(SagaBuilderTest.this::reserve).compensate(SagaBuilderTest.this::release)
                .onSuccess(ctx -> called[0] = true)
                .build();

            flow.hooks().onSuccess().accept(new OrderContext("x"));
            assertThat(called[0]).isTrue();
        }

        @Test
        void missing_hooks_default_to_noop() {
            var flow = builder()
                .step("step").invoke(SagaBuilderTest.this::reserve).compensate(SagaBuilderTest.this::release)
                .build();

            flow.hooks().onSuccess().accept(new OrderContext("x"));
            flow.hooks().onCompensated().accept(new OrderContext("x"));
            flow.hooks().onFailure().accept(new OrderContext("x"), "step", "error");
        }
    }

    @Nested
    class Validation {

        @Test
        void blank_saga_name_throws() {
            assertThatThrownBy(() -> SagaBuilder.forSaga("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void build_without_steps_throws() {
            assertThatThrownBy(() -> builder().build())
                    .isInstanceOf(SagaFlow.SagaFlowDefinitionException.class)
                    .hasMessageContaining("no steps");
        }

        @Test
        void step_without_invoker_throws() {
            assertThatThrownBy(() ->
                builder()
                    .step("no-invoker")
                    .build()
            ).isInstanceOf(SagaFlow.SagaFlowDefinitionException.class)
             .hasMessageContaining("no invoker");
        }

        @Test
        void blank_step_name_throws() {
            assertThatThrownBy(() -> builder().step("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void duplicate_step_names_propagate_to_flow_validation() {
            assertThatThrownBy(() ->
                builder()
                    .step("pay").invoke(SagaBuilderTest.this::reserve).compensate(SagaBuilderTest.this::release)
                    .step("pay").invoke(SagaBuilderTest.this::reserve).compensate(SagaBuilderTest.this::release)
                    .build()
            ).isInstanceOf(SagaFlow.SagaFlowDefinitionException.class)
             .hasMessageContaining("duplicate");
        }
    }
}
