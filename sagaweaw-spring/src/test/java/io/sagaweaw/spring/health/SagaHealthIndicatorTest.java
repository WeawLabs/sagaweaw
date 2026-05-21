package io.sagaweaw.spring.health;

import io.sagaweaw.spring.config.SagaProperties;
import io.sagaweaw.spring.repository.DeadLetterRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaHealthIndicatorTest {

    @Mock SagaRepository sagaRepository;
    @Mock DeadLetterRepository deadLetterRepository;

    SagaHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        SagaProperties properties = new SagaProperties(3, 1000L, 5000L, 8484, null, null, null, null);
        indicator = new SagaHealthIndicator(sagaRepository, deadLetterRepository, properties);
    }

    @Nested
    class WhenHealthy {
        @Test
        void returns_UP_with_details() {
            when(deadLetterRepository.countByReprocessedFalse()).thenReturn(0L);
            when(sagaRepository.countByStatus("EXECUTING")).thenReturn(2L);
            when(sagaRepository.countByStatus("COMPENSATING")).thenReturn(0L);
            when(sagaRepository.countStuck(any(Instant.class))).thenReturn(0L);

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("executing", 2L);
            assertThat(health.getDetails()).containsEntry("stuckSagas", 0L);
            assertThat(health.getDetails()).containsEntry("pendingDeadLetters", 0L);
        }
    }

    @Nested
    class WhenStuckSagas {
        @Test
        void returns_DOWN_when_any_stuck_saga_exists() {
            when(deadLetterRepository.countByReprocessedFalse()).thenReturn(0L);
            when(sagaRepository.countByStatus("EXECUTING")).thenReturn(1L);
            when(sagaRepository.countByStatus("COMPENSATING")).thenReturn(0L);
            when(sagaRepository.countStuck(any(Instant.class))).thenReturn(1L);

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("stuckSagas", 1L);
        }

        @Test
        void stuck_check_disabled_when_threshold_is_zero() {
            SagaProperties props = new SagaProperties(3, 1000L, 5000L, 8484, null, null,
                    new SagaProperties.Health(0, 0), null);
            indicator = new SagaHealthIndicator(sagaRepository, deadLetterRepository, props);

            when(deadLetterRepository.countByReprocessedFalse()).thenReturn(0L);
            when(sagaRepository.countByStatus("EXECUTING")).thenReturn(5L);
            when(sagaRepository.countByStatus("COMPENSATING")).thenReturn(0L);

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("stuckSagas", 0L);
        }
    }

    @Nested
    class WhenDeadLettersExceedThreshold {
        @Test
        void returns_OUT_OF_SERVICE_when_threshold_exceeded() {
            SagaProperties props = new SagaProperties(3, 1000L, 5000L, 8484, null, null,
                    new SagaProperties.Health(30, 5), null);
            indicator = new SagaHealthIndicator(sagaRepository, deadLetterRepository, props);

            when(deadLetterRepository.countByReprocessedFalse()).thenReturn(6L);
            when(sagaRepository.countByStatus("EXECUTING")).thenReturn(0L);
            when(sagaRepository.countByStatus("COMPENSATING")).thenReturn(0L);
            when(sagaRepository.countStuck(any(Instant.class))).thenReturn(0L);

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
            assertThat(health.getDetails()).containsEntry("pendingDeadLetters", 6L);
        }

        @Test
        void dead_letters_below_threshold_still_returns_UP() {
            SagaProperties props = new SagaProperties(3, 1000L, 5000L, 8484, null, null,
                    new SagaProperties.Health(30, 5), null);
            indicator = new SagaHealthIndicator(sagaRepository, deadLetterRepository, props);

            when(deadLetterRepository.countByReprocessedFalse()).thenReturn(4L);
            when(sagaRepository.countByStatus("EXECUTING")).thenReturn(0L);
            when(sagaRepository.countByStatus("COMPENSATING")).thenReturn(0L);
            when(sagaRepository.countStuck(any(Instant.class))).thenReturn(0L);

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }

        @Test
        void dead_letters_with_zero_threshold_returns_UP() {
            when(deadLetterRepository.countByReprocessedFalse()).thenReturn(100L);
            when(sagaRepository.countByStatus("EXECUTING")).thenReturn(0L);
            when(sagaRepository.countByStatus("COMPENSATING")).thenReturn(0L);
            when(sagaRepository.countStuck(any(Instant.class))).thenReturn(0L);

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }
    }

    @Nested
    class WhenBothStuckAndDeadLetters {
        @Test
        void DOWN_takes_precedence_over_OUT_OF_SERVICE() {
            SagaProperties props = new SagaProperties(3, 1000L, 5000L, 8484, null, null,
                    new SagaProperties.Health(30, 5), null);
            indicator = new SagaHealthIndicator(sagaRepository, deadLetterRepository, props);

            when(deadLetterRepository.countByReprocessedFalse()).thenReturn(10L);
            when(sagaRepository.countByStatus("EXECUTING")).thenReturn(1L);
            when(sagaRepository.countByStatus("COMPENSATING")).thenReturn(0L);
            when(sagaRepository.countStuck(any(Instant.class))).thenReturn(1L);

            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        }
    }
}
