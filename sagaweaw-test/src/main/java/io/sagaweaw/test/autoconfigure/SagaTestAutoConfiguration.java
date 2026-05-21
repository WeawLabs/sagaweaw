package io.sagaweaw.test.autoconfigure;

import io.sagaweaw.test.SagaTestKit;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.SagaRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures SagaTestKit when sagaweaw-test is on the classpath.
 * Activated via META-INF/spring/org.springframework.boot.test.autoconfigure.
 * No annotation needed in the test class — just add the dependency.
 */
@AutoConfiguration
@ConditionalOnClass(SagaTestKit.class)
public class SagaTestAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SagaTestKit sagaTestKit(SagaRepository sagaRepository,
                                   SagaStepRepository stepRepository,
                                   SagaMapper mapper) {
        return new SagaTestKit(sagaRepository, stepRepository, mapper);
    }
}
