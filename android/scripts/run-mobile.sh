#!/usr/bin/env bash
# Build, install, and launch the Android Muxy client on an emulator.
# Usage:
#   scripts/run-mobile.sh                       # build + install + launch on default (phone)
#   scripts/run-mobile.sh tablet-7              # 7-inch tablet (Nexus 7)
#   scripts/run-mobile.sh tablet-10             # 10-inch tablet (Pixel Tablet)
#   scripts/run-mobile.sh stop                  # force-stop the app on the running emulator
#   scripts/run-mobile.sh restart               # stop, then build + install + launch
#   scripts/run-mobile.sh logs                  # tail logcat for the app
#
# Options (combine with any of the above):
#   --version X.Y.Z    Override versionName for this build. Major >=1 enables billing.
#                      Example: scripts/run-mobile.sh --version 1.0.0  (paywall enabled)
#                               scripts/run-mobile.sh --version 0.9.0  (free, billing off)
#   --hardware         Target a USB-connected device (skips emulator boot).
#                      Example: scripts/run-mobile.sh --hardware
#                               scripts/run-mobile.sh logs --hardware
#
# Tablet AVDs are created on first use (reusing the system image already
# installed for the default emulator).
set -euo pipefail

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME is not set. Point it at your Android SDK." >&2
  exit 1
fi
SDK="$ANDROID_HOME"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
PKG="com.muxy.app"
ACTIVITY="$PKG/.MainActivity"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

DEFAULT_AVD="${MUXY_AVD:-muxy_pixel}"

VERSION_OVERRIDE=""
USE_HARDWARE=0
ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION_OVERRIDE="$2"
      shift 2
      ;;
    --version=*)
      VERSION_OVERRIDE="${1#--version=}"
      shift
      ;;
    --hardware)
      USE_HARDWARE=1
      shift
      ;;
    *)
      ARGS+=("$1")
      shift
      ;;
  esac
done
set -- "${ARGS[@]}"

if [[ "$USE_HARDWARE" == "1" ]]; then
  ADB_TARGET=(-d)
else
  ADB_TARGET=(-e)
fi

if [[ -n "$VERSION_OVERRIDE" && ! "$VERSION_OVERRIDE" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Error: --version must be X.Y.Z (got '$VERSION_OVERRIDE')" >&2
  exit 1
fi

cmd="${1:-run}"
AVD_NAME="$DEFAULT_AVD"
NEW_AVD_LCD_W=""
NEW_AVD_LCD_H=""
NEW_AVD_DENSITY=""
NEW_AVD_DEVICE=""

case "$cmd" in
  tablet-7)
    AVD_NAME="muxy_tablet_7"
    NEW_AVD_LCD_W="1200"
    NEW_AVD_LCD_H="1920"
    NEW_AVD_DENSITY="240"
    NEW_AVD_DEVICE="Nexus 7"
    cmd="run"
    ;;
  tablet-10)
    AVD_NAME="muxy_tablet_10"
    NEW_AVD_LCD_W="1600"
    NEW_AVD_LCD_H="2560"
    NEW_AVD_DENSITY="320"
    NEW_AVD_DEVICE="pixel_tablet"
    cmd="run"
    ;;
esac

stop_app() {
  if "$ADB" "${ADB_TARGET[@]}" get-state >/dev/null 2>&1; then
    "$ADB" "${ADB_TARGET[@]}" shell am force-stop "$PKG" 2>/dev/null && echo "Muxy stopped" || echo "Muxy not running"
  else
    echo "No device attached"
  fi
}

case "$cmd" in
  stop)
    stop_app
    exit 0
    ;;
  restart)
    stop_app
    ;;
  logs)
    exec "$ADB" "${ADB_TARGET[@]}" logcat -v color -s "MuxyClient:* AndroidRuntime:E System.err:W $PKG:*"
    ;;
  run|"")
    ;;
  *)
    echo "Unknown command: $cmd"
    echo "Usage: $0 [run|tablet-7|tablet-10|stop|restart|logs]"
    exit 1
    ;;
esac

