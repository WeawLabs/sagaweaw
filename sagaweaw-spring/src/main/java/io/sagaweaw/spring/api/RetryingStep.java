package io.sagaweaw.spring.api;

import java.time.Instant;

public record RetryingStep(
        String  sagaId,
        String  sagaName,
        String  stepName,
        int     attempt,
        int     maxAttempts,
        Instant nextRetryAt
) {}
