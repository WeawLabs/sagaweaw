package io.sagaweaw.spring.repository;

import io.sagaweaw.spring.entity.DeadLetterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, String> {

    List<DeadLetterEntity> findByReprocessedFalseOrderByCreatedAtAsc();

    List<DeadLetterEntity> findBySagaIdOrderByCreatedAtAsc(String sagaId);

    boolean existsBySagaIdAndReprocessedFalse(String sagaId);

    long countByReprocessedFalse();
}
