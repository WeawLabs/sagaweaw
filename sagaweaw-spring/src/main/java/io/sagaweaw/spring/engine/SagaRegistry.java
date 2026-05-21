package io.sagaweaw.spring.engine;

import io.sagaweaw.core.SagaBuilder;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaDefinition;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.spring.annotation.Saga;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Scans all @Saga beans at startup, compiles their SagaFlow,
 * and provides lookup by class for the engine.
 * Fails fast at startup if any definition is invalid —
 * better a startup error than a runtime failure at 3am.
 */
@Component
@Slf4j
public class SagaRegistry {

    private final ApplicationContext applicationContext;
    private final Map<Class<?>, SagaFlow<?>> registry = new HashMap<>();
    private final Map<String, Class<?>> contextClasses = new HashMap<>();

    public SagaRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    void initialize() {
        Map<String, Object> sagaBeans =
                applicationContext.getBeansWithAnnotation(Saga.class);

        sagaBeans.values().forEach(bean -> {
            if (!(bean instanceof SagaDefinition<?> definition)) {
                log.warn("Bean {} is annotated with @Saga but does not implement SagaDefinition — skipped",
                        bean.getClass().getSimpleName());
                return;
            }

            Saga annotation = bean.getClass().getAnnotation(Saga.class);
            compile(definition, annotation.name());
        });

        log.info("SagaRegistry initialized with {} saga(s): {}",
                registry.size(),
                registry.keySet().stream().map(Class::getSimpleName).toList());
    }

    private <C extends SagaContext> void compile(
            SagaDefinition<C> definition, String name) {
        try {
            SagaBuilder<C> builder = SagaBuilder.forSaga(name);
            SagaFlow<C> flow = definition.define(builder);
            registry.put(definition.getClass(), flow);
            contextClasses.put(name, extractContextClass(definition));
            log.debug("Compiled SagaFlow '{}' with {} step(s)", name, flow.totalSteps());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to compile Saga '%s' defined in %s: %s"
                            .formatted(name, definition.getClass().getSimpleName(), e.getMessage()), e);
        }
    }

    private Class<?> extractContextClass(SagaDefinition<?> definition) {
        Class<?> targetClass = org.springframework.aop.support.AopUtils.getTargetClass(definition);
        for (var genericInterface : targetClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType pt
                    && pt.getRawType() == SagaDefinition.class) {
                return (Class<?>) pt.getActualTypeArguments()[0];
            }
        }
        throw new IllegalStateException(
                "Cannot resolve context class for '%s' — ensure it directly implements SagaDefinition<YourContext>"
                        .formatted(targetClass.getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    public <C extends SagaContext> SagaFlow<C> getFlow(
            Class<? extends SagaDefinition<C>> sagaClass) {
        SagaFlow<?> flow = registry.get(sagaClass);
        if (flow == null) {
            throw new IllegalArgumentException(
                    "No SagaFlow found for class '%s'. Is it annotated with @Saga?"
                            .formatted(sagaClass.getSimpleName()));
        }
        return (SagaFlow<C>) flow;
    }

    public Class<?> getContextClass(String sagaName) {
        return contextClasses.get(sagaName);
    }

    public Map<Class<?>, SagaFlow<?>> all() {
        return Collections.unmodifiableMap(registry);
    }
}
