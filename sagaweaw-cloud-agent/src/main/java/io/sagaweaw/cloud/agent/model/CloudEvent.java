package io.sagaweaw.cloud.agent.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single saga lifecycle event to be sent to Sagaweaw Cloud.
 * Each event has a stable UUID used for idempotent ingestion on the Cloud side.
 */
public class CloudEvent {

    private final String eventId;
    private final EventType type;
    private final String sagaId;
    private final String sagaName;
    private final String environment;
    private final Instant occurredAt;
    private final Map<String, Object> payload;

    public CloudEvent(EventType type, String sagaId, String sagaName,
                      String environment, Map<String, Object> payload) {
        this.eventId = UUID.randomUUID().toString();
        this.type = type;
        this.sagaId = sagaId;
        this.sagaName = sagaName;
        this.environment = environment;
        this.occurredAt = Instant.now();
        this.payload = payload;
    }

    public String getEventId() { return eventId; }
    public EventType getType() { return type; }
    public String getSagaId() { return sagaId; }
    public String getSagaName() { return sagaName; }
    public String getEnvironment() { return environment; }
    public Instant getOccurredAt() { return occurredAt; }
    public Map<String, Object> getPayload() { return payload; }
}
