package io.sagaweaw.spring.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SagaStepEntityTest {

    private static SagaStepEntity step() {
        return SagaStepEntity.create(new SagaEntity(), "reserve-inventory", 0, 3);
    }

    @Nested
    class Create {

        @Test
        void setsInitialState() {
            SagaStepEntity s = step();

            assertThat(s.getStepName()).isEqualTo("reserve-inventory");
            assertThat(s.getStepOrder()).isZero();
            assertThat(s.getMaxAttempts()).isEqualTo(3);
            assertThat(s.getStatus()).isEqualTo("PENDING");
            assertThat(s.getAttempt()).isZero();
        }
    }

    @Nested
    class MarkExecuting {

        @Test
        void setsStatusAndIncrementsAttempt() {
            SagaStepEntity s = step();
            s.markExecuting("{\"qty\":1}");

            assertThat(s.getStatus()).isEqualTo("EXECUTING");
            assertThat(s.getAttempt()).isEqualTo(1);
            assertThat(s.getInputPayload()).isEqualTo("{\"qty\":1}");
            assertThat(s.getExecutedAt()).isNotNull();
        }

        @Test
        void incrementsAttemptOnEachCall() {
            SagaStepEntity s = step();
            s.markExecuting("{}");
            s.markExecuting("{}");

            assertThat(s.getAttempt()).isEqualTo(2);
        }
    }

    @Nested
    class MarkCompleted {

        @Test
        void setsStatusAndOutputPayload() {
            SagaStepEntity s = step();
            s.markExecuting("{}");
            s.markCompleted("{\"reservationId\":\"r1\"}");

            assertThat(s.getStatus()).isEqualTo("COMPLETED");
            assertThat(s.getOutputPayload()).isEqualTo("{\"reservationId\":\"r1\"}");
        }

        @Test
        void clearsNextRetryAt() {
            SagaStepEntity s = step();
            s.markExecuting("{}");
            s.setNextRetryAt(Instant.now().plusSeconds(10));
            s.markCompleted("{}");

            assertThat(s.getNextRetryAt()).isNull();
        }

        @Test
        void setsDurationMs() {
            SagaStepEntity s = step();
            s.markExecuting("{}");
            s.markCompleted("{}");

            assertThat(s.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        }
    }

    @Nested
    class MarkFailed {

        @Test
        void setsStatusAndErrorFields() {
            SagaStepEntity s = step();
            s.markFailed("timeout", "stack...", null);

            assertThat(s.getStatus()).isEqualTo("FAILED");
            assertThat(s.getLastError()).isEqualTo("timeout");
            assertThat(s.getErrorTrace()).isEqualTo("stack...");
        }

        @Test
        void setsCompletedAtWhenNoRetry() {
            SagaStepEntity s = step();
            s.markFailed("err", null, null);

            assertThat(s.getCompletedAt()).isNotNull();
            assertThat(s.getNextRetryAt()).isNull();
        }

        @Test
        void clearsCompletedAtWhenRetryScheduled() {
            SagaStepEntity s = step();
            Instant retry = Instant.now().plusSeconds(30);
            s.markFailed("err", null, retry);

            assertThat(s.getCompletedAt()).isNull();
            assertThat(s.getNextRetryAt()).isEqualTo(retry);
        }
    }

    @Nested
    class MarkCompensating {

        @Test
        void setsStatus() {
            SagaStepEntity s = step();
            s.markCompensating();

            assertThat(s.getStatus()).isEqualTo("COMPENSATING");
        }
    }

    @Nested
    class MarkCompensated {

        @Test
        void setsStatusAndCompletedAt() {
            SagaStepEntity s = step();
            s.markCompensated();

            assertThat(s.getStatus()).isEqualTo("COMPENSATED");
            assertThat(s.getCompletedAt()).isNotNull();
        }
    }

    @Nested
    class MarkSkipped {

        @Test
        void setsStatus() {
            SagaStepEntity s = step();
            s.markSkipped();

            assertThat(s.getStatus()).isEqualTo("SKIPPED");
        }
    }

    @Nested
    class MarkPending {

        @Test
        void resetsAllFieldsToInitialState() {
            SagaStepEntity s = step();
            s.markExecuting("{}");
            s.markFailed("timeout", "stack...", Instant.now().plusSeconds(30));

            s.markPending();

            assertThat(s.getStatus()).isEqualTo("PENDING");
            assertThat(s.getAttempt()).isZero();
            assertThat(s.getLastError()).isNull();
            assertThat(s.getErrorTrace()).isNull();
            assertThat(s.getNextRetryAt()).isNull();
            assertThat(s.getCompletedAt()).isNull();
        }

        @Test
        void allowsStepToBeExecutedAgainAfterReset() {
            SagaStepEntity s = step();
            s.markExecuting("{}");
            s.markFailed("err", null, null);

            s.markPending();
            s.markExecuting("{\"retry\":true}");

            assertThat(s.getStatus()).isEqualTo("EXECUTING");
            assertThat(s.getAttempt()).isEqualTo(1);
        }
    }

    @Nested
    class IsExhausted {

        @Test
        void falseWhenAttemptsUnderMax() {
            SagaStepEntity s = step(); // maxAttempts=3, attempt=0
            assertThat(s.isExhausted()).isFalse();
        }

        @Test
        void trueWhenAttemptsReachMax() {
            SagaStepEntity s = step();
            s.markExecuting("{}");
            s.markExecuting("{}");
            s.markExecuting("{}"); // attempt=3, maxAttempts=3

            assertThat(s.isExhausted()).isTrue();
        }

        @Test
        void falseWhenMaxAttemptsIsZero() {
            SagaStepEntity s = SagaStepEntity.create(new SagaEntity(), "step", 0, 0);
            s.markExecuting("{}");
            s.markExecuting("{}");
            s.markExecuting("{}");

            assertThat(s.isExhausted()).isFalse();
        }
    }
}
