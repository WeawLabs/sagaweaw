package io.sagaweaw.spring.interceptor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.core.StepOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Records step duration, success and failure counters via Micrometer.
 * Auto-activated only when Micrometer is on the classpath.
 * Exported metrics:
 * - sagaweaw.steps.duration (timer, tags: step, outcome=success|error)
 * - sagaweaw.steps.completed (counter, tag: step)
 * - sagaweaw.steps.failed (counter, tag: step)
 */
@Component
@Order(1)
@ConditionalOnClass(MeterRegistry.class)
@RequiredArgsConstructor
public class MetricsInterceptor implements SagaStepInterceptor {

    private final MeterRegistry meterRegistry;

    @Override
    public <C extends SagaContext> StepOutput intercept(
            SagaStep<C> step, C context, StepExecutionChain chain) throws Exception {

        long start = System.currentTimeMillis();
        String outcome = "success";
        try {
            StepOutput output = chain.proceed(step, context);

            meterRegistry.counter("sagaweaw.steps.completed",
                    "step", step.name()).increment();

            return output;

        } catch (Exception e) {
            outcome = "error";
            meterRegistry.counter("sagaweaw.steps.failed",
                    "step", step.name()).increment();
            throw e;

        } finally {
            long duration = System.currentTimeMillis() - start;
            Timer.builder("sagaweaw.steps.duration")
                    .tag("step", step.name())
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(duration, TimeUnit.MILLISECONDS);
        }
    }
}
