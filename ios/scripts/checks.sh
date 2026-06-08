#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v swiftlint >/dev/null 2>&1; then
  echo "SwiftLint is required. Install it with: brew install swiftlint"
  exit 1
fi

echo "Linting iOS..."
swiftlint lint --quiet

if [ -z "${SIM_NAME:-}" ]; then
  SIM_NAME=$(xcrun simctl list devices available -j | python3 -c "
import json, sys
data = json.load(sys.stdin)
for runtime, devices in sorted(data['devices'].items(), reverse=True):
    for device in devices:
        if device['isAvailable'] and device['name'].startswith('iPhone'):
            print(device['name'])
            sys.exit(0)
sys.exit(1)
" 2>/dev/null) || { echo "No available iPhone simulator found"; exit 1; }
fi

echo "Using simulator: $SIM_NAME"
SIM_NAME="$SIM_NAME" scripts/run.sh test
