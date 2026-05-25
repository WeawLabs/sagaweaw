package io.sagaweaw.spring.api;

import io.sagaweaw.core.SagaEngine.SagaNotFoundException;
import io.sagaweaw.core.SagaInstance;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.spring.config.SagaProperties;
import io.sagaweaw.spring.engine.SagaRegistry;
import io.sagaweaw.spring.engine.SpringSagaEngine;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.DeadLetterRepository;
import io.sagaweaw.spring.repository.OutboxMessageRepository;
import io.sagaweaw.spring.repository.SagaEventRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static java.time.temporal.ChronoUnit.MINUTES;

@RestController
@RequestMapping("/api/sagas")
@RequiredArgsConstructor
public class SagaObservabilityController {

    private final SagaRepository          sagaRepository;
    private final DeadLetterRepository    deadLetterRepository;
    private final SagaEventRepository     sagaEventRepository;
    private final SagaStepRepository      sagaStepRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final SpringSagaEngine        engine;
    private final SagaMapper              mapper;
    private final SagaProperties          properties;
    private final SagaRegistry            registry;

    @GetMapping
    public List<SagaInstance> list(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "50")  int size,
            @RequestParam(required = false)     String status,
            @RequestParam(required = false)     String name,
            @RequestParam(required = false)     String id,
            @RequestParam(required = false)     String contextSearch,
            @RequestParam(required = false)     String idempotencyKey,
            @RequestParam(required = false)     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (idempotencyKey != null) {
            return sagaRepository.findByIdempotencyKey(idempotencyKey)
                    .map(mapper::toInstance)
                    .map(List::of)
                    .orElse(List.of());
        }

        if (contextSearch != null && !contextSearch.isBlank()) {
            return sagaRepository.findByContextContaining("%" + contextSearch + "%", pageable)
                    .stream().map(mapper::toInstance).toList();
        }

        if (id != null) {
            return sagaRepository.findByIdStartingWith(id, pageable)
                    .stream().map(mapper::toInstance).toList();
        }

        boolean hasDate    = from != null || to != null;
        Instant effectFrom = from != null ? from : Instant.EPOCH;
        Instant effectTo   = to   != null ? to   : Instant.now().plusSeconds(60);

