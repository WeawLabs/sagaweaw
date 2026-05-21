package io.sagaweaw.spring.engine;

import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.core.StepOutput;
import io.sagaweaw.spring.entity.OutboxMessageEntity;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.entity.SagaStepEntity;
import io.sagaweaw.spring.interceptor.SagaStepInterceptor;
import io.sagaweaw.spring.interceptor.StepExecutionChain;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.OutboxMessageRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Executes a single step atomically.
 * The @Transactional boundary here is the core of ADR-009:
 * step state update AND outbox message write happen in the same transaction.
 * If the JVM dies between commit and Kafka publish,
 * the OutboxRelay will republish. The consumer deduplicates via idempotency-key.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StepExecutor {

    private final SagaStepRepository stepRepository;
    private final OutboxMessageRepository outboxRepository;
    private final SagaMapper mapper;
    private final List<SagaStepInterceptor> interceptors;

    @Transactional(noRollbackFor = StepExecutionException.class)
    public <C extends SagaContext> StepOutput execute(
            SagaEntity saga,
            SagaStepEntity stepEntity,
            SagaStep<C> stepDefinition,
            C context) {

        stepEntity.markExecuting(mapper.toJson(context));
        stepRepository.save(stepEntity);

        StepOutput output;
        org.slf4j.MDC.put("sagaId",   saga.getId());
        org.slf4j.MDC.put("sagaName", saga.getName());
        org.slf4j.MDC.put("stepName", stepEntity.getStepName());
        org.slf4j.MDC.put("attempt",  String.valueOf(stepEntity.getAttempt()));
        try {
            output = new StepExecutionChain(interceptors).proceed(stepDefinition, context);
        } catch (Exception e) {
            throw new StepExecutionException(stepEntity.getStepName(), e);
        } finally {
            org.slf4j.MDC.remove("sagaId");
            org.slf4j.MDC.remove("sagaName");
            org.slf4j.MDC.remove("stepName");
            org.slf4j.MDC.remove("attempt");
        }

        stepEntity.markCompleted(mapper.toJson(output));
        stepRepository.save(stepEntity);

        writeOutboxMessage(saga, stepEntity, context);

        log.debug("Step '{}' completed in saga '{}'",
                stepEntity.getStepName(), saga.getName());

        return output;
    }

    @Transactional(noRollbackFor = StepCompensationException.class)
    public <C extends SagaContext> void compensate(
            SagaEntity saga,
            SagaStepEntity stepEntity,
            SagaStep<C> stepDefinition,
            C context) {

        stepEntity.markCompensating();
        stepRepository.save(stepEntity);

        StepOutput originalOutput = deserializeOutput(stepEntity.getOutputPayload());

        org.slf4j.MDC.put("sagaId",   saga.getId());
        org.slf4j.MDC.put("sagaName", saga.getName());
        org.slf4j.MDC.put("stepName", stepEntity.getStepName());
        org.slf4j.MDC.put("attempt",  String.valueOf(stepEntity.getAttempt()));
        try {
            stepDefinition.compensate(context, originalOutput);
        } catch (Exception e) {
            throw new StepCompensationException(stepEntity.getStepName(), e);
        } finally {
            org.slf4j.MDC.remove("sagaId");
            org.slf4j.MDC.remove("sagaName");
            org.slf4j.MDC.remove("stepName");
            org.slf4j.MDC.remove("attempt");
        }

        stepEntity.markCompensated();
        stepRepository.save(stepEntity);

        log.debug("Step '{}' compensated in saga '{}'",
                stepEntity.getStepName(), saga.getName());
    }

    private void writeOutboxMessage(SagaEntity saga, SagaStepEntity step, Object context) {
        String idempotencyKey = "%s:%s:%d".formatted(
                saga.getId(), step.getStepName(), step.getAttempt());

        String headers = mapper.toJson(Map.of("idempotency-key", idempotencyKey));
        String topic   = "sagaweaw.%s.%s".formatted(saga.getName(), step.getStepName());
        String payload = mapper.toJson(context);

        OutboxMessageEntity message = OutboxMessageEntity.create(
                saga.getId(), step.getStepName(), topic, payload, headers);

        outboxRepository.save(message);
    }

    private StepOutput deserializeOutput(String json) {
        if (json == null || json.isBlank()) return StepOutput.EMPTY;
        return mapper.fromJson(json, StepOutput.class);
    }

    public static class StepExecutionException extends RuntimeException {
        public StepExecutionException(String stepName, Throwable cause) {
            super("Step '%s' threw an exception during execution".formatted(stepName), cause);
        }
    }

    public static class StepCompensationException extends RuntimeException {
        public StepCompensationException(String stepName, Throwable cause) {
            super("Step '%s' threw an exception during compensation".formatted(stepName), cause);
        }
    }
}
