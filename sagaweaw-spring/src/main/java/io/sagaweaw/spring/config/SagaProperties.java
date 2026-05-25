package io.sagaweaw.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sagaweaw")
public record SagaProperties(
        @DefaultValue("3")    int maxRetries,
        @DefaultValue("1000") long retryDelayMs,
        @DefaultValue("5000") long outboxPollIntervalMs,
        @DefaultValue("8484") int observabilityPort,
        WebSocket websocket,
        Observability observability,
        Health health,
        Dashboard dashboard,
        Tracing tracing
) {

    public record Dashboard(
            @DefaultValue("standalone") String mode,
            @DefaultValue("/sagaweaw")  String path
    ) {
        public boolean isEmbedded() {
            return "embedded".equalsIgnoreCase(mode);
        }
    }

    public record Observability(
            String token,
            @DefaultValue("true") boolean enabled
    ) {}

    /**
     * sagaweaw.tracing.enabled=false disables OTel span generation entirely.
     * Spans are only emitted when micrometer-tracing (or micrometer-tracing-bridge-otel)
     * is on the classpath — this property provides an explicit opt-out.
     */
    public record Tracing(
            @DefaultValue("true") boolean enabled
    ) {}

    /**
     * stuckThresholdMinutes: sagas in EXECUTING/COMPENSATING with updatedAt older than this
     * are considered stuck and flip the health status to DOWN. Set to 0 to disable.
     * deadLetterAlertThreshold: when pending dead letters exceed this value the status becomes
     * OUT_OF_SERVICE. Set to 0 (default) to report dead letters as detail only, never as status.
     */
    public record Health(
            @DefaultValue("15") int stuckThresholdMinutes,
            @DefaultValue("0")  int deadLetterAlertThreshold
    ) {}

    /**
     * Optional STOMP broker relay for multi-pod deployments.
     * When relayHost is set, Sagaweaw switches from the in-memory broker
     * to a full STOMP relay (e.g. RabbitMQ with STOMP plugin).
     * Without relay, WebSocket events from one pod are NOT delivered to
     * clients connected to other pods — only use the in-memory broker
     * for single-pod / local development.
     *
     * Example application.yml:
     * sagaweaw:
     *   websocket:
     *     relay-host: rabbitmq.internal
     *     relay-port: 61613
     *     relay-login: sagaweaw
     *     relay-passcode: secret
     */
    public record WebSocket(
            String relayHost,
            @DefaultValue("61613") int relayPort,
            String relayLogin,
            String relayPasscode
    ) {
        public boolean hasRelay() {
            return relayHost != null && !relayHost.isBlank();
        }

        public boolean hasRelayCredentials() {
            return relayLogin != null && !relayLogin.isBlank()
                    && relayPasscode != null && !relayPasscode.isBlank();
        }
    }
}
