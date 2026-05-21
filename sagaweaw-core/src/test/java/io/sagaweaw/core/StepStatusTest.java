package io.sagaweaw.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepStatusTest {

    private static final Instant T = Instant.parse("2024-06-01T12:00:00Z");

    // ---- fixture helpers — one canonical instance of each state ----

    static StepStatus.Pending pending() {
        return new StepStatus.Pending();
    }

    static StepStatus.Executing executing() {
        return new StepStatus.Executing(T, 1);
    }

    static StepStatus.Completed completed() {
        return new StepStatus.Completed(T, 120L, "{\"orderId\":42}");
    }

    static StepStatus.Failed failedRetriable() {
        return new StepStatus.Failed(T, 1, "connection timeout", null, false);
    }

    static StepStatus.Failed failedExhausted() {
        return new StepStatus.Failed(T, 3, "connection timeout", null, true);
    }

    static StepStatus.Compensating compensating() {
        return new StepStatus.Compensating(T);
    }

    static StepStatus.Compensated compensated() {
        return new StepStatus.Compensated(T, 80L);
    }

    static StepStatus.Skipped skipped() {
        return new StepStatus.Skipped();
    }

    // ---- parameterized test sources ----

    static Stream<StepStatus> terminalStatuses() {
        return Stream.of(completed(), compensated(), skipped());
    }

    static Stream<StepStatus> nonTerminalStatuses() {
        return Stream.of(pending(), executing(), failedRetriable(), failedExhausted(), compensating());
    }

    static Stream<StepStatus> statusesThatNeedCompensation() {
        return Stream.of(completed());
    }

    static Stream<StepStatus> statusesThatDoNotNeedCompensation() {
        return Stream.of(pending(), executing(), failedRetriable(), failedExhausted(),
                compensating(), compensated(), skipped());
    }

    static Stream<StepStatus> failedStatuses() {
        return Stream.of(failedRetriable(), failedExhausted());
    }

    static Stream<StepStatus> nonFailedStatuses() {
        return Stream.of(pending(), executing(), completed(), compensating(), compensated(), skipped());
    }

    static Stream<StepStatus> nonExhaustedStatuses() {
        return Stream.of(pending(), executing(), completed(), failedRetriable(),
                compensating(), compensated(), skipped());
    }

    static Stream<Arguments> persistenceNameMappings() {
        return Stream.of(
            Arguments.of(pending(),         "PENDING"),
            Arguments.of(executing(),       "EXECUTING"),
            Arguments.of(completed(),       "COMPLETED"),
            Arguments.of(failedRetriable(), "FAILED"),
            Arguments.of(failedExhausted(), "FAILED"),
            Arguments.of(compensating(),    "COMPENSATING"),
            Arguments.of(compensated(),     "COMPENSATED"),
            Arguments.of(skipped(),         "SKIPPED")
        );
    }

    // ================================================================

    @Nested
    class IsTerminal {

        @ParameterizedTest(name = "{0} → isTerminal = true")
        @MethodSource("io.sagaweaw.core.StepStatusTest#terminalStatuses")
        void terminal_states_return_true(StepStatus status) {
            assertThat(status.isTerminal()).isTrue();
        }

        @ParameterizedTest(name = "{0} → isTerminal = false")
        @MethodSource("io.sagaweaw.core.StepStatusTest#nonTerminalStatuses")
        void non_terminal_states_return_false(StepStatus status) {
            assertThat(status.isTerminal()).isFalse();
        }
    }

    @Nested
    class NeedsCompensation {

        @ParameterizedTest(name = "{0} → needsCompensation = true")
        @MethodSource("io.sagaweaw.core.StepStatusTest#statusesThatNeedCompensation")
        void only_completed_needs_compensation(StepStatus status) {
            assertThat(status.needsCompensation()).isTrue();
        }

        @ParameterizedTest(name = "{0} → needsCompensation = false")
        @MethodSource("io.sagaweaw.core.StepStatusTest#statusesThatDoNotNeedCompensation")
        void all_other_states_do_not_need_compensation(StepStatus status) {
            assertThat(status.needsCompensation()).isFalse();
        }

        @Test
        void skipped_step_does_not_need_compensation() {
            // Skipped means the step never ran — nothing to undo
            assertThat(skipped().needsCompensation()).isFalse();
        }
    }

    @Nested
    class IsFailed {

        @ParameterizedTest(name = "{0} → isFailed = true")
        @MethodSource("io.sagaweaw.core.StepStatusTest#failedStatuses")
        void any_failed_instance_returns_true_regardless_of_exhausted_flag(StepStatus status) {
            assertThat(status.isFailed()).isTrue();
        }

        @ParameterizedTest(name = "{0} → isFailed = false")
        @MethodSource("io.sagaweaw.core.StepStatusTest#nonFailedStatuses")
        void non_failed_states_return_false(StepStatus status) {
            assertThat(status.isFailed()).isFalse();
        }
    }

    @Nested
    class IsExhausted {

        @Test
        void exhausted_failed_returns_true() {
            assertThat(failedExhausted().isExhausted()).isTrue();
        }

        @Test
        void retriable_failed_returns_false() {
            // Still has attempts left — engine will schedule a retry, not compensation
            assertThat(failedRetriable().isExhausted()).isFalse();
        }

        @ParameterizedTest(name = "{0} → isExhausted = false")
        @MethodSource("io.sagaweaw.core.StepStatusTest#nonExhaustedStatuses")
        void non_failed_states_are_never_exhausted(StepStatus status) {
            assertThat(status.isExhausted()).isFalse();
        }
    }

    @Nested
    class PersistenceName {

        @ParameterizedTest(name = "{0} → \"{1}\"")
        @MethodSource("io.sagaweaw.core.StepStatusTest#persistenceNameMappings")
        void maps_every_state_to_its_db_column_value(StepStatus status, String expected) {
            assertThat(status.persistenceName()).isEqualTo(expected);
        }

        @Test
        void failed_and_failed_exhausted_share_the_same_persistence_name() {
            // exhausted is runtime state — DB column stores only FAILED in both cases
            assertThat(failedRetriable().persistenceName())
                    .isEqualTo(failedExhausted().persistenceName());
        }
    }

    @Nested
    class RecordEquality {

        @Test
        void same_values_produce_equal_records() {
            assertThat(new StepStatus.Executing(T, 2))
                    .isEqualTo(new StepStatus.Executing(T, 2));
        }

        @Test
        void different_attempt_numbers_produce_different_records() {
            assertThat(new StepStatus.Executing(T, 1))
                    .isNotEqualTo(new StepStatus.Executing(T, 2));
        }

        @Test
        void completed_carries_output_payload() {
            var status = new StepStatus.Completed(T, 120L, "{\"orderId\":42}");
            assertThat(status.output()).isEqualTo("{\"orderId\":42}");
        }

        @Test
        void failed_carries_error_details() {
            var status = new StepStatus.Failed(T, 3, "timeout", "stack trace here", true);
            assertThat(status.errorMessage()).isEqualTo("timeout");
            assertThat(status.errorTrace()).isEqualTo("stack trace here");
            assertThat(status.totalAttempts()).isEqualTo(3);
            assertThat(status.exhausted()).isTrue();
        }
    }

    @Nested
    class FromPersistenceName {

        @Test
        void pending() {
            assertThat(StepStatus.fromPersistenceName("PENDING")).isInstanceOf(StepStatus.Pending.class);
        }

        @Test
        void executing() {
            assertThat(StepStatus.fromPersistenceName("EXECUTING")).isInstanceOf(StepStatus.Executing.class);
        }

        @Test
        void completed() {
            assertThat(StepStatus.fromPersistenceName("COMPLETED")).isInstanceOf(StepStatus.Completed.class);
        }

        @Test
        void failed() {
            assertThat(StepStatus.fromPersistenceName("FAILED")).isInstanceOf(StepStatus.Failed.class);
        }

        @Test
        void compensating() {
            assertThat(StepStatus.fromPersistenceName("COMPENSATING")).isInstanceOf(StepStatus.Compensating.class);
        }

        @Test
        void compensated() {
            assertThat(StepStatus.fromPersistenceName("COMPENSATED")).isInstanceOf(StepStatus.Compensated.class);
        }

        @Test
        void skipped() {
            assertThat(StepStatus.fromPersistenceName("SKIPPED")).isInstanceOf(StepStatus.Skipped.class);
        }

        @Test
        void roundtrip_persistenceName_is_stable() {
            for (String name : new String[]{"PENDING","EXECUTING","COMPLETED","FAILED","COMPENSATING","COMPENSATED","SKIPPED"}) {
                assertThat(StepStatus.fromPersistenceName(name).persistenceName()).isEqualTo(name);
            }
        }

        @Test
        void unknown_name_throws() {
            assertThatThrownBy(() -> StepStatus.fromPersistenceName("BOGUS"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BOGUS");
        }
    }
}
