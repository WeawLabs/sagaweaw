package io.sagaweaw.kotlin

import io.sagaweaw.core.RetryPolicy
import io.sagaweaw.core.SagaBuilder
import io.sagaweaw.core.SagaContext
import java.util.function.Consumer
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Kotlin DSL scope for configuring a single saga step.
 *
 * Wraps SagaBuilder.SagaStepBuilder and exposes unambiguous Kotlin-friendly
 * methods — no Consumer<C> or explicit type annotations required.
 *
 * Created by the [step] extensions; never instantiated directly.
 */
class KotlinStepDsl<C : SagaContext> @PublishedApi internal constructor(
    @PublishedApi internal val builder: SagaBuilder.SagaStepBuilder<C>,
) {

    fun invoke(action: (C) -> Unit) {
        builder.invoke(Consumer(action))
    }

    fun compensate(action: (C) -> Unit) {
        builder.compensate(Consumer(action))
    }

    fun retry(policy: RetryPolicy) {
        builder.retryPolicy(policy)
    }

    fun timeout(duration: Duration) {
        builder.timeout(duration.toJavaDuration())
    }
}
