package io.sagaweaw.spring.interceptor;

import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.core.StepOutput;

/**
 * Extension point for cross-cutting concerns in step execution.
 * Implementations are auto-discovered by the engine and chained
 * in order of @Order. The last link in the chain calls the real invoker.
 * Built-in implementations provided by Sagaweaw:
 * - MetricsInterceptor: auto-activated when Micrometer is on the classpath
 * - TracingInterceptor: auto-activated when OpenTelemetry is on the classpath
 * Custom interceptors can be registered as Spring beans.
 */
public interface SagaStepInterceptor {

    <C extends SagaContext> StepOutput intercept(
            SagaStep<C> step,
            C context,
            StepExecutionChain chain
    ) throws Exception;
}
