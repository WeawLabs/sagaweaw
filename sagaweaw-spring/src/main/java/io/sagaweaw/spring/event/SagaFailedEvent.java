package io.sagaweaw.spring.event;

public record SagaFailedEvent(
        String sagaId,
        String sagaName,
        String failedStep,
        String errorMessage
) {}
