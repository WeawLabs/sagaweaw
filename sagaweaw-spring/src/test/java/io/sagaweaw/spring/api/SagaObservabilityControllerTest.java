package io.sagaweaw.spring.api;

import io.sagaweaw.core.SagaInstance;
import io.sagaweaw.core.SagaStatus;
import io.sagaweaw.spring.config.SagaProperties;
import io.sagaweaw.spring.engine.SpringSagaEngine;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.DeadLetterRepository;
import io.sagaweaw.spring.repository.OutboxMessageRepository;
import io.sagaweaw.spring.repository.SagaEventRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaObservabilityControllerTest {

    @Mock SagaRepository          sagaRepository;
    @Mock DeadLetterRepository    deadLetterRepository;
    @Mock SagaEventRepository     sagaEventRepository;
    @Mock SagaStepRepository      sagaStepRepository;
    @Mock OutboxMessageRepository outboxMessageRepository;
    @Mock SpringSagaEngine        engine;
    @Mock SagaMapper              mapper;

    private SagaObservabilityController controller;

    private static final SagaProperties DEFAULT_PROPS =
            new SagaProperties(3, 1000L, 5000L, 8484, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        controller = new SagaObservabilityController(
                sagaRepository, deadLetterRepository, sagaEventRepository,
                sagaStepRepository, outboxMessageRepository, engine, mapper, DEFAULT_PROPS, null);
    }

    @Nested
    class List_ {

        @Test
        void without_filters_calls_findAllWithSteps_with_pageable() {
            when(sagaRepository.findAllWithSteps(any())).thenReturn(List.of());

            controller.list(0, 50, null, null, null, null, null, null, null);

            verify(sagaRepository).findAllWithSteps(
                    PageRequest.of(0, 50, Sort.by("createdAt").descending()));
        }

        @Test
        void with_status_filter_uses_pageable() {
            when(sagaRepository.findByStatusOrderByCreatedAtDesc(eq("FAILED"), any())).thenReturn(List.of());

            controller.list(0, 10, "FAILED", null, null, null, null, null, null);

            verify(sagaRepository).findByStatusOrderByCreatedAtDesc(
                    eq("FAILED"),
                    eq(PageRequest.of(0, 10, Sort.by("createdAt").descending())));
        }

        @Test
        void with_name_filter_uses_pageable() {
            when(sagaRepository.findByNameContaining(eq("order-processing"), any())).thenReturn(List.of());

            controller.list(2, 25, null, "order-processing", null, null, null, null, null);

            verify(sagaRepository).findByNameContaining(
                    eq("order-processing"),
                    eq(PageRequest.of(2, 25, Sort.by("createdAt").descending())));
        }

        @Test
        void with_idempotency_key_returns_single_result() {
            var entity   = mock(io.sagaweaw.spring.entity.SagaEntity.class);
            var instance = sagaInstance("saga-idem");
            when(sagaRepository.findByIdempotencyKey("order-123")).thenReturn(Optional.of(entity));
            when(mapper.toInstance(entity)).thenReturn(instance);

            List<SagaInstance> result = controller.list(0, 50, null, null, null, null, "order-123", null, null);

            assertThat(result).containsExactly(instance);
        }

        @Test
        void results_are_mapped_to_instances() {
            var entity   = mock(io.sagaweaw.spring.entity.SagaEntity.class);
            var instance = sagaInstance("saga-1");
            when(sagaRepository.findAllWithSteps(any())).thenReturn(List.of(entity));
            when(mapper.toInstance(entity)).thenReturn(instance);

            List<SagaInstance> result = controller.list(0, 50, null, null, null, null, null, null, null);

            assertThat(result).containsExactly(instance);
        }
    }

    @Nested
    class Get {

        @Test
        void returns_200_when_saga_found() {
            var instance = sagaInstance("saga-42");
            when(engine.findById("saga-42")).thenReturn(Optional.of(instance));

            ResponseEntity<SagaInstance> response = controller.get("saga-42");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(instance);
        }

        @Test
        void returns_404_when_saga_not_found() {
            when(engine.findById("missing")).thenReturn(Optional.empty());

            ResponseEntity<SagaInstance> response = controller.get("missing");

            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Nested
    class Metrics {

        @Test
        void returns_counts_and_success_rate() {
            when(sagaRepository.count()).thenReturn(111L);
            when(sagaRepository.countByStatus("STARTED")).thenReturn(5L);
            when(sagaRepository.countByStatus("EXECUTING")).thenReturn(2L);
            when(sagaRepository.countByStatus("COMPLETED")).thenReturn(100L);
            when(sagaRepository.countByStatus("COMPENSATED")).thenReturn(3L);
            when(sagaRepository.countByStatus("FAILED")).thenReturn(1L);
            when(sagaRepository.countGroupedByName()).thenReturn(List.of());
            when(deadLetterRepository.countByReprocessedFalse()).thenReturn(0L);
            when(outboxMessageRepository.countByPublished(false)).thenReturn(0L);

            SagaMetrics metrics = controller.metrics();

            assertThat(metrics.started()).isEqualTo(5L);
            assertThat(metrics.completed()).isEqualTo(100L);
            assertThat(metrics.failed()).isEqualTo(1L);
            assertThat(metrics.deadLetters()).isEqualTo(0L);
            // successRate = 100 / (100+1+3) * 100 = 96.2%
            assertThat(metrics.successRate()).isEqualTo(96.2);
            assertThat(metrics.byName()).isEmpty();
        }
    }

    private SagaInstance sagaInstance(String id) {
        return new SagaInstance(id, "test-saga",
                new SagaStatus.Completed(Instant.now(), 0L),
                "{}", List.of(),
                Instant.now(), Instant.now(), Instant.now(), 1);
    }
}
