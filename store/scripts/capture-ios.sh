#!/usr/bin/env bash
set -euo pipefail

[ "$#" -ge 2 ] && OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
cd "$(dirname "$0")/.."

SHOTS=(projects agent editor git diff files preview dev theme-editor-tokyonight theme-editor-gruvbox-dark theme-editor-catppuccin-latte)

usage() {
  cat <<USAGE
Usage: scripts/capture-ios.sh <simulator name or UDID> <output-dir> [shots]

Captures demo-mode screenshots in an iOS simulator with the MuxyStoreScreenshots UI tests.
Shots are comma separated; all are captured by default:

  ${SHOTS[*]}

For a foldable such as the iPhone Duo, unfold it in DeviceHub first; the largest display is captured.
USAGE
}

[ "$#" -ge 2 ] || { usage >&2; exit 1; }

NAME="$1"
ONLY="${3:-}"
IOS="$(cd ../ios && pwd)"
SIM=$(xcrun simctl list devices available -j | python3 -I -c "
import json, sys
target = sys.argv[1]
devices = [d for runtime in json.load(sys.stdin)['devices'].values() for d in runtime if target in (d['name'], d['udid'])]
print(devices[-1]['udid'] if devices else '')
" "$NAME")
[ -n "$SIM" ] || { echo "No simulator named '$NAME'." >&2; exit 1; }
echo "Capturing on simulator $SIM"

REQUESTED=("${SHOTS[@]}")
[ -z "$ONLY" ] || IFS=',' read -r -a REQUESTED <<< "$ONLY"
TESTS=()
for shot in "${REQUESTED[@]}"; do
  [[ " ${SHOTS[*]} " == *" $shot "* ]] || { echo "Unknown shot '$shot'." >&2; exit 1; }
  TESTS+=("-only-testing:MuxyStoreScreenshots/StoreScreenshotTests/test_${shot//-/_}")
done

prepare() {
  local locale
  xcrun simctl boot "$SIM" 2>/dev/null || true
  xcrun simctl bootstatus "$SIM" -b >/dev/null
  locale=$(xcrun simctl spawn "$SIM" defaults read -g AppleLocale 2>/dev/null || true)
  if [[ "$locale" != en_US* ]]; then
    xcrun simctl spawn "$SIM" defaults write -g AppleLocale en_US
    xcrun simctl spawn "$SIM" defaults write -g AppleLanguages -array en
    xcrun simctl spawn "$SIM" defaults write -g AppleICUForce24HourTime -bool NO
    xcrun simctl shutdown "$SIM"
    xcrun simctl boot "$SIM"
    xcrun simctl bootstatus "$SIM" -b >/dev/null
  fi
  xcrun simctl ui "$SIM" appearance dark
  xcrun simctl status_bar "$SIM" override --time "9:41" --dataNetwork wifi --wifiMode active --wifiBars 3 \
    --cellularMode active --cellularBars 4 --batteryState discharging --batteryLevel 100 --operatorName ""
}

prepare
mkdir -p "$OUT"

TEST_RUNNER_STORE_SCREENSHOTS_DIR="$OUT" xcodebuild \
  -project "$IOS/Muxy.xcodeproj" \
  -scheme Muxy \
  -configuration Debug \
  -derivedDataPath "$IOS/.build/xcode" \
  -destination "id=$SIM" \
  -parallel-testing-enabled NO \
  "${TESTS[@]}" \
  test -quiet

ls "$OUT"
