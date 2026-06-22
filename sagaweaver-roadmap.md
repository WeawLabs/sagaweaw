# Sagaweaw — Roadmap Estratégico

> Documento interno — não vai ao repositório público.

---

## 🚨 Task 71 — Migrar sagaweaw de Spring Boot 4.x para 3.x (versão 1.0.9)

> **Prioridade: CRÍTICA — bloqueia adoção.** sagaweaw-kotlin compilado com Spring Boot 4.x (Kotlin 2.2)
> é incompatível com 90%+ dos projetos Spring Boot em produção (Spring Boot 3.x + Kotlin 2.0).
> A migração para Spring Boot 3.x resolve isso definitivamente, sem nenhum workaround.

### Por que Spring Boot 3.x

- Spring Boot 3.x usa Spring Framework 6.x, compilado com Kotlin 1.9/2.0
- sagaweaw-kotlin compilado sobre Spring Boot 3.x funciona nativamente com projetos Kotlin 2.0+
- Spring Boot 3.x é o baseline de 90%+ dos projetos Spring Boot em produção em 2025–2026
- Spring Boot 4.x (lançado nov/2025) está em adoção inicial, minoria do mercado
- ADR-015 precisa ser revisado: a premissa "Spring Boot 4.x garante compatibilidade com ecossistema atual" estava errada — o ecossistema atual é 3.x

### Escopo de mudanças no código (sagaweaw-spring)

**1. pom.xml raiz — 1 linha**
```xml
<!-- de -->
<artifactId>spring-boot-starter-parent</artifactId>
<version>4.0.6</version>
<!-- para -->
<artifactId>spring-boot-starter-parent</artifactId>
<version>3.4.x</version>   <!-- usar latest 3.4.x estável -->
```
Isso automaticamente puxa Spring Framework 6.x e Kotlin 2.0.x gerenciados pelo BOM.

**2. Jackson: tools.jackson → com.fasterxml.jackson — 8 arquivos**

Esta é a mudança mais trabalhosa. Jackson 3.x (`tools.jackson`) é exclusivo do Spring Boot 4.x.
Jackson 2.x (`com.fasterxml.jackson`) é o padrão no Spring Boot 3.x.

| Arquivo | Mudanças |
|---------|----------|
| `config/SagaAutoConfiguration.java` | `ObjectMapper` import |
| `config/SagaJacksonModule.java` | **Maior mudança** — API diferente entre Jackson 2 e 3: `JacksonModule` → `SimpleModule`, `SerializationContext` → `SerializerProvider`, `DeserializationContext` mantém o nome mas pacote muda |
| `config/SagaFlywayAutoConfiguration.java` | Sem Jackson, mas tem Spring Boot 4.x import (ver abaixo) |
| `api/SagaTriggerController.java` | `ObjectMapper` import |
| `api/SagaWebSocketHandler.java` | `ObjectMapper` import |
| `mapper/SagaMapper.java` | `ObjectMapper`, `DeserializationFeature`, `JacksonException` imports |
| `scheduler/OutboxRelay.java` | `ObjectMapper`, `TypeReference` imports |
| `test/.../SagaMapperTest.java` | `ObjectMapper` import |
| `test/.../SagaWebSocketHandlerTest.java` | `ObjectMapper` import |

**3. Spring Boot 4.x pacotes renomeados — 3 arquivos**

Spring Boot 4.x moveu alguns pacotes que precisam voltar para os nomes do 3.x:

| Arquivo | De (Boot 4.x) | Para (Boot 3.x) |
|---------|---------------|------------------|
| `config/SagaFlywayAutoConfiguration.java` | `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration` | `org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration` |
| `health/SagaHealthIndicator.java` | `org.springframework.boot.health.contributor.Health` | `org.springframework.boot.actuate.health.Health` |
| `health/SagaHealthIndicator.java` | `org.springframework.boot.health.contributor.HealthIndicator` | `org.springframework.boot.actuate.health.HealthIndicator` |
| `config/SagaAutoConfiguration.java` | `afterName` tem nomes Boot 4.x duplicados — limpar, manter só 3.x | já tem 3.x como backward compat — remover os 4.x |

**4. `SagaAutoConfiguration.java` — Customizers Boot 4.x vs 3.x**

Os customizers de HTTP client mudaram de pacote. Verificar e ajustar:
- `org.springframework.boot.restclient.RestTemplateCustomizer` → `org.springframework.boot.web.client.RestTemplateCustomizer`
- `org.springframework.boot.restclient.RestClientCustomizer` → verificar equivalente 3.x
- `org.springframework.boot.webclient.WebClientCustomizer` → `org.springframework.boot.web.reactive.function.client.WebClientCustomizer`

### Escopo de mudanças na documentação

**README.md e README.pt-BR.md**
- Atualizar badge Kotlin para mostrar "2.0+" (era implícito 2.2)
- Seção de requirements: mencionar Spring Boot 3.x+ e Kotlin 2.0+
- Remover o requisito de Kotlin 2.2 que havia sido adicionado

**doc-sagawaew/docs/getting-started/kotlin.md**
- Remover o warning box sobre Kotlin 2.1+ que foi adicionado (já não será necessário)
- Atualizar para informar que Kotlin 2.0+ é suportado nativamente

**doc-sagawaew/docs/getting-started/quickstart.md**
- Confirmar que Spring Boot 3.x + 4.x são ambos suportados

**sagaweaver-roadmap.md**
- Atualizar ADR-015

### Critério de conclusão

