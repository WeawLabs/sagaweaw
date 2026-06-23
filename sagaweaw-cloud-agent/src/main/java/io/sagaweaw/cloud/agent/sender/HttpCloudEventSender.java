package io.sagaweaw.cloud.agent.sender;

import io.sagaweaw.cloud.agent.model.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends batches of saga events to the Sagaweaw Cloud ingest endpoint.
 * Never throws — failures are logged at WARN level. The client app is never impacted.
 */
public class HttpCloudEventSender implements CloudEventSender {

    private static final Logger log = LoggerFactory.getLogger(HttpCloudEventSender.class);

    private final RestClient restClient;
    private final String apiKey;

    public HttpCloudEventSender(String endpoint, String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(endpoint)
                .build();
    }

    @Override
    public boolean send(List<CloudEvent> events) {
        if (events.isEmpty()) return true;
        try {
            restClient.post()
                    .uri("/api/ingest/v1/events")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(Map.of("events", events))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("[sagaweaw-cloud-agent] Sent {} events to Cloud", events.size());
            return true;
        } catch (Exception e) {
            log.warn("[sagaweaw-cloud-agent] Failed to send {} events to Cloud: {}", events.size(), e.getMessage());
            return false;
        }
    }
}
