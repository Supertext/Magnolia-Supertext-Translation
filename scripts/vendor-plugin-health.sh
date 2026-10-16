#!/bin/sh
# Copies the Java part of Supertext Plugin Health (version reports to the Supertext Commando
# Center, the API key check result, the ping answer) from the Commando Center repository into
# lib/supertext-plugin-health, with its package changed from com.supertext.pluginhealth to
# com.supertext.magnolia.translation.internal.pluginhealth.
# Maven compiles this copy into the module's jar (build-helper-maven-plugin in pom.xml), like
# the bundled SDK, so sites install nothing besides the jar. Nothing else to run: `mvn install`
# picks the copy up.
#
#   scripts/vendor-plugin-health.sh [commit-or-branch]   (default: main)
#   PLUGIN_HEALTH_DIR=/path/to/clone scripts/vendor-plugin-health.sh   (use a local clone as it is)
set -eu
REF="${1:-main}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [ -n "${PLUGIN_HEALTH_DIR:-}" ]; then
    SRC="$PLUGIN_HEALTH_DIR"
else
    git clone --quiet https://github.com/Supertext/Commando-Center-Supertext-Translation "$TMP/center"
    git -C "$TMP/center" checkout --quiet "$REF"
    SRC="$TMP/center"
fi
COMMIT="$(git -C "$SRC" rev-parse HEAD)"
# The project's own <version>, the first one after its <artifactId>.
VERSION="$(sed -n '/<artifactId>supertext-plugin-health<\/artifactId>/,/<\/version>/s:.*<version>\(.*\)</version>.*:\1:p' "$SRC/plugin-health/java/pom.xml" | head -n 1)"
if [ -z "$VERSION" ]; then
    echo "vendor-plugin-health: no version in plugin-health/java/pom.xml" >&2
    exit 1
fi

DEST="$ROOT/lib/supertext-plugin-health"
PKG_DIR="com/supertext/magnolia/translation/internal/pluginhealth"
rm -rf "$DEST"
mkdir -p "$DEST/src/$PKG_DIR"
# Everything lives in the one package com.supertext.pluginhealth.
cp "$SRC"/plugin-health/java/src/main/java/com/supertext/pluginhealth/*.java "$DEST/src/$PKG_DIR/"
cp "$SRC/LICENSE" "$DEST/LICENSE"
find "$DEST/src" -name '*.java' -exec sed -i 's/\bcom\.supertext\.pluginhealth\b/com.supertext.magnolia.translation.internal.pluginhealth/g' {} +
printf '%s\n' "$COMMIT" > "$DEST/COMMIT"
printf '%s\n' "$VERSION" > "$DEST/VERSION"
cat > "$DEST/README.md" <<EOT
# Supertext Plugin Health (bundled copy)

Java part of [Supertext Plugin Health](https://github.com/Supertext/Commando-Center-Supertext-Translation/tree/main/plugin-health),
version $VERSION, commit \`$COMMIT\`, package \`com.supertext.magnolia.translation.internal.pluginhealth\` instead of \`com.supertext.pluginhealth\`.
Compiled into the module's jar (see \`build-helper-maven-plugin\` in \`pom.xml\`).

Don't edit these files: fix it in Commando-Center-Supertext-Translation/plugin-health, then run \`scripts/vendor-plugin-health.sh\`.
EOT
if grep -rqE 'com\.supertext\.pluginhealth([^.A-Za-z]|$)' "$DEST/src"; then
    echo "vendor-plugin-health: unprefixed com.supertext.pluginhealth reference left in $DEST/src" >&2
    exit 1
fi
echo "lib/supertext-plugin-health: Supertext Plugin Health (Java) $VERSION from commit $COMMIT"
