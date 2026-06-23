package io.sagaweaw.cloud.agent.scheduler;

import io.sagaweaw.cloud.agent.CloudAgentProperties;
import io.sagaweaw.cloud.agent.buffer.EventBuffer;
import io.sagaweaw.cloud.agent.model.CloudEvent;
import io.sagaweaw.cloud.agent.sender.CloudEventSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Periodically drains the local SQLite buffer and sends events to Sagaweaw Cloud.
 * Runs every {@code sagaweaw.cloud.flush-interval-ms} milliseconds (default: 5000).
 * If sending fails, events stay in the buffer and are retried on the next cycle.
 */
public class FlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(FlushScheduler.class);

    private final EventBuffer buffer;
    private final CloudEventSender sender;
    private final int batchSize;

    public FlushScheduler(EventBuffer buffer, CloudEventSender sender, CloudAgentProperties properties) {
        this.buffer = buffer;
        this.sender = sender;
        this.batchSize = properties.getBatchSize();
    }

    @Scheduled(fixedDelayString = "${sagaweaw.cloud.flush-interval-ms:5000}")
    public void flush() {
        List<CloudEvent> batch = buffer.drain(batchSize);
        if (batch.isEmpty()) return;

        boolean sent = sender.send(batch);
        if (sent) {
            List<String> ids = batch.stream().map(CloudEvent::getEventId).toList();
            buffer.delete(ids);
            log.debug("[sagaweaw-cloud-agent] Flushed {} events to Cloud", batch.size());
        }
    }
}
