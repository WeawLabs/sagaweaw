package io.sagaweaw.spring.repository;

import io.sagaweaw.spring.entity.SagaArchiveEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaArchiveRepository extends JpaRepository<SagaArchiveEntity, String> {}
