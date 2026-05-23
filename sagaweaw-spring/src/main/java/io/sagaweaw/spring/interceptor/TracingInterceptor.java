package io.sagaweaw.spring.interceptor;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
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
@ConditionalOnClass(ObservationRegistry.class)
@RequiredArgsConstructor
public class TracingInterceptor implements SagaStepInterceptor {

    private final ObservationRegistry observationRegistry;

    @Override
    public <C extends SagaContext> StepOutput intercept(
            SagaStep<C> step, C context, StepExecutionChain chain) throws Exception {

        Observation obs = Observation.createNotStarted("sagaweaw.step.invoke", observationRegistry)
                .lowCardinalityKeyValue("saga.step.name", step.name())
                .lowCardinalityKeyValue("saga.step.type", step.type().name());

        String sagaId   = MDC.get("sagaId");
        String sagaName = MDC.get("sagaName");
        String attempt  = MDC.get("attempt");
        if (sagaId   != null) obs.lowCardinalityKeyValue("saga.id",           sagaId);
        if (sagaName != null) obs.lowCardinalityKeyValue("saga.name",         sagaName);
        if (attempt  != null) obs.lowCardinalityKeyValue("saga.step.attempt", attempt);

        obs.start();
        try {
            return chain.proceed(step, context);
        } catch (Exception e) {
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }
}
