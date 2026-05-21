package io.sagaweaw.spring.interceptor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.core.StepOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsInterceptorTest {

    private record Ctx(String id) implements SagaContext {}

    /** Chain that always returns StepOutput.EMPTY. */
    private static StepExecutionChain successChain() {
        return new StepExecutionChain(List.of()) {
            @Override
            public <C extends SagaContext> StepOutput proceed(SagaStep<C> step, C context) {
                return StepOutput.EMPTY;
            }
        };
    }

    /** Chain that always throws the given exception. */
    private static StepExecutionChain failingChain(RuntimeException ex) {
        return new StepExecutionChain(List.of()) {
            @Override
            public <C extends SagaContext> StepOutput proceed(SagaStep<C> step, C context) {
                throw ex;
            }
        };
    }

    @Mock SagaStep<Ctx> step;

    private MeterRegistry      registry;
    private MetricsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        registry    = new SimpleMeterRegistry();
        interceptor = new MetricsInterceptor(registry);
        when(step.name()).thenReturn("charge-payment");
    }

    @Nested
    class OnSuccess {

        @Test
        void increments_completed_counter() throws Exception {
            interceptor.intercept(step, new Ctx("1"), successChain());

            Counter counter = registry.find("sagaweaw.steps.completed")
                    .tag("step", "charge-payment").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void records_duration_with_success_outcome() throws Exception {
            interceptor.intercept(step, new Ctx("1"), successChain());

            Timer timer = registry.find("sagaweaw.steps.duration")
                    .tag("step", "charge-payment")
                    .tag("outcome", "success")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        void does_not_increment_failed_counter() throws Exception {
            interceptor.intercept(step, new Ctx("1"), successChain());

            Counter counter = registry.find("sagaweaw.steps.failed")
                    .tag("step", "charge-payment").counter();
            assertThat(counter).isNull();
        }
    }

    @Nested
    class OnFailure {

        @Test
        void increments_failed_counter() {
            assertThatThrownBy(() ->
                    interceptor.intercept(step, new Ctx("1"), failingChain(new RuntimeException("boom"))))
                    .isInstanceOf(RuntimeException.class);

            Counter counter = registry.find("sagaweaw.steps.failed")
                    .tag("step", "charge-payment").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void records_duration_with_error_outcome() {
            assertThatThrownBy(() ->
                    interceptor.intercept(step, new Ctx("1"), failingChain(new RuntimeException("boom"))))
                    .isInstanceOf(RuntimeException.class);

            Timer timer = registry.find("sagaweaw.steps.duration")
                    .tag("step", "charge-payment")
                    .tag("outcome", "error")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        void does_not_increment_completed_counter() {
            assertThatThrownBy(() ->
                    interceptor.intercept(step, new Ctx("1"), failingChain(new RuntimeException("boom"))))
                    .isInstanceOf(RuntimeException.class);

            Counter counter = registry.find("sagaweaw.steps.completed")
                    .tag("step", "charge-payment").counter();
            assertThat(counter).isNull();
        }

        @Test
        void rethrows_original_exception() {
            RuntimeException cause = new RuntimeException("service down");
            assertThatThrownBy(() ->
                    interceptor.intercept(step, new Ctx("1"), failingChain(cause)))
                    .isSameAs(cause);
        }
    }

    @Nested
    class OutcomeTagIsolation {

        @Test
        void success_and_error_timers_are_separate_series() throws Exception {
            interceptor.intercept(step, new Ctx("1"), successChain());
            assertThatThrownBy(() ->
                    interceptor.intercept(step, new Ctx("2"), failingChain(new RuntimeException())));

            assertThat(registry.find("sagaweaw.steps.duration")
                    .tag("outcome", "success").timer().count()).isEqualTo(1);
            assertThat(registry.find("sagaweaw.steps.duration")
                    .tag("outcome", "error").timer().count()).isEqualTo(1);
        }
    }
}
