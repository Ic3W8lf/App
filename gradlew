#!/usr/bin/env sh
# Lightweight Gradle bootstrapper for environments where the Gradle Wrapper JAR
# is not available. Uses the version declared in gradle-wrapper.properties.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
DIST_URL=$(sed -n 's/^distributionUrl=//p' "$PROPS" | sed 's/\\:/:/g')
DIST_NAME=$(basename "$DIST_URL")
VERSION=$(printf '%s' "$DIST_NAME" | sed -E 's/^gradle-([0-9.]+)-.*$/\1/')
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/manual/gradle-$VERSION"
if [ ! -x "$CACHE/bin/gradle" ]; then
  mkdir -p "$(dirname "$CACHE")"
  TMP="${TMPDIR:-/tmp}/gradle-$VERSION-$$.zip"
  echo "Downloading $DIST_URL"
  if command -v curl >/dev/null 2>&1; then curl -fL "$DIST_URL" -o "$TMP"
  elif command -v wget >/dev/null 2>&1; then wget -O "$TMP" "$DIST_URL"
  else echo "Error: curl or wget is required to download Gradle." >&2; exit 1
  fi
  mkdir -p "$(dirname "$CACHE")"
  UNPACK="${CACHE}.unpack"
  rm -rf "$UNPACK"; mkdir -p "$UNPACK"
  if command -v unzip >/dev/null 2>&1; then unzip -q "$TMP" -d "$UNPACK"
  else echo "Error: unzip is required to unpack Gradle." >&2; exit 1
  fi
  rm -f "$TMP"
  mv "$UNPACK/gradle-$VERSION" "$CACHE"
  rmdir "$UNPACK" 2>/dev/null || true
fi
exec "$CACHE/bin/gradle" "$@"
