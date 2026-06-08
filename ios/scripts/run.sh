#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT="Muxy.xcodeproj"
SCHEME="Muxy"
APP_ID="com.muxy.app"
APP_NAME="Muxy.app"
DERIVED=".build/xcode"

if [ "${1:-}" = "stop" ]; then
  xcrun simctl terminate booted "$APP_ID" 2>/dev/null && echo "Muxy stopped" || echo "Muxy not running"
  exit 0
fi

if [ "${1:-}" = "restart" ]; then
  xcrun simctl terminate booted "$APP_ID" 2>/dev/null && echo "Muxy stopped" || echo "Muxy not running"
  shift
fi

SIM_NAME="${SIM_NAME:-iPhone 16e}"
SIM_ID=$(xcrun simctl list devices available -j | python3 -c "
import json, sys
data = json.load(sys.stdin)
for runtime, devices in data['devices'].items():
    for d in devices:
        if d['name'] == '$SIM_NAME' and d['isAvailable']:
            print(d['udid']); sys.exit(0)
print(''); sys.exit(1)
" 2>/dev/null) || { echo "Simulator '$SIM_NAME' not found"; exit 1; }

if [ "${1:-}" = "test" ]; then
  echo "Testing Muxy (unit tests)..."
  xcodebuild test \
    -project "$PROJECT" \
    -scheme "$SCHEME" \
    -destination "id=$SIM_ID" \
    -only-testing:MuxyTests \
    -quiet
  echo "Tests passed"
  exit 0
fi

xcrun simctl boot "$SIM_ID" 2>/dev/null || true
open -a Simulator

echo "Building Muxy..."
xcodebuild \
  -project "$PROJECT" \
  -scheme "$SCHEME" \
  -sdk iphonesimulator \
  -destination "id=$SIM_ID" \
  -derivedDataPath "$DERIVED" \
  build -quiet

xcrun simctl install "$SIM_ID" "$DERIVED/Build/Products/Debug-iphonesimulator/$APP_NAME"
xcrun simctl launch "$SIM_ID" "$APP_ID"

LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "unknown")
echo ""
echo "Muxy running on $SIM_NAME"
echo "Connect using: 127.0.0.1:4865 (simulator) or $LOCAL_IP:4865 (real device)"
