package io.sagaweaw.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SagaContextTest {

    record MinimalContext(String orderId) implements SagaContext {}

    record FullContext(String orderId) implements SagaContext {
        @Override
        public Optional<String> businessKey() {
            return Optional.of("order #" + orderId);
        }

        @Override
        public Map<String, String> metadata() {
            return Map.of("tenant", "fintech-abc", "channel", "mobile-app");
        }
    }

    @Test
    void default_businessKey_is_empty() {
        assertThat(new MinimalContext("ord-1").businessKey()).isEmpty();
    }

    @Test
    void default_metadata_is_empty_map() {
        assertThat(new MinimalContext("ord-1").metadata()).isEmpty();
    }

    @Test
    void implementation_can_override_businessKey() {
        var ctx = new FullContext("48291");
        assertThat(ctx.businessKey()).contains("order #48291");
    }

    @Test
    void implementation_can_override_metadata() {
        var ctx = new FullContext("48291");
        assertThat(ctx.metadata())
                .containsEntry("tenant", "fintech-abc")
                .containsEntry("channel", "mobile-app");
    }
}
