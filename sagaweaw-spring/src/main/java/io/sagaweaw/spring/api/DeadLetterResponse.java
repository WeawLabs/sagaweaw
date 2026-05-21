package io.sagaweaw.spring.api;

import io.sagaweaw.spring.entity.DeadLetterEntity;

import java.time.Instant;

public record DeadLetterResponse(
        String  id,
        String  sagaId,
        String  sagaName,
        String  stepName,
        String  errorMessage,
        String  errorTrace,
        String  contextSnapshot,
        Instant createdAt,
        boolean reprocessed,
        Instant reprocessedAt,
        String  reprocessedBy
) {
    public static DeadLetterResponse from(DeadLetterEntity e, String sagaName) {
        return new DeadLetterResponse(
                e.getId(),
                e.getSagaId(),
                sagaName,
                e.getStepName(),
                e.getErrorMessage(),
                e.getErrorTrace(),
                e.getContextSnapshot(),
                e.getCreatedAt(),
                e.isReprocessed(),
                e.getReprocessedAt(),
                e.getReprocessedBy()
        );
    }
}
