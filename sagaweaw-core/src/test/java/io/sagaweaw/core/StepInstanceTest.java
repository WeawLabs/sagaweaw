package io.sagaweaw.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepInstanceTest {

    private static final Instant T = Instant.parse("2026-01-01T10:00:00Z");

    private StepInstance stepWith(StepStatus status) {
        return new StepInstance("reserve-inventory", 0, status, 1, 3,
                null, null, null, null, null, T, T, 100L);
    }

    @Test
    void null_name_throws() {
        assertThatThrownBy(() ->
                new StepInstance(null, 0, new StepStatus.Pending(), 0, 3,
                        null, null, null, null, null, null, null, 0)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_status_throws() {
        assertThatThrownBy(() ->
                new StepInstance("step", 0, null, 0, 3,
                        null, null, null, null, null, null, null, 0)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void isCompleted_true_for_Completed() {
        assertThat(stepWith(new StepStatus.Completed(T, 100L, null)).isCompleted()).isTrue();
    }

    @Test
    void isCompleted_false_for_other_states() {
        assertThat(stepWith(new StepStatus.Pending()).isCompleted()).isFalse();
        assertThat(stepWith(new StepStatus.Executing(T, 1)).isCompleted()).isFalse();
    }

    @Test
    void isFailed_true_only_when_exhausted() {
        var exhausted    = new StepStatus.Failed(T, 3, "timeout", "", true);
        var notExhausted = new StepStatus.Failed(T, 1, "timeout", "", false);

        assertThat(stepWith(exhausted).isFailed()).isTrue();
        assertThat(stepWith(notExhausted).isFailed()).isFalse();
    }

    @Test
    void isCompensated_true_for_Compensated() {
        assertThat(stepWith(new StepStatus.Compensated(T, 50L)).isCompensated()).isTrue();
    }

    @Test
    void wasSkipped_true_for_Skipped() {
        assertThat(stepWith(new StepStatus.Skipped()).wasSkipped()).isTrue();
    }

    @Test
    void wasSkipped_false_for_Pending() {
        assertThat(stepWith(new StepStatus.Pending()).wasSkipped()).isFalse();
    }
}
