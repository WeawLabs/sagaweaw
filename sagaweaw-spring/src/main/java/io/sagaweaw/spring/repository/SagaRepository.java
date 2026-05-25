package io.sagaweaw.spring.repository;

import io.sagaweaw.spring.entity.SagaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SagaRepository extends JpaRepository<SagaEntity, String> {

    Optional<SagaEntity> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = "steps")
    Optional<SagaEntity> findWithStepsById(String id);

    // Eagerly fetches steps to avoid N+1 — use these for listing endpoints
    @EntityGraph(attributePaths = "steps")
    List<SagaEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @EntityGraph(attributePaths = "steps")
    List<SagaEntity> findByNameOrderByCreatedAtDesc(String name, Pageable pageable);

    @EntityGraph(attributePaths = "steps")
    @Query("SELECT s FROM SagaEntity s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY s.createdAt DESC")
    List<SagaEntity> findByNameContaining(@Param("name") String name, Pageable pageable);

    @EntityGraph(attributePaths = "steps")
    @Query("SELECT s FROM SagaEntity s WHERE s.id LIKE CONCAT(:prefix, '%') ORDER BY s.createdAt DESC")
    List<SagaEntity> findByIdStartingWith(@Param("prefix") String prefix, Pageable pageable);

    @EntityGraph(attributePaths = "steps")
    @Query("SELECT s FROM SagaEntity s WHERE CAST(s.contextJson AS String) LIKE :pattern ORDER BY s.createdAt DESC")
    List<SagaEntity> findByContextContaining(@Param("pattern") String pattern, Pageable pageable);

    @EntityGraph(attributePaths = "steps")
    @Query("SELECT s FROM SagaEntity s WHERE s.status IN ('EXECUTING', 'COMPENSATING') AND s.updatedAt < :threshold ORDER BY s.updatedAt ASC")
    List<SagaEntity> findStuck(@Param("threshold") Instant threshold, Pageable pageable);

    @EntityGraph(attributePaths = "steps")
    @Query("SELECT s FROM SagaEntity s ORDER BY s.createdAt DESC")
    List<SagaEntity> findAllWithSteps(Pageable pageable);

    long countByStatus(String status);

    long countByStatusAndName(String status, String name);

    @EntityGraph(attributePaths = "steps")
    @Query("SELECT s FROM SagaEntity s WHERE s.createdAt BETWEEN :from AND :to ORDER BY s.createdAt DESC")
    List<SagaEntity> findByDateRange(@Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Pageable pageable);

    @EntityGraph(attributePaths = "steps")
    @Query("SELECT s FROM SagaEntity s WHERE s.status = :status AND s.createdAt BETWEEN :from AND :to ORDER BY s.createdAt DESC")
    List<SagaEntity> findByStatusAndDateRange(@Param("status") String status,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to,
                                              Pageable pageable);

    @EntityGraph(attributePaths = "steps")
    @Query("SELECT s FROM SagaEntity s WHERE s.name = :name AND s.createdAt BETWEEN :from AND :to ORDER BY s.createdAt DESC")
    List<SagaEntity> findByNameAndDateRange(@Param("name") String name,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to,
                                            Pageable pageable);

    @Query("SELECT s.name, COUNT(s), " +
           "SUM(CASE WHEN s.status = 'COMPLETED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN s.status IN ('FAILED', 'COMPENSATED') THEN 1 ELSE 0 END) " +
           "FROM SagaEntity s GROUP BY s.name ORDER BY COUNT(s) DESC")
    List<Object[]> countGroupedByName();

    @Query(value = "SELECT name, " +
                   "AVG(EXTRACT(EPOCH FROM (completed_at - created_at)) * 1000) " +
                   "FROM sagas WHERE status = 'COMPLETED' AND completed_at IS NOT NULL " +
                   "GROUP BY name",
           nativeQuery = true)
    List<Object[]> avgDurationByName();

    @Query("SELECT COUNT(s) FROM SagaEntity s WHERE s.status IN ('EXECUTING', 'COMPENSATING') AND s.updatedAt < :threshold")
    long countStuck(@Param("threshold") Instant threshold);

    @Query("SELECT s FROM SagaEntity s WHERE s.status IN ('COMPLETED', 'COMPENSATED') AND s.updatedAt < :threshold ORDER BY s.updatedAt ASC")
    List<SagaEntity> findCompletedBefore(@Param("threshold") Instant threshold, Pageable pageable);

    @Query("SELECT s FROM SagaEntity s WHERE s.status = 'FAILED' AND s.updatedAt < :threshold ORDER BY s.updatedAt ASC")
    List<SagaEntity> findFailedBefore(@Param("threshold") Instant threshold, Pageable pageable);

    @Query(value = """
            SELECT instance_id,
                   MAX(updated_at) AS last_seen,
                   SUM(CASE WHEN status IN ('EXECUTING', 'STARTED', 'COMPENSATING') THEN 1 ELSE 0 END) AS active_sagas
            FROM sagas
            WHERE instance_id IS NOT NULL AND updated_at > :since
            GROUP BY instance_id
            ORDER BY last_seen DESC
            """, nativeQuery = true)
    List<Object[]> findActiveInstances(@Param("since") Instant since);

    // Returns 0 on version mismatch — engine must throw OptimisticLockException (ADR-004)
    @Transactional
    @Modifying
    @Query("""
        UPDATE SagaEntity s
        SET s.status = :newStatus, s.updatedAt = :now, s.version = s.version + 1
        WHERE s.id = :sagaId AND s.version = :expectedVersion
    """)
    int updateStatusWithVersion(
            @Param("sagaId") String sagaId,
            @Param("newStatus") String newStatus,
            @Param("expectedVersion") int expectedVersion,
            @Param("now") Instant now
    );
}
