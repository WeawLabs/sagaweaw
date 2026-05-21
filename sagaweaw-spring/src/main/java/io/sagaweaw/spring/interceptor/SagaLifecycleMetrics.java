package io.sagaweaw.spring.interceptor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.sagaweaw.spring.event.SagaCompensatedEvent;
import io.sagaweaw.spring.event.SagaCompletedEvent;
import io.sagaweaw.spring.event.SagaFailedEvent;
import io.sagaweaw.spring.event.SagaStartedEvent;
import io.sagaweaw.spring.repository.OutboxMessageRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Records saga-level lifecycle metrics via Micrometer.
 * Complements {@link MetricsInterceptor} which tracks individual steps.
 * Exported metrics:
 * - sagaweaw.sagas.started     (counter, tag: saga)
 * - sagaweaw.sagas.completed   (counter, tag: saga)
 * - sagaweaw.sagas.failed      (counter, tags: saga, step)
 * - sagaweaw.sagas.compensated (counter, tag: saga)
 * - sagaweaw.sagas.duration    (timer, tag: saga)
 * - sagaweaw.outbox.pending    (gauge)
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
public class SagaLifecycleMetrics {

    private final MeterRegistry meterRegistry;

    public SagaLifecycleMetrics(MeterRegistry meterRegistry,
                                OutboxMessageRepository outboxMessageRepository) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("sagaweaw.outbox.pending",
                      outboxMessageRepository, r -> (double) r.countByPublished(false))
             .description("Outbox messages pending publication")
             .register(meterRegistry);
    }

    @EventListener
    public void onStarted(SagaStartedEvent event) {
        meterRegistry.counter("sagaweaw.sagas.started",
                "saga", event.sagaName()).increment();
    }

    @EventListener
    public void onCompleted(SagaCompletedEvent event) {
        meterRegistry.counter("sagaweaw.sagas.completed",
                "saga", event.sagaName()).increment();
        Timer.builder("sagaweaw.sagas.duration")
             .tag("saga", event.sagaName())
             .register(meterRegistry)
             .record(event.durationMs(), TimeUnit.MILLISECONDS);
    }

    @EventListener
    public void onFailed(SagaFailedEvent event) {
        meterRegistry.counter("sagaweaw.sagas.failed",
                "saga", event.sagaName(),
                "step", event.failedStep()).increment();
    }

    @EventListener
    public void onCompensated(SagaCompensatedEvent event) {
        meterRegistry.counter("sagaweaw.sagas.compensated",
                "saga", event.sagaName()).increment();
    }
}
