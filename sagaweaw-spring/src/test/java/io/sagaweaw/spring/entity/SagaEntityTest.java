package io.sagaweaw.spring.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SagaEntityTest {

    @Nested
    class Create {

        @Test
        void setsNameAndInitialStatus() {
            SagaEntity entity = SagaEntity.create("order-processing", "{}", null);

            assertThat(entity.getName()).isEqualTo("order-processing");
            assertThat(entity.getStatus()).isEqualTo("STARTED");
        }

        @Test
        void setsContextJson() {
            SagaEntity entity = SagaEntity.create("x", "{\"orderId\":\"1\"}", null);

            assertThat(entity.getContextJson()).isEqualTo("{\"orderId\":\"1\"}");
        }

        @Test
        void defaultsContextJsonWhenNull() {
            SagaEntity entity = SagaEntity.create("x", null, null);

            assertThat(entity.getContextJson()).isEqualTo("{}");
        }

        @Test
        void setsIdempotencyKey() {
            SagaEntity entity = SagaEntity.create("x", "{}", "order-42");

            assertThat(entity.getIdempotencyKey()).isEqualTo("order-42");
        }

        @Test
        void setsTimestamps() {
            SagaEntity entity = SagaEntity.create("x", "{}", null);

            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        void completedAtIsNullInitially() {
            SagaEntity entity = SagaEntity.create("x", "{}", null);

            assertThat(entity.getCompletedAt()).isNull();
        }

        @Test
        void stepsListIsEmptyInitially() {
            SagaEntity entity = SagaEntity.create("x", "{}", null);

            assertThat(entity.getSteps()).isEmpty();
        }
    }

    @Nested
    class MarkCompleted {

        @Test
        void setsCompletedAt() {
            SagaEntity entity = SagaEntity.create("x", "{}", null);
            entity.markCompleted();

            assertThat(entity.getCompletedAt()).isNotNull();
        }

        @Test
        void updatesUpdatedAt() throws InterruptedException {
            SagaEntity entity = SagaEntity.create("x", "{}", null);
            var before = entity.getUpdatedAt();
            Thread.sleep(1);
            entity.markCompleted();

            assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    class MarkUpdated {

        @Test
        void updatesUpdatedAt() throws InterruptedException {
            SagaEntity entity = SagaEntity.create("x", "{}", null);
            var before = entity.getUpdatedAt();
            Thread.sleep(1);
            entity.markUpdated();

            assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(before);
        }
    }
}
