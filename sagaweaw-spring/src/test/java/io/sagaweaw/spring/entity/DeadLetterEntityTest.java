package io.sagaweaw.spring.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterEntityTest {

    @Nested
    class Create {

        @Test
        void setsAllFields() {
            DeadLetterEntity dl = DeadLetterEntity.create(
                    "saga-1", "charge", "timeout", "stack...", "{\"orderId\":\"1\"}");

            assertThat(dl.getSagaId()).isEqualTo("saga-1");
            assertThat(dl.getStepName()).isEqualTo("charge");
            assertThat(dl.getErrorMessage()).isEqualTo("timeout");
            assertThat(dl.getErrorTrace()).isEqualTo("stack...");
            assertThat(dl.getContextSnapshot()).isEqualTo("{\"orderId\":\"1\"}");
        }

        @Test
        void reprocessedIsFalseInitially() {
            DeadLetterEntity dl = DeadLetterEntity.create("s", "step", null, null, null);

            assertThat(dl.isReprocessed()).isFalse();
            assertThat(dl.getReprocessedAt()).isNull();
            assertThat(dl.getReprocessedBy()).isNull();
        }

        @Test
        void setsCreatedAt() {
            DeadLetterEntity dl = DeadLetterEntity.create("s", "step", null, null, null);

            assertThat(dl.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    class MarkReprocessed {

        @Test
        void setsReprocessedFields() {
            DeadLetterEntity dl = DeadLetterEntity.create("s", "step", null, null, null);
            dl.markReprocessed("admin@example.com");

            assertThat(dl.isReprocessed()).isTrue();
            assertThat(dl.getReprocessedAt()).isNotNull();
            assertThat(dl.getReprocessedBy()).isEqualTo("admin@example.com");
        }
    }
}
