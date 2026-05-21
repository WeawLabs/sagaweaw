package io.sagaweaw.spring.api;

import io.sagaweaw.spring.entity.SagaEventEntity;

import java.time.Instant;

public record SagaEventResponse(
        String  id,
        String  sagaId,
        String  stepName,
        String  eventType,
        String  payload,
        Instant createdAt
) {
    public static SagaEventResponse from(SagaEventEntity e) {
        return new SagaEventResponse(
                e.getId(),
                e.getSaga().getId(),
                e.getStepName(),
                e.getEventType(),
                e.getPayload(),
                e.getCreatedAt()
        );
    }
}
