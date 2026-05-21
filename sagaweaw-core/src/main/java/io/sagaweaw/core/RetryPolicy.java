package io.sagaweaw.core;

import java.time.Duration;

/**
 * Strategy for retrying a failed step.
 *
 * The attempt parameter is 1-based: 1 = first attempt, 2 = first retry, and so on.
 * shouldRetry(n) answers: "should we retry after the n-th attempt failed?"
 */
public interface RetryPolicy {

    boolean shouldRetry(int attempt);

    Duration nextDelay(int attempt);

    boolean isInfinite();

    int maxAttempts();

    // -- Factories --------------------------------------------------------

    static RetryPolicy none() {
        return new FixedRetryPolicy(1, Duration.ZERO);
    }

    static RetryPolicy fixed(int maxAttempts, Duration delay) {
        return new FixedRetryPolicy(maxAttempts, delay);
    }

    static RetryPolicy exponential(int maxAttempts, Duration initialDelay) {
        return new ExponentialRetryPolicy(maxAttempts, initialDelay);
    }

    // Infinite retry — step becomes RETRIABLE (ADR-006). No compensator allowed.
    static RetryPolicy infinite(Duration delay) {
        return new InfiniteRetryPolicy(delay);
    }

    static RetryPolicy defaultPolicy() {
        return exponential(3, Duration.ofSeconds(5));
    }
}

// -- Package-private implementations ------------------------------------------

record FixedRetryPolicy(int maxAttempts, Duration delay) implements RetryPolicy {
    @Override public boolean  shouldRetry(int attempt) { return attempt < maxAttempts; }
    @Override public Duration nextDelay(int attempt)   { return delay; }
    @Override public boolean  isInfinite()             { return false; }
    @Override public int      maxAttempts()            { return maxAttempts; }
    @Override public String   toString() {
        return "FixedRetry{maxAttempts=%d, delay=%s}".formatted(maxAttempts, delay);
    }
}

record ExponentialRetryPolicy(int maxAttempts, Duration initialDelay) implements RetryPolicy {
    @Override public boolean shouldRetry(int attempt) { return attempt < maxAttempts; }
    @Override public Duration nextDelay(int attempt) {
        // cap at 5 minutes — protection against unreasonable delays in high-attempt scenarios
        long ms = Math.min(initialDelay.toMillis() * (1L << (attempt - 1)), 300_000L);
        return Duration.ofMillis(ms);
    }
    @Override public boolean isInfinite()  { return false; }
    @Override public int     maxAttempts() { return maxAttempts; }
    @Override public String  toString() {
        return "ExponentialRetry{maxAttempts=%d, initialDelay=%s, cap=PT5M}".formatted(maxAttempts, initialDelay);
    }
}

record InfiniteRetryPolicy(Duration delay) implements RetryPolicy {
    @Override public boolean  shouldRetry(int attempt) { return true; }
    @Override public Duration nextDelay(int attempt)   { return delay; }
    @Override public boolean  isInfinite()             { return true; }
    @Override public int      maxAttempts()            { return 0; }
    @Override public String   toString() {
        return "InfiniteRetry{delay=%s}".formatted(delay);
    }
}
