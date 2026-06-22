# Contexto para o Claude — Sagaweaw Library

## Transferência de repositório

Este repositório foi transferido do perfil pessoal `github.com/amosjuda/sagaweaw` para a organização `github.com/WeawLabs/sagaweaw`.

Antes de qualquer tarefa neste repositório, verifique e corrija:

### Na lib (`Weaw/sagaweaw`)
- `pom.xml` raiz e de cada módulo — tags `<url>`, `<scm><url>`, `<scm><connection>`, `<scm><developerConnection>` ainda apontam para o perfil pessoal
- Todos os arquivos `README.md` — links, badges e referências ao repositório
- `.github/workflows/` — qualquer referência hardcoded ao repo antigo
- `CONTRIBUTING.md` — links de fork e clone
- Qualquer outro arquivo que referencie `github.com/amosjuda/sagaweaw`

### Na documentação (`Weaw/doc-sagaweaw`)
- Todos os arquivos `.md` com links para o repositório da lib
- Exemplos de `git clone` ou URLs de contribuição
- Badges de CI/CD, coverage ou versão que apontem para o repo antigo

### O que NÃO muda
- `groupId: dev.sagaweaw` no Maven Central — permanece igual
- Artefatos já publicados no Maven Central — não são afetados
- Dependências de projetos que usam a lib via Maven — continuam funcionando

## Estrutura do projeto

Multi-módulo Maven:
- `sagaweaw-core` — engine de orquestração
- `sagaweaw-spring` — integração Spring
- `sagaweaw-spring-boot-starter` — auto-configuração Spring Boot
- `sagaweaw-kotlin` — suporte Kotlin
- `sagaweaw-test` — utilitários de teste (SagaTestKit)
- `sagaweaw-cloud-agent` — (em desenvolvimento) agent que conecta ao Sagaweaw Cloud

## Relação com o Sagaweaw Cloud

Este repositório é a lib open-source (gratuita). O backend SaaS privado está em `Weaw/sagaweaw-cloud`. O módulo `sagaweaw-cloud-agent` que será adicionado aqui é o conector entre os dois — roda na aplicação do cliente e envia eventos para o Cloud via API key.
