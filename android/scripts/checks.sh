#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Linting Android..."
./gradlew ktlintCheck :app:lintDebug
