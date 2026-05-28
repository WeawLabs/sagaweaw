package io.sagaweaw.kotlin

import io.sagaweaw.core.IdempotencyKey
import io.sagaweaw.core.SagaContext
import io.sagaweaw.core.SagaDefinition
import io.sagaweaw.core.SagaExecution
import io.sagaweaw.spring.SagaManager

/**
 * Reified extension for SagaManager.start().
 *
 * Eliminates Class<T> boilerplate — Kotlin infers the saga class at compile time.
 *
 * Java:   sagaManager.start(OrderSaga.class, context)
 * Kotlin: sagaManager.start<OrderSaga>(context)
 */
inline fun <reified S : SagaDefinition<C>, C : SagaContext> SagaManager.start(
    context: C,
): SagaExecution = start(S::class.java, context)

inline fun <reified S : SagaDefinition<C>, C : SagaContext> SagaManager.start(
    context: C,
    idempotencyKey: IdempotencyKey,
): SagaExecution = start(S::class.java, context, idempotencyKey)
