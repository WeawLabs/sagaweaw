package io.sagaweaw.spring.repository;

import io.sagaweaw.spring.entity.OutboxMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, String> {

    @Query("""
        SELECT m FROM OutboxMessageEntity m
        WHERE m.published = false
        ORDER BY m.createdAt ASC
    """)
    List<OutboxMessageEntity> findUnpublished(Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
        UPDATE OutboxMessageEntity m
        SET m.published = true, m.publishedAt = :now
        WHERE m.id = :id
    """)
    void markPublished(@Param("id") String id, @Param("now") Instant now);

    long countByPublished(boolean published);
}
