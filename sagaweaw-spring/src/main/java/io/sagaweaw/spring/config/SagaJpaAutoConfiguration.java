package io.sagaweaw.spring.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Registers sagaweaw entity and repository packages into AutoConfigurationPackages
 * so that JpaRepositoriesAutoConfiguration and HibernateJpaAutoConfiguration
 * discover them regardless of the host application's base package.
 *
 * beforeClassName references avoid a compile-time dependency on spring-boot-autoconfigure
 * classes that are optional in sagaweaw-spring.
 */
@AutoConfiguration(
    beforeName = {
        // Spring Boot 3.x
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        // Spring Boot 4.x
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "io.sagaweaw.spring.config.SagaAutoConfiguration"
    }
)
@ConditionalOnClass(JpaRepository.class)
@ConditionalOnProperty(prefix = "sagaweaw", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(SagaJpaAutoConfiguration.SagaPackagesRegistrar.class)
public class SagaJpaAutoConfiguration {

    static class SagaPackagesRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            AutoConfigurationPackages.register(registry,
                    "io.sagaweaw.spring.entity",
                    "io.sagaweaw.spring.repository");
        }
    }
}
