package io.sagaweaw.spring.api;

import io.sagaweaw.spring.engine.SpringSagaEngine;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.repository.DeadLetterRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterRepository deadLetterRepository;
    private final SagaRepository       sagaRepository;
    private final SpringSagaEngine     engine;

    @GetMapping
    public List<DeadLetterResponse> list(
            @RequestParam(defaultValue = "false") boolean includeReprocessed,
            @RequestParam(required = false) String sagaId) {
        List<io.sagaweaw.spring.entity.DeadLetterEntity> entities;
        if (sagaId != null && !sagaId.isBlank()) {
            entities = deadLetterRepository.findBySagaIdOrderByCreatedAtAsc(sagaId);
        } else {
            entities = includeReprocessed
                    ? deadLetterRepository.findAll()
                    : deadLetterRepository.findByReprocessedFalseOrderByCreatedAtAsc();
        }
        Map<String, String> nameMap = loadSagaNames(entities.stream().map(e -> e.getSagaId()).toList());
        return entities.stream()
                .map(e -> DeadLetterResponse.from(e, nameMap.get(e.getSagaId())))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeadLetterResponse> get(@PathVariable String id) {
        return deadLetterRepository.findById(id).map(e -> {
            String sagaName = sagaRepository.findById(e.getSagaId())
                    .map(SagaEntity::getName).orElse(null);
            return ResponseEntity.ok(DeadLetterResponse.from(e, sagaName));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<?> reprocess(@PathVariable String id) {
        var found = deadLetterRepository.findById(id);
        if (found.isEmpty()) return ResponseEntity.notFound().build();

        var dl = found.get();
        try {
            engine.reprocess(dl.getSagaId());
            dl.markReprocessed("api");
            deadLetterRepository.save(dl);
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.unprocessableEntity().body(Map.of("error", msg));
        }
    }

    @PostMapping("/reprocess-batch")
    public ResponseEntity<Map<String, Long>> reprocessBatch(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body.getOrDefault("ids", List.of());
        long reprocessed = 0;
        long failed      = 0;
        for (String id : ids) {
            try {
                var found = deadLetterRepository.findById(id);
                if (found.isEmpty()) { failed++; continue; }
                var dl = found.get();
                engine.reprocess(dl.getSagaId());
                dl.markReprocessed("api");
                deadLetterRepository.save(dl);
                reprocessed++;
            } catch (Exception e) {
                failed++;
            }
        }
        return ResponseEntity.ok(Map.of("reprocessed", reprocessed, "failed", failed));
    }

    @GetMapping("/export")
    public ResponseEntity<String> export() {
        var entities = deadLetterRepository.findByReprocessedFalseOrderByCreatedAtAsc();
        Map<String, String> nameMap = loadSagaNames(entities.stream().map(e -> e.getSagaId()).toList());
        StringBuilder csv = new StringBuilder("sagaId,sagaName,stepName,createdAt,errorMessage,context\n");
        for (var e : entities) {
            appendCsvRow(csv,
                    e.getSagaId(),
                    nameMap.getOrDefault(e.getSagaId(), ""),
                    e.getStepName(),
                    e.getCreatedAt() != null ? e.getCreatedAt().toString() : "",
                    e.getErrorMessage(),
                    truncate(e.getContextSnapshot(), 200));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "dead-letters.csv");
        return ResponseEntity.ok().headers(headers).body(csv.toString());
    }

    private Map<String, String> loadSagaNames(List<String> sagaIds) {
        return sagaRepository.findAllById(sagaIds).stream()
                .collect(Collectors.toMap(SagaEntity::getId, SagaEntity::getName));
    }

    private static void appendCsvRow(StringBuilder sb, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(fields[i]));
        }
        sb.append('\n');
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }
}
