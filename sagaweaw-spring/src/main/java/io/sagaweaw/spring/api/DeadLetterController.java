package io.sagaweaw.spring.api;

import io.sagaweaw.spring.engine.SpringSagaEngine;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.repository.DeadLetterRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
            @RequestParam(defaultValue = "false") boolean includeReprocessed) {
        var entities = includeReprocessed
                ? deadLetterRepository.findAll()
                : deadLetterRepository.findByReprocessedFalseOrderByCreatedAtAsc();
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

    private Map<String, String> loadSagaNames(List<String> sagaIds) {
        return sagaRepository.findAllById(sagaIds).stream()
                .collect(Collectors.toMap(SagaEntity::getId, SagaEntity::getName));
    }
}
