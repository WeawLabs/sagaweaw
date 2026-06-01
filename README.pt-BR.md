<div align="center">

# Sagaweaw

**Orquestração de sagas para Spring Boot. Sem novos servidores. Sem novos conceitos. Só o seu banco de dados.**

[![Maven Central](https://img.shields.io/maven-central/v/dev.sagaweaw/sagaweaw-spring-boot-starter?color=blue)](https://central.sonatype.com/artifact/dev.sagaweaw/sagaweaw-spring-boot-starter)
[![CI](https://github.com/amosjuda/sagaweaw/actions/workflows/ci.yml/badge.svg)](https://github.com/amosjuda/sagaweaw/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange)](https://openjdk.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-suportado-7F52FF)](https://kotlinlang.org/)

📖 [Documentação](https://doc.sagaweaw.dev) · [README in English](README.md) · [Discussions](https://github.com/amosjuda/sagaweaw/discussions) · [Issues](https://github.com/amosjuda/sagaweaw/issues)

</div>

---

## O problema que todo time de microsserviços enfrenta

Pagamento cobrado. Estoque reservado. Aí o serviço de envio dá timeout.

E agora? Você tem dinheiro descontado, estoque bloqueado e nenhum pedido criado. Se tiver sorte, alguém escreveu um script de recuperação manual. Se não tiver, um chamado de suporte chega às 2h da manhã.

**A maioria dos times resolve isso com flags, schedulers e esperança. Sempre quebra em produção.**

O padrão saga resolve esse problema — mas todas as ferramentas existentes cobram um preço alto: um cluster novo para operar (Temporal), uma curva de aprendizado íngreme (Axon), ou uma plataforma opaca (Eventuate).

O Sagaweaw é diferente. Roda no PostgreSQL, MySQL ou H2 que você já tem. Uma dependência. Zero infraestrutura extra.

---

## Veja na prática

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

Se `create-shipment` falhar, o Sagaweaw chama automaticamente `refund()` e `release()` em ordem inversa — com persistência completa, retry com backoff exponencial e trilha de auditoria. Você escreve a lógica de negócio. O Sagaweaw cuida do resto.

Os tipos de step são inferidos pelo que você declara:
- `.invoke()` + `.compensate()` → **COMPENSABLE** — pode ser desfeito
- Só `.invoke()` → **PIVOT** — ponto de não-retorno
- `.invoke()` + retry infinito + sem compensate → **RETRIABLE** — tenta até conseguir

---

## Quickstart

**1. Adicione a dependência**

```xml
<dependency>
    <groupId>dev.sagaweaw</groupId>
    <artifactId>sagaweaw-spring-boot-starter</artifactId>
    <version>1.0.9</version>
</dependency>
<!-- Obrigatório para criação automática do schema -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- Adicione flyway-database-postgresql se usar PostgreSQL -->
```

**2. Escreva sua saga**

Na maioria dos microsserviços (sagas que chamam serviços externos), implemente `SagaSampler` e adicione `@AutoStart`. O contexto de exemplo dispara automaticamente no startup — nenhum código extra necessário.

```java
@Saga("order-processing")
@AutoStart                             // dispara sampleContext() no startup
@Component
public class OrderSaga
        implements SagaDefinition<OrderSaga.Context>,
                   SagaSampler<OrderSaga.Context> {

    private final InventoryService inventoryService;
    private final PaymentService   paymentService;
    private final ShippingService  shippingService;

    @Override
    public Context sampleContext() {
        return new Context(UUID.randomUUID(), "customer-42",
                           "item-99", 2, new BigDecimal("99.90"));
    }

    @Override
    public SagaFlow<Context> define(SagaBuilder<Context> saga) {
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

**3. Configure**

```properties
# application.properties
sagaweaw.observability.token=${SAGAWEAW_TOKEN}
sagaweaw.kafka.enabled=false

# Somente em dev — nunca configure isso em produção
sagaweaw.auto-start.enabled=true
```

**4. Suba a aplicação e abra o dashboard**

```
http://localhost:8484
```

A saga dispara automaticamente com o contexto de exemplo. Você vê cada step executar em tempo real, com contexto completo, retries e trilha de compensação. Sem curl. Sem controller extra.

> **Wiring de produção:** injete `SagaManager` onde o evento de negócio acontece e chame `sagaManager.start(OrderSaga.class, context)`. É a única mudança necessária para produção.

O Sagaweaw cria o schema, registra sua saga e cuida de tudo.

---

## Kotlin

Usando Kotlin? Adicione o módulo `sagaweaw-kotlin` para DSL idiomática — sem `Consumer<T>`, sem `::class.java`, sem `Optional`.

```xml
<dependency>
    <groupId>dev.sagaweaw</groupId>
    <artifactId>sagaweaw-kotlin</artifactId>
    <version>1.0.9</version>
</dependency>
```

```kotlin
import io.sagaweaw.kotlin.*

// Contexto — estenda KSagaContext para usar String? em vez de Optional<String>
data class OrderContext(val orderId: UUID, val amount: BigDecimal) : KSagaContext() {
    override fun key() = orderId.toString()
}

// Saga — DSL limpa, sem Consumer<T>
@Saga(name = "order-processing")
class OrderSaga(
    private val inventoryService: InventoryService,
    private val paymentService: PaymentService,
) : SagaDefinition<OrderContext> {

    override fun define(saga: SagaBuilder<OrderContext>) = saga
        .step("reserve-inventory") {
            invoke { ctx -> inventoryService.reserve(ctx.orderId) }
            compensate { ctx -> inventoryService.release(ctx.orderId) }
        }
        .step("charge-payment") {
            invoke { ctx -> paymentService.charge(ctx.orderId, ctx.amount) }
            compensate { ctx -> paymentService.refund(ctx.orderId) }
            retry(exponentialRetry(3, 5.seconds))
        }
        .build()
}

// Disparo — reified, sem ::class.java
sagaManager.start<OrderSaga>(context)
```

---

## O que você ganha de graça

### Dashboard de debug em tempo real

Abra `http://localhost:8484`, insira o token e veja cada execução de saga em tempo real:

- **Timeline de steps** — status, duração, contagem de retry e erro de cada step
- **Snapshots de contexto** — os dados de negócio em cada ponto da execução
- **Busca por ID de negócio** — digite um `orderId`, `customerId` ou qualquer valor do contexto e encontre todas as sagas relacionadas na hora
- **Detecção de sagas travadas** — sagas paradas em `EXECUTING` além do tempo configurado aparecem em vermelho com badge "STUCK"
- **Diagrama do fluxo da saga** — veja a sequência esperada de steps versus o que realmente aconteceu
- **Sagas relacionadas** — no detalhe de qualquer saga, veja todas as outras sagas que compartilham o mesmo ID de negócio
- **Reprocessamento em lote de dead letters** — selecione várias mensagens com falha e reprocesse de uma vez
- **Exportação CSV** — exporte listas de sagas filtradas e dead letters como CSV para análise offline

### OpenTelemetry

Se você já tem OpenTelemetry no classpath, os spans do Sagaweaw aparecem **automaticamente** no seu backend existente — sem nenhuma configuração extra.

```xml
<!-- Se você já usa OTel, é só isso que precisa -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

Cada evento do ciclo de vida da saga gera um span com os atributos corretos:

```
saga.started         saga.name=order-processing  saga.id=f3a9b2c1
saga.step.invoke     saga.step.name=charge-payment  saga.step.attempt=2
saga.step.failed     saga.step.name=charge-payment
saga.step.compensating
saga.compensated
```

Funciona com Jaeger, Grafana Tempo, Honeycomb, Datadog, Zipkin e qualquer backend compatível com OTel. Desabilite com `sagaweaw.tracing.enabled=false`.

### Prometheus & Grafana

Adicione `micrometer-registry-prometheus` ao classpath. Sem nenhuma configuração extra.

```promql
# Taxa de sucesso (últimas 24h)
rate(sagaweaw_sagas_completed_total[24h])
  / (rate(sagaweaw_sagas_completed_total[24h]) + rate(sagaweaw_sagas_failed_total[24h]))

# Dead letters pendentes — configure alerta aqui
sagaweaw_dead_letters_pending
```

Um dashboard Grafana pré-construído está incluso. No dashboard, clique no botão de download para obter o JSON — importe no seu Grafana em 30 segundos.

### Logs estruturados sem configuração

Toda linha de log dentro de um step é automaticamente enriquecida:

```
sagaId=f3a9b2c1  sagaName=order-processing  stepName=charge-payment  attempt=2
```

Filtre por `sagaId` no ELK, Loki ou Splunk e trace qualquer execução de ponta a ponta.

---

## Operações

### Alertas via webhook

Receba notificações para eventos críticos com uma única propriedade:

```properties
sagaweaw.alerts.webhook-url=https://hooks.slack.com/services/...
sagaweaw.alerts.on-dead-letter=true
sagaweaw.alerts.on-stuck-saga=true
sagaweaw.alerts.failure-rate-threshold=0.05
```

Funciona com Slack, Discord, Teams, PagerDuty e qualquer endpoint HTTP POST. Zero dependências novas.

### Retenção de dados configurável

Controle por quanto tempo os dados das sagas ficam no seu banco:

```properties
# Arquiva sagas COMPLETED e COMPENSATED após 30 dias
sagaweaw.data.retention-days=30

# Mantém sagas FAILED por mais tempo — o time vai querer investigar e reprocessar
sagaweaw.data.failed-retention-days=90
```

Um job noturno arquiva as sagas elegíveis para uma tabela `sagas_archive`, preservando o contexto completo e o histórico de steps para auditoria. Padrão: nunca deletar.

### Suporte a múltiplas instâncias

Rode N instâncias contra o mesmo banco sem nenhuma configuração extra. Cada saga registra qual instância a criou. Veja todas as instâncias ativas e a contagem de sagas em tempo real:

```
GET /api/instances?hoursBack=2
```

```properties
# Opcional: sobrescreva o ID de instância gerado automaticamente
sagaweaw.instance.id=${HOSTNAME}
```

---

## Por que não Temporal, Axon ou Eventuate?

|                                | Sagaweaw | Temporal        | Axon       | Eventuate |
|--------------------------------|----------|-----------------|------------|-----------|
| Infraestrutura extra           | **Nenhuma** | Cluster Temporal | Nenhuma | Nenhuma |
| Usa o banco que você já tem    | ✅       | ❌              | ❌         | ❌        |
| Linhas para uma saga completa  | **~25**  | ~70             | ~80        | ~50       |
| Dashboard de debug nativo      | ✅       | ✅              | ✅ (pago)  | ❌        |
| Spans OpenTelemetry            | ✅       | ✅              | ✅         | ❌        |
| Auto-configuration Spring Boot | ✅       | ❌              | ✅         | ✅        |
| Curva de aprendizado           | **Baixa** | Alta           | Alta       | Média     |

Você já tem PostgreSQL. Você já conhece Spring Boot. O Sagaweaw fala a sua língua.

---

## Usando um assistente de IA?

🤖 **Usando Cursor, Copilot, Claude ou ChatGPT?** Cole esse prompt e substitua `[SEU CONTEXTO]`:

```
Você vai me ajudar a implementar o Sagaweaw no meu projeto Spring Boot.
O Sagaweaw orquestra transações distribuídas com compensação automática.
Steps com .compensate() são COMPENSABLE, steps sem .compensate() são PIVOT
(ponto de não-retorno), steps com retry infinito e sem compensate são
RETRIABLE. O engine cuida da persistência, retry com backoff exponencial e
compensação em ordem inversa. SagaContext é imutável. Nunca use @Transactional
na classe da Saga — o engine gerencia as transações internamente.

Meu projeto: [SEU CONTEXTO — descreva o fluxo de negócio e os serviços envolvidos]

Gere a classe Saga completa e mostre como disparar ela.
```

---

## Contribuindo

O Sagaweaw está em desenvolvimento ativo e adoraríamos sua ajuda. Seja um bug fix, uma ideia de feature nova, ou só feedback de quem usou em produção — toda contribuição importa.

- 🐛 **Encontrou um bug?** [Abra uma issue](https://github.com/amosjuda/sagaweaw/issues/new?template=bug_report.md)
- 💡 **Tem uma ideia?** [Inicie uma discussão](https://github.com/amosjuda/sagaweaw/discussions)
- 🔧 **Quer contribuir com código?** Leia [CONTRIBUTING.md](CONTRIBUTING.md) — o setup leva 5 minutos

---

## Requisitos

- Java 17 LTS+
- Spring Boot 3.2+ (recomendado) ou 4.x
- Kotlin 2.0+ (se usar `sagaweaw-kotlin`)
- PostgreSQL 14+ (recomendado), MySQL 8.0+ ou H2

---

## Licença

Apache 2.0 — livre para uso em projetos comerciais, para sempre.
