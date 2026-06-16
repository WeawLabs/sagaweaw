package io.sagaweaw.spring.engine;

import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaEngine;
import io.sagaweaw.core.SagaEngine.SagaNotFoundException;
import io.sagaweaw.core.SagaExecution;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.core.SagaInstance;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.spring.entity.DeadLetterEntity;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.entity.SagaEventEntity;
import io.sagaweaw.spring.entity.SagaStepEntity;
import io.sagaweaw.spring.event.SagaCompensatedEvent;
import io.sagaweaw.spring.event.SagaCompletedEvent;
import io.sagaweaw.spring.event.SagaFailedEvent;
import io.sagaweaw.spring.event.SagaStartedEvent;
import io.sagaweaw.spring.event.StepCompletedEvent;
import io.sagaweaw.spring.event.StepFailedEvent;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.DeadLetterRepository;
import io.sagaweaw.spring.repository.SagaEventRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.sagaweaw.spring.config.SagaProperties;
import io.sagaweaw.core.IdempotencyKey;
import io.sagaweaw.core.SagaContextTooLargeException;
import io.sagaweaw.core.SagaStatus;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpringSagaEngine implements SagaEngine {

    private final SagaRegistry           registry;
    private final SagaRepository         sagaRepository;
    private final SagaStepRepository     stepRepository;
    private final SagaEventRepository    eventRepository;
    private final DeadLetterRepository   deadLetterRepository;
    private final StepExecutor           stepExecutor;
    private final CompensationExecutor   compensationExecutor;
    private final SagaMapper             mapper;
    private final ApplicationEventPublisher publisher;
    private final SagaProperties         properties;

    private static final String INSTANCE_ID = resolveInstanceId();

    private static String resolveInstanceId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            return host + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 12);
        }
    }

    private String effectiveInstanceId() {
        SagaProperties.Instance inst = properties.instance();
        if (inst != null && inst.id() != null && !inst.id().isBlank()) return inst.id();
        return INSTANCE_ID;
    }

    // ================================================================
    // PUBLIC API
    // ================================================================

    @Override
    @Transactional
    public <C extends SagaContext> SagaExecution start(SagaFlow<C> flow, C context) {
        return doStart(flow, context, null);
    }

    @Override
    @Transactional
    public <C extends SagaContext> SagaExecution start(SagaFlow<C> flow, C context,
                               IdempotencyKey idempotencyKey) {
        Optional<SagaEntity> existing =
                sagaRepository.findByIdempotencyKey(idempotencyKey.value());

        if (existing.isPresent()) {
            SagaEntity entity = existing.get();
            log.debug("Returning existing saga '{}' for idempotency key '{}'",
                    entity.getId(), idempotencyKey.value());
            return SagaExecution.existingExecution(
                    entity.getId(), entity.getName(), entity.getCreatedAt());
        }

        return doStart(flow, context, idempotencyKey.value());
    }

    @Override
    @Transactional
    public SagaExecution reprocess(String sagaId) {
        SagaEntity saga = findEntityById(sagaId);

        List<SagaStepEntity> steps = stepRepository.findBySagaIdOrderByStepOrderAsc(sagaId);
        steps.stream()
                .filter(s -> !"COMPLETED".equals(s.getStatus()))
                .forEach(s -> {
                    s.markPending();
                    stepRepository.save(s);
                });

        saga.setStatus("STARTED");
        saga = sagaRepository.save(saga);
        return doReprocess(saga, load(saga));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SagaInstance> findById(String sagaId) {
        return sagaRepository.findById(sagaId).map(mapper::toInstance);
    }

    @Override
    @Transactional
    public void executeStep(String sagaId, String stepName, int attempt) {
        SagaEntity saga = findEntityById(sagaId);
        doExecuteStep(saga, stepName, load(saga));
    }

    @Override
    @Transactional
    public void startCompensation(String sagaId, String failedStep) {
        SagaEntity saga = findEntityById(sagaId);
        transition(sagaId, io.sagaweaw.core.SagaStatus.fromPersistenceName("COMPENSATING"));
        doStartCompensation(saga, failedStep, load(saga));
    }

    @Override
    @Transactional
    public void transition(String sagaId, SagaStatus newStatus) {
        SagaEntity saga = findEntityById(sagaId);
        int updated = sagaRepository.updateStatusWithVersion(
                sagaId, newStatus.persistenceName(), saga.getVersion(), Instant.now());

        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Concurrent modification detected for saga '%s'".formatted(sagaId));
        }
    }

    // ================================================================
    // TYPE-CAPTURE HELPERS  (bind C once load() resolves it)
    // ================================================================

    private <C extends SagaContext> SagaExecution doReprocess(
            SagaEntity saga, LoadedSaga<C> loaded) {
        return executeFlow(saga, loaded.flow(), loaded.context());
    }

    private <C extends SagaContext> void doExecuteStep(
            SagaEntity saga, String stepName, LoadedSaga<C> loaded) {

        String sagaId = saga.getId();

        SagaStepEntity stepEntity = stepRepository
                .findBySagaIdAndStepName(sagaId, stepName)
                .orElseThrow(() -> new SagaNotFoundException(sagaId));

        SagaStep<C> definition = loaded.flow().steps().stream()
                .filter(s -> s.name().equals(stepName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Step '%s' not found in flow '%s'"
                                .formatted(stepName, loaded.flow().sagaName())));

        saga = runStep(saga, stepEntity, definition, loaded.flow(), loaded.context());

        // After a successful retry, continue the flow from the next pending step.
        // If the step failed again, nextRetryAt is set and RetryScheduler will pick it up.
        if ("COMPLETED".equals(stepEntity.getStatus())) {
            executeFlow(saga, loaded.flow(), loaded.context());
        }
    }

    private <C extends SagaContext> void doStartCompensation(
            SagaEntity saga, String failedStep, LoadedSaga<C> loaded) {
        runCompensation(saga, loaded.flow(), loaded.context(), failedStep);
    }

    // ================================================================
    // INTERNAL FLOW
    // ================================================================

    private <C extends SagaContext> SagaExecution doStart(
            SagaFlow<C> flow, C context, String idempotencyKey) {

        String contextJson = mapper.toJson(context);
        enforceContextSizeLimit(flow.sagaName(), contextJson);

        SagaEntity saga = SagaEntity.create(flow.sagaName(), contextJson, idempotencyKey);
        saga.setInstanceId(effectiveInstanceId());
        saga = sagaRepository.save(saga);

        createStepEntities(saga, flow);

        eventRepository.save(SagaEventEntity.of(saga, "SAGA_STARTED", null));

        publisher.publishEvent(new SagaStartedEvent(
                saga.getId(), saga.getName(), Instant.now()));

        log.info("Saga '{}' started with id '{}'", flow.sagaName(), saga.getId());

        return executeFlow(saga, flow, context);
    }

    private <C extends SagaContext> SagaExecution executeFlow(
            SagaEntity saga, SagaFlow<C> flow, C context) {

        List<SagaStep<C>> steps = flow.steps();

        for (SagaStep<C> definition : steps) {
            String currentSagaId = saga.getId();
            SagaStepEntity entity = stepRepository
                    .findBySagaIdAndStepName(currentSagaId, definition.name())
                    .orElseThrow(() -> new IllegalStateException(
                            "Step entity '%s' not found for saga '%s'"
                                    .formatted(definition.name(), currentSagaId)));

            if ("COMPLETED".equals(entity.getStatus()) ||
                    "COMPENSATED".equals(entity.getStatus()) ||
                    "SKIPPED".equals(entity.getStatus())) {
                continue;
            }

            saga = runStep(saga, entity, definition, flow, context);

            if (!"COMPLETED".equals(entity.getStatus())) {
                return SagaExecution.newExecution(saga.getId(), saga.getName(), saga.getCreatedAt());
            }
        }

        completeSaga(saga, flow, context);
        return SagaExecution.newExecution(saga.getId(), saga.getName(), saga.getCreatedAt());
    }

    private <C extends SagaContext> SagaEntity runStep(
            SagaEntity saga,
            SagaStepEntity entity,
            SagaStep<C> definition,
            SagaFlow<C> flow,
            C context) {

        saga.setStatus("EXECUTING");
        saga = sagaRepository.save(saga);

        eventRepository.save(SagaEventEntity.of(
                saga, definition.name(), "STEP_STARTED", null));

        try {
            long stepStart = System.currentTimeMillis();
            stepExecutor.execute(saga, entity, definition, context);
            long durationMs = System.currentTimeMillis() - stepStart;

            eventRepository.save(SagaEventEntity.of(
                    saga, definition.name(), "STEP_COMPLETED", null));

            publisher.publishEvent(new StepCompletedEvent(
                    saga.getId(), saga.getName(), definition.name(), durationMs));

        } catch (StepExecutor.StepExecutionException e) {
            handleStepFailure(saga, entity, definition, flow, context, e);
        }

        return saga;
    }

    private <C extends SagaContext> void handleStepFailure(
            SagaEntity saga,
            SagaStepEntity entity,
            SagaStep<C> definition,
            SagaFlow<C> flow,
            C context,
            Exception cause) {

        String errorMessage = cause.getCause() != null
                ? cause.getCause().getMessage()
                : cause.getMessage();

        publisher.publishEvent(new StepFailedEvent(
                saga.getId(), definition.name(), entity.getAttempt(), errorMessage));

        eventRepository.save(SagaEventEntity.of(
                saga, definition.name(), "STEP_FAILED", null));

        if (definition.shouldRetry(entity.getAttempt())) {
            Instant nextRetry = Instant.now().plus(
                    definition.delayBeforeRetry(entity.getAttempt()));
            entity.markFailed(errorMessage, stackTraceOf(cause), nextRetry);
            stepRepository.save(entity);

            log.warn("Step '{}' failed — scheduled retry at {}",
                    definition.name(), nextRetry);
            return;
        }

        entity.markFailed(errorMessage, stackTraceOf(cause), null);
        stepRepository.save(entity);

        skipRemainingSteps(saga, definition.name(), flow);
        runCompensation(saga, flow, context, definition.name());
    }

    private <C extends SagaContext> void runCompensation(
            SagaEntity saga, SagaFlow<C> flow, C context, String failedStep) {

        saga.setStatus("COMPENSATING");
        saga = sagaRepository.save(saga);

        try {
            compensationExecutor.compensate(saga, flow, context);

            saga.setStatus("COMPENSATED");
            saga.markCompleted();
            saga = sagaRepository.save(saga);

            eventRepository.save(SagaEventEntity.of(saga, "SAGA_COMPENSATED", null));

            publisher.publishEvent(new SagaCompensatedEvent(
                    saga.getId(), saga.getName(), failedStep, Instant.now()));

            flow.hooks().onCompensated().accept(context);

        } catch (Exception e) {
            log.error("Compensation failed for saga '{}' — moving to dead letter",
                    saga.getId(), e);

            saga.setStatus("FAILED");
            saga.markCompleted();
            saga = sagaRepository.save(saga);

            moveToDeadLetter(saga, failedStep, e, mapper.toJson(context));

            publisher.publishEvent(new SagaFailedEvent(
                    saga.getId(), saga.getName(), failedStep, e.getMessage()));

            flow.hooks().onFailure().accept(context, failedStep, e.getMessage());
        }
    }

    private <C extends SagaContext> void completeSaga(
            SagaEntity saga, SagaFlow<C> flow, C context) {

        saga.setStatus("COMPLETED");
        saga.markCompleted();
        saga = sagaRepository.save(saga);

        eventRepository.save(SagaEventEntity.of(saga, "SAGA_COMPLETED", null));

        publisher.publishEvent(new SagaCompletedEvent(
                saga.getId(), saga.getName(),
                saga.getCompletedAt().toEpochMilli() - saga.getCreatedAt().toEpochMilli()));

        flow.hooks().onSuccess().accept(context);

        log.info("Saga '{}' completed successfully in {}ms",
                saga.getName(),
                saga.getCompletedAt().toEpochMilli() - saga.getCreatedAt().toEpochMilli());
    }

    // ================================================================
    // HELPERS
    // ================================================================

    /**
     * Recovers the typed SagaFlow<C> and context C from a persisted SagaEntity.
     * The two casts are correct by construction: the registry always stores
     * SagaFlow<C> keyed by the saga name, and the context class matches C.
     * Java erases C at runtime so the casts are unavoidable at this boundary.
     */
    @SuppressWarnings("unchecked")
    private <C extends SagaContext> LoadedSaga<C> load(SagaEntity saga) {
        SagaFlow<C> flow = (SagaFlow<C>) findFlow(saga.getName());
        Class<C> contextClass = (Class<C>) registry.getContextClass(flow.sagaName());
        C context = mapper.fromJson(saga.getContextJson(), contextClass);
        return new LoadedSaga<>(flow, context);
    }

    private void createStepEntities(SagaEntity saga, SagaFlow<?> flow) {
        List<SagaStepEntity> entities = flow.steps().stream()
                .map(step -> SagaStepEntity.create(
                        saga,
                        step.name(),
                        step.order(),
                        step.retryPolicy().maxAttempts()))
                .toList();

        stepRepository.saveAll(entities);
    }

    private <C extends SagaContext> void skipRemainingSteps(
            SagaEntity saga, String failedStep, SagaFlow<C> flow) {
        List<SagaStep<C>> remaining = flow.steps().stream()
                .dropWhile(s -> !s.name().equals(failedStep))
                .skip(1)
                .toList();

        remaining.forEach(step -> {
            stepRepository.findBySagaIdAndStepName(saga.getId(), step.name())
                    .ifPresent(entity -> {
                        entity.markSkipped();
                        stepRepository.save(entity);
                    });
        });
    }

    private void moveToDeadLetter(SagaEntity saga, String stepName,
                                  Exception e, String contextSnapshot) {
        DeadLetterEntity dead = DeadLetterEntity.create(
                saga.getId(), stepName,
                e.getMessage(), stackTraceOf(e),
                contextSnapshot);

        deadLetterRepository.save(dead);
    }

    private void enforceContextSizeLimit(String sagaName, String contextJson) {
        if (contextJson == null) return;
        SagaProperties.Engine engine = properties.engine();
        int maxBytes = (engine != null && engine.maxContextBytes() > 0) ? engine.maxContextBytes() : 0;
        if (maxBytes > 0 && contextJson.length() > maxBytes) {
            throw new SagaContextTooLargeException(sagaName, maxBytes, contextJson.length());
        }
    }

    private SagaEntity findEntityById(String sagaId) {
        return sagaRepository.findById(sagaId)
                .orElseThrow(() -> new SagaNotFoundException(sagaId));
    }

    private SagaFlow<?> findFlow(String sagaName) {
        return registry.all().values().stream()
                .filter(f -> f.sagaName().equals(sagaName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No SagaFlow found for saga name '%s'".formatted(sagaName)));
    }

    private String stackTraceOf(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private record LoadedSaga<C extends SagaContext>(SagaFlow<C> flow, C context) {}
}
