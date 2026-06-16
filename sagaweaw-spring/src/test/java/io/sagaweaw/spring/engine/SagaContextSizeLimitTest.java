package io.sagaweaw.spring.engine;

import io.sagaweaw.core.SagaBuilder;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaContextTooLargeException;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.spring.config.SagaProperties;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.DeadLetterRepository;
import io.sagaweaw.spring.repository.SagaEventRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaContextSizeLimitTest {

    @Mock SagaRegistry           registry;
    @Mock SagaRepository         sagaRepository;
    @Mock SagaStepRepository     stepRepository;
    @Mock SagaEventRepository    eventRepository;
    @Mock DeadLetterRepository   deadLetterRepository;
    @Mock StepExecutor           stepExecutor;
    @Mock CompensationExecutor   compensationExecutor;
    @Mock SagaMapper             mapper;
    @Mock ApplicationEventPublisher publisher;

    private record Ctx(String value) implements SagaContext {}

    private SpringSagaEngine engineWithLimit(int maxBytes) {
        SagaProperties props = new SagaProperties(
                3, 1000L, 5000L, 8484,
                null,
                new SagaProperties.Observability("token", null, true, null, null),
                null, null, null, null, null, null,
                new SagaProperties.Engine(maxBytes)
        );
        return new SpringSagaEngine(registry, sagaRepository, stepRepository,
                eventRepository, deadLetterRepository, stepExecutor,
                compensationExecutor, mapper, publisher, props);
    }

    private SagaFlow<Ctx> flow(String name) {
        return SagaBuilder.<Ctx>forSaga(name)
                .step("noop").invoke(ctx -> {}).build();
    }

    @Test
    void throwsWhenSerializedContextExceedsLimit() {
        var engine = engineWithLimit(10);
        var ctx = new Ctx("x");
        when(mapper.toJson(ctx)).thenReturn("{\"value\":\"" + "x".repeat(100) + "\"}");

        assertThatThrownBy(() -> engine.start(flow("test-saga"), ctx))
                .isInstanceOf(SagaContextTooLargeException.class)
                .hasMessageContaining("test-saga")
                .hasMessageContaining("10");
    }

    @Test
    void exceptionCarriesAccurateMetadata() {
        var engine = engineWithLimit(10);
        var ctx = new Ctx("x");
        String bigJson = "{\"value\":\"" + "x".repeat(100) + "\"}";
        when(mapper.toJson(ctx)).thenReturn(bigJson);

        assertThatThrownBy(() -> engine.start(flow("test-saga"), ctx))
                .isInstanceOfSatisfying(SagaContextTooLargeException.class, ex -> {
                    assertThat(ex.getSagaName()).isEqualTo("test-saga");
                    assertThat(ex.getMaxBytes()).isEqualTo(10);
                    assertThat(ex.getActualBytes()).isEqualTo(bigJson.length());
                });
    }

    @Test
    void doesNotThrowSizeLimitWhenContextIsUnderLimit() {
        var engine = engineWithLimit(1000);
        var ctx = new Ctx("hi");
        when(mapper.toJson(ctx)).thenReturn("{\"value\":\"hi\"}");

        // Will fail further down (mocked repo returns null), but must NOT be SagaContextTooLargeException
        assertThatThrownBy(() -> engine.start(flow("test-saga"), ctx))
                .isNotInstanceOf(SagaContextTooLargeException.class);
    }

    @Test
    void disabledWhenMaxBytesIsZero() {
        var engine = engineWithLimit(0);
        var ctx = new Ctx("x");
        when(mapper.toJson(ctx)).thenReturn("x".repeat(1_000_000));

        assertThatThrownBy(() -> engine.start(flow("test-saga"), ctx))
                .isNotInstanceOf(SagaContextTooLargeException.class);
    }
}
