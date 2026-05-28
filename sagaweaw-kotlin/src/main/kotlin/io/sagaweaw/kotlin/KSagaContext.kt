package io.sagaweaw.kotlin

import io.sagaweaw.core.SagaContext
import java.util.Optional

/**
 * Kotlin-friendly base for SagaContext.
 *
 * Replaces Optional<String> with idiomatic String? so Kotlin users
 * don't need to import or use Java Optional directly.
 *
 * Usage:
 * ```kotlin
 * data class Context(val orderId: UUID) : KSagaContext() {
 *     override fun key() = "order-$orderId"
 * }
 * ```
 */
abstract class KSagaContext : SagaContext {

    open fun key(): String? = null

    final override fun businessKey(): Optional<String> = Optional.ofNullable(key())
}