- [x] `./mvnw clean install -DskipTests` passa sem erros — BUILD SUCCESS (Spring Boot 3.4.5, Kotlin 1.9.25)
- [x] sagaweaw-kotlin compila com Kotlin 1.9 nativo — zero erros de metadata
- [ ] ararahq-api compila com Kotlin original `2.0.21` — pendente publicação 1.0.9
- [ ] Versão publicada como `1.0.9` no Maven Central

---

## 🔢 Release — Como bumpar a versão

**Use o script `scripts/set-version.sh`** para atualizar a versão em TODOS os lugares de uma vez:

```bash
./scripts/set-version.sh 1.0.10
```

O script atualiza automaticamente:
- Todos os `pom.xml` (via `mvnw versions:set`)
- `README.md` e `README.pt-BR.md`
- `doc-sagaweaw/docusaurus.config.ts` (constante `sagaweawVersion`)
- Todos os `.md` e `.mdx` da documentação

Sem o script, esses são os lugares que precisam ser atualizados manualmente:
1. `pom.xml` raiz — `<version>X.X.X</version>`
2. `sagaweaw-kotlin/pom.xml` — `<version>X.X.X</version>` no parent
3. `README.md` — snippets Maven e Gradle
4. `README.pt-BR.md` — idem
5. `doc-sagaweaw/docusaurus.config.ts` — constante `sagaweawVersion`
6. `doc-sagaweaw/docs/getting-started/quickstart.md`
7. `doc-sagaweaw/docs/getting-started/kotlin.md`
8. `doc-sagaweaw/docs/intro.md`

Após o script: commit + publicar no Maven Central (ver `SAGAWEAW_RELEASE_GUIDE.md`).

---

## ⚠️ Kotlin — Requisito de versão do sagaweaw-kotlin

`sagaweaw-kotlin` exige **Kotlin 2.1+** no projeto do consumidor.

**Por quê:** o módulo depende de `sagaweaw-spring-boot-starter`, que usa Spring Boot 4.x → Spring Framework 7.x → compilado com Kotlin 2.2. O compilador Kotlin faz checagem de metadata binária no classpath inteiro. Um projeto em Kotlin 2.0 não consegue ler a metadata 2.2 do Spring Framework 7.x, mesmo que não use nenhuma feature Kotlin do Spring diretamente.

**Por que não dá compilar sagaweaw-kotlin com Kotlin 2.0:** sagaweaw-kotlin depende de `sagaweaw-spring-boot-starter` → Spring Boot 4.x → Spring Framework 7.x, todos compilados com Kotlin 2.2. O compilador Kotlin 2.0 recusa compilar código que importa dependências com metadata 2.2, mesmo que sagaweaw-kotlin não use nenhuma feature Kotlin dessas dependências.

**Decisão tomada (1.0.9):** sagaweaw-kotlin é e sempre será compilado com Kotlin 2.2 (versão do Spring Boot 4.x). O requisito mínimo no projeto consumidor é **Kotlin 2.1+**. Isso está documentado em `docs/getting-started/kotlin.md` com o fix de uma linha para quem está em 2.0.x.

**Se algum usuário reportar problema:** a solução é atualizar o plugin Kotlin no `build.gradle.kts` para `2.2.21`. É backward-compatible com Spring Boot 3.x.

---

---

## Visão Geral

**Sagaweaw** é uma biblioteca Java/Spring Boot open-source para orquestração de transações distribuídas com compensação automática, observabilidade em tempo real e dashboard web.

**Mercado-alvo:** internacional (EN como idioma principal de produto e comunidade), com Brasil como mercado secundário forte e base inicial de validação.

**Stack:**
- Backend: Java 17 LTS+ (runtime), Java 21 (build) + Spring Boot 4.x
- Mensageria: Apache Kafka (opcional)
- Banco: PostgreSQL (recomendado), MySQL 8+, H2
- Frontend: React + TypeScript + Tailwind
- Infra: Docker + GitHub Actions

**Posicionamento:** "Temporal e Axon requerem cluster novo ou curva de aprendizado enorme. Sagaweaw roda no PostgreSQL que você já tem — zero infraestrutura extra." Este é o equivalente ao que Resilience4j fez com o Hystrix: posicionar contra um incumbente caro/complexo e oferecer algo que funciona no que o dev já usa.

---

## Princípios Inegociáveis

> Toda decisão técnica, de API e de produto é avaliada contra esses princípios. Se contradiz um deles, volta pra mesa.

1. **Developer Experience primeiro.** Critério de aceitação: *"Um dev que nunca viu o Sagaweaw consegue usar isso em menos de 10 minutos lendo só o README?"* Se não, a API está errada.

2. **Zero inferências que o dev não pediu.** O engine infere StepType pelo que o dev declara. `.compensate()` → COMPENSABLE. Sem `.compensate()` → PIVOT. Retry infinito → RETRIABLE.

3. **Zero infraestrutura extra.** Roda com o PostgreSQL que o time já tem. Nenhum novo cluster, nenhum serviço novo. Maior diferencial sobre Temporal e Axon.

4. **Estado sempre legível por humano.** Qualquer pessoa com acesso ao banco entende o estado atual com um `SELECT`. Sem formato binário, sem event replay obrigatório.

5. **O exemplo é o produto.** Uma saga completa com 3 steps e 1 compensação deve caber em menos de 30 linhas. Se não cabe, a API está complexa — iteramos na API.

6. **Dashboard feito para devs, legível para CTOs.** Público primário: dev debugando às 23h. Secundário: CTO entendendo o estado sem ler código. Dev ganha quando há conflito.

