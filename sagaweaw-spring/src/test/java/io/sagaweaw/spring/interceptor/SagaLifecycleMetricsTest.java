package io.sagaweaw.spring.interceptor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.sagaweaw.spring.event.SagaCompensatedEvent;
import io.sagaweaw.spring.event.SagaCompletedEvent;
import io.sagaweaw.spring.event.SagaFailedEvent;
import io.sagaweaw.spring.event.SagaStartedEvent;
import io.sagaweaw.spring.repository.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaLifecycleMetricsTest {

    @Mock OutboxMessageRepository outboxRepo;

    private SimpleMeterRegistry registry;
    private SagaLifecycleMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new SagaLifecycleMetrics(registry, outboxRepo);
    }

    @Nested
    class OutboxGauge {

        @Test
        void gauge_reflects_pending_count() {
            when(outboxRepo.countByPublished(false)).thenReturn(12L);

            Gauge gauge = registry.find("sagaweaw.outbox.pending").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(12.0);
        }

        @Test
        void gauge_returns_zero_when_none_pending() {
            when(outboxRepo.countByPublished(false)).thenReturn(0L);

            assertThat(registry.find("sagaweaw.outbox.pending").gauge().value()).isEqualTo(0.0);
        }
    }

    @Nested
    class OnStarted {

        @Test
        void increments_started_counter() {
            metrics.onStarted(new SagaStartedEvent("id1", "PaymentSaga", Instant.now()));

            Counter c = registry.find("sagaweaw.sagas.started").tag("saga", "PaymentSaga").counter();
            assertThat(c).isNotNull();
            assertThat(c.count()).isEqualTo(1.0);
        }

        @Test
        void saga_tag_isolates_types() {
            metrics.onStarted(new SagaStartedEvent("id1", "PaymentSaga", Instant.now()));
            metrics.onStarted(new SagaStartedEvent("id2", "RefundSaga",  Instant.now()));

            assertThat(registry.find("sagaweaw.sagas.started").tag("saga", "PaymentSaga").counter().count()).isEqualTo(1.0);
            assertThat(registry.find("sagaweaw.sagas.started").tag("saga", "RefundSaga").counter().count()).isEqualTo(1.0);
        }
    }

    @Nested
    class OnCompleted {

        @Test
        void increments_completed_counter() {
            metrics.onCompleted(new SagaCompletedEvent("id1", "PaymentSaga", 120L));

            Counter c = registry.find("sagaweaw.sagas.completed").tag("saga", "PaymentSaga").counter();
            assertThat(c).isNotNull();
            assertThat(c.count()).isEqualTo(1.0);
        }

        @Test
        void records_duration_timer() {
            metrics.onCompleted(new SagaCompletedEvent("id1", "PaymentSaga", 300L));

            Timer timer = registry.find("sagaweaw.sagas.duration").tag("saga", "PaymentSaga").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        void does_not_increment_failed_counter() {
            metrics.onCompleted(new SagaCompletedEvent("id1", "PaymentSaga", 120L));

            assertThat(registry.find("sagaweaw.sagas.failed").counter()).isNull();
        }
    }

    @Nested
    class OnFailed {

        @Test
        void increments_failed_counter_with_both_tags() {
            metrics.onFailed(new SagaFailedEvent("id1", "PaymentSaga", "charge-card", "timeout"));

            Counter c = registry.find("sagaweaw.sagas.failed")
                    .tag("saga", "PaymentSaga")
                    .tag("step", "charge-card")
                    .counter();
            assertThat(c).isNotNull();
            assertThat(c.count()).isEqualTo(1.0);
        }

        @Test
        void step_tag_isolates_failure_origin() {
            metrics.onFailed(new SagaFailedEvent("id1", "PaymentSaga", "charge-card",  "timeout"));
            metrics.onFailed(new SagaFailedEvent("id2", "PaymentSaga", "notify-email", "smtp down"));

            assertThat(registry.find("sagaweaw.sagas.failed").tag("step", "charge-card").counter().count()).isEqualTo(1.0);
            assertThat(registry.find("sagaweaw.sagas.failed").tag("step", "notify-email").counter().count()).isEqualTo(1.0);
        }
    }

    @Nested
    class OnCompensated {

        @Test
        void increments_compensated_counter() {
            metrics.onCompensated(new SagaCompensatedEvent("id1", "PaymentSaga", "charge-card", Instant.now()));

            Counter c = registry.find("sagaweaw.sagas.compensated").tag("saga", "PaymentSaga").counter();
            assertThat(c).isNotNull();
            assertThat(c.count()).isEqualTo(1.0);
        }

        @Test
        void does_not_increment_failed_counter() {
            metrics.onCompensated(new SagaCompensatedEvent("id1", "PaymentSaga", "charge-card", Instant.now()));

            assertThat(registry.find("sagaweaw.sagas.failed").counter()).isNull();
        }
    }
}
