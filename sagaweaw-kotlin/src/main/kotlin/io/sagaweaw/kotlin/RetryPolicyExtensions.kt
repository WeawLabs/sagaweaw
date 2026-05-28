package io.sagaweaw.kotlin

import io.sagaweaw.core.RetryPolicy
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Retry policy factories with kotlin.time.Duration support.
 *
 * Java:   RetryPolicy.exponential(3, Duration.ofSeconds(5))
 * Kotlin: exponentialRetry(3, 5.seconds)
 */
fun exponentialRetry(maxAttempts: Int, initialDelay: Duration): RetryPolicy =
    RetryPolicy.exponential(maxAttempts, initialDelay.toJavaDuration())

fun fixedRetry(maxAttempts: Int, delay: Duration): RetryPolicy =
    RetryPolicy.fixed(maxAttempts, delay.toJavaDuration())

fun infiniteRetry(delay: Duration): RetryPolicy =
    RetryPolicy.infinite(delay.toJavaDuration())
