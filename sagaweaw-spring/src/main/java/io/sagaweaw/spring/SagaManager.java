package io.sagaweaw.spring;

import io.sagaweaw.core.IdempotencyKey;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaDefinition;
import io.sagaweaw.core.SagaExecution;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.spring.engine.SagaRegistry;
import io.sagaweaw.spring.engine.SpringSagaEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The single entry point for developers using Sagaweaw.
 * Inject this bean and call start() — nothing else is needed.
 *
 * <pre>
 * {@literal @}Autowired
 * private SagaManager sagaManager;
 *
 * sagaManager.start(OrderSaga.class, new OrderContext(orderId));
 * sagaManager.start(OrderSaga.class, new OrderContext(orderId),
 *     IdempotencyKey.of("order-" + orderId));
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class SagaManager {

    private final SagaRegistry     registry;
    private final SpringSagaEngine engine;

    public <C extends SagaContext> SagaExecution start(
            Class<? extends SagaDefinition<C>> sagaClass,
            C context) {

        SagaFlow<C> flow = registry.getFlow(sagaClass);
        return engine.start(flow, context);
    }

    public <C extends SagaContext> SagaExecution start(
            Class<? extends SagaDefinition<C>> sagaClass,
            C context,
            IdempotencyKey idempotencyKey) {

        SagaFlow<C> flow = registry.getFlow(sagaClass);
        return engine.start(flow, context, idempotencyKey);
    }

    /**
     * Triggers a saga by name with a pre-deserialized context.
     * Used by the built-in trigger endpoint — callers obtain the context class
     * from SagaRegistry.getContextClass(sagaName) and deserialize before calling this.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SagaExecution startByName(String sagaName, SagaContext context) {
        SagaFlow flow = registry.findByName(sagaName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No saga registered with name '" + sagaName + "'. "
                        + "Check that the saga is annotated with @Saga and @Component."));
        return engine.start(flow, context);
    }
}
