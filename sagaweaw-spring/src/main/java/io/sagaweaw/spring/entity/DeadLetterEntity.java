package io.sagaweaw.spring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "dead_letters",
        indexes = {
                @Index(name = "idx_dead_letters_saga_id",  columnList = "saga_id"),
                @Index(name = "idx_dead_letters_unreproc", columnList = "reprocessed, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class DeadLetterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(name = "saga_id", nullable = false, updatable = false)
    private String sagaId;

    @Column(name = "step_name", nullable = false, length = 255)
    private String stepName;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "error_trace", columnDefinition = "text")
    private String errorTrace;

    // Context snapshot at failure time — enables reprocessing without the original saga (ADR-005)
    @Column(name = "context_snapshot", columnDefinition = "text")
    private String contextSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean reprocessed = false;

    @Column(name = "reprocessed_at")
    private Instant reprocessedAt;

    @Column(name = "reprocessed_by", length = 255)
    private String reprocessedBy;

    public static DeadLetterEntity create(String sagaId, String stepName,
                                          String errorMessage, String errorTrace,
                                          String contextSnapshot) {
        DeadLetterEntity entity = new DeadLetterEntity();
        entity.sagaId          = sagaId;
        entity.stepName        = stepName;
        entity.errorMessage    = errorMessage;
        entity.errorTrace      = errorTrace;
        entity.contextSnapshot = contextSnapshot;
        entity.createdAt       = Instant.now();
        return entity;
    }

    public void markReprocessed(String reprocessedBy) {
        this.reprocessed   = true;
        this.reprocessedAt = Instant.now();
        this.reprocessedBy = reprocessedBy;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
