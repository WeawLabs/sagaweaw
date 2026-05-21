package io.sagaweaw.spring.api;

import java.util.List;

public record SagaMetrics(
        long   total,
        long   started,
        long   executing,
        long   completed,
        long   compensated,
        long   failed,
        long   deadLetters,
        double successRate,
        List<SagaNameMetrics> byName,
        long   outboxPending
) {
    public record SagaNameMetrics(
            String name,
            long   total,
            long   completed,
            long   failed
    ) {}
}
