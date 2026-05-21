package io.sagaweaw.spring.repository;

import io.sagaweaw.spring.entity.SagaEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SagaEventRepository extends JpaRepository<SagaEventEntity, String> {

    List<SagaEventEntity> findBySagaIdOrderByCreatedAtAsc(String sagaId);

    List<SagaEventEntity> findBySagaIdAndEventTypeOrderByCreatedAtAsc(
            String sagaId, String eventType);

    long countByEventType(String eventType);
}
