package io.sagaweaw.spring.event;

public record SagaCompletedEvent(
        String sagaId,
        String sagaName,
        long durationMs
) {}
