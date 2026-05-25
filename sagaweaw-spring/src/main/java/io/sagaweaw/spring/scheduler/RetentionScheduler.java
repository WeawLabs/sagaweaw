package io.sagaweaw.spring.scheduler;

import io.sagaweaw.spring.config.SagaProperties;
import io.sagaweaw.spring.entity.SagaArchiveEntity;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.entity.SagaStepEntity;
import io.sagaweaw.spring.repository.SagaArchiveRepository;
import io.sagaweaw.spring.repository.SagaEventRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class RetentionScheduler {

    private final SagaRepository        sagaRepository;
    private final SagaStepRepository    stepRepository;
    private final SagaEventRepository   eventRepository;
    private final SagaArchiveRepository archiveRepository;
    private final SagaProperties        properties;

    @Scheduled(cron = "${sagaweaw.data.archive-cron:0 0 2 * * *}")
    @Transactional
    public void archive() {
        SagaProperties.Data cfg = properties.data();
        if (cfg == null) return;

        // Pass 1 — COMPLETED and COMPENSATED (short retention, default 30 days)
        if (cfg.retentionDays() > 0) {
            Instant threshold = Instant.now().minus(cfg.retentionDays(), ChronoUnit.DAYS);
            List<SagaEntity> completed = sagaRepository.findCompletedBefore(threshold, PageRequest.of(0, 500));
            if (!completed.isEmpty()) {
                log.info("sagaweaw: archiving {} COMPLETED/COMPENSATED saga(s) older than {} days",
                        completed.size(), cfg.retentionDays());
                completed.forEach(this::archiveSaga);
            }
        }

        // Pass 2 — FAILED (longer retention, default 90 days — dev needs time to investigate)
        int failedDays = cfg.failedRetentionDays() > 0 ? cfg.failedRetentionDays()
                       : cfg.retentionDays() > 0       ? cfg.retentionDays()
                       : 0;
        if (failedDays > 0) {
            Instant failedThreshold = Instant.now().minus(failedDays, ChronoUnit.DAYS);
            List<SagaEntity> failed = sagaRepository.findFailedBefore(failedThreshold, PageRequest.of(0, 500));
            if (!failed.isEmpty()) {
                log.info("sagaweaw: archiving {} FAILED saga(s) older than {} days",
                        failed.size(), failedDays);
                failed.forEach(this::archiveSaga);
            }
        }
    }

    private void archiveSaga(SagaEntity saga) {
        List<SagaStepEntity> steps = stepRepository.findBySagaIdOrderByStepOrderAsc(saga.getId());
        archiveRepository.save(SagaArchiveEntity.from(saga, buildStepsSnapshot(steps)));
        eventRepository.deleteBySagaId(saga.getId());
        stepRepository.deleteBySagaId(saga.getId());
        sagaRepository.delete(saga);
    }

    private static String buildStepsSnapshot(List<SagaStepEntity> steps) {
        if (steps.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < steps.size(); i++) {
            SagaStepEntity s = steps.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(escape(s.getStepName())).append('"')
              .append(",\"status\":\"").append(s.getStatus()).append('"')
              .append(",\"attempt\":").append(s.getAttempt())
              .append(",\"durationMs\":").append(s.getDurationMs() != null ? s.getDurationMs() : "null")
              .append('}');
        }
        return sb.append(']').toString();
    }

    private static String escape(String v) {
        return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
