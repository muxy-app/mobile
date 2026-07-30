#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

APP_ID="com.muxy.app"
APP_NAME="Muxy.app"
APP_EXECUTABLE="Muxy"
TARGET="simulator"

if [ "${1:-}" = "phone" ] || [ "${1:-}" = "iphone" ] || [ "${1:-}" = "device" ]; then
  TARGET="iphone"
  shift
fi

if [ "$TARGET" = "iphone" ]; then
  DEVICE_JSON=$(mktemp -t muxy-ios-devices)
  PROCESS_JSON=""
  trap 'rm -f "$DEVICE_JSON" "$PROCESS_JSON"' EXIT

  xcrun devicectl list devices --json-output "$DEVICE_JSON" --quiet

  DEVICE_VALUES=$(python3 - "$DEVICE_JSON" "${IOS_DEVICE_ID:-}" <<'PY'
import json
import sys

path, requested = sys.argv[1:]
devices = json.load(open(path))["result"]["devices"]
devices = [
    device
    for device in devices
    if device.get("hardwareProperties", {}).get("deviceType") == "iPhone"
    and device.get("connectionProperties", {}).get("pairingState") == "paired"
    and device.get("connectionProperties", {}).get("tunnelState") != "unavailable"
]
if requested:
    devices = [
        device
        for device in devices
        if requested
        in {
            device.get("identifier", ""),
            device.get("hardwareProperties", {}).get("udid", ""),
            device.get("deviceProperties", {}).get("name", ""),
        }
    ]
if not devices:
    raise SystemExit(1)
device = devices[0]
print(device["identifier"])
print(device["hardwareProperties"]["udid"])
print(device["deviceProperties"]["name"])
PY
  ) || {
    echo "No paired iPhone found. Connect and unlock it, trust this Mac, and enable Developer Mode."
    exit 1
  }

  CORE_DEVICE_ID=$(printf '%s\n' "$DEVICE_VALUES" | sed -n '1p')
  DEVICE_UDID=$(printf '%s\n' "$DEVICE_VALUES" | sed -n '2p')
  DEVICE_NAME=$(printf '%s\n' "$DEVICE_VALUES" | sed -n '3p')

  if [ "${1:-}" = "stop" ] || [ "${1:-}" = "restart" ]; then
    PROCESS_JSON=$(mktemp -t muxy-ios-processes)
    xcrun devicectl device info processes --device "$CORE_DEVICE_ID" --json-output "$PROCESS_JSON" --quiet
    PID=$(python3 - "$PROCESS_JSON" "$APP_NAME" "$APP_EXECUTABLE" <<'PY'
import json
import sys

path, app_name, executable = sys.argv[1:]
processes = json.load(open(path))["result"]["runningProcesses"]
suffix = f"/{app_name}/{executable}"
for process in processes:
    if process.get("executable", "").removesuffix("/").endswith(suffix):
        print(process["processIdentifier"])
        raise SystemExit(0)
raise SystemExit(1)
PY
    ) || PID=""

    if [ -n "$PID" ]; then
      xcrun devicectl device process terminate --device "$CORE_DEVICE_ID" --pid "$PID" --quiet
      echo "Muxy stopped on $DEVICE_NAME"
    else
      echo "Muxy not running on $DEVICE_NAME"
    fi

    if [ "${1:-}" = "stop" ]; then
      exit 0
    fi

    shift
  fi

  echo "Running Muxy on $DEVICE_NAME..."
  npx expo run:ios --device "$DEVICE_UDID" "$@"
  exit 0
fi

if [ "${1:-}" = "stop" ]; then
  xcrun simctl terminate booted "$APP_ID" 2>/dev/null && echo "Muxy stopped" || echo "Muxy not running"
  exit 0
fi

if [ "${1:-}" = "restart" ]; then
  xcrun simctl terminate booted "$APP_ID" 2>/dev/null && echo "Muxy stopped" || echo "Muxy not running"
  shift
fi

SIM_NAME="${SIM_NAME:-iPhone 16e}"

exec npx expo run:ios --device "$SIM_NAME" "$@"
