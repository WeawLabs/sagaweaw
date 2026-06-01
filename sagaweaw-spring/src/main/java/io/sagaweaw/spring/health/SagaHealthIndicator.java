package io.sagaweaw.spring.health;

import io.sagaweaw.spring.config.SagaProperties;
import io.sagaweaw.spring.repository.DeadLetterRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SagaHealthIndicator implements HealthIndicator {

    private final SagaRepository sagaRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final SagaProperties properties;

    public SagaHealthIndicator(SagaRepository sagaRepository,
                                DeadLetterRepository deadLetterRepository,
                                SagaProperties properties) {
        this.sagaRepository = sagaRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.properties = properties;
    }

    @Override
    public Health health() {
        long pendingDeadLetters = deadLetterRepository.countByReprocessedFalse();
        long executing          = sagaRepository.countByStatus("EXECUTING");
        long compensating       = sagaRepository.countByStatus("COMPENSATING");

        SagaProperties.Health cfg = properties.health();
        int stuckMinutes          = cfg != null ? cfg.stuckThresholdMinutes()      : 30;
        int deadLetterThreshold   = cfg != null ? cfg.deadLetterAlertThreshold()   : 0;

        long stuckSagas = 0;
        if (stuckMinutes > 0) {
            Instant threshold = Instant.now().minus(stuckMinutes, ChronoUnit.MINUTES);
            stuckSagas = sagaRepository.countStuck(threshold);
        }

        Health.Builder builder;
        if (stuckSagas > 0) {
            builder = Health.down();
        } else if (deadLetterThreshold > 0 && pendingDeadLetters > deadLetterThreshold) {
            builder = Health.outOfService();
        } else {
            builder = Health.up();
        }

        return builder
                .withDetail("executing",          executing)
                .withDetail("compensating",        compensating)
                .withDetail("stuckSagas",          stuckSagas)
                .withDetail("pendingDeadLetters",  pendingDeadLetters)
                .build();
    }
}
