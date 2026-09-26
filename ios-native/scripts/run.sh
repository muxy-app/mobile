#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT="Muxy.xcodeproj"
SCHEME="Muxy"
APP_ID="com.muxy.app"
APP_NAME="Muxy.app"
DERIVED="$PWD/.build/xcode"
ACTION="${1:-run}"

usage() {
  cat <<'USAGE'
Usage: scripts/run.sh [command]

  run                     Build and launch in Simulator (default)
  build                   Build for Simulator without booting or launching
  device [name or ID]      Build, install, and launch on a paired iPhone or iPad
  build-device [name or ID] Build for a paired device without installing or launching
  devices                 List simulators and paired devices
  test                    Run unit tests in Simulator
  stop                    Stop the app in the selected simulator
  restart                 Rebuild and relaunch in the selected simulator
  help                    Show this help

The Muxy SDK comes from scripts/sdk.sh. Install it first with: scripts/sdk.sh install

Environment:
  SIM_NAME          Select a simulator by name, creating it if needed
  SIM_ID            Select an existing simulator by UDID (overrides SIM_NAME)
  DEVICE_ID         Select a paired device when no name or ID argument is given
  DEVELOPMENT_TEAM  Override the project's signing team for a device build
USAGE
}

fail() {
  echo "Error: $*" >&2
  exit 1
}

case "$ACTION" in
  help|-h|--help) usage; exit 0 ;;
  device|build-device) [ "$#" -le 2 ] || fail "Too many arguments. Use scripts/run.sh help." ;;
  run|build|devices|test|stop|restart) [ "$#" -le 1 ] || fail "Too many arguments. Use scripts/run.sh help." ;;
  *) usage >&2; fail "Unknown command: $ACTION" ;;
esac

xcodebuild -version >/dev/null 2>&1 || fail "Select a full Xcode installation with: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer"
command -v python3 >/dev/null 2>&1 || fail "Python 3 is required. Install Xcode's command line tools or Python 3."

if [ "$ACTION" = "devices" ]; then
  xcrun simctl list devices available
  xcrun devicectl list devices
  exit 0
fi

require_sdk() {
  scripts/sdk.sh check || exit 1
}

BUILD_ARGS=(-project "$PROJECT" -scheme "$SCHEME" -configuration Debug -derivedDataPath "$DERIVED")

build_app() {
  local sdk="$1"
  local destination="$2"
  shift 2
  require_sdk
  echo "Building Muxy ($sdk)..."
  xcodebuild "${BUILD_ARGS[@]}" -sdk "$sdk" -destination "$destination" "$@" build -quiet || return $?
  echo "Built: $DERIVED/Build/Products/Debug-$sdk/$APP_NAME"
}

if [ "$ACTION" = "build" ]; then
  build_app iphonesimulator "generic/platform=iOS Simulator"
  exit 0
fi

if [ "$ACTION" = "device" ] || [ "$ACTION" = "build-device" ]; then
  DEVICE_ID=$(python3 scripts/destinations.py device "${2:-${DEVICE_ID:-}}")
  SIGNING_ARGS=(-allowProvisioningUpdates -allowProvisioningDeviceRegistration)
  if [ -n "${DEVELOPMENT_TEAM:-}" ]; then
    SIGNING_ARGS+=("DEVELOPMENT_TEAM=$DEVELOPMENT_TEAM")
  fi
  echo "Keep the device unlocked. Developer Mode and a signing account in Xcode are required."
  if ! build_app iphoneos "id=$DEVICE_ID" "${SIGNING_ARGS[@]}"; then
    fail "Device build failed. Check the Xcode errors above. For signing, open Muxy.xcodeproj and check Muxy > Signing & Capabilities. For an unavailable destination, check Xcode > Window > Devices and Simulators and use an Xcode version compatible with the device's iOS."
  fi
  if [ "$ACTION" = "build-device" ]; then
    exit 0
  fi
  if ! xcrun devicectl device install app --device "$DEVICE_ID" "$DERIVED/Build/Products/Debug-iphoneos/$APP_NAME"; then
    fail "Installation failed. Unlock the device, trust this Mac, and check signing and device support in Xcode."
  fi
  if ! xcrun devicectl device process launch --device "$DEVICE_ID" --terminate-existing "$APP_ID"; then
    fail "Launch failed. Unlock the device, enable Settings > Privacy & Security > Developer Mode, and trust the developer under Settings > General > VPN & Device Management if prompted."
  fi
  LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "<your Mac's LAN IP>")
  echo "Muxy running on device. Muxy 1: connect to your Mac at $LOCAL_IP:4865 on the same network. Muxy 2: scan the code from Muxy > Settings > Mobile or muxy mobile pair."
  exit 0
fi

if [ "$ACTION" = "stop" ]; then
  SIM_ID=$(python3 scripts/destinations.py simulator --existing)
  xcrun simctl terminate "$SIM_ID" "$APP_ID" 2>/dev/null && echo "Muxy stopped" || echo "Muxy not running"
  exit 0
fi

SIM_ID=$(python3 scripts/destinations.py simulator)

if [ "$ACTION" = "test" ]; then
  require_sdk
  echo "Testing Muxy (unit tests)..."
  xcodebuild "${BUILD_ARGS[@]}" -destination "id=$SIM_ID" -only-testing:MuxyTests test -quiet
  echo "Tests passed"
  exit 0
fi

build_app iphonesimulator "id=$SIM_ID"
xcrun simctl bootstatus "$SIM_ID" -b
open -a Simulator --args -CurrentDeviceUDID "$SIM_ID"
xcrun simctl install "$SIM_ID" "$DERIVED/Build/Products/Debug-iphonesimulator/$APP_NAME"
xcrun simctl launch --terminate-running-process "$SIM_ID" "$APP_ID"

echo "Muxy running in Simulator. Muxy 1: connect to your Mac at 127.0.0.1:4865. Muxy 2: paste the link from Muxy > Settings > Mobile > Copy Link or muxy mobile pair."
