package io.sagaweaw.cloud.agent.sender;

import io.sagaweaw.cloud.agent.model.CloudEvent;

import java.util.List;

public interface CloudEventSender {

    /**
     * Sends a batch of events to Sagaweaw Cloud.
     * Returns true if the batch was accepted, false otherwise.
     */
    boolean send(List<CloudEvent> events);
}
