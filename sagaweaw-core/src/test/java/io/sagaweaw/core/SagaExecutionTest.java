package io.sagaweaw.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaExecutionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void null_sagaId_throws() {
        assertThatThrownBy(() -> new SagaExecution(null, "order-saga", NOW, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_sagaName_throws() {
        assertThatThrownBy(() -> new SagaExecution("id-1", null, NOW, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_startedAt_throws() {
        assertThatThrownBy(() -> new SagaExecution("id-1", "order-saga", null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fields_are_accessible() {
        var exec = new SagaExecution("uuid-42", "pix-payment", NOW, false);

        assertThat(exec.sagaId()).isEqualTo("uuid-42");
        assertThat(exec.sagaName()).isEqualTo("pix-payment");
        assertThat(exec.startedAt()).isEqualTo(NOW);
        assertThat(exec.idempotent()).isFalse();
    }

    @Test
    void idempotent_flag_is_true_when_execution_already_existed() {
        var exec = new SagaExecution("uuid-42", "pix-payment", NOW, true);
        assertThat(exec.idempotent()).isTrue();
    }

    @Test
    void equality_is_value_based() {
        var a = new SagaExecution("uuid-1", "order-saga", NOW, false);
        var b = new SagaExecution("uuid-1", "order-saga", NOW, false);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
