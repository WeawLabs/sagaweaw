package io.sagaweaw.cloud.agent.model;

public enum EventType {
    SAGA_STARTED,
    SAGA_COMPLETED,
    SAGA_FAILED,
    SAGA_COMPENSATED,
    STEP_COMPLETED,
    STEP_FAILED,
    STEP_COMPENSATED
}
