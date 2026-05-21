package io.sagaweaw.spring.event;

import java.time.Instant;

public record SagaCompensatedEvent(
        String sagaId,
        String sagaName,
        String failedStep,
        Instant at
) {}