7. **Testabilidade como feature de primeira classe.** O Sagaweaw fornece `SagaTestKit` nativamente — simular falhas em steps específicos é parte da biblioteca.

---

## ADRs — Registro de Decisões Arquiteturais

> Cada ADR explica o **porquê** de uma decisão que surpreenderia um contribuidor sem contexto. O histórico completo fica aqui.

| ADR | Decisão | Alternativa rejeitada |
|-----|---------|----------------------|
| **ADR-001** | Modelo **CP** (Consistência + Tolerância a Partição) | AP — inconsistência aceitável |
| **ADR-002** | Consistência **forte** dentro do engine (PostgreSQL + ACID); consistência **eventual** entre microsserviços via Saga | Consistência forte end-to-end via 2PC |
| **ADR-003** | **At-least-once** com idempotent consumer — nunca exactly-once | Exactly-once (custo operacional alto, problema resolvido pelo Outbox) |
| **ADR-004** | **Sem Redis** no core — optimistic locking via coluna `version` no PostgreSQL resolve concorrência | Redis distributed lock (infra extra, contradiz princípio 3) |
| **ADR-005** | **Orchestration** sobre Choreography — observabilidade centralizada é requisito do produto; orquestrador stateless, escala horizontalmente | Choreography (dificulta rastreamento de sagas) |
| **ADR-006** | **StepType inferido** pelo engine, não declarado pelo dev | `.type(StepType.PIVOT)` obrigatório (boilerplate, curva de aprendizado) |
| **ADR-007** | `saga_steps` salva **input e output** de cada step em JSONB — compensações usam dados reais da execução | Compensação usa estado atual do contexto (causa compensação assimétrica) |
| **ADR-008** | Status `EXECUTING` na tabela `sagas` como **Semantic Lock nativo** — queries de observabilidade filtram esse estado automaticamente | Lock explícito via tabela separada |
| **ADR-009** | **Polling Publisher** sobre CDC (Debezium) — zero infra extra, latência de segundos aceitável para sagas de negócio | CDC/Debezium (requer Kafka Connect + infra, contradiz princípio 3) |
| **ADR-010** | `OutboxRelay` adiciona **idempotency-key** como header Kafka em toda mensagem — fecha ciclo at-least-once sem configuração extra | Consumer verifica duplicatas no banco (acoplamento alto) |
| **ADR-011** | **Multi-banco** (PostgreSQL, MySQL, H2) — Flyway com `locations: {vendor}`; entidades sem `columnDefinition = "jsonb"` | PostgreSQL-only (limita adoção) |
| **ADR-012** | **Flyway embedded** — migrations SQL empacotadas no JAR em `db/migration/{vendor}/`; dev não executa DDL manualmente em nenhum ambiente | Script SQL entregue na documentação para execução manual |
| **ADR-013** | Dashboard em **porta standalone 8484** como padrão; modo embedded opcional via `sagaweaw.dashboard.mode=embedded` | Sempre embedded na porta do cliente (conflito de porta, dificulta onboarding) |
| **ADR-014** | **Token-based auth** para a API de observabilidade via `HandlerInterceptor` próprio; **403 por padrão** se token não configurado; sem dependência de Spring Security | Spring Security (dependência pesada obrigatória, configuração complexa para quem já tem Security no projeto) |
| **ADR-015** | **Spring Boot 3.x** como baseline (revertido em 1.0.9) — Jackson 2.x (`com.fasterxml.jackson.*`); cobre 90%+ dos projetos Spring Boot em produção; `sagaweaw-kotlin` funciona com Kotlin 2.0+ nativamente. Decisão original (Spring Boot 4.x) foi revertida pois Spring Boot 4.x + Kotlin 2.2 bloqueava adoção em projetos Spring Boot 3.x + Kotlin 2.0 | Spring Boot 4.x (abandonado — criava incompatibilidade de metadata Kotlin) |
| **ADR-016** | **Modo `embedded` como default para o dashboard** (1.0.11) — jar publicado no Maven Central inclui os assets do dashboard em `META-INF/sagaweaw-dashboard/` (profile `frontend-build` ativo no release); ao subir a aplicação, `/sagaweaw` já responde sem nenhuma configuração extra além do token. Modo `standalone` (porta 8484) continua disponível e é o padrão para desenvolvimento local com Vite. | `standalone` como default (obrigava o dev a clonar o repo e rodar `npm run dev` apontando para produção — impraticável) |

---

## Fase 1 — Core Engine (Histórico)

> MVP concluído. A base do engine está em produção no Maven Central como `dev.sagaweaw:sagaweaw-spring-boot-starter:1.0.1`.

**Critério de conclusão:** dev adiciona 1 dependency, escreve uma Saga com 3 steps em < 30 linhas, sobe com `docker-compose up`, vê resultado no dashboard, tudo em < 15 minutos.

### Tasks concluídas

