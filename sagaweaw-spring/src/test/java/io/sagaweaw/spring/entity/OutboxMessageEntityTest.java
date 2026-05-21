package io.sagaweaw.spring.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMessageEntityTest {

    @Nested
    class Create {

        @Test
        void setsAllFields() {
            OutboxMessageEntity msg = OutboxMessageEntity.create(
                    "saga-1", "charge", "payment.charge", "{\"amount\":100}", "{\"key\":\"v\"}");

            assertThat(msg.getSagaId()).isEqualTo("saga-1");
            assertThat(msg.getStepName()).isEqualTo("charge");
            assertThat(msg.getTopic()).isEqualTo("payment.charge");
            assertThat(msg.getPayload()).isEqualTo("{\"amount\":100}");
            assertThat(msg.getHeaders()).isEqualTo("{\"key\":\"v\"}");
        }

        @Test
        void publishedIsFalseInitially() {
            OutboxMessageEntity msg = OutboxMessageEntity.create("s", "s", "t", "{}", null);

            assertThat(msg.isPublished()).isFalse();
            assertThat(msg.getPublishedAt()).isNull();
            assertThat(msg.getPublishAttempts()).isZero();
        }

        @Test
        void setsCreatedAt() {
            OutboxMessageEntity msg = OutboxMessageEntity.create("s", "s", "t", "{}", null);

            assertThat(msg.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    class MarkPublished {

        @Test
        void setsPublishedAndTimestamp() {
            OutboxMessageEntity msg = OutboxMessageEntity.create("s", "s", "t", "{}", null);
            msg.markPublished();

            assertThat(msg.isPublished()).isTrue();
            assertThat(msg.getPublishedAt()).isNotNull();
        }
    }

    @Nested
    class IncrementAttempt {

        @Test
        void incrementsCounter() {
            OutboxMessageEntity msg = OutboxMessageEntity.create("s", "s", "t", "{}", null);
            msg.incrementAttempt();
            msg.incrementAttempt();

            assertThat(msg.getPublishAttempts()).isEqualTo(2);
        }
    }
}
