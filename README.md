# Sagaweaw

**Distributed saga orchestration for Spring Boot — automatic compensation, retry, and built-in dashboard.**

---

📖 **[Full Documentation](https://doc.sagaweaw.dev)** — step types in depth, all configuration properties, WebSocket API, Dead Letter Queue, architecture decisions, and more.

[README em Português](README.pt-BR.md) · [Issues](https://github.com/amosjuda/sagaweaw/issues)

---

## The problem

You have 3 microservices. Payment charged. Inventory reserved. Shipment creation fails.

How do you undo the payment and release the inventory — reliably, automatically, with full visibility? Most teams write that logic by hand. It breaks in production.

---

## Quickstart

**1. Add the dependencies**

```xml
<dependency>
    <groupId>dev.sagaweaw</groupId>
    <artifactId>sagaweaw-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
<!-- Flyway is required for automatic schema creation -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- Add flyway-database-postgresql if using PostgreSQL -->
```

**2. Write your saga** — keep the class in the same package as the services it uses

```java
@Saga(name = "order-processing")
@RequiredArgsConstructor
public class OrderSaga implements SagaDefinition<OrderContext> {

    private final InventoryService inventoryService;
    private final PaymentService   paymentService;
    private final ShippingService  shippingService;

    @Override
    public SagaFlow<OrderContext> define(SagaBuilder<OrderContext> saga) {
        return saga
            .step("reserve-inventory")
                .invoke(ctx -> inventoryService.reserve(ctx.itemId(), ctx.quantity()))
                .compensate(ctx -> inventoryService.release(ctx.itemId(), ctx.quantity()))

            .step("charge-payment")
                .invoke(ctx -> paymentService.charge(ctx.customerId(), ctx.amount()))
                .compensate(ctx -> paymentService.refund(ctx.chargeId()))
                .retryPolicy(RetryPolicy.exponential(3, Duration.ofSeconds(5)))

            .step("create-shipment")
                .invoke(ctx -> shippingService.schedule(ctx.orderId(), ctx.itemId()))
                .compensate(ctx -> shippingService.cancel(ctx.orderId()))

            .build();
    }
}
```

Step types are inferred automatically: `.invoke()` + `.compensate()` = **COMPENSABLE** (undoable); `.invoke()` only = **PIVOT** (point of no return); `.invoke()` + infinite retry + no compensate = **RETRIABLE**.

**3. Configure the token**

Generate a secret: `openssl rand -hex 32`

```bash
# .env  — never commit this file
SAGAWEAW_TOKEN=<your-generated-token>
```

```properties
# application.properties
sagaweaw.observability.token=${SAGAWEAW_TOKEN}
sagaweaw.kafka.enabled=false
```

> ⚠️ Always use `${SAGAWEAW_TOKEN}` in `application.properties` — never paste the token directly. Anyone with repo access would have access to your observability API.

**4. Start it**

```java
sagaManager.start(OrderSaga.class, new OrderContext(orderId, customerId, itemId, quantity, amount));
```

If `create-shipment` fails, Sagaweaw automatically calls `paymentService.refund()` and `inventoryService.release()` in reverse order, with full persistence and audit trail.

---

## Integration with your existing stack

Your sagas appear automatically in the tools you already use — no extra configuration.

### Prometheus & Grafana

Add `micrometer-registry-prometheus` to your classpath. Sagaweaw publishes counters and gauges automatically:

```promql
# Saga success rate (last 24h)
rate(sagaweaw_sagas_completed_total[24h])
  / (rate(sagaweaw_sagas_completed_total[24h]) + rate(sagaweaw_sagas_failed_total[24h]))

# Dead letters pending (alert on this)
sagaweaw_dead_letters_pending

# Outbox lag (messages waiting to publish to Kafka)
sagaweaw_outbox_pending
```

Scrape endpoint: `your-app/actuator/prometheus`

### ELK / Loki / Splunk (MDC)

Every log line emitted inside a saga step is automatically enriched with:

```
sagaId=f3a9b2c1  sagaName=order-processing  stepName=charge-payment  attempt=2
```

No configuration required. Filter your log aggregator by `sagaName` or `sagaId` and you have the full correlation chain for any saga execution.

### Real-time debug dashboard

For focused saga debugging — see each step's timeline, inspect context snapshots, and reprocess dead letters — open **http://localhost:8484** and enter your token.

The dashboard is a lens for saga-specific debug. Your primary observability remains in Prometheus, Grafana, and your log aggregator.

---

## Why not Temporal, Axon, or Eventuate?

| | Sagaweaw | Temporal | Axon | Eventuate |
|---|---|---|---|---|
| Extra infrastructure | None | Temporal cluster | None | None |
| Works with your existing DB | ✅ | ❌ | ❌ | ❌ |
| Lines for a basic saga | ~25 | ~70 | ~80 | ~50 |
| Built-in dashboard | ✅ | ✅ | ✅ (paid) | ❌ |
| Spring Boot auto-configuration | ✅ | ❌ | ✅ | ✅ |
| Learning curve | Low | High | High | Medium |

**You already have a database.** PostgreSQL, MySQL, or H2 — no cluster to provision, no new server to operate. One dependency and you're running.

---

## Using an AI assistant?

🤖 **Using Cursor, Copilot, Claude, or ChatGPT?** Paste this into your AI assistant and replace `[YOUR CONTEXT]` — it will generate your first saga in seconds:

```
You are helping me implement Sagaweaw in my Spring Boot project.
Sagaweaw orchestrates distributed transactions with automatic compensation.
Steps with .compensate() are COMPENSABLE, steps without are PIVOT (point of
no return), steps with infinite retry and no compensate are RETRIABLE.
The engine handles persistence, retry with exponential backoff, and
compensation in reverse order. SagaContext is immutable. Never use
@Transactional on the saga class — the engine manages transactions internally.

My project: [YOUR CONTEXT — describe the business flow and services involved]

Generate the complete Saga class and show me how to trigger it.
```

---

## Requirements

- Java 21+
- Spring Boot 3.x or 4.x
- PostgreSQL 14+ (recommended), MySQL 8.0+, or H2

---

## License

Apache 2.0 — free to use in commercial projects.
