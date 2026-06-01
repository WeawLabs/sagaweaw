package io.sagaweaw.spring.api;

import io.sagaweaw.spring.event.SagaCompensatedEvent;
import io.sagaweaw.spring.event.SagaCompletedEvent;
import io.sagaweaw.spring.event.SagaFailedEvent;
import io.sagaweaw.spring.event.SagaStartedEvent;
import io.sagaweaw.spring.event.StepCompensatedEvent;
import io.sagaweaw.spring.event.StepCompletedEvent;
import io.sagaweaw.spring.event.StepFailedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards saga events to the dashboard via WebSocket.
 * Uses @TransactionalEventListener(phase = AFTER_COMMIT) so the dashboard only
 * receives events for state that is actually committed to the DB.
 * If the transaction rolls back, the event is silently discarded — no phantom updates.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaWebSocketHandler {

    private static final String TOPIC = "/topic/sagas";

    private final SimpMessagingTemplate messaging;
    private final ObjectMapper          objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SagaStartedEvent event)      { send(event); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SagaCompletedEvent event)    { send(event); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SagaCompensatedEvent event)  { send(event); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SagaFailedEvent event)       { send(event); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StepCompletedEvent event)    { send(event); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StepCompensatedEvent event)  { send(event); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StepFailedEvent event)       { send(event); }

    private void send(Object event) {
        try {
            messaging.convertAndSend(TOPIC, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to broadcast event {} via WebSocket",
                    event.getClass().getSimpleName(), e);
        }
    }
}
