package io.sagaweaw.spring.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "sagas",
        indexes = {
                @Index(name = "idx_sagas_status",      columnList = "status"),
                @Index(name = "idx_sagas_name",        columnList = "name"),
                @Index(name = "idx_sagas_idempotency", columnList = "idempotency_key", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SagaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String contextJson = "{}";

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    @Column(length = 255)
    private String idempotencyKey;

    // Optimistic locking — prevents concurrent state overwrites without Redis (ADR-004)
    @Version
    @Column(nullable = false)
    private int version = 0;

    @OneToMany(
            mappedBy = "saga",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    @OrderBy("stepOrder ASC")
    private List<SagaStepEntity> steps = new ArrayList<>();

    public static SagaEntity create(String name, String contextJson, String idempotencyKey) {
        SagaEntity entity = new SagaEntity();
        entity.name           = name;
        entity.status         = "STARTED";
        entity.contextJson    = contextJson != null ? contextJson : "{}";
        entity.idempotencyKey = idempotencyKey;
        entity.createdAt      = Instant.now();
        entity.updatedAt      = Instant.now();
        return entity;
    }

    public void markUpdated() {
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.completedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
