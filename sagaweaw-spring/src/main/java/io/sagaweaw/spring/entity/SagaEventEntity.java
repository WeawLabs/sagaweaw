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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// INSERT-only — no UPDATE or DELETE ever. Compensation appends a new STEP_COMPENSATED event.
@Entity
@Table(
        name = "saga_events",
        indexes = {
                @Index(name = "idx_saga_events_saga_id", columnList = "saga_id"),
                @Index(name = "idx_saga_events_created", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor
public class SagaEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saga_id", nullable = false, updatable = false)
    private SagaEntity saga;

    @Column(name = "step_name", length = 255)
    private String stepName;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SagaEventEntity of(SagaEntity saga, String eventType, String payload) {
        return of(saga, null, eventType, payload);
    }

    public static SagaEventEntity of(SagaEntity saga, String stepName,
                                     String eventType, String payload) {
        SagaEventEntity event = new SagaEventEntity();
        event.saga      = saga;
        event.stepName  = stepName;
        event.eventType = eventType;
        event.payload   = payload;
        event.createdAt = Instant.now();
        return event;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
