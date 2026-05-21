package io.sagaweaw.spring.engine;

import io.sagaweaw.core.SagaBuilder;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.core.StepOutput;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.entity.SagaEventEntity;
import io.sagaweaw.spring.entity.SagaStepEntity;
import io.sagaweaw.spring.event.StepCompensatedEvent;
import io.sagaweaw.spring.repository.SagaEventRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensationExecutorTest {

    private record Ctx(String id) implements SagaContext {}

    @Mock SagaStepRepository      stepRepository;
    @Mock SagaEventRepository     eventRepository;
    @Mock StepExecutor            stepExecutor;
    @Mock ApplicationEventPublisher publisher;

    private CompensationExecutor executor;
    private SagaEntity           saga;

    @BeforeEach
    void setUp() {
        executor = new CompensationExecutor(stepRepository, eventRepository, stepExecutor, publisher);
        saga     = mock(SagaEntity.class);
        when(saga.getId()).thenReturn("saga-99");
        when(saga.getName()).thenReturn("order-processing");
    }

    @Nested
    class HappyPath {

        @Test
        void compensates_only_steps_with_a_compensator() {
            SagaStepEntity entity = stepEntityNamed("reserve-inventory");
            when(stepRepository.findCompensableSteps("saga-99")).thenReturn(List.of(entity));

            SagaStep<Ctx> compensableStep = compensableStep("reserve-inventory");
            SagaFlow<Ctx> flow = flowWithSteps(List.of(compensableStep));

            executor.compensate(saga, flow, new Ctx("x"));

            verify(stepExecutor).compensate(any(), any(), any(), any());
        }

        @Test
        void publishes_StepCompensatedEvent_per_compensated_step() {
            SagaStepEntity entity = stepEntityNamed("reserve-inventory");
            when(stepRepository.findCompensableSteps("saga-99")).thenReturn(List.of(entity));

            executor.compensate(saga, flowWithSteps(List.of(compensableStep("reserve-inventory"))), new Ctx("x"));

            ArgumentCaptor<StepCompensatedEvent> captor = ArgumentCaptor.forClass(StepCompensatedEvent.class);
            verify(publisher).publishEvent(captor.capture());
            assertThat(captor.getValue().sagaId()).isEqualTo("saga-99");
            assertThat(captor.getValue().stepName()).isEqualTo("reserve-inventory");
        }

        @Test
        void saves_STEP_COMPENSATED_event_entity_per_step() {
            SagaStepEntity entity = stepEntityNamed("reserve-inventory");
            when(stepRepository.findCompensableSteps("saga-99")).thenReturn(List.of(entity));

            executor.compensate(saga, flowWithSteps(List.of(compensableStep("reserve-inventory"))), new Ctx("x"));

            // 2 saves: COMPENSATION_STARTED + STEP_COMPENSATED
            verify(eventRepository, times(2)).save(any());
        }

        @Test
        void saves_COMPENSATION_STARTED_event_first() {
            when(stepRepository.findCompensableSteps("saga-99")).thenReturn(List.of());
            SagaFlow<Ctx> flow = flowWithSteps(List.of());

            executor.compensate(saga, flow, new Ctx("x"));

            verify(eventRepository).save(any());
        }

        @Test
        void compensates_multiple_steps() {
            SagaStepEntity e1 = stepEntityNamed("step-a");
            SagaStepEntity e2 = stepEntityNamed("step-b");
            when(stepRepository.findCompensableSteps("saga-99")).thenReturn(List.of(e1, e2));

            SagaStep<Ctx> s1 = compensableStep("step-a");
            SagaStep<Ctx> s2 = compensableStep("step-b");
            SagaFlow<Ctx> flow = flowWithSteps(List.of(s1, s2));

            executor.compensate(saga, flow, new Ctx("x"));

            verify(stepExecutor, times(2)).compensate(any(), any(), any(), any());
            verify(publisher, times(2)).publishEvent(any(StepCompensatedEvent.class));
        }
    }

    @Nested
    class PivotAndRetriableSkip {

        @Test
        void steps_with_no_compensator_are_silently_skipped() {
            SagaStepEntity pivotEntity = stepEntityNamed("transmit-to-bacen");
            when(stepRepository.findCompensableSteps("saga-99")).thenReturn(List.of(pivotEntity));

            // flow.compensableStepsInReverseOrder() returns empty — no compensable steps
            SagaFlow<Ctx> flow = flowWithSteps(List.of());

            executor.compensate(saga, flow, new Ctx("x"));

            verify(stepExecutor, never()).compensate(any(), any(), any(), any());
            verify(publisher, never()).publishEvent(any(StepCompensatedEvent.class));
        }
    }

    @Nested
    class OnFailure {

        @Test
        void rethrows_StepCompensationException() {
            SagaStepEntity entity = stepEntityNamed("reserve-inventory");
            when(stepRepository.findCompensableSteps("saga-99")).thenReturn(List.of(entity));

            doThrow(new StepExecutor.StepCompensationException("reserve-inventory", new RuntimeException("tx failed")))
                    .when(stepExecutor).compensate(any(), any(), any(), any());

            assertThatThrownBy(() ->
                    executor.compensate(saga, flowWithSteps(List.of(compensableStep("reserve-inventory"))), new Ctx("x")))
                    .isInstanceOf(StepExecutor.StepCompensationException.class);
        }

        @Test
        void does_not_publish_event_when_compensation_throws() {
            SagaStepEntity entity = stepEntityNamed("reserve-inventory");
            when(stepRepository.findCompensableSteps("saga-99")).thenReturn(List.of(entity));

            doThrow(new StepExecutor.StepCompensationException("reserve-inventory", new RuntimeException()))
                    .when(stepExecutor).compensate(any(), any(), any(), any());

            assertThatThrownBy(() ->
                    executor.compensate(saga, flowWithSteps(List.of(compensableStep("reserve-inventory"))), new Ctx("x")));

            verify(publisher, never()).publishEvent(any(StepCompensatedEvent.class));
        }
    }

    // ---- helpers ----

    private SagaStepEntity stepEntityNamed(String name) {
        SagaStepEntity entity = mock(SagaStepEntity.class);
        when(entity.getStepName()).thenReturn(name);
        return entity;
    }

    private SagaStep<Ctx> compensableStep(String name) {
        return SagaBuilder.<Ctx>forSaga("test")
                .step(name)
                .invoke(ctx -> StepOutput.EMPTY)
                .compensate(ctx -> {})
                .build()
                .steps()
                .get(0);
    }

    private SagaFlow<Ctx> flowWithSteps(List<SagaStep<Ctx>> compensableSteps) {
        SagaFlow<Ctx> flow = mock(SagaFlow.class);
        when(flow.compensableStepsInReverseOrder()).thenReturn(compensableSteps);
        return flow;
    }
}
