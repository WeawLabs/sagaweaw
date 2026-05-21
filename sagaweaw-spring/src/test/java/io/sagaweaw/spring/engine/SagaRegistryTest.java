package io.sagaweaw.spring.engine;

import io.sagaweaw.core.SagaBuilder;
import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaDefinition;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.spring.annotation.Saga;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SagaRegistryTest {

    private record TestContext(String id) implements SagaContext {}

    @Saga(name = "order-saga")
    static class OrderSagaDefinition implements SagaDefinition<TestContext> {
        @Override
        public SagaFlow<TestContext> define(SagaBuilder<TestContext> saga) {
            return saga.step("step-1").invoke(ctx -> {}).build();
        }
    }

    @Saga(name = "payment-saga")
    static class PaymentSagaDefinition implements SagaDefinition<TestContext> {
        @Override
        public SagaFlow<TestContext> define(SagaBuilder<TestContext> saga) {
            return saga.step("step-1").invoke(ctx -> {}).build();
        }
    }

    private SagaRegistry registryWith(Object... beans) {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> beanMap = new java.util.LinkedHashMap<>();
        for (Object bean : beans) {
            beanMap.put(bean.getClass().getSimpleName(), bean);
        }
        when(ctx.getBeansWithAnnotation(Saga.class)).thenReturn(beanMap);
        SagaRegistry registry = new SagaRegistry(ctx);
        registry.initialize();
        return registry;
    }

    @Nested
    class Initialize {

        @Test
        void compiles_all_annotated_saga_definitions() {
            SagaRegistry registry = registryWith(new OrderSagaDefinition());

            assertThat(registry.all()).hasSize(1);
        }

        @Test
        void compiles_multiple_sagas() {
            SagaRegistry registry = registryWith(new OrderSagaDefinition(), new PaymentSagaDefinition());

            assertThat(registry.all()).hasSize(2);
        }

        @Test
        void skips_non_SagaDefinition_beans_with_warning() {
            Object notASaga = new Object() {};
            ApplicationContext ctx = mock(ApplicationContext.class);
            when(ctx.getBeansWithAnnotation(Saga.class)).thenReturn(Map.of("bogus", notASaga));
            SagaRegistry registry = new SagaRegistry(ctx);
            registry.initialize();

            assertThat(registry.all()).isEmpty();
        }
    }

    @Nested
    class GetFlow {

        @Test
        void returns_compiled_flow_for_registered_class() {
            SagaRegistry registry = registryWith(new OrderSagaDefinition());

            SagaFlow<TestContext> flow = registry.getFlow(OrderSagaDefinition.class);

            assertThat(flow).isNotNull();
            assertThat(flow.sagaName()).isEqualTo("order-saga");
        }

        @Test
        void throws_for_unknown_class() {
            SagaRegistry registry = registryWith(new OrderSagaDefinition());

            assertThatThrownBy(() -> registry.getFlow(PaymentSagaDefinition.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PaymentSagaDefinition");
        }
    }

    // Intermediate abstract class — SagaDefinition<C> is NOT in getGenericInterfaces() of the concrete class
    static abstract class AbstractSagaBase<C extends SagaContext> implements SagaDefinition<C> {}

    @Saga(name = "indirect-saga")
    static class IndirectSagaDefinition extends AbstractSagaBase<TestContext> {
        @Override
        public SagaFlow<TestContext> define(SagaBuilder<TestContext> saga) {
            return saga.step("step-1").invoke(ctx -> {}).build();
        }
    }

    @Nested
    class GetContextClass {

        @Test
        void returns_context_class_for_registered_saga() {
            SagaRegistry registry = registryWith(new OrderSagaDefinition());

            Class<?> ctx = registry.getContextClass("order-saga");

            assertThat(ctx).isEqualTo(TestContext.class);
        }

        @Test
        void returns_null_for_unknown_saga_name() {
            SagaRegistry registry = registryWith(new OrderSagaDefinition());

            assertThat(registry.getContextClass("unknown-saga")).isNull();
        }

        @Test
        void fails_fast_at_startup_when_context_class_cannot_be_resolved() {
            // IndirectSagaDefinition extends AbstractSagaBase, so getGenericInterfaces()
            // on the concrete class finds AbstractSagaBase, not SagaDefinition directly.
            // This would have silently returned null before the fix, causing NPE at reprocess time.
            assertThatThrownBy(() -> registryWith(new IndirectSagaDefinition()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("IndirectSagaDefinition");
        }
    }

    @Nested
    class All {

        @Test
        void returned_map_is_unmodifiable() {
            SagaRegistry registry = registryWith(new OrderSagaDefinition());

            assertThatThrownBy(() -> registry.all().put(Object.class, null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
