package io.sagaweaw.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepOutputTest {

    @Nested
    class Empty {

        @Test
        void empty_constant_is_empty() {
            assertThat(StepOutput.EMPTY.isEmpty()).isTrue();
        }

        @Test
        void empty_constant_has_no_values() {
            assertThat(StepOutput.EMPTY.values()).isEmpty();
        }
    }

    @Nested
    class SingleValue {

        @Test
        void of_creates_output_with_one_entry() {
            var output = StepOutput.of("chargeId", "ch_123");
            assertThat(output.isEmpty()).isFalse();
            assertThat(output.require("chargeId", String.class)).isEqualTo("ch_123");
        }
    }

    @Nested
    class Require {

        @Test
        void returns_value_cast_to_requested_type() {
            var output = StepOutput.of("amount", 150);
            assertThat(output.require("amount", Integer.class)).isEqualTo(150);
        }

        @Test
        void throws_StepOutputMissingKeyException_when_key_is_absent() {
            var output = StepOutput.of("chargeId", "ch_123");
            assertThatThrownBy(() -> output.require("nonExistent", String.class))
                    .isInstanceOf(StepOutput.StepOutputMissingKeyException.class)
                    .hasMessageContaining("nonExistent")
                    .hasMessageContaining("chargeId"); // available keys listed in message
        }

        @Test
        void throws_ClassCastException_when_type_is_wrong() {
            var output = StepOutput.of("chargeId", "ch_123");
            assertThatThrownBy(() -> output.require("chargeId", Integer.class))
                    .isInstanceOf(ClassCastException.class);
        }
    }

    @Nested
    class Get {

        @Test
        void returns_optional_with_value_when_key_exists() {
            var output = StepOutput.of("chargeId", "ch_123");
            assertThat(output.get("chargeId", String.class)).contains("ch_123");
        }

        @Test
        void returns_empty_optional_when_key_is_absent() {
            assertThat(StepOutput.EMPTY.get("chargeId", String.class)).isEmpty();
        }
    }

    @Nested
    class BuilderTest {

        @Test
        void builder_produces_output_with_all_entries() {
            var output = StepOutput.builder()
                    .put("chargeId", "ch_123")
                    .put("currency", "BRL")
                    .build();

            assertThat(output.require("chargeId", String.class)).isEqualTo("ch_123");
            assertThat(output.require("currency", String.class)).isEqualTo("BRL");
        }

        @Test
        void builder_result_is_immutable() {
            var output = StepOutput.builder().put("key", "value").build();
            assertThatThrownBy(() -> output.values().put("extra", "boom"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Immutability {

        @Test
        void mutating_source_map_after_construction_does_not_affect_output() {
            var source = new HashMap<String, Object>();
            source.put("chargeId", "ch_123");

            var output = new StepOutput(source);
            source.put("chargeId", "mutated");

            // compact constructor copied the map — original value preserved
            assertThat(output.require("chargeId", String.class)).isEqualTo("ch_123");
        }

        @Test
        void values_map_is_unmodifiable() {
            var output = StepOutput.of("key", "value");
            assertThatThrownBy(() -> output.values().put("extra", "boom"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
