package io.sagaweaw.spring.alert;

import io.sagaweaw.spring.config.SagaProperties;
import io.sagaweaw.spring.event.SagaFailedEvent;
import io.sagaweaw.spring.repository.SagaRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import static java.time.temporal.ChronoUnit.MINUTES;

public class AlertListener {

    private final AlertWebhookService webhook;
    private final SagaRepository      sagaRepository;
    private final SagaProperties      properties;

    // avoid re-alerting the same stuck saga on every poll cycle
    private final Set<String> alertedStuck = ConcurrentHashMap.newKeySet();

    public AlertListener(AlertWebhookService webhook,
                         SagaRepository sagaRepository,
                         SagaProperties properties) {
        this.webhook        = webhook;
        this.sagaRepository = sagaRepository;
        this.properties     = properties;
    }

    @EventListener
    public void onSagaFailed(SagaFailedEvent event) {
        webhook.notifyDeadLetter(event.sagaId(), event.sagaName(), event.failedStep(), event.errorMessage());
    }

    @Scheduled(fixedDelayString = "${sagaweaw.alerts.stuck-check-interval-ms:60000}")
    public void checkStuck() {
        SagaProperties.Alerts cfg = properties.alerts();
        if (cfg == null || !cfg.isEnabled() || !cfg.onStuckSaga()) return;

        SagaProperties.Health health = properties.health();
        int minutes = health != null ? health.stuckThresholdMinutes() : 15;
        if (minutes <= 0) return;

        Instant threshold = Instant.now().minus(minutes, MINUTES);
        sagaRepository.findStuck(threshold, PageRequest.of(0, 50))
                .forEach(saga -> {
                    if (alertedStuck.add(saga.getId())) {
                        webhook.notifyStuckSaga(saga.getId(), saga.getName());
                    }
                });

        // clear IDs that are no longer stuck (resolved or completed)
        alertedStuck.removeIf(id -> sagaRepository.findById(id)
                .map(e -> !Set.of("EXECUTING", "COMPENSATING").contains(e.getStatus()))
                .orElse(true));
    }

    @Scheduled(fixedDelayString = "${sagaweaw.alerts.failure-rate-check-interval-ms:300000}")
    public void checkFailureRate() {
        SagaProperties.Alerts cfg = properties.alerts();
        if (cfg == null || !cfg.isEnabled() || cfg.failureRateThreshold() <= 0.0) return;

        long completed   = sagaRepository.countByStatus("COMPLETED");
        long failed      = sagaRepository.countByStatus("FAILED");
        long compensated = sagaRepository.countByStatus("COMPENSATED");
        long terminal    = completed + failed + compensated;
        if (terminal == 0) return;

        double rate = (double) failed / terminal;
        webhook.notifyFailureRate(rate);
    }
}