- [x] **Task 1:** Configurar monorepo Maven multi-module + publicação Maven Central
- [x] **Task 2:** Criar `SagaDefinition` interface e `SagaBuilder` (Fluent API)
- [x] **Task 3:** Criar `SagaStep` com invoke + compensate + retryPolicy — StepType inferido internamente
- [x] **Task 4:** Criar `SagaEngine` — State Machine central
- [x] **Task 5:** Implementar persistência (`SagaRepository` com Spring Data JPA)
- [x] **Task 6:** Implementar `StepExecutor` com transação atômica (step + outbox na mesma TX)
- [x] **Task 7:** Implementar `CompensationExecutor` — compensações em ordem inversa via `step_order`
- [x] **Task 8:** Implementar `RetryScheduler` — backoff exponencial com `@Scheduled`
- [x] **Task 9:** Implementar `OutboxRelay` — polling que publica mensagens no Kafka
- [x] **Task 10:** Criar `SagaAutoConfiguration` (Spring Boot Starter)
- [x] **Task 11:** Testes de integração com Testcontainers (PostgreSQL + Kafka) — fix Docker 29.x via `docker-java.properties`
- [x] **Task 12:** Exemplo funcional end-to-end (OrderSaga, PixPaymentSaga)
- [x] **Task 14:** READMEs em inglês e português com marketing copy
- [x] **Task 15:** Repositório `sagaweaw-docs` (Docusaurus) — guias, ADRs, API reference, PT-BR + EN
- [x] **Task 16:** `SagaTestKit` com `simulateFail`, `simulateTimeout` e `assertCompensated`
- [x] **Task 17/21:** Flyway auto-migration embedded — migrations SQL empacotadas no JAR
- [x] **Task 19:** Métricas Micrometer automáticas (saga-level + step-level counters e gauges)
- [x] **Task 20:** CI com GitHub Actions — build + testes unitários + testes de integração + SpotBugs + Checkstyle
- [x] **Task 22:** `SagaHealthIndicator` — `/actuator/health/sagas` com sagas stuck e dead letters
- [x] **Task 23:** `@SagaweawTest` test slice — contexto Spring mínimo sem Testcontainers
- [x] **Task 24:** `X-Saga-ID` header propagation — correlação de logs entre serviços
- [x] **Task 26:** Interceptor de token embutido — 403 por padrão, sem Spring Security
- [x] **Task 28:** Suporte multi-banco — PostgreSQL, MySQL e H2; Flyway com locations `{vendor}`
- [x] **Task 39:** MDC enriquecido — `sagaId`, `sagaName`, `stepName`, `attempt` em todo log dentro de um step

---

## Fase 2 — Dashboard (Histórico)

> Dashboard React funcional, modo standalone porta 8484. Lançado junto com v1.0.1.

### Tasks concluídas

- [x] **Task 29:** SagaFeed + SagaTimeline em React + TypeScript + Tailwind; i18n PT-BR + EN; token gate
- [x] **Task 30:** WebSocket STOMP — atualizações em tempo real no SagaFeed
- [x] **Task 31:** Tela Dead Letter Queue — lista + botão reprocessar individual
- [x] **Task 32:** Tela de Métricas — volume por hora, taxa de sucesso/falha, latência P50/P95
- [x] **Task 33:** Modo embedded — dashboard servido pelo servidor do cliente em `/sagaweaw`
- [x] **Task 34/34b:** Sidebars colapsáveis — Retry Queue + Outbox health + distribuição por tipo de saga
- [x] **Task 35:** Bottleneck de steps — duração média e P95 por step_name agrupado por saga type
- [x] **Task 37:** Layout fixo tipo viewport — feed com scroll próprio, paginação de 20 itens, live-reload na page 0
- [x] **Task 38:** Saga-level Micrometer counters — `sagaweaw.sagas.started/completed/failed/compensated`
- [x] **Task 40:** READMEs reposicionados — Prometheus/Grafana/MDC como observabilidade principal; dashboard como lente de debug
- [x] **Task 41:** Dead Letters corrigido — reprocessar retorna 422 com mensagem legível; trace scrollável
- [x] **Task 42:** SuccessBar horizontal segmentada substituindo donut SVG
- [x] **Task 43:** Header UX — GearIcon com dropdown de configurações; BookIcon para documentação
- [x] **Task 44:** RightPanel — duração média por tipo de saga; seção de integrações com MDC e Prometheus

---

## Fase 2 — Expansão de Produto e Crescimento

> O produto já funciona. Agora o objetivo é tornar a lib indispensável para times que já usam Spring Boot — e garantir que as pessoas certas a conheçam. Esta fase cobre: produto técnico, site/docs, conteúdo e crescimento de comunidade. Não adianta ter features e ninguém ver.

---

### 2A — Segurança e Integração com o Ecossistema que o Dev já usa

- [x] **Task 72:** `@SagaMask` — anotação para mascarar campos sensíveis no `context_json`
  - Campos anotados com `@SagaMask` são substituídos por `"[REDACTED]"` antes de serem escritos no banco, retornados pela API e incluídos em dead letters
  - Implementado via `BeanSerializerModifier` no `SagaJacksonModule` — zero configuração extra
  - Uso: `@SagaMask String email;` no Context da saga
  - Importante: mascaramento é unidirecional — compensações que precisam do dado original devem buscá-lo na fonte
  - Localização: `SagaMask.java` em `sagaweaw-core`, lógica em `SagaJacksonModule.java`

- [x] **Task 73:** CORS auto-config via `sagaweaw.observability.cors.allowed-origins`
  - A lib configura CORS automaticamente apenas para `/api/sagas/**` e `/api/dead-letters/**`
  - Não toca no CORS do restante da aplicação do usuário
  - Ativado apenas quando a propriedade estiver configurada
  - Resolve o problema do dashboard standalone (porta 8484) acessando a API em outra origem
  - Exemplo: `sagaweaw.observability.cors.allowed-origins=http://localhost:8484,https://staging.myapp.com`



> O maior insight de observabilidade em 2024-2025: 69% dos devs Java já rodam OpenTelemetry em produção. Se o Sagaweaw aparecer automaticamente no Jaeger/Grafana Tempo/Datadog do dev, ele para de ser "mais uma lib" e vira parte do ecossistema. Esta é a feature que mais converte adoção.

