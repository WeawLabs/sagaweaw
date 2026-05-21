package io.sagaweaw.core;

import java.util.function.Consumer;

/**
 * Lifecycle callbacks invoked at critical points of a Saga's execution.
 * Built by SagaBuilder via .onSuccess(), .onCompensated(), .onFailure().
 */
public record SagaFlowHooks<C extends SagaContext>(
        Consumer<C> onSuccess,
        Consumer<C> onCompensated,
        TriConsumer<C, String, String> onFailure
) {

    public static <C extends SagaContext> SagaFlowHooks<C> empty() {
        return new SagaFlowHooks<>(ctx -> {}, ctx -> {}, (ctx, step, error) -> {});
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
