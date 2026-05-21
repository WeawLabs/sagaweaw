package io.sagaweaw.spring.scheduler;

import io.sagaweaw.spring.engine.SpringSagaEngine;
import io.sagaweaw.spring.entity.SagaStepEntity;
import io.sagaweaw.spring.repository.SagaStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

/**
 * Polls for failed steps whose retry window has passed and re-executes them.
 * Responsibility boundary: this class only asks "is there work to do?"
 * and delegates. RetryPolicy decides delay. StepExecutor handles execution.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetryScheduler {

    private final SagaStepRepository stepRepository;
    private final SpringSagaEngine   engine;

    @Scheduled(fixedDelayString = "${sagaweaw.retry.polling-interval-ms:2000}")
    public void poll() {
        List<SagaStepEntity> stepsToRetry =
                stepRepository.findStepsToRetry(Instant.now(), PageRequest.of(0, 100));

        if (stepsToRetry.isEmpty()) return;

        log.debug("RetryScheduler found {} step(s) to retry", stepsToRetry.size());

        stepsToRetry.forEach(step -> {
            try {
                engine.executeStep(
                        step.getSaga().getId(),
                        step.getStepName(),
                        step.getAttempt()
                );
            } catch (Exception e) {
                log.error("Retry failed for step '{}' in saga '{}' — will retry again later",
                        step.getStepName(), step.getSaga().getId(), e);
            }
        });
    }
}
