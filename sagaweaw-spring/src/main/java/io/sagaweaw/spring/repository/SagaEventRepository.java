package io.sagaweaw.spring.repository;

import io.sagaweaw.spring.entity.SagaEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SagaEventRepository extends JpaRepository<SagaEventEntity, String> {

    List<SagaEventEntity> findBySagaIdOrderByCreatedAtAsc(String sagaId);

    List<SagaEventEntity> findBySagaIdAndEventTypeOrderByCreatedAtAsc(
            String sagaId, String eventType);

    long countByEventType(String eventType);

    @Modifying
    @Query("DELETE FROM SagaEventEntity e WHERE e.saga.id = :sagaId")
    void deleteBySagaId(@Param("sagaId") String sagaId);
}
