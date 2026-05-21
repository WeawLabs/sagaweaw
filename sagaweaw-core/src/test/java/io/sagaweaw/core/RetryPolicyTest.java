package io.sagaweaw.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    @Nested
    class None {

        @Test
        void does_not_retry_after_first_attempt() {
            assertThat(RetryPolicy.none().shouldRetry(1)).isFalse();
        }

        @Test
        void is_not_infinite() {
            assertThat(RetryPolicy.none().isInfinite()).isFalse();
        }
    }

    @Nested
    class Fixed {

        @Test
        void retries_while_below_max_attempts() {
            var policy = RetryPolicy.fixed(3, Duration.ofSeconds(1));
            assertThat(policy.shouldRetry(1)).isTrue();
            assertThat(policy.shouldRetry(2)).isTrue();
            assertThat(policy.shouldRetry(3)).isFalse();
        }

        @Test
        void delay_is_constant_across_attempts() {
            var delay = Duration.ofSeconds(2);
            var policy = RetryPolicy.fixed(3, delay);
            assertThat(policy.nextDelay(1)).isEqualTo(delay);
            assertThat(policy.nextDelay(2)).isEqualTo(delay);
        }

        @Test
        void is_not_infinite() {
            assertThat(RetryPolicy.fixed(3, Duration.ZERO).isInfinite()).isFalse();
        }
    }

    @Nested
    class Exponential {

        @Test
        void retries_while_below_max_attempts() {
            var policy = RetryPolicy.exponential(4, Duration.ofSeconds(1));
            assertThat(policy.shouldRetry(1)).isTrue();
            assertThat(policy.shouldRetry(3)).isTrue();
            assertThat(policy.shouldRetry(4)).isFalse();
        }

        @Test
        void delay_doubles_each_attempt() {
            var policy = RetryPolicy.exponential(10, Duration.ofSeconds(1));
            assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofSeconds(1));
            assertThat(policy.nextDelay(2)).isEqualTo(Duration.ofSeconds(2));
            assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofSeconds(4));
        }

        @Test
        void delay_is_capped_at_five_minutes() {
            var policy = RetryPolicy.exponential(100, Duration.ofSeconds(1));
            assertThat(policy.nextDelay(99)).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        void is_not_infinite() {
            assertThat(RetryPolicy.exponential(3, Duration.ofSeconds(1)).isInfinite()).isFalse();
        }
    }

    @Nested
    class Infinite {

        @Test
        void always_retries() {
            var policy = RetryPolicy.infinite(Duration.ofSeconds(5));
            assertThat(policy.shouldRetry(1)).isTrue();
            assertThat(policy.shouldRetry(1000)).isTrue();
        }

        @Test
        void delay_is_constant() {
            var delay = Duration.ofSeconds(5);
            var policy = RetryPolicy.infinite(delay);
            assertThat(policy.nextDelay(1)).isEqualTo(delay);
            assertThat(policy.nextDelay(50)).isEqualTo(delay);
        }

        @Test
        void is_infinite() {
            assertThat(RetryPolicy.infinite(Duration.ofSeconds(1)).isInfinite()).isTrue();
        }
    }

    @Nested
    class DefaultPolicy {

        @Test
        void allows_three_total_attempts() {
            var policy = RetryPolicy.defaultPolicy();
            assertThat(policy.shouldRetry(1)).isTrue();
            assertThat(policy.shouldRetry(2)).isTrue();
            assertThat(policy.shouldRetry(3)).isFalse();
        }

        @Test
        void is_not_infinite() {
            assertThat(RetryPolicy.defaultPolicy().isInfinite()).isFalse();
        }
    }

    @Nested
    class MaxAttempts {

        @Test
        void none_has_one_attempt() {
            assertThat(RetryPolicy.none().maxAttempts()).isEqualTo(1);
        }

        @Test
        void fixed_exposes_configured_max() {
            assertThat(RetryPolicy.fixed(7, Duration.ofSeconds(1)).maxAttempts()).isEqualTo(7);
        }

        @Test
        void exponential_exposes_configured_max() {
            assertThat(RetryPolicy.exponential(5, Duration.ofSeconds(1)).maxAttempts()).isEqualTo(5);
        }

        @Test
        void infinite_returns_zero() {
            assertThat(RetryPolicy.infinite(Duration.ofSeconds(1)).maxAttempts()).isZero();
        }
    }
}
