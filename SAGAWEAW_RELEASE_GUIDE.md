# Sagaweaw — Guia de Release e Infraestrutura

Documento gerado em 20/05/2026. Registro completo de como a biblioteca foi publicada, configurada e hospedada.

---

## Repositórios

| Repositório | URL | Descrição |
|-------------|-----|-----------|
| Biblioteca (público) | github.com/WeawLabs/sagaweaw | Repo público, 1 commit limpo por versão |
| Doc (público) | github.com/amosjuda/doc-sagaweaw | Docusaurus, hospedado na Vercel |
| Biblioteca (histórico) | github.com/WeawLabs/sagaweaw | Histórico completo de desenvolvimento |
| Doc (histórico) | github.com/WeawLabs/doc-sagawaew | Histórico completo |

**Clone local:** `/home/amos/git/Weaw/sagaweaw`
**Remote atual:** `git@github.com:WeawLabs/sagaweaw.git`

---

## Domínio

- **Domínio:** `sagaweaw.dev`
- **Registrador:** Porkbun
- **DNS gerenciado por:** Cloudflare (via Porkbun)

### Registros DNS configurados

| Type | Host | Value | Finalidade |
|------|------|-------|-----------|
| TXT | @ | `7eekwgt26g` | Verificação namespace Maven Central |
| CNAME | doc | *(configurar na Vercel)* | Documentação |

---

## Maven Central

- **Portal:** central.sonatype.org
- **Namespace verificado:** `dev.sagaweaw`
- **GroupId da biblioteca:** `dev.sagaweaw`
- **Credenciais:** salvas em `~/.m2/settings.xml`

### Como publicar uma nova versão

```bash
# 1. Atualizar versão em todos os pom.xml (ex: 1.0.0 → 1.1.0)
find . -name "pom.xml" -exec sed -i 's|<version>VERSAO_ATUAL</version>|<version>NOVA_VERSAO</version>|g' {} \;

# 2. Rodar o deploy (pede passphrase da chave GPG)
export GPG_TTY=$(tty)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw deploy -Prelease --no-transfer-progress -DskipTests -Dgpg.passphrase=SUA_SENHA_GPG

chave gpg - BF32FC7931BA2936B099F7A3C6172567CF03FAE8
pharafase - cria uma senha forte e guarda bem — vai precisar dela no deploy

comando completo = 
export GPG_TTY=$(tty)
  export MAVEN_GPG_PASSPHRASE="cria uma senha forte e guarda bem — vai precisar dela no deploy"
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw deploy -Prelease,frontend-build \
    --no-transfer-progress \
    -DskipTests \
    -Dgpg.keyname=BF32FC7931BA2936B099F7A3C6172567CF03FAE8 \
    -pl sagaweaw-core,sagaweaw-spring,sagaweaw-spring-boot-starter,sagaweaw-kotlin,sagaweaw-test \
    -am

# IMPORTANTE: o profile frontend-build inclui o dashboard React no jar do sagaweaw-spring.
# Sem ele, o modo embedded não tem arquivos para servir (META-INF/sagaweaw-dashboard/ vazio).
# Requer Node.js instalado na máquina de release (o plugin instala automaticamente v22.11.0).

# 3. Acessar central.sonatype.org/publishing/deployments e clicar em Publish

# 4. Aguardar 10min-2h para aparecer no Maven Central
```

### Dependência para usuários da biblioteca

```xml
<dependency>
    <groupId>dev.sagaweaw</groupId>
    <artifactId>sagaweaw-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Chave GPG

- **Fingerprint:** `BF32FC7931BA2936B099F7A3C6172567CF03FAE8`
- **Key ID:** `C6172567CF03FAE8`
- **Tamanho:** RSA 4096
- **Keyserver:** keys.openpgp.org
- **Email:** amosjudasilva@gmail.com

### Verificar se a chave está na máquina
```bash
gpg --list-secret-keys --keyid-format LONG
```

### Reenviar para keyserver (se necessário)
```bash
gpg --keyserver keys.openpgp.org --send-keys BF32FC7931BA2936B099F7A3C6172567CF03FAE8
```

### Exportar chave (backup)
```bash
gpg --export-secret-keys --armor BF32FC7931BA2936B099F7A3C6172567CF03FAE8 > sagaweaw-gpg-private.asc
```
> Guarde esse arquivo em local seguro (não commitar nunca).

---

## Arquivo ~/.m2/settings.xml

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>SEU_TOKEN_USERNAME</username>
      <password>SEU_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```
> Token gerado em: central.sonatype.org → Avatar → Generate User Token

---

## Como atualizar o repo público no GitHub

O repo público (`WeawLabs/sagaweaw`) usa commits orphan — cada versão é um commit único sem histórico anterior.

```bash
# Fazer as alterações nos arquivos, então:
git checkout --orphan public-release
git add .
git commit -m "chore: release vX.Y.Z"
git push git@github.com:WeawLabs/sagaweaw.git public-release:main --force
git checkout main
git branch -D public-release
```

---

## Documentação — Vercel

- **Repo:** github.com/amosjuda/doc-sagaweaw
- **Framework:** Docusaurus
- **URL pública:** doc.sagaweaw.dev *(configurar CNAME na Vercel)*

### Como configurar na Vercel (primeira vez)
1. Acessar vercel.com → New Project
2. Importar `amosjuda/doc-sagaweaw`
3. Framework: Docusaurus
4. Após deploy, ir em Settings → Domains → adicionar `doc.sagaweaw.dev`
5. Vercel fornece um CNAME — adicionar no Porkbun

### Como atualizar a doc
```bash
cd /home/amos/git/Weaw/doc-sagawaew
# fazer as alterações
git add .
git commit -m "docs: descrição da alteração"
git push
# Vercel faz deploy automático a cada push
```

---

## Módulos da biblioteca

| Módulo | Descrição |
|--------|-----------|
| `sagaweaw-core` | Contratos e interfaces puras (sem Spring) |
| `sagaweaw-spring` | Implementação Spring Boot |
| `sagaweaw-spring-boot-starter` | Auto-configuration |
| `sagaweaw-test` | Utilitários para testes (`@SagaweawTest`) |
| `sagaweaw-examples` | Exemplos de uso (order, pix) |
| `sagaweaw-dashboard` | Dashboard React/TypeScript embutido |

---

## Stack técnica

- Java 17 LTS+ (build requer Java 21)
- Spring Boot 4.x
- Maven multi-module
- PostgreSQL (produção), H2/MySQL suportados
- Apache Kafka (opcional)
- Flyway (migrations automáticas)
- React + TypeScript + Tailwind (dashboard)

**Build local:**
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw test
```
