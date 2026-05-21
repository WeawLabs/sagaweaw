package io.sagaweaw.spring.event;

import java.time.Instant;

public record SagaStartedEvent(
        String sagaId,
        String sagaName,
        Instant at
) {}
