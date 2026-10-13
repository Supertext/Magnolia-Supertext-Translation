#!/usr/bin/env bash
# Builds the Supertext Java SDK (com.supertext:supertext-sdk) at a pinned commit and installs it
# into the local Maven repository, so `mvn install` of this module can find it.
#
# The SDK isn't published to Maven Central yet. Once it is, delete this script and its calls
# (ci.yml, release.yml, demo/Dockerfile) and let Maven download it.
#
# Usage: tools/sdk/install-sdk.sh
#   SUPERTEXT_SDK_SRC=/path/to/Supertext-API-SDK-and-Guide  build from a local checkout instead
#   SUPERTEXT_SDK_FORCE=1                                   rebuild even if the version is installed
set -euo pipefail

# Keep in sync with <supertext-sdk.version> in pom.xml.
SDK_VERSION=0.1.0
SDK_COMMIT=6468cf62630cf37a966ccadec94f65df39fb8a37
SDK_REPO=https://github.com/Supertext/Supertext-API-SDK-and-Guide

pom="$(dirname "$0")/../../pom.xml"
wanted=$(sed -n 's:.*<supertext-sdk.version>\(.*\)</supertext-sdk.version>.*:\1:p' "$pom" | head -n 1)
if [[ "$wanted" != "$SDK_VERSION" ]]; then
  echo "pom.xml wants Supertext SDK '$wanted', this script builds $SDK_VERSION: update both." >&2
  exit 1
fi

repo_dir=$(mvn -B -q help:evaluate -Dexpression=settings.localRepository -DforceStdout 2>/dev/null || true)
repo_dir=${repo_dir:-$HOME/.m2/repository}
installed="$repo_dir/com/supertext/supertext-sdk/$SDK_VERSION"
# The commit the installed jar was built from: a cached ~/.m2 (CI) may hold an older build of the same version.
marker="$installed/built-from-commit"
if [[ -f "$installed/supertext-sdk-$SDK_VERSION.jar" && "$(cat "$marker" 2>/dev/null)" == "$SDK_COMMIT" \
      && -z "${SUPERTEXT_SDK_FORCE:-}" && -z "${SUPERTEXT_SDK_SRC:-}" ]]; then
  echo "Supertext SDK $SDK_VERSION ($SDK_COMMIT) is already installed."
  exit 0
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

if [[ -n "${SUPERTEXT_SDK_SRC:-}" ]]; then
  src="$SUPERTEXT_SDK_SRC"
  echo "Building the Supertext SDK from $src"
elif command -v git >/dev/null; then
  src="$work/sdk"
  git init -q "$src"
  git -C "$src" fetch -q --depth 1 "$SDK_REPO" "$SDK_COMMIT"
  git -C "$src" checkout -q FETCH_HEAD
  echo "Building the Supertext SDK at $SDK_COMMIT"
else
  src="$work/sdk"
  mkdir -p "$src"
  curl -fsSL "$SDK_REPO/archive/$SDK_COMMIT.tar.gz" | tar -xz -C "$src" --strip-components 1
  echo "Building the Supertext SDK at $SDK_COMMIT"
fi

actual=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$src/sdk/java/pom.xml" | head -n 1)
if [[ "$actual" != "$SDK_VERSION" ]]; then
  echo "Expected Supertext SDK $SDK_VERSION, the source has $actual." >&2
  exit 1
fi

mvn -B -q -f "$src/sdk/java/pom.xml" install -DskipTests
if [[ -n "${SUPERTEXT_SDK_SRC:-}" ]]; then
  echo "local build" > "$marker"
else
  echo "$SDK_COMMIT" > "$marker"
fi
echo "Installed com.supertext:supertext-sdk:$SDK_VERSION"
