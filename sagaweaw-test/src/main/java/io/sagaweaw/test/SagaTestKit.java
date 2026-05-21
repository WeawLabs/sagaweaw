package io.sagaweaw.test;

import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.core.StepOutput;
import io.sagaweaw.spring.interceptor.SagaStepInterceptor;
import io.sagaweaw.spring.interceptor.StepExecutionChain;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.SagaRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import io.sagaweaw.test.assertion.SagaAssertions;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testing utility that simulates step failures, timeouts and partial failures
 * without mocking or modifying the engine.
 * Works by plugging into the existing interceptor chain as the highest-priority
 * interceptor. When a step has a configured behavior, it acts before the real
 * invoker runs — using the same retry and compensation flow as production.
 * Usage:
 * <pre>
 * {@literal @}Autowired SagaTestKit testKit;
 * testKit.simulateFailureOn("charge-payment");
 * sagaManager.start(OrderSaga.class, context);
 * testKit.assertSaga(sagaId)
 *     .isCompensated()
 *     .hasCompensatedStep("reserve-inventory");
 * </pre>
 */

@Component
@Order(0)
@RequiredArgsConstructor
public class SagaTestKit implements SagaStepInterceptor {

    private final SagaRepository     sagaRepository;
    private final SagaStepRepository stepRepository;
    private final SagaMapper         mapper;

    private final Map<String, StepBehavior> behaviors   = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();

    // ================================================================
    // SIMULATE — configure behavior before starting the saga
    // ================================================================

    public void simulateFailureOn(String stepName) {
        simulateFailureOn(stepName, new SimulatedStepException(
                "Simulated failure on step '%s'".formatted(stepName)));
    }

    public void simulateFailureOn(String stepName, RuntimeException exception) {
        behaviors.put(stepName, (invoker, counts) -> {
            throw exception;
        });
    }

    public void simulateTimeoutOn(String stepName, Duration delay) {
        behaviors.put(stepName, (invoker, counts) -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new SimulatedTimeoutException(stepName, delay);
        });
    }

    /**
     * Fails the step for the first {@code times} invocations, then succeeds.
     * Tests that the retry mechanism eventually recovers.
     */
    public void simulateFailureOnFirstAttempts(String stepName, int times) {
        callCounts.put(stepName, new AtomicInteger(0));
        behaviors.put(stepName, (invoker, counts) -> {
            int attempt = counts.get(stepName).incrementAndGet();
            if (attempt <= times) {
                throw new SimulatedStepException(
                        "%s — simulated failure on attempt %d of %d"
                                .formatted(stepName, attempt, times));
            }
            return invoker.call();
        });
    }

    /**
     * Clears all configured behaviors and call counters.
     * Called automatically by SagaweawIntegrationTest before each test.
     */
    public void reset() {
        behaviors.clear();
        callCounts.clear();
    }

    // ================================================================
    // ASSERT — fluent assertions after saga execution
    // ================================================================

    public SagaAssertions assertSaga(String sagaId) {
        return new SagaAssertions(sagaId, sagaRepository, stepRepository, mapper);
    }

    // ================================================================
    // INTERCEPTOR
    // ================================================================

    @Override
    public <C extends SagaContext> StepOutput intercept(
            SagaStep<C> step, C context, StepExecutionChain chain) throws Exception {

        StepBehavior behavior = behaviors.get(step.name());
        if (behavior == null) {
            return chain.proceed(step, context);
        }

        return behavior.execute(() -> chain.proceed(step, context), callCounts);
    }

    // ================================================================
    // INTERNAL
    // ================================================================

    @FunctionalInterface
    private interface StepBehavior {
        StepOutput execute(
                Callable<StepOutput> realInvoker,
                Map<String, AtomicInteger> callCounts
        ) throws Exception;
    }

    public static class SimulatedStepException extends RuntimeException {
        public SimulatedStepException(String message) {
            super(message);
        }
    }

    public static class SimulatedTimeoutException extends RuntimeException {
        public SimulatedTimeoutException(String stepName, Duration delay) {
            super("Simulated timeout on step '%s' after %s".formatted(stepName, delay));
        }
    }
}
