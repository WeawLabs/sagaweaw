package io.sagaweaw.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    @Test
    void null_value_throws() {
        assertThatThrownBy(() -> new IdempotencyKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blank_value_throws() {
        assertThatThrownBy(() -> new IdempotencyKey("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_factory_produces_equivalent_instance() {
        assertThat(IdempotencyKey.of("order-42"))
                .isEqualTo(new IdempotencyKey("order-42"));
    }

    @Test
    void value_is_accessible() {
        assertThat(new IdempotencyKey("pix-99").value()).isEqualTo("pix-99");
    }

    @Test
    void toString_returns_raw_value() {
        assertThat(new IdempotencyKey("pix-99").toString()).isEqualTo("pix-99");
    }

    @Test
    void equality_is_value_based() {
        var a = IdempotencyKey.of("order-1");
        var b = IdempotencyKey.of("order-1");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void different_values_are_not_equal() {
        assertThat(IdempotencyKey.of("order-1"))
                .isNotEqualTo(IdempotencyKey.of("order-2"));
    }
}
