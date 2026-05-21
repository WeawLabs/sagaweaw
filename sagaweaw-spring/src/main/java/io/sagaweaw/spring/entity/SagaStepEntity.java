package io.sagaweaw.spring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "saga_steps",
        indexes = {
                @Index(name = "idx_saga_steps_saga_id", columnList = "saga_id"),
                @Index(name = "idx_saga_steps_retry",   columnList = "status, next_retry_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SagaStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saga_id", nullable = false, updatable = false)
    private SagaEntity saga;

    @Column(name = "step_name", nullable = false, length = 255)
    private String stepName;

    // CompensationExecutor orders DESC by this field to compensate in reverse (ADR-007)
    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempt = 0;

    // 0 = unlimited (RETRIABLE steps)
    @Column(nullable = false)
    private int maxAttempts = 3;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "error_trace", columnDefinition = "text")
    private String errorTrace;

    // Snapshot at execution time — compensation always uses this, never the live context (ADR-007)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload")
    private String inputPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_payload")
    private String outputPayload;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    public static SagaStepEntity create(SagaEntity saga, String stepName,
                                        int stepOrder, int maxAttempts) {
        SagaStepEntity entity = new SagaStepEntity();
        entity.saga        = saga;
        entity.stepName    = stepName;
        entity.stepOrder   = stepOrder;
        entity.maxAttempts = maxAttempts;
        entity.status      = "PENDING";
        return entity;
    }

    public void markExecuting(String inputPayloadJson) {
        this.status       = "EXECUTING";
        this.executedAt   = Instant.now();
        this.inputPayload = inputPayloadJson;
        this.attempt++;
    }

    public void markCompleted(String outputPayloadJson) {
        long duration      = Instant.now().toEpochMilli() - executedAt.toEpochMilli();
        this.status        = "COMPLETED";
        this.outputPayload = outputPayloadJson;
        this.completedAt   = Instant.now();
        this.durationMs    = duration;
        this.nextRetryAt   = null;
    }

    public void markFailed(String error, String trace, Instant nextRetry) {
        this.status      = "FAILED";
        this.lastError   = error;
        this.errorTrace  = trace;
        this.nextRetryAt = nextRetry;
        this.completedAt = nextRetry == null ? Instant.now() : null;
    }

    public void markCompensating() {
        this.status = "COMPENSATING";
    }

    public void markCompensated() {
        this.status      = "COMPENSATED";
        this.completedAt = Instant.now();
    }

    public void markSkipped() {
        this.status = "SKIPPED";
    }

    public void markPending() {
        this.status      = "PENDING";
        this.attempt     = 0;
        this.lastError   = null;
        this.errorTrace  = null;
        this.nextRetryAt = null;
        this.completedAt = null;
    }

    public boolean isExhausted() {
        return maxAttempts > 0 && attempt >= maxAttempts;
    }
}
