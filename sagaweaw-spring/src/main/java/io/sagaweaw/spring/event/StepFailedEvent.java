package io.sagaweaw.spring.event;

public record StepFailedEvent(
        String sagaId,
        String stepName,
        int attempt,
        String errorMessage
) {}