ensure_avd() {
  local name="$1" lcd_w="$2" lcd_h="$3" lcd_density="$4" device_name="$5"
  ANDROID_HOME="$SDK" "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$name" && return 0

  local avd_root="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
  local src_avd="$avd_root/$DEFAULT_AVD.avd"
  local src_ini="$avd_root/$DEFAULT_AVD.ini"
  if [[ ! -d "$src_avd" || ! -f "$src_ini" ]]; then
    echo "Default AVD '$DEFAULT_AVD' not found at $src_avd; cannot clone." >&2
    echo "Create '$DEFAULT_AVD' first (Android Studio Device Manager), then retry." >&2
    return 1
  fi

  echo "Cloning '$DEFAULT_AVD' -> '$name' (${lcd_w}x${lcd_h} @ ${lcd_density}dpi, device: $device_name)..."
  local dst_avd="$avd_root/$name.avd"
  local dst_ini="$avd_root/$name.ini"
  rm -rf "$dst_avd" "$dst_ini"
  cp -R "$src_avd" "$dst_avd"
  sed "s|$DEFAULT_AVD|$name|g" "$src_ini" > "$dst_ini"

  rm -f "$dst_avd"/userdata-qemu.img* "$dst_avd"/snapshots.img 2>/dev/null
  rm -rf "$dst_avd/snapshots" 2>/dev/null

  local cfg="$dst_avd/config.ini"
  set_avd_prop() {
    local key="$1" value="$2"
    if grep -qE "^${key}[[:space:]]*=" "$cfg"; then
      sed -i.bak -E "s|^${key}[[:space:]]*=.*|${key}=${value}|" "$cfg"
    else
      echo "${key}=${value}" >> "$cfg"
    fi
  }
  set_avd_prop "AvdId" "$name"
  set_avd_prop "avd.ini.displayname" "$name"
  set_avd_prop "hw.lcd.width" "$lcd_w"
  set_avd_prop "hw.lcd.height" "$lcd_h"
  set_avd_prop "hw.lcd.density" "$lcd_density"
  set_avd_prop "hw.device.name" "$device_name"
  set_avd_prop "hw.initialOrientation" "portrait"
  set_avd_prop "fastboot.forceColdBoot" "yes"
  set_avd_prop "fastboot.forceFastBoot" "no"
  sed -i.bak -E "/^hw\.device\.hash2[[:space:]]*=/d" "$cfg"
  rm -f "$cfg.bak"
  echo "  created."
}

if [[ -n "$NEW_AVD_LCD_W" ]]; then
  ensure_avd "$AVD_NAME" "$NEW_AVD_LCD_W" "$NEW_AVD_LCD_H" "$NEW_AVD_DENSITY" "$NEW_AVD_DEVICE" || exit 1
fi

EMU_LOG="/tmp/muxy-emulator-$AVD_NAME.log"

is_booted() {
  local serial
  serial="$("$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1; exit}')"
  [ -n "$serial" ] || return 1
  [ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]
}

booted_avd_name() {
  local serial
  serial="$("$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {print $1; exit}')"
  [ -n "$serial" ] || return 1
  "$ADB" -s "$serial" emu avd name 2>/dev/null | head -n 1 | tr -d '\r'
}

wait_for_boot() {
  local timeout="${1:-180}"
  local waited=0
  echo -n "  waiting for boot"
  while [ "$waited" -lt "$timeout" ]; do
    if is_booted; then
      echo " ready"
      return 0
    fi
    echo -n "."
    sleep 2
    waited=$((waited + 2))
  done
  echo " timeout"
  return 1
}

kill_emulators() {
  echo "Killing existing emulators and resetting adb..."
  "$ADB" devices 2>/dev/null | awk 'NR>1 && $1 ~ /^emulator-/ {print $1}' | while read -r serial; do
    [ -n "$serial" ] && "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
  done
  pkill -f "qemu-system" 2>/dev/null || true
  pkill -f "$SDK/emulator/" 2>/dev/null || true
  "$ADB" kill-server >/dev/null 2>&1 || true
  sleep 1
  "$ADB" start-server >/dev/null 2>&1 || true
}

