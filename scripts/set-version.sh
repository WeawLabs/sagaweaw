#!/bin/bash
# =============================================================================
# set-version.sh — Atualiza a versão do sagaweaw em TODOS os lugares de uma vez.
#
# Uso:
#   ./scripts/set-version.sh 1.0.10
#
# O que este script atualiza:
#   1. Todos os pom.xml do projeto Maven (via mvnw versions:set)
#   2. README.md e README.pt-BR.md (referências de versão nos snippets)
#   3. doc-sagawaew/docusaurus.config.ts (constante SAGAWEAW_VERSION)
#   4. Todos os .md/.mdx da documentação (ocorrências de versão nos snippets)
#
# Após rodar o script, o único trabalho restante é:
#   - Fazer commit das mudanças
#   - Publicar no Maven Central (ver SAGAWEAW_RELEASE_GUIDE.md)
# =============================================================================

set -e

NEW_VERSION="${1:?Uso: $0 <nova-versao> (ex: $0 1.0.10)}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
DOC_ROOT="$ROOT/../doc-sagawaew"

# Detecta versão atual nos poms
CURRENT_VERSION=$(JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  "$ROOT/mvnw" help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | head -1)

echo "📦 Atualizando sagaweaw: $CURRENT_VERSION → $NEW_VERSION"

# 1. Atualiza todos os pom.xml
echo "  → pom.xml (todos os módulos)"
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  "$ROOT/mvnw" versions:set -DnewVersion="$NEW_VERSION" -DgenerateBackupPoms=false \
  --no-transfer-progress -q

# 2. Atualiza READMEs
echo "  → README.md e README.pt-BR.md"
sed -i "s/$CURRENT_VERSION/$NEW_VERSION/g" "$ROOT/README.md" "$ROOT/README.pt-BR.md"

# 3. Atualiza constante de versão no docusaurus.config.ts
if [ -f "$DOC_ROOT/docusaurus.config.ts" ]; then
  echo "  → docusaurus.config.ts (SAGAWEAW_VERSION)"
  sed -i "s/sagaweawVersion: '$CURRENT_VERSION'/sagaweawVersion: '$NEW_VERSION'/" \
    "$DOC_ROOT/docusaurus.config.ts"
fi

# 4. Atualiza todos os arquivos de documentação
echo "  → docs/ (todos os .md e .mdx)"
find "$DOC_ROOT/docs" -name "*.md" -o -name "*.mdx" | \
  xargs sed -i "s/$CURRENT_VERSION/$NEW_VERSION/g"

# 5. Atualiza dashboard package.json
if [ -f "$ROOT/sagaweaw-dashboard/package.json" ]; then
  echo "  → sagaweaw-dashboard/package.json"
  sed -i "s/\"version\": \"[^\"]*\"/\"version\": \"$NEW_VERSION\"/" \
    "$ROOT/sagaweaw-dashboard/package.json"
fi

echo ""
echo "✅ Versão atualizada para $NEW_VERSION em todos os lugares."
echo ""
echo "Próximos passos:"
echo "  git add -A && git commit -m 'chore(release): bump version to $NEW_VERSION'"
echo "  Ver SAGAWEAW_RELEASE_GUIDE.md para publicar no Maven Central."
