package io.sagaweaw.spring.event;

public record StepCompletedEvent(
        String sagaId,
        String sagaName,
        String stepName,
        long durationMs
) {}