        if (hasDate && status != null) {
            return sagaRepository.findByStatusAndDateRange(status, effectFrom, effectTo, pageable)
                    .stream().map(mapper::toInstance).toList();
        }
        if (hasDate && name != null) {
            return sagaRepository.findByNameAndDateRange(name, effectFrom, effectTo, pageable)
                    .stream().map(mapper::toInstance).toList();
        }
        if (hasDate) {
            return sagaRepository.findByDateRange(effectFrom, effectTo, pageable)
                    .stream().map(mapper::toInstance).toList();
        }
        if (status != null) {
            return sagaRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    .stream().map(mapper::toInstance).toList();
        }
        if (name != null) {
            return sagaRepository.findByNameContaining(name, pageable)
                    .stream().map(mapper::toInstance).toList();
        }
        return sagaRepository.findAllWithSteps(pageable)
                .stream().map(mapper::toInstance).toList();
    }

    @GetMapping("/stuck")
    public List<SagaInstance> stuck() {
        SagaProperties.Health cfg = properties.health();
        int minutes   = cfg != null ? cfg.stuckThresholdMinutes() : 15;
        Instant threshold = Instant.now().minus(minutes, MINUTES);
        return sagaRepository.findStuck(threshold, PageRequest.of(0, 50))
                .stream().map(mapper::toInstance).toList();
    }

    public record StepDefinition(String name, String type, int order, boolean compensable) {}

    @GetMapping("/definition/{sagaName}")
    public ResponseEntity<List<StepDefinition>> definition(@PathVariable String sagaName) {
        return registry.findByName(sagaName)
                .map(flow -> {
                    List<SagaStep<?>> steps = (List<SagaStep<?>>) flow.steps();
                    List<StepDefinition> result = new ArrayList<>();
                    for (int i = 0; i < steps.size(); i++) {
                        SagaStep<?> s = steps.get(i);
                        result.add(new StepDefinition(s.name(), s.type().name(), i + 1, s.canBeCompensated()));
                    }
                    return result;
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SagaInstance> get(@PathVariable String id) {
        return engine.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private static final Pattern CONTEXT_VALUE = Pattern.compile(":\"([a-zA-Z0-9\\-_]{4,60})\"");

    @GetMapping("/{id}/related")
    public ResponseEntity<List<SagaInstance>> related(@PathVariable String id) {
        return sagaRepository.findById(id)
                .map(saga -> {
                    List<String> searchTerms = extractContextValues(saga.getContextJson());
                    if (searchTerms.isEmpty()) return List.<SagaInstance>of();

                    Set<String> seen = new HashSet<>(Set.of(id));
                    List<SagaInstance> related = new ArrayList<>();
                    PageRequest small = PageRequest.of(0, 6, Sort.by("createdAt").descending());

                    for (String term : searchTerms) {
                        if (related.size() >= 10) break;
                        sagaRepository.findByContextContaining("%" + term + "%", small)
                                .stream()
                                .filter(e -> seen.add(e.getId()))
                                .map(mapper::toInstance)
                                .forEach(related::add);
                    }
                    return related.subList(0, Math.min(10, related.size()));
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private List<String> extractContextValues(String contextJson) {
        if (contextJson == null || contextJson.isBlank() || contextJson.equals("{}")) return List.of();
        List<String> values = new ArrayList<>();
        Matcher m = CONTEXT_VALUE.matcher(contextJson);
        while (m.find()) values.add(m.group(1));
        return values.stream().distinct().limit(5).toList();
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<SagaEventResponse>> events(@PathVariable String id) {
        if (!sagaRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                sagaEventRepository.findBySagaIdOrderByCreatedAtAsc(id)
                        .stream()
                        .map(SagaEventResponse::from)
                        .toList()
        );
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<?> reprocess(@PathVariable String id) {
        try {
            engine.reprocess(id);
            return ResponseEntity.accepted().build();
        } catch (SagaNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.unprocessableContent().body(Map.of("error", msg));
        }
    }

    @GetMapping("/steps/retrying")
    public List<RetryingStep> retrying(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return sagaStepRepository.findRetrying(PageRequest.of(page, size))
                .stream()
                .map(s -> new RetryingStep(
                        s.getSaga().getId(),
                        s.getSaga().getName(),
                        s.getStepName(),
                        s.getAttempt(),
                        s.getMaxAttempts(),
                        s.getNextRetryAt()))
                .toList();
    }

    public record SagaNameStats(String name, long total, long completed, long failed, Long avgDurationMs) {}

    public record StepStats(String stepName, String sagaName, Long avgDurationMs, long total, long failed) {}

    @GetMapping("/stats/by-name")
    public List<SagaNameStats> statsByName() {
        Map<String, Long> avgMap = sagaRepository.avgDurationByName().stream()
                .filter(row -> row[1] != null)
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        return sagaRepository.countGroupedByName().stream()
                .map(row -> new SagaNameStats(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        avgMap.get((String) row[0])))
                .toList();
    }

    @GetMapping("/steps/stats")
    public List<StepStats> stepsStats() {
        return sagaStepRepository.stepStats().stream()
                .map(row -> new StepStats(
                        (String) row[0],
                        (String) row[1],
                        row[2] != null ? ((Number) row[2]).longValue() : null,
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).longValue()))
                .toList();
    }

    @GetMapping("/metrics")
    public SagaMetrics metrics() {
        long completed   = sagaRepository.countByStatus("COMPLETED");
        long failed      = sagaRepository.countByStatus("FAILED");
        long compensated = sagaRepository.countByStatus("COMPENSATED");
        long terminal    = completed + failed + compensated;
        double successRate = terminal > 0
                ? Math.round((double) completed / terminal * 1000.0) / 10.0
                : 0.0;

        List<SagaMetrics.SagaNameMetrics> byName = sagaRepository.countGroupedByName()
                .stream()
                .map(row -> new SagaMetrics.SagaNameMetrics(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue()
                ))
                .toList();

        return new SagaMetrics(
                sagaRepository.count(),
                sagaRepository.countByStatus("STARTED"),
                sagaRepository.countByStatus("EXECUTING"),
                completed,
                compensated,
                failed,
                deadLetterRepository.countByReprocessedFalse(),
                successRate,
                byName,
                outboxMessageRepository.countByPublished(false)
        );
    }
}
