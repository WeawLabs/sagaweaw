<div align="center">

# Sagaweaw

**Saga orchestration for Spring Boot. No new servers. No new concepts. Just your database.**

[![Maven Central](https://img.shields.io/maven-central/v/dev.sagaweaw/sagaweaw-spring-boot-starter?color=blue)](https://central.sonatype.com/artifact/dev.sagaweaw/sagaweaw-spring-boot-starter)
[![CI](https://github.com/amosjuda/sagaweaw/actions/workflows/ci.yml/badge.svg)](https://github.com/amosjuda/sagaweaw/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange)](https://openjdk.org/)

📖 [Documentation](https://doc.sagaweaw.dev) · [README em Português](README.pt-BR.md) · [Discussions](https://github.com/amosjuda/sagaweaw/discussions) · [Issues](https://github.com/amosjuda/sagaweaw/issues)

</div>

---

## The problem every microservice team faces

Payment charged. Inventory reserved. Then the shipping service times out.

Now what? You have money taken, stock locked, and no shipment. If you're lucky, someone wrote a manual recovery script. If you're not, a support ticket shows up at 2am.

**Most teams handle this with a mix of flags, schedulers, and hope. It always breaks in production.**

The saga pattern solves this — but every existing tool makes you pay a steep price: a new cluster to operate (Temporal), a steep learning curve (Axon), or a platform you can't see inside (Eventuate).

Sagaweaw is different. It runs on the PostgreSQL, MySQL, or H2 you already have. One dependency. Zero extra infrastructure.

---

## See it in action

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

If `create-shipment` fails, Sagaweaw automatically calls `refund()` and `release()` in reverse order — with full persistence, retry with exponential backoff, and audit trail. You write the business logic. Sagaweaw handles everything else.

Step types are inferred from what you declare:
- `.invoke()` + `.compensate()` → **COMPENSABLE** — can be undone
- `.invoke()` only → **PIVOT** — point of no return
- `.invoke()` + infinite retry + no compensate → **RETRIABLE** — keeps trying until it succeeds

---

## Quickstart

**1. Add the dependency**

```xml
<dependency>
    <groupId>dev.sagaweaw</groupId>
    <artifactId>sagaweaw-spring-boot-starter</artifactId>
    <version>1.0.4</version>
</dependency>
<!-- Required for automatic schema creation -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- Add flyway-database-postgresql if using PostgreSQL -->
```

**2. Write your saga** (the class above is all you need)

**3. Configure the token**

```bash
# .env — never commit this file
SAGAWEAW_TOKEN=$(openssl rand -hex 32)
```

```properties
# application.properties
sagaweaw.observability.token=${SAGAWEAW_TOKEN}
# Kafka is optional — remove this line only if you add spring-kafka to your classpath
sagaweaw.kafka.enabled=false
```

**4. Start it**

```java
sagaManager.start(OrderSaga.class, new OrderContext(orderId, customerId, itemId, quantity, amount));
```

That's it. Sagaweaw creates the schema, registers your saga, and handles the rest.

---

## What you get out of the box

### Real-time debug dashboard

Open `http://localhost:8484`, enter your token, and see every saga execution in real time:

- **Step timeline** — status, duration, retry count, and error for each step
- **Context snapshots** — the business data at every point of execution
- **Search by business ID** — type an `orderId`, `customerId`, or any value from the context and find every saga related to it instantly
- **Stuck saga detection** — sagas stuck in `EXECUTING` beyond a configurable threshold are highlighted in red with a "STUCK" badge
- **Saga flow diagram** — see the expected step sequence vs. what actually happened
- **Related sagas** — from any saga detail, see all other sagas sharing the same business ID
- **Batch dead letter reprocessing** — select multiple failed messages and reprocess them at once
- **CSV export** — export filtered saga lists and dead letters as CSV for offline analysis

### OpenTelemetry

If you already have OpenTelemetry in your classpath, Sagaweaw's spans appear **automatically** in your existing backend — no extra configuration.

```xml
<!-- If you're already using OTel, that's all you need -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

Every saga lifecycle event produces a span with the right attributes:

```
saga.started         saga.name=order-processing  saga.id=f3a9b2c1
saga.step.invoke     saga.step.name=charge-payment  saga.step.attempt=2
saga.step.failed     saga.step.name=charge-payment
saga.step.compensating
saga.compensated
```

Works with Jaeger, Grafana Tempo, Honeycomb, Datadog, Zipkin, and any OTel-compatible backend. Disable with `sagaweaw.tracing.enabled=false`.

### Prometheus & Grafana

Add `micrometer-registry-prometheus` to your classpath. No extra configuration.

```promql
# Saga success rate (last 24h)
rate(sagaweaw_sagas_completed_total[24h])
  / (rate(sagaweaw_sagas_completed_total[24h]) + rate(sagaweaw_sagas_failed_total[24h]))

# Dead letters pending — alert on this
sagaweaw_dead_letters_pending
```

A pre-built Grafana dashboard is included. From the dashboard UI, click the download button to get the JSON — import it into your Grafana instance in 30 seconds.

### Structured logs with zero config

Every log line inside a saga step is automatically enriched:

```
sagaId=f3a9b2c1  sagaName=order-processing  stepName=charge-payment  attempt=2
```

Filter by `sagaId` in ELK, Loki, or Splunk and trace any execution end-to-end.

---

## Operations

### Webhook alerts

Receive notifications for critical events with a single property:

```properties
sagaweaw.alerts.webhook-url=https://hooks.slack.com/services/...
sagaweaw.alerts.on-dead-letter=true
sagaweaw.alerts.on-stuck-saga=true
sagaweaw.alerts.failure-rate-threshold=0.05
```

Works with Slack, Discord, Teams, PagerDuty, and any HTTP POST endpoint. Zero new dependencies.

### Configurable data retention

Control how long saga data stays in your database:

```properties
# Archive COMPLETED and COMPENSATED sagas after 30 days
sagaweaw.data.retention-days=30

# Keep FAILED sagas longer — your team will want to investigate and reprocess
sagaweaw.data.failed-retention-days=90
```

A nightly job archives eligible sagas to a `sagas_archive` table, preserving the full context and step history for audit. Default: never delete.

### Multi-instance support

Run N instances against the same database with no extra configuration. Each saga records which instance created it. See all active instances and their live saga counts:

```
GET /api/instances?hoursBack=2
```

```properties
# Optional: override the auto-generated instance ID
sagaweaw.instance.id=${HOSTNAME}
```

---

## Why not Temporal, Axon, or Eventuate?

|                               | Sagaweaw | Temporal       | Axon       | Eventuate |
|-------------------------------|----------|----------------|------------|-----------|
| Extra infrastructure          | **None** | Temporal cluster | None     | None      |
| Uses your existing DB         | ✅       | ❌             | ❌         | ❌        |
| Lines for a complete saga     | **~25**  | ~70            | ~80        | ~50       |
| Built-in debug dashboard      | ✅       | ✅             | ✅ (paid)  | ❌        |
| OpenTelemetry spans           | ✅       | ✅             | ✅         | ❌        |
| Spring Boot auto-configuration| ✅       | ❌             | ✅         | ✅        |
| Learning curve                | **Low**  | High           | High       | Medium    |

You already have PostgreSQL. You already know Spring Boot. Sagaweaw speaks your language.

---

## Using an AI assistant?

🤖 **Using Cursor, Copilot, Claude, or ChatGPT?** Paste this prompt and replace `[YOUR CONTEXT]`:

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

## Contributing

Sagaweaw is actively developed and we'd love your help. Whether it's a bug fix, a new feature idea, or just feedback from using it in a real project — all contributions matter.

- 🐛 **Found a bug?** [Open an issue](https://github.com/amosjuda/sagaweaw/issues/new?template=bug_report.md)
- 💡 **Have an idea?** [Start a discussion](https://github.com/amosjuda/sagaweaw/discussions)
- 🔧 **Want to contribute code?** Read [CONTRIBUTING.md](CONTRIBUTING.md) — the setup takes 5 minutes

---

## Requirements

- Java 17 LTS+
- Spring Boot 3.x or 4.x
- PostgreSQL 14+ (recommended), MySQL 8.0+, or H2

---

## License

Apache 2.0 — free to use in commercial projects, forever.
