# Using Sagaweaw with Kafka

Sagaweaw works fully without Kafka. The outbox pattern is built in regardless —
every completed step writes a message to the `sagaweaw_outbox_messages` table.
Kafka is the optional delivery mechanism that reads from that table and publishes
the messages to topics.

## Without Kafka (default)

No extra configuration needed. Outbox messages accumulate in the database.
You can query them via the observability API or process them with your own consumer.

```properties
# Nothing to configure — Kafka is off by default
```

## Enabling Kafka

Add `spring-kafka` to your classpath:

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Configure your broker:

```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
```

That's all. Sagaweaw auto-detects `KafkaTemplate` and starts the outbox relay automatically.

## Disabling Kafka explicitly

If `spring-kafka` is on your classpath for another reason but you don't want Sagaweaw
publishing to Kafka:

```properties
sagaweaw.kafka.enabled=false
```

## How the outbox relay works

The `OutboxRelay` polls `sagaweaw_outbox_messages` every `sagaweaw.outbox-poll-interval-ms`
milliseconds (default: 5000 ms). For each pending message it:

1. Publishes to Kafka topic `sagaweaw.<saga-name>.<step-name>`
2. Marks the message as published in the database
3. Includes the header `idempotency-key: <saga-id>:<step-name>:<attempt>` so your
   consumer can deduplicate retries

## Tuning

| Property | Default | Description |
|---|---|---|
| `sagaweaw.outbox-poll-interval-ms` | `5000` | How often the relay checks for pending messages |
| `sagaweaw.kafka.enabled` | `true` (when spring-kafka is present) | Set to `false` to disable |

## Consumer deduplication

Each Kafka message carries the header `idempotency-key`. Use it to implement
idempotent consumers:

```java
@KafkaListener(topics = "sagaweaw.order-saga.charge-payment")
public void handle(
        @Payload String payload,
        @Header("idempotency-key") String idempotencyKey) {
    if (alreadyProcessed(idempotencyKey)) return;
    // process...
    markProcessed(idempotencyKey);
}
```

## Topic naming

Topics follow the pattern `sagaweaw.<saga-name>.<step-name>`, for example:

- `sagaweaw.order-saga.charge-payment`
- `sagaweaw.order-saga.reserve-inventory`
- `sagaweaw.payment-saga.authorize-card`

Create them in advance with the replication factor and retention appropriate for
your environment, or enable `auto.create.topics.enable=true` in your broker for
local development.
