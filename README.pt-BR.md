<div align="center">

# Sagaweaw

**Orquestração de sagas para Spring Boot. Sem novos servidores. Sem novos conceitos. Só o seu banco de dados.**

[![Maven Central](https://img.shields.io/maven-central/v/dev.sagaweaw/sagaweaw-spring-boot-starter?color=blue)](https://central.sonatype.com/artifact/dev.sagaweaw/sagaweaw-spring-boot-starter)
[![CI](https://github.com/amosjuda/sagaweaw/actions/workflows/ci.yml/badge.svg)](https://github.com/amosjuda/sagaweaw/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange)](https://openjdk.org/)

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
    <version>1.0.1</version>
</dependency>
<!-- Obrigatório para criação automática do schema -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- Adicione flyway-database-postgresql se usar PostgreSQL -->
```

**2. Escreva sua saga** (a classe acima já é tudo que você precisa)

**3. Configure o token**

```bash
# .env — nunca commite esse arquivo
SAGAWEAW_TOKEN=$(openssl rand -hex 32)
```

```properties
# application.properties
sagaweaw.observability.token=${SAGAWEAW_TOKEN}
# Kafka é opcional — remova essa linha apenas se adicionar spring-kafka ao classpath
sagaweaw.kafka.enabled=false
```

**4. Dispare a saga**

```java
sagaManager.start(OrderSaga.class, new OrderContext(orderId, customerId, itemId, quantity, amount));
```

É isso. O Sagaweaw cria o schema, registra sua saga e cuida do resto.

---

## O que você ganha de graça

### Dashboard de debug em tempo real

Abra `http://localhost:8484`, insira o token e veja cada execução de saga em tempo real — timeline de steps, snapshots de contexto, histórico de retry e dead letters que você pode reprocessar com um clique.

### Prometheus & Grafana

Adicione `micrometer-registry-prometheus` ao classpath. Sem nenhuma configuração extra.

```promql
# Taxa de sucesso (últimas 24h)
rate(sagaweaw_sagas_completed_total[24h])
  / (rate(sagaweaw_sagas_completed_total[24h]) + rate(sagaweaw_sagas_failed_total[24h]))

# Dead letters pendentes — configure alerta aqui
sagaweaw_dead_letters_pending
```

### Logs estruturados sem configuração

Toda linha de log dentro de um step é automaticamente enriquecida:

```
sagaId=f3a9b2c1  sagaName=order-processing  stepName=charge-payment  attempt=2
```

Filtre por `sagaId` no ELK, Loki ou Splunk e trace qualquer execução de ponta a ponta.

---

## Por que não Temporal, Axon ou Eventuate?

|                                | Sagaweaw | Temporal        | Axon       | Eventuate |
|--------------------------------|----------|-----------------|------------|-----------|
| Infraestrutura extra           | **Nenhuma** | Cluster Temporal | Nenhuma | Nenhuma |
| Usa o banco que você já tem    | ✅       | ❌              | ❌         | ❌        |
| Linhas para uma saga completa  | **~25**  | ~70             | ~80        | ~50       |
| Dashboard de debug nativo      | ✅       | ✅              | ✅ (pago)  | ❌        |
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

- Java 21+
- Spring Boot 3.x ou 4.x
- PostgreSQL 14+ (recomendado), MySQL 8.0+ ou H2

---

## Licença

Apache 2.0 — livre para uso em projetos comerciais, para sempre.