- [x] **Task 50:** OpenTelemetry — spans automáticos para cada event do ciclo de vida da saga, usando a Micrometer Observation API (Spring Boot 3.x: `micrometer-tracing-bridge-otel`; Spring Boot 4.x: `spring-boot-starter-opentelemetry`)
  - Spans gerados: `saga.started`, `saga.step.invoke`, `saga.step.completed`, `saga.step.failed`, `saga.step.compensating`, `saga.step.compensated`, `saga.completed`, `saga.compensated`
  - Atributos OTel em cada span: `saga.name`, `saga.id`, `saga.step.name`, `saga.step.attempt`, `saga.step.type`
  - Zero configuração do dev — se OTel estiver no classpath, spans aparecem automaticamente no backend configurado (Jaeger, Grafana Tempo, Honeycomb, Datadog, Zipkin)
  - `sagaweaw.tracing.enabled=false` para desabilitar

- [x] **Task 51:** Grafana Dashboard template — arquivo JSON pré-construído com painéis para as métricas Micrometer do Sagaweaw
  - Painéis: taxa de sucesso/falha por saga type, latência P50/P95, dead letters pendentes, volume por hora, sagas em execução agora
  - Publicado em `docs/grafana-dashboard.json` e linkado na documentação
  - Meta: dev faz Import no Grafana, cola o JSON, vê seus dados em 30 segundos

- [x] **Task 52:** Kafka como opt-in explícito — `sagaweaw.kafka.enabled=false` como default documentado com destaque; tornar visível que o Sagaweaw funciona 100% sem Kafka; guia separado "Using Sagaweaw with Kafka" para quem quer Outbox → Kafka

- [ ] **Task 69:** H2 in-memory como modo dev zero-config — eliminar completamente a necessidade de banco de dados para começar a usar o Sagaweaw localmente
  - Quando nenhum datasource externo está configurado, o starter detecta H2 no classpath e sobe automaticamente com schema em memória
  - Dev adiciona a dependência, escreve a saga, sobe a aplicação → vê no dashboard. Sem Docker, sem banco configurado, sem configuração extra
  - Análogo ao `temporal server start-dev` (Temporal) e ao `SimpleEventStore` (Axon) — zero-friction default
  - H2 já é suportado nas migrations Flyway (Task 28). O trabalho aqui é a auto-detecção e fallback automático na `SagaAutoConfiguration`
  - Critério de aceitação: dev com Spring Boot + H2 no classpath + sagaweaw-spring-boot-starter vê a primeira saga no dashboard sem configurar nada além do `sagaweaw.observability.token`

---

### 2B — Dashboard: dados que só o Sagaweaw tem

> O dashboard já tem bons gráficos. O que falta são **dados específicos de negócio** que o dev só consegue ver aqui — não no Grafana genérico. Prioridade: funcionalidade que o dev queira abrir às 23h para debugar.

- [x] **Task 53:** Busca global por ID de negócio — campo no SagaFeed que procura no `context_json`; ex: digitar `abc-123` encontra a saga com `"orderId":"abc-123"` no contexto; endpoint `GET /api/sagas/search?q=abc-123`; essencial para suporte ("o pedido X falhou — o que aconteceu?")

- [x] **Task 54:** Detecção de sagas travadas (stuck) — saga com status `EXECUTING` há mais de N minutos sem atualização é destacada em vermelho com badge "STUCK"; `sagaweaw.stuck-saga-threshold-minutes=15` configurável; aparece como seção separada no dashboard e dispara alerta webhook (Task 58)

- [x] **Task 55:** Sagas relacionadas — no detalhe de uma saga, seção "Related sagas" mostrando outras sagas com o mesmo `orderId`/`customerId` no contexto; implementado como query por similaridade no `context_json`; ajuda a entender o histórico completo de uma transação de negócio

- [x] **Task 56:** Visualização do fluxo da saga — diagrama estático dos steps em sequência (não execution timeline, mas a definição da saga); endpoint `GET /api/sagas/{sagaName}/definition` retorna step names, tipos e sequência; renderizado como fluxograma SVG simples no detalhe da saga; o dev vê "qual é o fluxo esperado" vs "o que aconteceu"

- [x] **Task 57:** Reprocessamento em lote de dead letters — checkbox em cada dead letter + botão "Reprocess selected"; endpoint `POST /api/dead-letters/reprocess-batch` com body `{"ids": [...]}`; útil quando um serviço externo ficou fora por 1h e gerou dezenas de dead letters

---

### 2C — Alertas, Retenção e Multi-instância

- [x] **Task 58:** Alertas via webhook — configurar uma URL de webhook para receber eventos críticos; apenas `application.properties`, sem UI de configuração
  ```properties
  sagaweaw.alerts.webhook-url=https://hooks.slack.com/services/...
  sagaweaw.alerts.on-dead-letter=true
  sagaweaw.alerts.on-stuck-saga=true
  sagaweaw.alerts.failure-rate-threshold=0.05
  ```
  - Payload JSON padronizado: `{event, sagaId, sagaName, stepName, timestamp, details}`
  - Funciona com Slack, Discord, Teams, PagerDuty, qualquer endpoint HTTP POST
  - Implementado com `RestTemplate` — zero dependência nova