boot_emulator() {
  if ! ANDROID_HOME="$SDK" "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD_NAME"; then
    echo "AVD '$AVD_NAME' not found. Available AVDs:"
    ANDROID_HOME="$SDK" "$EMULATOR" -list-avds 2>/dev/null | sed 's/^/  /'
    return 1
  fi

  echo "Starting emulator '$AVD_NAME'..."
  ANDROID_HOME="$SDK" \
    "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-save -no-snapshot-load >"$EMU_LOG" 2>&1 &
  local emu_pid=$!
  echo "  emulator pid=$emu_pid  log=$EMU_LOG"

  sleep 2
  if ! kill -0 "$emu_pid" 2>/dev/null; then
    echo "Emulator process exited immediately. Last 20 log lines:"
    tail -n 20 "$EMU_LOG" 2>/dev/null || true
    return 1
  fi

  if ! wait_for_boot 180; then
    echo "Emulator failed to boot within timeout. Last 20 log lines:"
    tail -n 20 "$EMU_LOG" 2>/dev/null || true
    return 1
  fi
}

"$ADB" start-server >/dev/null 2>&1 || true

if [[ "$USE_HARDWARE" == "1" ]]; then
  HW_SERIAL="$("$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}')"
  if [[ -z "$HW_SERIAL" ]]; then
    echo "No USB device found. Run 'adb devices' and check that:"
    echo "  - USB debugging is enabled in Developer Options"
    echo "  - The phone is unlocked and the RSA prompt was accepted"
    exit 1
  fi
  echo "Targeting USB device: $HW_SERIAL"
fi

CURRENT_AVD=""
if [[ "$USE_HARDWARE" == "0" ]] && is_booted; then
  CURRENT_AVD="$(booted_avd_name 2>/dev/null || true)"
fi

if [[ "$USE_HARDWARE" == "1" ]]; then
  echo "Skipping emulator boot (--hardware)."
elif [[ -n "$CURRENT_AVD" && "$CURRENT_AVD" == "$AVD_NAME" ]]; then
  echo "Emulator '$AVD_NAME' already booted; reusing it."
elif [[ -n "$CURRENT_AVD" && "$CURRENT_AVD" != "$AVD_NAME" ]]; then
  echo "A different AVD is booted ('$CURRENT_AVD'); switching to '$AVD_NAME'."
  kill_emulators
  if ! boot_emulator; then
    kill_emulators
    boot_emulator || { echo "Giving up on emulator boot."; exit 1; }
  fi
else
  kill_emulators
  if ! boot_emulator; then
    kill_emulators
    boot_emulator || { echo "Giving up on emulator boot."; exit 1; }
  fi
fi

GRADLE_VERSION_ARGS=()
if [[ -n "$VERSION_OVERRIDE" ]]; then
  echo "Using versionName=$VERSION_OVERRIDE for this build"
  GRADLE_VERSION_ARGS+=("-PversionName=$VERSION_OVERRIDE")
fi

echo "Building debug APK..."
GRADLE_USER_HOME="$HOME/.gradle" \
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}" \
  "$ROOT_DIR/gradlew" -q -p "$ROOT_DIR" \
    -Pandroid.builder.sdkDownload=false \
    ${GRADLE_VERSION_ARGS[@]+"${GRADLE_VERSION_ARGS[@]}"} \
    assembleDebug

if [ ! -f "$APK" ]; then
  echo "APK not found at $APK"
  exit 1
fi

echo "Installing $APK"
INSTALL_FLAGS=(-r)
[[ "$USE_HARDWARE" == "1" ]] && INSTALL_FLAGS+=(-d)
"$ADB" "${ADB_TARGET[@]}" install "${INSTALL_FLAGS[@]}" "$APK" >/dev/null
echo "Launching $ACTIVITY"
"$ADB" "${ADB_TARGET[@]}" shell am start -n "$ACTIVITY" >/dev/null

LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "unknown")
echo
if [[ "$USE_HARDWARE" == "1" ]]; then
  echo "Muxy running on USB device '$HW_SERIAL'"
  echo "Connect using: $LOCAL_IP:4865 (Mac host on the same Wi-Fi)"
else
  echo "Muxy running on emulator '$AVD_NAME'"
  echo "Connect using: 10.0.2.2:4865 (emulator <-> Mac host) or $LOCAL_IP:4865 (real device)"
fi
if [[ "$USE_HARDWARE" == "1" ]]; then
  echo "Tail logs:   scripts/run-mobile.sh logs --hardware"
else
  echo "Tail logs:   scripts/run-mobile.sh logs"
fi
