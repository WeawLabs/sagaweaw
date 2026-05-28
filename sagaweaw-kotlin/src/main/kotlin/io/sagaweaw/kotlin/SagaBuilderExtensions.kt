package io.sagaweaw.kotlin

import io.sagaweaw.core.SagaBuilder
import io.sagaweaw.core.SagaContext

/**
 * Kotlin step DSL for SagaBuilder.
 *
 * The 2-arg signature (name + lambda) is unambiguous to Kotlin — it shadows
 * neither Java member, which only takes 1 arg. This is the entry point for
 * the first step in a saga definition.
 *
 * Usage:
 * ```kotlin
 * override fun define(saga: SagaBuilder<Context>): SagaFlow<Context> = saga
 *     .step("reserve-inventory") {
 *         invoke { ctx -> inventoryService.reserve(ctx.itemId) }
 *         compensate { ctx -> inventoryService.release(ctx.itemId) }
 *     }
 *     .step("charge-payment") {
 *         invoke { ctx -> paymentService.charge(ctx.customerId, ctx.amount) }
 *         compensate { ctx -> paymentService.refund(ctx.chargeId) }
 *         retry(exponentialRetry(3, 5.seconds))
 *     }
 *     .build()
 * ```
 */
fun <C : SagaContext> SagaBuilder<C>.step(
    name: String,
    block: KotlinStepDsl<C>.() -> Unit,
): SagaBuilder.SagaStepBuilder<C> {
    val javaStep = step(name)
    KotlinStepDsl(javaStep).block()
    return javaStep
}

/**
 * Kotlin step DSL for SagaStepBuilder — enables chaining after the first step.
 *
 * Same 2-vs-1-arg disambiguation as above: Java member takes 1 arg,
 * this extension takes 2 (name + lambda), so Kotlin picks the right one.
 */
fun <C : SagaContext> SagaBuilder.SagaStepBuilder<C>.step(
    name: String,
    block: KotlinStepDsl<C>.() -> Unit,
): SagaBuilder.SagaStepBuilder<C> {
    val javaStep = step(name)
    KotlinStepDsl(javaStep).block()
    return javaStep
}
