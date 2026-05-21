package io.sagaweaw.test.assertion;

import io.sagaweaw.core.SagaInstance;
import io.sagaweaw.core.StepInstance;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.SagaRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluent assertions for saga state after execution.
 * Uses Awaitility to handle async saga completion —
 * the dev does not need to add sleep() calls in tests.
 *
 * <pre>
 * testKit.assertSaga(execution.getSagaId())
 *     .withinSeconds(10)
 *     .isCompensated()
 *     .hasCompensatedStep("reserve-inventory")
 *     .hasFailedStep("charge-payment");
 * </pre>
 */
public class SagaAssertions {

    private final String         sagaId;
    private final SagaRepository sagaRepository;
    private final SagaMapper     mapper;

    private Duration timeout = Duration.ofSeconds(10);

    public SagaAssertions(String sagaId,
                          SagaRepository sagaRepository,
                          SagaStepRepository stepRepository,
                          SagaMapper mapper) {
        this.sagaId         = sagaId;
        this.sagaRepository = sagaRepository;
        this.mapper         = mapper;
    }

    public SagaAssertions withinSeconds(int seconds) {
        this.timeout = Duration.ofSeconds(seconds);
        return this;
    }

    public SagaAssertions isCompleted() {
        awaitStatus("COMPLETED");
        return this;
    }

    public SagaAssertions isCompensated() {
        awaitStatus("COMPENSATED");
        return this;
    }

    public SagaAssertions isFailed() {
        awaitStatus("FAILED");
        return this;
    }

    public SagaAssertions hasCompensatedStep(String stepName) {
        awaitStepStatus(stepName, "COMPENSATED");
        return this;
    }

    public SagaAssertions hasCompletedStep(String stepName) {
        awaitStepStatus(stepName, "COMPLETED");
        return this;
    }

    public SagaAssertions hasFailedStep(String stepName) {
        awaitStepStatus(stepName, "FAILED");
        return this;
    }

    public SagaAssertions hasSkippedStep(String stepName) {
        awaitStepStatus(stepName, "SKIPPED");
        return this;
    }

    public SagaAssertions hasRetryCount(String stepName, int expectedRetries) {
        await().atMost(timeout).untilAsserted(() -> {
            StepInstance step = loadSaga().getStep(stepName);
            assertThat(step.attempt() - 1)
                    .as("Expected step '%s' to have %d retries", stepName, expectedRetries)
                    .isEqualTo(expectedRetries);
        });
        return this;
    }

    public SagaAssertions compensationExecutedInReverseOrder(String... stepNames) {
        await().atMost(timeout).untilAsserted(() -> {
            SagaInstance saga = loadSaga();
            for (int i = 0; i < stepNames.length - 1; i++) {
                StepInstance current = saga.getStep(stepNames[i]);
                StepInstance next    = saga.getStep(stepNames[i + 1]);
                assertThat(current.completedAt())
                        .as("Step '%s' should have been compensated before '%s'",
                                stepNames[i], stepNames[i + 1])
                        .isBeforeOrEqualTo(next.completedAt());
            }
        });
        return this;
    }

    // ================================================================
    // INTERNAL
    // ================================================================

    private void awaitStatus(String expectedStatus) {
        await().atMost(timeout).untilAsserted(() -> {
            SagaInstance saga = loadSaga();
            assertThat(saga.status().persistenceName())
                    .as("Expected saga '%s' to have status %s", sagaId, expectedStatus)
                    .isEqualTo(expectedStatus);
        });
    }

    private void awaitStepStatus(String stepName, String expectedStatus) {
        await().atMost(timeout).untilAsserted(() -> {
            StepInstance step = loadSaga().getStep(stepName);
            assertThat(step.status().persistenceName())
                    .as("Expected step '%s' to have status %s", stepName, expectedStatus)
                    .isEqualTo(expectedStatus);
        });
    }

    private SagaInstance loadSaga() {
        return sagaRepository.findWithStepsById(sagaId)
                .map(mapper::toInstance)
                .orElseThrow(() -> new AssertionError(
                        "Saga '%s' not found in database".formatted(sagaId)));
    }
}
