package io.sagaweaw.spring.interceptor;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.core.StepOutput;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@ConditionalOnClass(Tracer.class)
@RequiredArgsConstructor
public class TracingInterceptor implements SagaStepInterceptor {

    private final Tracer tracer;

    @Override
    public <C extends SagaContext> StepOutput intercept(
            SagaStep<C> step, C context, StepExecutionChain chain) throws Exception {

        Span span = tracer.spanBuilder("sagaweaw.step." + step.name())
                .setAttribute("saga.step.type", step.type().name())
                .startSpan();

        String sagaId = MDC.get("sagaId");
        if (sagaId != null) {
            span.setAttribute("saga.id", sagaId);
        }

        try (Scope scope = span.makeCurrent()) {
            StepOutput output = chain.proceed(step, context);
            span.setAttribute("saga.step.status", "COMPLETED");
            return output;
        } catch (Exception e) {
            span.setAttribute("saga.step.status", "FAILED");
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
