package io.sagaweaw.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SagaStatusTest {

    // ---- fixture helpers — one canonical instance of each state ----

    static SagaStatus.Started started() {
        return new SagaStatus.Started();
    }

    static SagaStatus.Executing executing() {
        return new SagaStatus.Executing("reserve-inventory");
    }

    static SagaStatus.Completed completed() {
        return new SagaStatus.Completed(Instant.now(), 350L);
    }

    static SagaStatus.Compensating compensating() {
        return new SagaStatus.Compensating("charge-payment", "connection timeout");
    }

    static SagaStatus.Compensated compensated() {
        return new SagaStatus.Compensated("charge-payment");
    }

    static SagaStatus.Failed failed() {
        return new SagaStatus.Failed("compensation also failed — manual intervention required");
    }

    // ---- parameterized test sources ----

    static Stream<SagaStatus> terminalStatuses() {
        return Stream.of(completed(), compensated(), failed());
    }

    static Stream<SagaStatus> nonTerminalStatuses() {
        return Stream.of(started(), executing(), compensating());
    }

    static Stream<SagaStatus> allStatusesExceptCompensating() {
        return Stream.of(started(), executing(), completed(), compensated(), failed());
    }

    static Stream<SagaStatus> allStatusesExceptCompleted() {
        return Stream.of(started(), executing(), compensating(), compensated(), failed());
    }

    static Stream<Arguments> persistenceNameMappings() {
        return Stream.of(
            Arguments.of(started(),      "STARTED"),
            Arguments.of(executing(),    "EXECUTING"),
            Arguments.of(completed(),    "COMPLETED"),
            Arguments.of(compensating(), "COMPENSATING"),
            Arguments.of(compensated(),  "COMPENSATED"),
            Arguments.of(failed(),       "FAILED")
        );
    }

    // ================================================================

    @Nested
    class IsTerminal {

        @ParameterizedTest(name = "{0} → isTerminal = true")
        @MethodSource("io.sagaweaw.core.SagaStatusTest#terminalStatuses")
        void terminal_states_return_true(SagaStatus status) {
            assertThat(status.isTerminal()).isTrue();
        }

        @ParameterizedTest(name = "{0} → isTerminal = false")
        @MethodSource("io.sagaweaw.core.SagaStatusTest#nonTerminalStatuses")
        void non_terminal_states_return_false(SagaStatus status) {
            assertThat(status.isTerminal()).isFalse();
        }
    }

    @Nested
    class IsCompensating {

        @Test
        void compensating_returns_true() {
            assertThat(compensating().isCompensating()).isTrue();
        }

        @ParameterizedTest(name = "{0} → isCompensating = false")
        @MethodSource("io.sagaweaw.core.SagaStatusTest#allStatusesExceptCompensating")
        void all_other_states_return_false(SagaStatus status) {
            assertThat(status.isCompensating()).isFalse();
        }
    }

    @Nested
    class IsSuccessful {

        @Test
        void completed_returns_true() {
            assertThat(completed().isSuccessful()).isTrue();
        }

        @ParameterizedTest(name = "{0} → isSuccessful = false")
        @MethodSource("io.sagaweaw.core.SagaStatusTest#allStatusesExceptCompleted")
        void all_other_states_return_false(SagaStatus status) {
            assertThat(status.isSuccessful()).isFalse();
        }
    }

    @Nested
    class PersistenceName {

        @ParameterizedTest(name = "{0} → \"{1}\"")
        @MethodSource("io.sagaweaw.core.SagaStatusTest#persistenceNameMappings")
        void maps_every_state_to_its_db_column_value(SagaStatus status, String expected) {
            assertThat(status.persistenceName()).isEqualTo(expected);
        }
    }

    @Nested
    class ConvenienceConstructors {

        @Test
        void started_sets_timestamp_to_now() {
            var before = Instant.now();
            var status = new SagaStatus.Started();
            assertThat(status.at()).isBetween(before, Instant.now());
        }

        @Test
        void executing_sets_started_at_to_now() {
            var before = Instant.now();
            var status = new SagaStatus.Executing("step");
            assertThat(status.startedAt()).isBetween(before, Instant.now());
        }

        @Test
        void compensating_sets_timestamp_to_now() {
            var before = Instant.now();
            var status = new SagaStatus.Compensating("step", "error");
            assertThat(status.at()).isBetween(before, Instant.now());
        }

        @Test
        void compensated_sets_timestamp_to_now() {
            var before = Instant.now();
            var status = new SagaStatus.Compensated("step");
            assertThat(status.at()).isBetween(before, Instant.now());
        }

        @Test
        void failed_sets_timestamp_to_now() {
            var before = Instant.now();
            var status = new SagaStatus.Failed("reason");
            assertThat(status.at()).isBetween(before, Instant.now());
        }
    }

    @Nested
    class RecordEquality {

        @Test
        void same_values_produce_equal_records() {
            var instant = Instant.parse("2024-01-01T10:00:00Z");
            assertThat(new SagaStatus.Started(instant))
                .isEqualTo(new SagaStatus.Started(instant));
        }

        @Test
        void different_timestamps_produce_different_records() {
            var t1 = Instant.parse("2024-01-01T10:00:00Z");
            var t2 = Instant.parse("2024-01-01T10:00:01Z");
            assertThat(new SagaStatus.Started(t1))
                .isNotEqualTo(new SagaStatus.Started(t2));
        }

        @Test
        void executing_carries_current_step_name() {
            var status = new SagaStatus.Executing("charge-payment");
            assertThat(status.currentStep()).isEqualTo("charge-payment");
        }

        @Test
        void compensating_carries_failed_step_and_error_message() {
            var status = new SagaStatus.Compensating("charge-payment", "timeout");
            assertThat(status.failedStep()).isEqualTo("charge-payment");
            assertThat(status.errorMessage()).isEqualTo("timeout");
        }
    }
}
