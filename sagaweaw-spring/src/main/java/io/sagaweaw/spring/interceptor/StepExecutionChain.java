package io.sagaweaw.spring.interceptor;

import io.sagaweaw.core.SagaContext;
import io.sagaweaw.core.SagaStep;
import io.sagaweaw.core.StepOutput;

import java.util.List;

/**
 * Builds and runs the interceptor chain, ending with the real invoker.
 * A new chain runner is created per invocation so instances are reusable
 * across multiple calls without shared mutable state.
 */
public class StepExecutionChain {

    private final List<SagaStepInterceptor> interceptors;

    public StepExecutionChain(List<SagaStepInterceptor> interceptors) {
        this.interceptors = List.copyOf(interceptors);
    }

    public <C extends SagaContext> StepOutput proceed(SagaStep<C> step, C context)
            throws Exception {
        return proceedAt(0, step, context);
    }

    <C extends SagaContext> StepOutput proceedAt(int index, SagaStep<C> step, C context)
            throws Exception {
        if (index < interceptors.size()) {
            SagaStepInterceptor interceptor = interceptors.get(index);
            StepExecutionChain rest = new StepExecutionChain(interceptors) {
                @Override
                public <CC extends SagaContext> StepOutput proceed(SagaStep<CC> s, CC c)
                        throws Exception {
                    return proceedAt(index + 1, s, c);
                }
            };
            return interceptor.intercept(step, context, rest);
        }
        return step.invoke(context);
    }
}