- [x] **Task 59:** Export CSV — botão na tela de Dead Letters e no SagaFeed filtrado
  - Endpoints: `GET /api/dead-letters/export?format=csv` e `GET /api/sagas/export?status=FAILED&from=2025-01-01`
  - Colunas: sagaId, sagaName, stepName, status, createdAt, errorMessage, context (truncado a 200 chars)
  - Zero dependência de iText/PDFBox — gerado com `StringBuilder` e `text/csv` response

- [x] **Task 60:** Retenção configurável — `sagaweaw.data.retention-days=30` (default: nunca deletar); job `@Scheduled` noturno arquiva sagas concluídas há mais de N dias em `sagas_archive`; dead letters ficam até reprocessamento manual; dev controla o crescimento do banco sem perder auditoria

- [x] **Task 61:** Suporte a múltiplas instâncias — N instâncias compartilhando o mesmo banco (horizontal scaling); coluna `instance_id` em `sagas` registra qual instância criou; `GET /api/instances` lista instâncias ativas nas últimas N horas; dashboard agrega sem duplicar

---

### 2D — Site e Documentação: o problema mais urgente

> **Contexto:** Usuários iniciais reportaram que não entenderam o que o Sagaweaw faz. O site e as postagens estão técnicos demais. As pessoas precisam sentir a dor antes de ver a solução técnica. Um dev que não sente a dor em 10 segundos fecha a aba.
>
> Referência: Temporal abre com "Build invincible apps". Resilience4j abre com o problema de cascading failure. O Sagaweaw precisa abrir com a DOR — não com jargão técnico.

- [x] **Task 62:** Reescrever o hero da doc site (`doc.sagaweaw.dev`) — primeira tela responde em 5 segundos: "qual problema isso resolve para mim?"
- [ ] **Task 63:** Screenshot e GIF animado do dashboard — maior prova de valor é ver funcionando; GIF de 10 segundos mostrando saga começa → step falha → compensações executam → aparece na Dead Letters
- [x] **Task 64:** Seção "How it works" visual — diagrama simples (não código) mostrando o que acontece quando um step falha
- [x] **Task 65:** Reescrever o Getting Started — "In 5 minutes you'll have a saga running with automatic compensation"
- [x] **Task 66:** Página "Why not Temporal / Axon / Eventuate" — comparação honesta em linguagem humana
- [x] **Task 67:** Página de casos de uso — "Payment processing", "Order fulfillment", "User onboarding"
- [x] **Task 68:** Reservar seção "Teams using Sagaweaw" na doc
- [x] **Task 70:** Documentar staging e integração com observability cloud (Grafana Cloud, Datadog, New Relic)

---

### 2E — Dashboard em Produção: Zero Fricção (v1.0.11)

- [x] **Task 74:** Verificar e criar tela de login no dashboard React
- [x] **Task 75:** Mudar default do dashboard de `standalone` para `embedded`
- [x] **Task 76:** Incluir dashboard no jar publicado no Maven Central
- [x] **Task 77:** Bump de versão 1.0.10 → 1.0.11
- [x] **Task 78:** Atualizar README.md e README.pt-BR.md com fluxo de 5 passos em destaque
- [x] **Task 79:** Atualizar documentação (doc site) com páginas de produção e dev local separadas

---

### 2F — Comunidade e Amplificação

**Divulgação com pessoas que amplificam:**

- [ ] **Task 80:** Contato com Josh Long (Spring Developer Advocate, Broadcom) — @starbuxman no Twitter/X
- [ ] **Task 81:** Contato com Dan Vega (Spring Developer Advocate, Broadcom) — danvega.dev/contact
- [ ] **Task 82:** Contato com Giuliana Bezerra (Microsoft MVP, YouTuber Java BR) — @giulianabezerra no LinkedIn
- [ ] **Task 83:** Submissão de palestra no SouJava — "Zero-infra Saga Orchestration in Spring Boot"
- [ ] **Task 84:** Submissão para "This Week in Spring" newsletter
- [ ] **Task 85:** Identificar 10 empresas Spring Boot com microsserviços transacionais e entrar em contato direto
- [ ] **Task 86:** Template de mensagem para outreach
  ```
  Subject: Zero-infra saga orchestration for Spring Boot — want early access?

  I built Sagaweaw — saga orchestration on your existing PostgreSQL,
  no Temporal cluster needed. One dependency, automatic compensation,
  real-time debug dashboard on port 8484.

  Would you be open to 15 min to see if it fits your stack?
  I'll support your team directly.
  ```

---

### 2G — SDK Kotlin

- [x] **Task 87:** Criar módulo `sagaweaw-kotlin` dentro do mesmo repositório
- [x] **Task 88:** DSL idiomática Kotlin — resolver ambiguidade de `Consumer<C>` vs `SagaStepInvoker<C>`
- [x] **Task 89:** Registrar módulo no `pom.xml` raiz + publicar no Maven Central

---

### 2H — Segurança Operacional e Proteção de Dados

> **Contexto de design:** `@SagaMask` já impede PII de chegar ao banco. O risco real que sobra: campos *sem* `@SagaMask` que acidentalmente carregam PII; ausência de brute-force protection no endpoint de autenticação; impossibilidade de rotacionar o `SAGAWEAW_TOKEN` sem downtime; e ausência de limite de tamanho de contexto que permite DoS por contextos gigantes.

- [x] **Task 117:** Rate limiting no `ObservabilityTokenInterceptor` — proteção contra força bruta; lockout por IP após N tentativas; 429 com `Retry-After`

- [x] **Task 118:** Rotação de `SAGAWEAW_TOKEN` sem downtime — `sagaweaw.observability.previous-token` aceito em paralelo durante janela de troca; WARN logado ao usar token anterior

