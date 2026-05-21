package io.sagaweaw.spring.event;

import java.time.Instant;

public record StepCompensatedEvent(
        String sagaId,
        String stepName,
        Instant at
) {}
