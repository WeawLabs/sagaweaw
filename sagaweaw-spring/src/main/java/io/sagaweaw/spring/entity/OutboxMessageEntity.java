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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// Written in the SAME transaction as the step that produced it — atomicity guarantee (ADR-009)
@Entity
@Table(
        name = "outbox_messages",
        indexes = {
                @Index(name = "idx_outbox_unpublished", columnList = "published, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OutboxMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String id;

    // Intentionally no FK — outbox must be publishable even after the saga is archived
    @Column(name = "saga_id", nullable = false, updatable = false)
    private String sagaId;

    @Column(name = "step_name", length = 255)
    private String stepName;

    @Column(nullable = false, length = 255)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    // Must include idempotency-key header for at-least-once deduplication (ADR-010)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String headers;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static OutboxMessageEntity create(String sagaId, String stepName,
                                             String topic, String payload,
                                             String headers) {
        OutboxMessageEntity entity = new OutboxMessageEntity();
        entity.sagaId    = sagaId;
        entity.stepName  = stepName;
        entity.topic     = topic;
        entity.payload   = payload;
        entity.headers   = headers;
        entity.createdAt = Instant.now();
        return entity;
    }

    public void markPublished() {
        this.published   = true;
        this.publishedAt = Instant.now();
    }

    public void incrementAttempt() {
        this.publishAttempts++;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