- [x] **Task 119:** Limite de tamanho máximo do contexto — `sagaweaw.engine.max-context-bytes=65536`; lança `SagaContextTooLargeException` antes de qualquer persistência

---

### 2I — Core Engine: Gaps Críticos vs Temporal

> Estas duas features fecham os casos de uso onde times avaliam Temporal e decidem não usar Sagaweaw. São gaps arquiteturais reais, não cosmética. Implementar antes de expandir para outros frameworks — um engine mais completo beneficia todas as linguagens futuras.

- [ ] **Task 120:** **Timers duráveis** — `step.waitFor(Duration)` que persiste e sobrevive a reinicialização da JVM.

  **Problema atual:** sagas que precisam aguardar horas ou dias (retry em 6h se serviço externo estiver fora, cobrar assinatura em 30 dias, aguardar janela de processamento bancário) hoje precisam de solução externa. Isso é um blocker para casos como cobrança recorrente, onboarding com e-mail de follow-up, ou espera por aprovação manual com SLA.

  **Implementação:**
  - Coluna `resume_at TIMESTAMPTZ` na tabela `saga_steps` — step com timer fica em status `WAITING_TIMER`
  - `RetryScheduler` existente (já usa `@Scheduled`) passa a verificar também steps com `resume_at <= NOW()`
  - API do step: `.waitFor(Duration.ofHours(6))` e `.waitUntil(Instant)`
  - Kotlin DSL: `step("await-window") { waitFor(6.hours) }`
  - O timer sobrevive a restart porque está persistido no PostgreSQL — não é `Thread.sleep`
  - Limite prático documentado: timers de até 1 ano. Além disso, recomenda-se redesign da saga.

  ```java
  sagaBuilder
      .step("charge-attempt-1")
          .invoke(ctx -> gateway.charge(ctx.getAmount()))
          .compensate(ctx -> gateway.refund(ctx.getChargeId()))
      .step("await-retry-window")
          .waitFor(Duration.ofHours(6))          // dorme 6h, engine acorda automaticamente
      .step("charge-attempt-2")
          .invoke(ctx -> gateway.charge(ctx.getAmount()))
  ```

- [ ] **Task 121:** **Signals** — injetar eventos externos em uma saga em andamento.

  **Problema atual:** sagas com pontos de aprovação manual (pagamento aguardando confirmação do gerente, pedido aguardando estoque disponível) hoje precisam de polling ou arquitetura externa. Isso acopla o código do chamador ao banco do Sagaweaw.

  **Implementação:**
  - Step declara `waitForSignal("nome-do-signal")` — fica em status `WAITING_SIGNAL`
  - Endpoint REST na API de observabilidade: `POST /api/sagas/{sagaId}/signal/{signalName}` com payload JSON opcional
  - Engine despacha o step que aguardava, injeta payload no contexto da saga
  - Autenticação via token existente — o mesmo `X-Sagaweaw-Token`
  - Timeout opcional: se signal não chegar em N minutos, step falha com `SignalTimeoutException` → compensação automática
  - Kotlin DSL: `step("await-approval") { waitForSignal("manager-approved", timeout = 30.minutes) }`

  ```java
  // Na saga
  sagaBuilder
      .step("reserve-inventory")
          .invoke(ctx -> inventory.reserve(ctx.getProductId(), ctx.getQty()))
          .compensate(ctx -> inventory.release(ctx.getReservationId()))
      .step("await-fraud-clearance")
          .waitForSignal("fraud-cleared", timeout = Duration.ofMinutes(30))
      .step("charge-payment")
          .invoke(ctx -> gateway.charge(ctx.getAmount()))

  // No serviço de fraud (ou qualquer serviço externo)
  POST /api/sagas/01HX4K.../signal/fraud-cleared
  { "analysisId": "FA-8821", "risk": "low" }
  ```

  **Casos de uso habilitados por Signals:**
  - Aprovação manual em fluxos financeiros (>$10k via gerente)
  - Webhook de terceiro confirma pagamento processado
  - Estoque reabastecido após reserva inicial falhar
  - Confirmação de entrega via OTP do cliente

---

### 2J — Chaos Engineering (Dev Tool)

> Ferramenta de desenvolvimento e staging — injetar falhas intencionais para validar compensações antes de uma falha real em produção. O reporting completo e o Chaos Studio ficam no Sagaweaw Cloud (Pro). A lib entrega a mecânica de fault injection gratuitamente.

- [ ] **Task 122:** Fault injection via propriedades — o engine verifica configurações de chaos antes de executar cada step. Zero código no app do dev além das propriedades.

  ```properties
  # application-staging.properties — NUNCA em produção
  sagaweaw.chaos.enabled=true

  # Falhar X% das execuções de um step específico
  sagaweaw.chaos.sagas.order-fulfillment.steps.charge-payment.fail-rate=0.15

  # Adicionar latência artificial em ms
  sagaweaw.chaos.sagas.order-fulfillment.steps.reserve-inventory.latency-ms=2000

  # Lançar exceção específica
  sagaweaw.chaos.sagas.order-fulfillment.steps.charge-payment.exception=com.example.PaymentGatewayException
  ```

  Comportamento: quando `fail-rate=0.15`, 15% das execuções daquele step lançam `SagaChaosException`, triggering a compensação automática. O dev vê no dashboard (local ou Cloud) quais execuções foram intencionalmente caóticas com badge "⚡ CHAOS".

  **Proteções:** chaos mode só ativa se `sagaweaw.chaos.enabled=true` estiver explícito. Engine lança `IllegalStateException` ao subir em produção com chaos habilitado (detectado via Spring profile `prod` ou via variável `SAGAWEAW_ENV=production`). Nunca silencioso.

