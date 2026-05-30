package io.sagaweaw.spring.engine;

import io.sagaweaw.core.SagaSampler;
import io.sagaweaw.spring.SagaManager;
import io.sagaweaw.spring.annotation.AutoStart;
import io.sagaweaw.spring.annotation.Saga;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;

/**
 * Fires sample contexts for all {@code @AutoStart} sagas on application startup.
 *
 * <p>Only active when {@code sagaweaw.auto-start.enabled=true}.
 * Failures are logged as warnings and never crash the application.
 */
@RequiredArgsConstructor
@Slf4j
public class SagaAutoStartRunner implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final SagaManager        sagaManager;

    @Override
    public void run(ApplicationArguments args) {
        applicationContext.getBeansWithAnnotation(AutoStart.class)
                .values()
                .forEach(bean -> {
                    if (!(bean instanceof SagaSampler<?> sampler)) {
                        log.warn("@AutoStart on '{}' ignored — bean does not implement SagaSampler",
                                bean.getClass().getSimpleName());
                        return;
                    }
                    Saga sagaAnnotation = bean.getClass().getAnnotation(Saga.class);
                    if (sagaAnnotation == null) {
                        log.warn("@AutoStart on '{}' ignored — bean is not annotated with @Saga",
                                bean.getClass().getSimpleName());
                        return;
                    }
                    String sagaName = sagaAnnotation.name();
                    try {
                        var context = sampler.sampleContext();
                        sagaManager.startByName(sagaName, context);
                        log.info("sagaweaw.auto-start: fired sample for saga '{}'", sagaName);
                    } catch (Exception e) {
                        log.warn("sagaweaw.auto-start: failed to fire sample for saga '{}': {}",
                                sagaName, e.getMessage());
                    }
                });
    }
}
