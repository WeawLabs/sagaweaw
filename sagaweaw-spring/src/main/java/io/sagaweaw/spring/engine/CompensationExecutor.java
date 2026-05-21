package io.sagaweaw.spring.engine;

import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.entity.SagaEventEntity;
import io.sagaweaw.spring.entity.SagaStepEntity;
import io.sagaweaw.spring.event.StepCompensatedEvent;
import io.sagaweaw.spring.repository.SagaEventRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs compensation for every COMPLETED step in reverse order.
 * Each compensation runs in its own transaction via StepExecutor.
 * If one compensation fails, it is retried by RetryScheduler
 * independently of the others — partial compensation progress is preserved.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompensationExecutor {

    private final SagaStepRepository       stepRepository;
    private final SagaEventRepository      eventRepository;
    private final StepExecutor             stepExecutor;
    private final ApplicationEventPublisher publisher;

    public <C extends SagaContext> void compensate(
            SagaEntity saga,
            SagaFlow<C> flow,
            C context) {

        log.info("Starting compensation for saga '{}' id='{}'",
                saga.getName(), saga.getId());

        List<SagaStepEntity> completedEntities =
                stepRepository.findCompensableSteps(saga.getId());

        Map<String, SagaStep<C>> definitionsByName = flow.compensableStepsInReverseOrder()
                .stream()
                .collect(Collectors.toMap(SagaStep::name, Function.identity()));

        eventRepository.save(SagaEventEntity.of(
                saga, "COMPENSATION_STARTED", null));

        for (SagaStepEntity entity : completedEntities) {
            SagaStep<C> definition = definitionsByName.get(entity.getStepName());

            if (definition == null) {
                log.debug("Step '{}' has no compensator (PIVOT or RETRIABLE) — skipping",
                        entity.getStepName());
                continue;
            }

            try {
                stepExecutor.compensate(saga, entity, definition, context);

                eventRepository.save(SagaEventEntity.of(
                        saga, entity.getStepName(), "STEP_COMPENSATED", null));

                publisher.publishEvent(new StepCompensatedEvent(
                        saga.getId(), entity.getStepName(), Instant.now()));

            } catch (StepExecutor.StepCompensationException e) {
                log.error("Compensation failed for step '{}' in saga '{}' — will retry",
                        entity.getStepName(), saga.getId(), e);
                throw e;
            }
        }
    }
}
