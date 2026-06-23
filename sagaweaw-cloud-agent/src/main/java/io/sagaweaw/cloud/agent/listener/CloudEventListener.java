package io.sagaweaw.cloud.agent.listener;

import io.sagaweaw.cloud.agent.CloudAgentProperties;
import io.sagaweaw.cloud.agent.buffer.EventBuffer;
import io.sagaweaw.cloud.agent.model.CloudEvent;
import io.sagaweaw.cloud.agent.model.EventType;
import io.sagaweaw.spring.event.SagaCompensatedEvent;
import io.sagaweaw.spring.event.SagaCompletedEvent;
import io.sagaweaw.spring.event.SagaFailedEvent;
import io.sagaweaw.spring.event.SagaStartedEvent;
import io.sagaweaw.spring.event.StepCompensatedEvent;
import io.sagaweaw.spring.event.StepCompletedEvent;
import io.sagaweaw.spring.event.StepFailedEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;

/**
 * Intercepts all saga lifecycle events published by the sagaweaw lib
 * and stores them in the local buffer for async delivery to Sagaweaw Cloud.
 */
public class CloudEventListener {

    private final EventBuffer buffer;
    private final String environment;

    public CloudEventListener(EventBuffer buffer, CloudAgentProperties properties) {
        this.buffer = buffer;
        this.environment = properties.getEnvironment().name();
    }

    @EventListener
    public void on(SagaStartedEvent event) {
        buffer.store(new CloudEvent(
                EventType.SAGA_STARTED,
                event.sagaId(), event.sagaName(), environment,
                Map.of("at", event.at().toString())
        ));
    }

    @EventListener
    public void on(SagaCompletedEvent event) {
        buffer.store(new CloudEvent(
                EventType.SAGA_COMPLETED,
                event.sagaId(), event.sagaName(), environment,
                Map.of("durationMs", event.durationMs())
        ));
    }

    @EventListener
    public void on(SagaFailedEvent event) {
        buffer.store(new CloudEvent(
                EventType.SAGA_FAILED,
                event.sagaId(), event.sagaName(), environment,
                Map.of("failedStep", event.failedStep(), "errorMessage", event.errorMessage())
        ));
    }

    @EventListener
    public void on(SagaCompensatedEvent event) {
        buffer.store(new CloudEvent(
                EventType.SAGA_COMPENSATED,
                event.sagaId(), event.sagaName(), environment,
                Map.of("failedStep", event.failedStep(), "at", event.at().toString())
        ));
    }

    @EventListener
    public void on(StepCompletedEvent event) {
        buffer.store(new CloudEvent(
                EventType.STEP_COMPLETED,
                event.sagaId(), event.sagaName(), environment,
                Map.of("stepName", event.stepName(), "durationMs", event.durationMs())
        ));
    }

    @EventListener
    public void on(StepFailedEvent event) {
        buffer.store(new CloudEvent(
                EventType.STEP_FAILED,
                event.sagaId(), "", environment,
                Map.of("stepName", event.stepName(), "attempt", event.attempt(),
                        "errorMessage", event.errorMessage())
        ));
    }

    @EventListener
    public void on(StepCompensatedEvent event) {
        buffer.store(new CloudEvent(
                EventType.STEP_COMPENSATED,
                event.sagaId(), "", environment,
                Map.of("stepName", event.stepName(), "at", event.at().toString())
        ));
    }
}
