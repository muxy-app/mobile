#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v swiftlint >/dev/null 2>&1; then
  echo "SwiftLint is required. Install it with: brew install swiftlint"
  exit 1
fi

echo "Linting iOS..."
swiftlint lint --quiet
