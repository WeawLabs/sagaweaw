package io.sagaweaw.cloud.agent.buffer;

import io.sagaweaw.cloud.agent.model.CloudEvent;

import java.util.List;

public interface EventBuffer {

    /** Persists an event locally. Never throws — failures are logged and swallowed. */
    void store(CloudEvent event);

    /** Returns up to {@code limit} pending events, oldest first. */
    List<CloudEvent> drain(int limit);

    /** Removes events that were successfully sent. */
    void delete(List<String> eventIds);
}
