package io.sagaweaw.spring.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sagaweaw.core.SagaInstance;
import io.sagaweaw.core.SagaStatus;
import io.sagaweaw.core.StepInstance;
import io.sagaweaw.core.StepStatus;
import io.sagaweaw.spring.entity.SagaEntity;
import io.sagaweaw.spring.entity.SagaStepEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SagaMapper {

    private final ObjectMapper objectMapper;

    public SagaMapper(ObjectMapper objectMapper) {
        // Use a lenient copy: unknown fields (e.g. StepOutput.isEmpty() serialised as
        // "empty", or removed context fields in stored JSON) are silently ignored.
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public SagaInstance toInstance(SagaEntity entity) {
        List<StepInstance> steps = entity.getSteps().stream()
                .map(this::toStepInstance)
                .toList();

        return new SagaInstance(
                entity.getId(),
                entity.getName(),
                SagaStatus.fromPersistenceName(entity.getStatus()),
                entity.getContextJson(),
                steps,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCompletedAt(),
                entity.getVersion()
        );
    }

    public StepInstance toStepInstance(SagaStepEntity entity) {
        return new StepInstance(
                entity.getStepName(),
                entity.getStepOrder(),
                StepStatus.fromPersistenceName(entity.getStatus()),
                entity.getAttempt(),
                entity.getMaxAttempts(),
                entity.getInputPayload(),
                entity.getOutputPayload(),
                entity.getLastError(),
                entity.getErrorTrace(),
                entity.getNextRetryAt(),
                entity.getExecutedAt(),
                entity.getCompletedAt(),
                entity.getDurationMs() != null ? entity.getDurationMs() : 0L
        );
    }

    public String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Failed to serialize to JSON: {}", value.getClass().getSimpleName(), e);
            return "{}";
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to deserialize JSON to {}", type.getSimpleName(), e);
            return null;
        }
    }
}
