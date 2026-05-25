package io.sagaweaw.spring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sagas_archive")
@Getter
@Setter
@NoArgsConstructor
public class SagaArchiveEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "context_json", columnDefinition = "text", nullable = false)
    private String contextJson = "{}";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(nullable = false)
    private int version;

    @Column(name = "instance_id", length = 255)
    private String instanceId;

    @Column(name = "steps_snapshot", columnDefinition = "text")
    private String stepsSnapshot;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    public static SagaArchiveEntity from(SagaEntity saga, String stepsSnapshot) {
        SagaArchiveEntity a = new SagaArchiveEntity();
        a.id             = saga.getId();
        a.name           = saga.getName();
        a.status         = saga.getStatus();
        a.contextJson    = saga.getContextJson() != null ? saga.getContextJson() : "{}";
        a.createdAt      = saga.getCreatedAt();
        a.updatedAt      = saga.getUpdatedAt();
        a.completedAt    = saga.getCompletedAt();
        a.idempotencyKey = saga.getIdempotencyKey();
        a.version        = saga.getVersion();
        a.instanceId     = saga.getInstanceId();
        a.stepsSnapshot  = stepsSnapshot;
        a.archivedAt     = Instant.now();
        return a;
    }
}
