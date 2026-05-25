package io.sagaweaw.spring.alert;

import io.sagaweaw.spring.config.SagaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

public class AlertWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AlertWebhookService.class);

    private final SagaProperties properties;
    private final RestTemplate   restTemplate;

    public AlertWebhookService(SagaProperties properties) {
        this.properties  = properties;
        this.restTemplate = new RestTemplate();
    }

    public void notifyDeadLetter(String sagaId, String sagaName, String stepName, String errorMessage) {
        SagaProperties.Alerts cfg = properties.alerts();
        if (cfg == null || !cfg.isEnabled() || !cfg.onDeadLetter()) return;
        fire("DEAD_LETTER", sagaId, sagaName, stepName, errorMessage);
    }

    public void notifyStuckSaga(String sagaId, String sagaName) {
        SagaProperties.Alerts cfg = properties.alerts();
        if (cfg == null || !cfg.isEnabled() || !cfg.onStuckSaga()) return;
        fire("STUCK_SAGA", sagaId, sagaName, null, "Saga has been executing for longer than the configured threshold");
    }

    public void notifyFailureRate(double rate) {
        SagaProperties.Alerts cfg = properties.alerts();
        if (cfg == null || !cfg.isEnabled() || cfg.failureRateThreshold() <= 0.0) return;
        if (rate < cfg.failureRateThreshold()) return;
        fire("HIGH_FAILURE_RATE", null, null, null,
                String.format("Failure rate %.1f%% exceeds threshold %.1f%%", rate * 100, cfg.failureRateThreshold() * 100));
    }

    private void fire(String event, String sagaId, String sagaName, String stepName, String details) {
        String url = properties.alerts().webhookUrl();
        String body = buildPayload(event, sagaId, sagaName, stepName, details);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Void.class);
        } catch (Exception e) {
            log.warn("sagaweaw: webhook delivery failed [event={}, url={}]: {}", event, url, e.getMessage());
        }
    }

    private static String buildPayload(String event, String sagaId, String sagaName, String stepName, String details) {
        StringBuilder sb = new StringBuilder("{");
        appendField(sb, "event",     event,     true);
        appendField(sb, "sagaId",    sagaId,    true);
        appendField(sb, "sagaName",  sagaName,  true);
        appendField(sb, "stepName",  stepName,  true);
        appendField(sb, "timestamp", Instant.now().toString(), true);
        appendField(sb, "details",   details,   false);
        sb.append('}');
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value, boolean comma) {
        sb.append('"').append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(value.replace("\"", "\\\"")).append('"');
        }
        if (comma) sb.append(',');
    }
}
