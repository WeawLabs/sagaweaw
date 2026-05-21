# Sagaweaw

**Orquestração de sagas distribuídas para Spring Boot — compensação automática, retry e dashboard nativo.**

---

📖 **[Documentação Completa](https://doc.sagaweaw.dev)** — tipos de step em profundidade, todas as propriedades de configuração, API WebSocket, Dead Letter Queue, decisões de arquitetura e mais.

[README in English](README.md) · [Issues](https://github.com/amosjuda/sagaweaw/issues)

---

## O problema

Você tem 3 microsserviços. Pagamento cobrado. Estoque reservado. A criação do envio falha.

Como você desfaz o pagamento e libera o estoque — de forma confiável, automática, com visibilidade completa? A maioria dos times escreve essa lógica na mão. Ela quebra em produção.

---

## Quickstart

**1. Adicione as dependências**

```xml
<dependency>
    <groupId>dev.sagaweaw</groupId>
    <artifactId>sagaweaw-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<!-- Flyway é obrigatório para criação automática do schema -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- Adicione flyway-database-postgresql se usar PostgreSQL -->
```

**2. Escreva sua saga** — deixe a classe no mesmo pacote dos serviços que ela usa

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

Os tipos de step são inferidos automaticamente: `.invoke()` + `.compensate()` = **COMPENSABLE** (desazível); só `.invoke()` = **PIVOT** (ponto de não-retorno); `.invoke()` + retry infinito + sem compensate = **RETRIABLE**.

**3. Configure o token**

Gere um segredo: `openssl rand -hex 32`

```bash
# .env  — nunca commite esse arquivo
SAGAWEAW_TOKEN=<seu-token-gerado>
```

```properties
# application.properties
sagaweaw.observability.token=${SAGAWEAW_TOKEN}
sagaweaw.kafka.enabled=false
```

> ⚠️ Use sempre `${SAGAWEAW_TOKEN}` no `application.properties` — nunca cole o token diretamente. Qualquer pessoa com acesso ao repositório teria acesso à sua API de observabilidade.

**4. Dispare a saga**

```java
sagaManager.start(OrderSaga.class, new OrderContext(orderId, customerId, itemId, quantity, amount));
```

Se `create-shipment` falhar, o Sagaweaw chama automaticamente `paymentService.refund()` e `inventoryService.release()` em ordem inversa, com persistência completa e trilha de auditoria.

---

## Integração com a sua stack existente

Suas sagas aparecem automaticamente nas ferramentas que você já usa — sem configuração extra.

### Prometheus & Grafana

Adicione `micrometer-registry-prometheus` ao classpath. O Sagaweaw publica contadores e gauges automaticamente:

```promql
# Taxa de sucesso de sagas (últimas 24h)
rate(sagaweaw_sagas_completed_total[24h])
  / (rate(sagaweaw_sagas_completed_total[24h]) + rate(sagaweaw_sagas_failed_total[24h]))

# Dead letters pendentes (configure alerta aqui)
sagaweaw_dead_letters_pending

# Mensagens aguardando publicação no Kafka
sagaweaw_outbox_pending
```

Endpoint de scrape: `sua-app/actuator/prometheus`

### ELK / Loki / Splunk (MDC)

Toda linha de log emitida dentro de um step de saga é automaticamente enriquecida com:

```
sagaId=f3a9b2c1  sagaName=order-processing  stepName=charge-payment  attempt=2
```

Sem nenhuma configuração. Filtre seu agregador de logs por `sagaName` ou `sagaId` e você tem a cadeia de correlação completa de qualquer execução de saga.

### Dashboard de debug em tempo real

Para debug focado em sagas — veja a timeline de cada step, inspecione snapshots de contexto e reprocesse dead letters — abra **http://localhost:8484** e insira o token.

O dashboard é uma lente para debug específico de sagas. Sua observabilidade principal continua no Prometheus, Grafana e no seu agregador de logs.

---

## Por que não Temporal, Axon ou Eventuate?

| | Sagaweaw | Temporal | Axon | Eventuate |
|---|---|---|---|---|
| Infraestrutura extra | Nenhuma | Cluster Temporal | Nenhuma | Nenhuma |
| Usa o banco que você já tem | ✅ | ❌ | ❌ | ❌ |
| Linhas para uma saga básica | ~25 | ~70 | ~80 | ~50 |
| Dashboard nativo | ✅ | ✅ | ✅ (pago) | ❌ |
| Auto-configuration Spring Boot | ✅ | ❌ | ✅ | ✅ |
| Curva de aprendizado | Baixa | Alta | Alta | Média |

**Você já tem um banco de dados.** PostgreSQL, MySQL ou H2 — nenhum cluster para provisionar, nenhum novo servidor para operar. Uma dependência e você está rodando.

---

## Usando um assistente de IA?

🤖 **Usando Cursor, Copilot, Claude ou ChatGPT?** Cole isso no seu assistente de IA e substitua `[SEU CONTEXTO]` — ele gera sua primeira saga em segundos:

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

## Requisitos

- Java 21+
- Spring Boot 3.x ou 4.x
- PostgreSQL 14+ (recomendado), MySQL 8.0+ ou H2

---

## Licença

Apache 2.0 — livre para uso em projetos comerciais.
