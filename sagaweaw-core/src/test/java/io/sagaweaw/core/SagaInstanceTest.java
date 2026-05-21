package io.sagaweaw.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaInstanceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-01T10:00:01Z");

    private SagaInstance saga(SagaStatus status, List<StepInstance> steps, Instant completedAt) {
        return new SagaInstance("saga-1", "order-saga", status, "{}", steps, T0, T1, completedAt, 1);
    }

    private StepInstance step(String name, StepStatus status) {
        return new StepInstance(name, 0, status, 1, 3, null, null, null, null, null, T0, null, 0);
    }

    @Nested
    class Validation {

        @Test
        void null_id_throws() {
            assertThatThrownBy(() ->
                    new SagaInstance(null, "saga", new SagaStatus.Completed(T0, 100L),
                            "{}", List.of(), T0, T0, null, 1)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void null_name_throws() {
            assertThatThrownBy(() ->
                    new SagaInstance("id", null, new SagaStatus.Completed(T0, 100L),
                            "{}", List.of(), T0, T0, null, 1)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void null_status_throws() {
            assertThatThrownBy(() ->
                    new SagaInstance("id", "saga", null,
                            "{}", List.of(), T0, T0, null, 1)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void null_steps_normalized_to_empty_list() {
            var s = new SagaInstance("id", "saga", new SagaStatus.Started(T0),
                    "{}", null, T0, T0, null, 1);
            assertThat(s.steps()).isEmpty();
        }

        @Test
        void steps_list_is_immutable() {
            var s = saga(new SagaStatus.Started(T0), List.of(), null);
            assertThatThrownBy(() -> s.steps().add(step("x", new StepStatus.Pending())))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class StatusPredicates {

        @Test
        void isTerminal_true_for_Completed() {
            assertThat(saga(new SagaStatus.Completed(T0, 100L), List.of(), T1).isTerminal()).isTrue();
        }

        @Test
        void isTerminal_false_for_Executing() {
            assertThat(saga(new SagaStatus.Executing("step-1"), List.of(), null).isTerminal()).isFalse();
        }

        @Test
        void isRunning_true_for_Executing() {
            assertThat(saga(new SagaStatus.Executing("step-1"), List.of(), null).isRunning()).isTrue();
        }

        @Test
        void isRunning_false_for_Completed() {
            assertThat(saga(new SagaStatus.Completed(T0, 100L), List.of(), T1).isRunning()).isFalse();
        }

        @Test
        void isCompensated_true_for_Compensated() {
            assertThat(saga(new SagaStatus.Compensated("step-1"), List.of(), T1).isCompensated()).isTrue();
        }

        @Test
        void isCompensated_false_for_Executing() {
            assertThat(saga(new SagaStatus.Executing("step-1"), List.of(), null).isCompensated()).isFalse();
        }
    }

    @Nested
    class GetStep {

        @Test
        void finds_step_by_name() {
            var steps = List.of(
                    step("reserve-inventory", new StepStatus.Completed(T0, 100L, null)),
                    step("charge-payment",    new StepStatus.Pending())
            );
            var s = saga(new SagaStatus.Executing("charge-payment"), steps, null);

            assertThat(s.getStep("reserve-inventory").name()).isEqualTo("reserve-inventory");
        }

        @Test
        void throws_for_unknown_step_name() {
            var s = saga(new SagaStatus.Completed(T0, 100L), List.of(), T1);

            assertThatThrownBy(() -> s.getStep("nonexistent"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nonexistent");
        }
    }

    @Nested
    class Duration {

        @Test
        void durationMs_zero_while_running() {
            assertThat(saga(new SagaStatus.Executing("step-1"), List.of(), null).durationMs()).isZero();
        }

        @Test
        void durationMs_calculated_from_createdAt_to_completedAt() {
            var created   = Instant.parse("2026-01-01T10:00:00Z");
            var completed = Instant.parse("2026-01-01T10:00:02Z");
            var s = new SagaInstance("id", "saga", new SagaStatus.Completed(completed, 2000L),
                    "{}", List.of(), created, completed, completed, 1);

            assertThat(s.durationMs()).isEqualTo(2000L);
        }
    }
}
