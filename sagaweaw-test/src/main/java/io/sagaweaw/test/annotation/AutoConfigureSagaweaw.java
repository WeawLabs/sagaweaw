package io.sagaweaw.test.annotation;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Activates the Sagaweaw auto-configurations for a test slice.
 * The list of auto-configurations is in:
 * META-INF/spring/io.sagaweaw.test.annotation.AutoConfigureSagaweaw.imports
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ImportAutoConfiguration
public @interface AutoConfigureSagaweaw {
}
