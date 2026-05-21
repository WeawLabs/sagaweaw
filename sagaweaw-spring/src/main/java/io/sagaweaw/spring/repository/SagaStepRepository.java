package io.sagaweaw.spring.repository;

import io.sagaweaw.spring.entity.SagaStepEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SagaStepRepository extends JpaRepository<SagaStepEntity, String> {

    List<SagaStepEntity> findBySagaIdOrderByStepOrderAsc(String sagaId);

    List<SagaStepEntity> findBySagaIdOrderByStepOrderDesc(String sagaId);

    Optional<SagaStepEntity> findBySagaIdAndStepName(String sagaId, String stepName);

    @Query("""
        SELECT s FROM SagaStepEntity s
        WHERE s.status = 'FAILED'
          AND s.nextRetryAt IS NOT NULL
          AND s.nextRetryAt <= :now
        ORDER BY s.nextRetryAt ASC
    """)
    List<SagaStepEntity> findStepsToRetry(@Param("now") Instant now, Pageable pageable);

    @Query("""
        SELECT s FROM SagaStepEntity s
        JOIN FETCH s.saga
        WHERE s.status = 'FAILED'
          AND s.nextRetryAt IS NOT NULL
        ORDER BY s.nextRetryAt ASC
    """)
    List<SagaStepEntity> findRetrying(Pageable pageable);

    @Query("""
        SELECT s FROM SagaStepEntity s
        WHERE s.saga.id = :sagaId
          AND s.status = 'COMPLETED'
        ORDER BY s.stepOrder DESC
    """)
    List<SagaStepEntity> findCompensableSteps(@Param("sagaId") String sagaId);

    boolean existsBySagaIdAndStepNameAndStatus(String sagaId, String stepName, String status);

    @Query(value = """
            SELECT ss.step_name, s.name,
                   AVG(ss.duration_ms) AS avg_ms, COUNT(*) AS total,
                   SUM(CASE WHEN ss.status = 'FAILED' THEN 1 ELSE 0 END) AS failed
            FROM saga_steps ss
            JOIN sagas s ON ss.saga_id = s.id
            WHERE ss.duration_ms IS NOT NULL
            GROUP BY ss.step_name, s.name
            ORDER BY avg_ms DESC
            """, nativeQuery = true)
    List<Object[]> stepStats();
}
