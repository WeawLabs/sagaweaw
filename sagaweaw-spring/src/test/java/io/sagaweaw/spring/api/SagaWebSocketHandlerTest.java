package io.sagaweaw.spring.api;

import tools.jackson.databind.ObjectMapper;
import io.sagaweaw.spring.event.SagaCompensatedEvent;
import io.sagaweaw.spring.event.SagaCompletedEvent;
import io.sagaweaw.spring.event.SagaFailedEvent;
import io.sagaweaw.spring.event.SagaStartedEvent;
import io.sagaweaw.spring.event.StepCompensatedEvent;
import io.sagaweaw.spring.event.StepCompletedEvent;
import io.sagaweaw.spring.event.StepFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class SagaWebSocketHandlerTest {

    @Mock SimpMessagingTemplate messaging;

    private SagaWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        handler = new SagaWebSocketHandler(messaging, objectMapper);
    }

    @Test
    void saga_started_is_broadcast_to_topic() {
        handler.on(new SagaStartedEvent("saga-1", "order-processing", Instant.now()));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(messaging).convertAndSend(eq("/topic/sagas"), payload.capture());
        assertThat(payload.getValue()).contains("saga-1").contains("order-processing");
    }

    @Test
    void saga_completed_is_broadcast_to_topic() {
        handler.on(new SagaCompletedEvent("saga-2", "order-processing", 1234L));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(messaging).convertAndSend(eq("/topic/sagas"), payload.capture());
        assertThat(payload.getValue()).contains("saga-2");
    }

    @Test
    void step_completed_is_broadcast_to_topic() {
        handler.on(new StepCompletedEvent("saga-3", "order-processing", "charge-payment", 50L));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(messaging).convertAndSend(eq("/topic/sagas"), payload.capture());
        assertThat(payload.getValue()).contains("charge-payment");
    }

    @Test
    void step_compensated_is_broadcast_to_topic() {
        handler.on(new StepCompensatedEvent("saga-4", "reserve-inventory", Instant.now()));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(messaging).convertAndSend(eq("/topic/sagas"), payload.capture());
        assertThat(payload.getValue()).contains("reserve-inventory");
    }

    @Test
    void step_failed_is_broadcast_to_topic() {
        handler.on(new StepFailedEvent("saga-5", "charge-payment", 1, "connection refused"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(messaging).convertAndSend(eq("/topic/sagas"), payload.capture());
        assertThat(payload.getValue()).contains("connection refused");
    }

    @Test
    void websocket_send_failure_does_not_propagate() {
        doThrow(new RuntimeException("broker down"))
                .when(messaging).convertAndSend(eq("/topic/sagas"), (Object) org.mockito.ArgumentMatchers.any());

        assertThatNoException().isThrownBy(
                () -> handler.on(new SagaStartedEvent("saga-6", "test", Instant.now())));
    }
}
