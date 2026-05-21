package io.sagaweaw.spring.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class SagaWebSocketAuthInterceptor implements ChannelInterceptor {

    private final String expectedToken;

    public SagaWebSocketAuthInterceptor(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        if (expectedToken == null || expectedToken.isBlank()) {
            throw new MessageDeliveryException(message,
                    "Sagaweaw observability is locked — set sagaweaw.observability.token to enable WebSocket access.");
        }

        String auth = accessor.getFirstNativeHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String provided = auth.substring(7);
            if (constantTimeEquals(expectedToken, provided)) {
                return message;
            }
        }

        throw new MessageDeliveryException(message, "Invalid Sagaweaw observability token.");
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
