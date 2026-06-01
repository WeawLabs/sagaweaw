package io.sagaweaw.spring.scheduler;

import io.sagaweaw.spring.entity.OutboxMessageEntity;
import io.sagaweaw.spring.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads unpublished outbox messages and sends them to Kafka.
 * Runs at-least-once: if the relay publishes and dies before marking published,
 * the message is sent again on the next poll.
 * The idempotency-key header lets consumers deduplicate. (ADR-010)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxMessageRepository          outboxRepository;
    private final KafkaTemplate<String, String>    kafkaTemplate;
    private final ObjectMapper                     objectMapper;

    @Scheduled(fixedDelayString = "${sagaweaw.outbox.polling-interval-ms:1000}")
    public void relay() {
        List<OutboxMessageEntity> pending = outboxRepository.findUnpublished(
                PageRequest.of(0, 100));

        if (pending.isEmpty()) return;

        pending.forEach(message -> {
            try {
                publish(message);
                outboxRepository.markPublished(message.getId(), Instant.now());
            } catch (Exception e) {
                message.incrementAttempt();
                outboxRepository.save(message);
                log.error("Failed to publish outbox message '{}' to topic '{}' — will retry",
                        message.getId(), message.getTopic(), e);
            }
        });
    }

    private void publish(OutboxMessageEntity message) throws Exception {
        Map<String, String> headers = objectMapper.readValue(
                message.getHeaders(), new TypeReference<>() {});

        MessageBuilder<String> builder = MessageBuilder
                .withPayload(message.getPayload())
                .setHeader(KafkaHeaders.TOPIC, message.getTopic())
                .setHeader(KafkaHeaders.KEY, message.getSagaId());

        headers.forEach((key, value) ->
                builder.setHeader(key, value.getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(builder.build()).get();

        log.debug("Published outbox message '{}' to topic '{}'",
                message.getId(), message.getTopic());
    }
}
