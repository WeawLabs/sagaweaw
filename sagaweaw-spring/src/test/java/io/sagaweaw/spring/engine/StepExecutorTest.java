package io.sagaweaw.spring.engine;

import io.sagaweaw.core.SagaBuilder;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.core.SagaStepInvoker;
import io.sagaweaw.core.StepOutput;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.entity.SagaStepEntity;
import io.sagaweaw.spring.interceptor.SagaStepInterceptor;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.OutboxMessageRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StepExecutorTest {

    private record Ctx(String id) implements SagaContext {}

    @Mock SagaStepRepository      stepRepository;
    @Mock OutboxMessageRepository outboxRepository;
    @Mock SagaMapper              mapper;

    private StepExecutor executor;
    private SagaEntity   saga;
    private SagaStepEntity stepEntity;

    @BeforeEach
    void setUp() {
        executor   = new StepExecutor(stepRepository, outboxRepository, mapper, List.of());
        saga       = mock(SagaEntity.class);
        stepEntity = mock(SagaStepEntity.class);

        when(saga.getId()).thenReturn("saga-42");
        when(saga.getName()).thenReturn("order-processing");
        when(stepEntity.getStepName()).thenReturn("charge-payment");
        when(stepEntity.getAttempt()).thenReturn(1);
        when(mapper.toJson(any())).thenReturn("{}");
        when(mapper.fromJson(anyString(), any())).thenReturn(StepOutput.EMPTY);
    }

    // ---- factories for real SagaStep instances ----

    private SagaStep<Ctx> successStep() {
        return SagaBuilder.<Ctx>forSaga("test")
                .step("charge-payment").invoke(ctx -> StepOutput.EMPTY)
                .build().steps().get(0);
    }

    private SagaStep<Ctx> stepReturning(StepOutput output) {
        return SagaBuilder.<Ctx>forSaga("test")
                .step("charge-payment").invoke(ctx -> output)
                .build().steps().get(0);
    }

    private SagaStep<Ctx> failingStep(RuntimeException cause) {
        return SagaBuilder.<Ctx>forSaga("test")
                .step("charge-payment")
                .invoke((SagaStepInvoker<Ctx>) ctx -> { throw cause; })
                .build().steps().get(0);
    }

    private SagaStep<Ctx> compensableStep(RuntimeException compensationError) {
        return SagaBuilder.<Ctx>forSaga("test")
                .step("charge-payment")
                .invoke(ctx -> StepOutput.EMPTY)
                .compensate(ctx -> { throw compensationError; })
                .build().steps().get(0);
    }

    @Nested
    class Execute {

        @Test
        void marks_executing_before_invoking_step() throws Exception {
            executor.execute(saga, stepEntity, successStep(), new Ctx("1"));

            InOrder order = inOrder(stepEntity, stepRepository);
            order.verify(stepEntity).markExecuting(any());
            order.verify(stepRepository).save(stepEntity);
        }

        @Test
        void marks_completed_after_successful_invocation() throws Exception {
            executor.execute(saga, stepEntity, successStep(), new Ctx("1"));

            verify(stepEntity).markCompleted(any());
        }

        @Test
        void writes_outbox_message_on_success() throws Exception {
            executor.execute(saga, stepEntity, successStep(), new Ctx("1"));

            verify(outboxRepository).save(any());
        }

        @Test
        void returns_step_output() throws Exception {
            StepOutput expected = StepOutput.of("orderId", "99");

            StepOutput result = executor.execute(saga, stepEntity, stepReturning(expected), new Ctx("1"));

            assertThat(result).isEqualTo(expected);
        }

        @Test
        void wraps_step_exception_in_StepExecutionException() {
            RuntimeException cause = new RuntimeException("payment service down");

            assertThatThrownBy(() -> executor.execute(saga, stepEntity, failingStep(cause), new Ctx("1")))
                    .isInstanceOf(StepExecutor.StepExecutionException.class)
                    .hasCause(cause)
                    .hasMessageContaining("charge-payment");
        }

        @Test
        void does_not_write_outbox_when_step_throws() {
            assertThatThrownBy(() ->
                    executor.execute(saga, stepEntity, failingStep(new RuntimeException("boom")), new Ctx("1")));

            verify(outboxRepository, never()).save(any());
        }

        @Test
        void sets_mdc_saga_id_during_execution() throws Exception {
            String[] captured = {null};
            SagaStep<Ctx> step = SagaBuilder.<Ctx>forSaga("test")
                    .step("charge-payment")
                    .invoke(ctx -> {
                        captured[0] = MDC.get("sagaId");
                        return StepOutput.EMPTY;
                    })
                    .build().steps().get(0);

            executor.execute(saga, stepEntity, step, new Ctx("1"));

            assertThat(captured[0]).isEqualTo("saga-42");
        }

        @Test
        void clears_mdc_saga_id_after_success() throws Exception {
            executor.execute(saga, stepEntity, successStep(), new Ctx("1"));

            assertThat(MDC.get("sagaId")).isNull();
        }

        @Test
        void clears_mdc_saga_id_even_on_exception() {
            assertThatThrownBy(() ->
                    executor.execute(saga, stepEntity, failingStep(new RuntimeException()), new Ctx("1")));

            assertThat(MDC.get("sagaId")).isNull();
        }

        @Test
        void sets_full_mdc_enrichment_during_execution() throws Exception {
            String[] captured = {null, null, null, null};
            SagaStep<Ctx> step = SagaBuilder.<Ctx>forSaga("test")
                    .step("charge-payment")
                    .invoke(ctx -> {
                        captured[0] = MDC.get("sagaId");
                        captured[1] = MDC.get("sagaName");
                        captured[2] = MDC.get("stepName");
                        captured[3] = MDC.get("attempt");
                        return StepOutput.EMPTY;
                    })
                    .build().steps().get(0);

            executor.execute(saga, stepEntity, step, new Ctx("1"));

            assertThat(captured[0]).isEqualTo("saga-42");
            assertThat(captured[1]).isEqualTo("order-processing");
            assertThat(captured[2]).isEqualTo("charge-payment");
            assertThat(captured[3]).isEqualTo("1");
        }

        @Test
        void clears_mdc_enrichment_after_success() throws Exception {
            executor.execute(saga, stepEntity, successStep(), new Ctx("1"));

            assertThat(MDC.get("sagaName")).isNull();
            assertThat(MDC.get("stepName")).isNull();
            assertThat(MDC.get("attempt")).isNull();
        }

        @Test
        void clears_mdc_enrichment_even_on_exception() {
            assertThatThrownBy(() ->
                    executor.execute(saga, stepEntity, failingStep(new RuntimeException()), new Ctx("1")));

            assertThat(MDC.get("sagaName")).isNull();
            assertThat(MDC.get("stepName")).isNull();
            assertThat(MDC.get("attempt")).isNull();
        }
    }

    @Nested
    class Compensate {

        @Test
        void marks_compensating_before_running_compensator() throws Exception {
            SagaStep<Ctx> step = SagaBuilder.<Ctx>forSaga("test")
                    .step("charge-payment")
                    .invoke(ctx -> StepOutput.EMPTY)
                    .compensate(ctx -> {})
                    .build().steps().get(0);
            when(stepEntity.getOutputPayload()).thenReturn(null);

            executor.compensate(saga, stepEntity, step, new Ctx("1"));

            InOrder order = inOrder(stepEntity, stepRepository);
            order.verify(stepEntity).markCompensating();
            order.verify(stepRepository).save(stepEntity);
        }

        @Test
        void marks_compensated_after_successful_compensation() throws Exception {
            SagaStep<Ctx> step = SagaBuilder.<Ctx>forSaga("test")
                    .step("charge-payment")
                    .invoke(ctx -> StepOutput.EMPTY)
                    .compensate(ctx -> {})
                    .build().steps().get(0);
            when(stepEntity.getOutputPayload()).thenReturn(null);

            executor.compensate(saga, stepEntity, step, new Ctx("1"));

            verify(stepEntity).markCompensated();
        }

        @Test
        void wraps_compensator_exception_in_StepCompensationException() {
            RuntimeException cause = new RuntimeException("rollback failed");
            when(stepEntity.getOutputPayload()).thenReturn(null);

            assertThatThrownBy(() ->
                    executor.compensate(saga, stepEntity, compensableStep(cause), new Ctx("1")))
                    .isInstanceOf(StepExecutor.StepCompensationException.class)
                    .hasCause(cause)
                    .hasMessageContaining("charge-payment");
        }

        @Test
        void sets_full_mdc_enrichment_during_compensation() throws Exception {
            String[] captured = {null, null, null, null};
            SagaStep<Ctx> step = SagaBuilder.<Ctx>forSaga("test")
                    .step("charge-payment")
                    .invoke(ctx -> StepOutput.EMPTY)
                    .compensate(ctx -> {
                        captured[0] = MDC.get("sagaId");
                        captured[1] = MDC.get("sagaName");
                        captured[2] = MDC.get("stepName");
                        captured[3] = MDC.get("attempt");
                    })
                    .build().steps().get(0);
            when(stepEntity.getOutputPayload()).thenReturn(null);

            executor.compensate(saga, stepEntity, step, new Ctx("1"));

            assertThat(captured[0]).isEqualTo("saga-42");
            assertThat(captured[1]).isEqualTo("order-processing");
            assertThat(captured[2]).isEqualTo("charge-payment");
            assertThat(captured[3]).isEqualTo("1");
        }

        @Test
        void clears_mdc_enrichment_after_compensation() throws Exception {
            SagaStep<Ctx> step = SagaBuilder.<Ctx>forSaga("test")
                    .step("charge-payment")
                    .invoke(ctx -> StepOutput.EMPTY)
                    .compensate(ctx -> {})
                    .build().steps().get(0);
            when(stepEntity.getOutputPayload()).thenReturn(null);

            executor.compensate(saga, stepEntity, step, new Ctx("1"));

            assertThat(MDC.get("sagaId")).isNull();
            assertThat(MDC.get("sagaName")).isNull();
            assertThat(MDC.get("stepName")).isNull();
            assertThat(MDC.get("attempt")).isNull();
        }
    }

    @Nested
    class InterceptorChain {

        @Test
        void interceptor_wraps_step_invocation() throws Exception {
            boolean[] called = {false};
            // SagaStepInterceptor.intercept() has a method-level type parameter <C>
            // — Java cannot target generic SAMs with lambdas; anonymous class is required.
            SagaStepInterceptor interceptor = new SagaStepInterceptor() {
                @Override
                public <C extends SagaContext> StepOutput intercept(
                        SagaStep<C> step, C context, io.sagaweaw.spring.interceptor.StepExecutionChain chain)
                        throws Exception {
                    called[0] = true;
                    return chain.proceed(step, context);
                }
            };
            StepExecutor withInterceptor = new StepExecutor(
                    stepRepository, outboxRepository, mapper, List.of(interceptor));

            withInterceptor.execute(saga, stepEntity, successStep(), new Ctx("1"));

            assertThat(called[0]).isTrue();
        }
    }
}
