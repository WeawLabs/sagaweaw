package io.sagaweaw.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

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
        Tracing tracing,
        Alerts alerts,
        Data data,
        Instance instance
) {

    public record Dashboard(
            @DefaultValue("embedded")  String mode,
            @DefaultValue("/sagaweaw") String path
    ) {
        public boolean isEmbedded() {
            return "embedded".equalsIgnoreCase(mode);
        }
    }

    public record Observability(
            String token,
            String previousToken,
            @DefaultValue("true") boolean enabled,
            Cors cors,
            Auth auth
    ) {}

    /**
     * sagaweaw.observability.auth.max-attempts — failed auth attempts before lockout (default: 10).
     * sagaweaw.observability.auth.lockout-minutes — lockout duration in minutes (default: 15).
     * Set max-attempts=0 to disable rate limiting entirely.
     */
    public record Auth(
            @DefaultValue("10") int maxAttempts,
            @DefaultValue("15") int lockoutMinutes
    ) {}

    /**
     * sagaweaw.observability.cors.allowed-origins — comma-separated list of origins
     * that are allowed to call the sagaweaw observability API from a browser.
     * Only needed when the dashboard runs on a different origin than the app
     * (e.g. standalone mode with Vite dev server on port 8484, or an external monitoring host).
     * Not required in embedded mode (default) — the dashboard is served from the same origin.
     *
     * Example:
     *   sagaweaw.observability.cors.allowed-origins=http://localhost:8484,https://staging.myapp.com
     *
     * The lib configures CORS only for /api/sagas/** and /api/dead-letters/** —
     * it does not touch the rest of the application's CORS configuration.
     */
    public record Cors(List<String> allowedOrigins) {
        public boolean isConfigured() {
            return allowedOrigins != null && !allowedOrigins.isEmpty();
        }
    }

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
    /**
     * sagaweaw.alerts.webhook-url — HTTP POST endpoint; compatible with Slack, Discord, Teams,
     * PagerDuty and any generic webhook receiver.
     * failure-rate-threshold — fraction (0.0–1.0); 0.05 = alert when >5% of terminal sagas failed.
     * Set to 0 to disable the failure-rate check.
     */
    /**
     * sagaweaw.data.retention-days — sagas in terminal status (COMPLETED, COMPENSATED, FAILED)
     * older than N days are moved to sagas_archive and removed from the live tables.
     * Set to 0 (default) to never archive. Dead letters are always kept.
     */
    /**
     * sagaweaw.data.retention-days — COMPLETED and COMPENSATED sagas older than N days
     * are moved to sagas_archive. Default 0 = never archive.
     *
     * sagaweaw.data.failed-retention-days — FAILED sagas are kept longer for investigation
     * and manual reprocessing. Default 90 days (aligns with LGPD/PCI-DSS 90-day audit trail).
     * Set to 0 to use the same threshold as retention-days.
     *
     * Note: no major competitor (Temporal, Step Functions, Camunda, Conductor) differentiates
     * retention by status — this is a deliberate Sagaweaw differentiator.
     */
    public record Data(
            @DefaultValue("0")  int retentionDays,
            @DefaultValue("90") int failedRetentionDays
    ) {}

    /**
     * sagaweaw.instance.id — identifies this JVM instance in the sagas table.
     * Defaults to hostname + random suffix. Set explicitly in multi-pod deployments
     * where you want stable, readable IDs (e.g. pod name from Kubernetes downward API).
     *
     * Example: sagaweaw.instance.id=${HOSTNAME}
     */
    public record Instance(String id) {}

    public record Alerts(
            String webhookUrl,
            @DefaultValue("true")  boolean onDeadLetter,
            @DefaultValue("true")  boolean onStuckSaga,
            @DefaultValue("0.0")   double  failureRateThreshold
    ) {
        public boolean isEnabled() {
            return webhookUrl != null && !webhookUrl.isBlank();
        }
    }

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