- [ ] **Task 123:** `@SagaChaos` anotação por classe — alternativa às propriedades para testes programáticos:
  ```java
  @SagaweawTest
  @SagaChaos(saga = "order-fulfillment", step = "charge-payment", failRate = 0.5)
  class OrderFulfillmentChaosTest {
      @Test
      void compensations_handle_payment_failure() {
          // 50% dos charges falham — asserta que compensações executam
      }
  }
  ```

---

## Fase 3 — Multi-Framework e Multi-Linguagem

> Sagaweaw começa no Spring Boot, mas o padrão saga é universal. Expansão acontece quando tiver tração — não antes.

### 3.1 Quarkus (próximo após Spring Boot)

- [ ] **Task 110:** `sagaweaw-quarkus` — extensão Quarkus com CDI em vez de Spring IoC
- [ ] **Task 111:** Testes de integração com Quarkus Dev Services
- [ ] **Task 112:** README específico para Quarkus; guia de migração

### 3.2 Java puro (sem framework)

- [ ] **Task 113:** `sagaweaw-jdbc` — implementação do `SagaEngine` com JDBC puro
- [ ] **Task 114:** `sagaweaw-standalone` — JAR executável que sobe apenas o engine e a API de observabilidade

### 3.3 Micronaut

- [ ] **Task 115:** `sagaweaw-micronaut` — módulo com configuração via `@Factory`
- [ ] **Task 116:** Benchmark comparativo: Sagaweaw no Micronaut vs Spring vs Quarkus

### 3.4 Além da JVM (longo prazo)

> Avaliar apenas com tração real — não implementar por curiosidade.

- **Go:** mercado de microserviços cloud-native
- **Node.js / TypeScript:** ecossistema crescente de backends Node
- **Python:** MLOps e pipelines de dados com transações distribuídas

---

## Modelo de Negócio

> Referência de mercado (2025): Temporal Cloud a partir de $100/mês + $25/M ações; Orkes a partir de $695/mês. Vácuo identificado: nenhum produto de orquestração de sagas entre $0 e $695/mês.

### PLG — Product-Led Growth

```
Dev descobre a lib (GitHub / Baeldung / Hacker News / SouJava / dev.to)
      │
      ▼
Usa gratuitamente em produção
      │
      ▼
Sente a dor: histórico longo / alertas / múltiplos ambientes / multi-tenant
      │
      ▼
Assina Sagaweaw Cloud
```

### O que fica gratuito (para sempre)

- Biblioteca completa — engine, compensação, retry, outbox, `sagaweaw-kotlin`
- API de observabilidade local (`/api/sagas`)
- Dashboard local (porta 8484) + alertas webhook
- OpenTelemetry spans automáticos + Grafana dashboard template
- CSV export, retenção configurável no próprio banco
- Suporte via GitHub Discussions + todos os exemplos

### O que converte para pago (por ordem de impacto)

1. **Um case real em produção.** Uma empresa usando em prod com nome/logo vale mais que qualquer landing page.
2. **Aparecer no ecossistema do dev.** OTel spans no Jaeger/Datadog do dev — quando aparece nas ferramentas que ele já usa, vira parte do stack.
3. **Dor da retenção.** "Preciso ver o que aconteceu há 6 meses" — o banco de prod do cliente não guarda para sempre.
4. **Dor do multi-ambiente.** Time crescendo, dev/staging/prod separados, on-call sem VPN às 2h.
5. **Apresentações em comunidades.** Josh Long / SouJava — 30 minutos com demo ao vivo gera mais adoção que 6 meses de marketing.

### Distribuição por mercado

**Internacional (prioridade 1):**
- Contato Josh Long / Dan Vega (Tasks 80, 81)
- "This Week in Spring" newsletter (Task 84)

**Brasil (prioridade 2):**
- SouJava + Giuliana Bezerra (Tasks 83, 82)
- Outreach para fintechs BR: Creditas, Cora, Neon, Dock, CloudWalk (Task 85)

---

## Milestones

| Marco | Descrição | Status |
|-------|-----------|--------|
| M1 | Saga com compensação funcionando | ✅ |
| M2 | Outbox + Kafka integrado | ✅ |
| M3 | Spring Boot Starter publicado no Maven Central | ✅ |
| M4 | Quickstart de 15 minutos validado | ✅ |
| M5 | Dashboard mostrando sagas ao vivo | ✅ |
| M6 | OTel spans automáticos (aparece no Jaeger/Datadog do dev) | ⬜ |
| M7 | Hero do site reescrito com dor primeiro (sem jargão técnico) | ⬜ |
| M8 | GIF do dashboard publicado no README e doc site | ⬜ |
| M9 | Artigo Baeldung publicado | ⬜ |
| M10 | Show HN postado + r/java | ⬜ |
| M11 | 50 estrelas no GitHub | ⬜ |
| M12 | 100 estrelas no GitHub | ⬜ |
| M13 | Primeira empresa em produção (feedback real) | ⬜ |
| M14 | Apresentação no SouJava ou Spring Office Hours | ⬜ |
| M15 | `sagaweaw-kotlin` publicado no Maven Central | ⬜ |
| M15b | Dashboard acessível em produção sem setup extra — `/sagaweaw` funciona com 1 variável de ambiente | ✅ |
| M16 | Suporte a Quarkus (`sagaweaw-quarkus`) | ⬜ |
