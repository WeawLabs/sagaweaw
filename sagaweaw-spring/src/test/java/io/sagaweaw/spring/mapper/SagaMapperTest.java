package io.sagaweaw.spring.mapper;

import tools.jackson.databind.ObjectMapper;
import io.sagaweaw.core.SagaInstance;
import io.sagaweaw.core.SagaStatus;
import io.sagaweaw.core.StepInstance;
import io.sagaweaw.core.StepStatus;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.entity.SagaStepEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SagaMapperTest {

    private SagaMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SagaMapper(new ObjectMapper());
    }

    @Nested
    class ToInstance {

        @Test
        void maps_saga_entity_fields() {
            SagaEntity entity = mock(SagaEntity.class);
            when(entity.getId()).thenReturn("saga-1");
            when(entity.getName()).thenReturn("order-processing");
            when(entity.getStatus()).thenReturn("COMPLETED");
            when(entity.getContextJson()).thenReturn("{\"orderId\":\"42\"}");
            when(entity.getCreatedAt()).thenReturn(Instant.parse("2024-01-01T10:00:00Z"));
            when(entity.getUpdatedAt()).thenReturn(Instant.parse("2024-01-01T10:05:00Z"));
            when(entity.getCompletedAt()).thenReturn(Instant.parse("2024-01-01T10:05:00Z"));
            when(entity.getVersion()).thenReturn(3);
            when(entity.getSteps()).thenReturn(java.util.List.of());

            SagaInstance instance = mapper.toInstance(entity);

            assertThat(instance.id()).isEqualTo("saga-1");
            assertThat(instance.name()).isEqualTo("order-processing");
            assertThat(instance.status()).isInstanceOf(SagaStatus.Completed.class);
            assertThat(instance.steps()).isEmpty();
        }

        @Test
        void maps_step_entities_in_order() {
            SagaStepEntity step1 = mockStepEntity("step-1", 1, "COMPLETED");
            SagaStepEntity step2 = mockStepEntity("step-2", 2, "PENDING");

            SagaEntity entity = mock(SagaEntity.class);
            when(entity.getId()).thenReturn("saga-1");
            when(entity.getName()).thenReturn("test");
            when(entity.getStatus()).thenReturn("EXECUTING");
            when(entity.getContextJson()).thenReturn("{}");
            when(entity.getCreatedAt()).thenReturn(Instant.now());
            when(entity.getUpdatedAt()).thenReturn(Instant.now());
            when(entity.getCompletedAt()).thenReturn(null);
            when(entity.getVersion()).thenReturn(1);
            when(entity.getSteps()).thenReturn(java.util.List.of(step1, step2));

            SagaInstance instance = mapper.toInstance(entity);

            assertThat(instance.steps()).hasSize(2);
            assertThat(instance.steps().get(0).name()).isEqualTo("step-1");
            assertThat(instance.steps().get(1).name()).isEqualTo("step-2");
        }
    }

    @Nested
    class ToStepInstance {

        @Test
        void maps_completed_step_status() {
            SagaStepEntity entity = mockStepEntity("reserve-inventory", 1, "COMPLETED");

            StepInstance step = mapper.toStepInstance(entity);

            assertThat(step.name()).isEqualTo("reserve-inventory");
            assertThat(step.status()).isInstanceOf(StepStatus.Completed.class);
            assertThat(step.attempt()).isEqualTo(1);
        }

        @Test
        void maps_failed_step_status() {
            SagaStepEntity entity = mockStepEntity("charge-payment", 2, "FAILED");
            when(entity.getLastError()).thenReturn("connection timeout");

            StepInstance step = mapper.toStepInstance(entity);

            assertThat(step.status()).isInstanceOf(StepStatus.Failed.class);
            assertThat(step.lastError()).isEqualTo("connection timeout");
        }

        @Test
        void uses_zero_duration_when_entity_duration_is_null() {
            SagaStepEntity entity = mockStepEntity("step-x", 1, "PENDING");
            when(entity.getDurationMs()).thenReturn(null);

            StepInstance step = mapper.toStepInstance(entity);

            assertThat(step.durationMs()).isZero();
        }
    }

    @Nested
    class JsonRoundtrip {

        @Test
        void toJson_then_fromJson_is_identity_for_simple_object() {
            record Payload(String key, int value) {}
            Payload original = new Payload("orderId", 42);

            String json = mapper.toJson(original);
            Payload deserialized = mapper.fromJson(json, Payload.class);

            assertThat(deserialized.key()).isEqualTo("orderId");
            assertThat(deserialized.value()).isEqualTo(42);
        }

        @Test
        void toJson_returns_null_for_null_input() {
            assertThat(mapper.toJson(null)).isNull();
        }

        @Test
        void fromJson_returns_null_for_blank_input() {
            assertThat(mapper.fromJson("  ", String.class)).isNull();
        }
    }

    // ---- helpers ----

    private SagaStepEntity mockStepEntity(String name, int order, String status) {
        SagaStepEntity entity = mock(SagaStepEntity.class);
        when(entity.getStepName()).thenReturn(name);
        when(entity.getStepOrder()).thenReturn(order);
        when(entity.getStatus()).thenReturn(status);
        when(entity.getAttempt()).thenReturn(1);
        when(entity.getInputPayload()).thenReturn(null);
        when(entity.getOutputPayload()).thenReturn(null);
        when(entity.getMaxAttempts()).thenReturn(3);
        when(entity.getLastError()).thenReturn(null);
        when(entity.getErrorTrace()).thenReturn(null);
        when(entity.getNextRetryAt()).thenReturn(null);
        when(entity.getExecutedAt()).thenReturn(null);
        when(entity.getCompletedAt()).thenReturn(null);
        when(entity.getDurationMs()).thenReturn(100L);
        return entity;
    }
}
