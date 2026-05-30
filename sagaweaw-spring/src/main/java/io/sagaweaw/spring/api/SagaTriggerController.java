package io.sagaweaw.spring.api;

import tools.jackson.databind.ObjectMapper;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaExecution;
import io.sagaweaw.core.SagaSampler;
import io.sagaweaw.spring.SagaManager;
import io.sagaweaw.spring.engine.SagaRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in trigger endpoint — allows the dashboard (or any HTTP client) to
 * start a registered saga by name with a JSON context payload.
 *
 * Protected by the same ObservabilityTokenInterceptor as all /api/sagas/** paths.
 *
 * POST /api/sagas/trigger/{sagaName}
 *   Body: JSON object matching the saga's Context class fields.
 *   Returns: { sagaId, sagaName, startedAt, idempotent }
 *
 * GET /api/sagas/registry
 *   Returns: list of registered sagas with their context class and field schema.
 */
@RestController
@RequestMapping("/api/sagas")
@RequiredArgsConstructor
public class SagaTriggerController {

    private final SagaRegistry     registry;
    private final SagaManager      sagaManager;
    private final ObjectMapper     objectMapper;
    private final ApplicationContext applicationContext;

    @PostMapping("/trigger/{sagaName}")
    public ResponseEntity<?> trigger(
            @PathVariable String sagaName,
            @RequestBody Map<String, Object> body) {

        Class<?> contextClass = registry.getContextClass(sagaName);
        if (contextClass == null) {
            List<String> registered = registry.all().values().stream()
                    .map(f -> f.sagaName())
                    .sorted()
                    .toList();
            return ResponseEntity.badRequest().body(Map.of(
                    "error",      "No saga registered with name: " + sagaName,
                    "registered", registered
            ));
        }

        try {
            SagaContext context = (SagaContext) objectMapper.convertValue(body, contextClass);
            SagaExecution execution = sagaManager.startByName(sagaName, context);
            return ResponseEntity.ok(execution);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error",        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    "contextClass", contextClass.getSimpleName(),
                    "hint",         "Ensure the JSON body matches all required fields of " + contextClass.getSimpleName()
            ));
        }
    }

    @GetMapping("/registry")
    public ResponseEntity<List<Map<String, Object>>> listRegistry() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<Class<?>, io.sagaweaw.core.SagaFlow<?>> entry : registry.all().entrySet()) {
            String sagaName    = entry.getValue().sagaName();
            Class<?> contextClass = registry.getContextClass(sagaName);

            Map<String, String> fields = new LinkedHashMap<>();
            if (contextClass != null) {
                for (Field field : contextClass.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (field.isSynthetic())                       continue;
                    fields.put(field.getName(), field.getType().getSimpleName());
                }
            }

            Object sampleJson = resolveSampleContext(entry.getKey());

            Map<String, Object> entry2 = new LinkedHashMap<>();
            entry2.put("name",            sagaName);
            entry2.put("definitionClass", entry.getKey().getSimpleName());
            entry2.put("contextClass",    contextClass != null ? contextClass.getSimpleName() : "unknown");
            entry2.put("contextFields",   fields);
            entry2.put("sampleContext",   sampleJson);
            result.add(entry2);
        }

        result.sort((a, b) -> ((String) a.get("name")).compareTo((String) b.get("name")));
        return ResponseEntity.ok(result);
    }

    private Object resolveSampleContext(Class<?> definitionClass) {
        try {
            Object bean = applicationContext.getBean(definitionClass);
            if (bean instanceof SagaSampler<?> sampler) {
                return objectMapper.convertValue(sampler.sampleContext(), Map.class);
            }
        } catch (Exception ignored) {
            // bean not found or conversion failed — sampleContext remains null
        }
        return null;
    }
}
