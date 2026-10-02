#!/usr/bin/env bash
set -euo pipefail

ANDROID_DIR="$(cd "$(dirname "$0")/.." && pwd)"
NDK_VERSION=$(sed -n 's/.*ndkVersion = "\([^"]*\)".*/\1/p' "$ANDROID_DIR/app/build.gradle.kts")
[[ "$NDK_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Error: invalid NDK version in app/build.gradle.kts" >&2; exit 1; }
[[ -n "${ANDROID_HOME:-}" ]] || { echo "Error: set ANDROID_HOME to your Android SDK" >&2; exit 1; }
TOOLS="$ANDROID_HOME/cmdline-tools/latest/bin"

echo "Installing Android NDK $NDK_VERSION..."
if [[ -x "$TOOLS/android" ]]; then
  exec "$TOOLS/android" --no-metrics sdk install "ndk;$NDK_VERSION"
fi
[[ -x "$TOOLS/sdkmanager" ]] || { echo "Error: install Android command-line tools in $ANDROID_HOME" >&2; exit 1; }
exec "$TOOLS/sdkmanager" "ndk;$NDK_VERSION"
