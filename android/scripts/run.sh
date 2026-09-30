#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

APP_ID="com.muxy.app"
ACTIVITY="$APP_ID/.app.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"
BOOT_TIMEOUT=300
FIRST_EMULATOR_PORT=5554
LAST_EMULATOR_PORT=5584
ACTION="${1:-run}"

usage() {
  cat <<'USAGE'
Usage: scripts/run.sh [command]

  run               Build and launch in the emulator, booting one if none is running (default)
  build             Build the debug APK without a device
  device [serial]   Build, install, and launch on a connected phone
  devices           List emulators (AVDs) and connected devices
  test              Run the JVM unit tests
  test-device       Run the instrumented tests in the emulator
  stop              Stop the app in the emulator
  restart           Stop, rebuild, and relaunch the app in the emulator
  logs              Follow the app's Muxy logs and crashes
  help              Show this help

The Muxy SDK comes from scripts/sdk.sh. Install it first with: scripts/sdk.sh install

Environment:
  AVD_NAME       Use or boot this emulator instead of the running one or the first AVD
  DEVICE_SERIAL  Use this device or emulator for run, test-device, stop, restart, and logs,
                 and pick this phone for device when no serial is given
USAGE
}

fail() {
  echo "Error: $*" >&2
  exit 1
}

case "$ACTION" in
  help|-h|--help) usage; exit 0 ;;
  device) [ "$#" -le 2 ] || fail "Too many arguments. Use scripts/run.sh help." ;;
  run|build|devices|test|test-device|stop|restart|logs) [ "$#" -le 1 ] || fail "Too many arguments. Use scripts/run.sh help." ;;
  *) usage >&2; fail "Unknown command: $ACTION" ;;
esac

require_jdk() {
  local java version
  java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
  command -v "$java" >/dev/null 2>&1 || fail "JDK 21 is required. Install Temurin 21 and set JAVA_HOME."
  version=$("$java" -version 2>&1 | awk -F '"' '/version/ { print $2; exit }')
  [ "${version%%.*}" -ge 21 ] 2>/dev/null || fail "JDK 21 or newer is required, but java reports ${version:-an unknown version}."
}

require_android_sdk() {
  [ -n "${ANDROID_HOME:-}" ] || fail "Set ANDROID_HOME to your Android SDK, for example: export ANDROID_HOME=\"\$HOME/Library/Android/sdk\""
  [ -d "$ANDROID_HOME" ] || fail "ANDROID_HOME points to $ANDROID_HOME, which doesn't exist."
}

require_jdk
require_android_sdk

ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"

require_muxy_sdk() {
  scripts/sdk.sh check || exit 1
}

require_adb() {
  [ -x "$ADB" ] || fail "adb is missing. Install the platform-tools package into $ANDROID_HOME."
}

require_emulator() {
  [ -x "$EMULATOR" ] || fail "The emulator is missing. Install the emulator package into $ANDROID_HOME."
}

build_app() {
  require_muxy_sdk
  echo "Building Muxy (debug)..."
  ./gradlew :app:assembleDebug
  echo "Built: $PWD/$APK"
}

connected() {
  "$ADB" devices | awk -v kind="$1" 'NR > 1 && $2 == "device" && (($1 ~ /^emulator-/) == (kind == "emulator")) { print $1 }'
}

avd_name() {
  "$ADB" -s "$1" emu avd name 2>/dev/null | head -n 1 | tr -d '\r' || true
}

running_target() {
  local serial
  if [ -n "${DEVICE_SERIAL:-}" ]; then
    echo "$DEVICE_SERIAL"
    return
  fi
  for serial in $(connected emulator); do
    if [ -z "${AVD_NAME:-}" ] || [ "$(avd_name "$serial")" = "$AVD_NAME" ]; then
      echo "$serial"
      return
    fi
  done
}

free_emulator_port() {
  local port="$FIRST_EMULATOR_PORT"
  while "$ADB" devices | grep -q "^emulator-$port[[:space:]]"; do
    port=$((port + 2))
    [ "$port" -le "$LAST_EMULATOR_PORT" ] || fail "No free emulator port between $FIRST_EMULATOR_PORT and $LAST_EMULATOR_PORT."
  done
  echo "$port"
}

choose_avd() {
  local avds
  avds=$("$EMULATOR" -list-avds 2>/dev/null || true)
  [ -n "$avds" ] || fail "No emulator found. Create one in Android Studio's Device Manager, or with avdmanager."
  if [ -z "${AVD_NAME:-}" ]; then
    echo "$avds" | head -n 1
    return
  fi
  echo "$avds" | grep -qx "$AVD_NAME" || fail "No emulator named $AVD_NAME. Available: $(echo "$avds" | tr '\n' ' ')"
  echo "$AVD_NAME"
}

booted() {
  [ "$("$ADB" -s "$1" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" = "1" ] \
    && "$ADB" -s "$1" shell pm path android >/dev/null 2>&1
}

boot_emulator() {
  local avd port serial log pid waited=0
  require_emulator
  avd=$(choose_avd) || exit 1
  port=$(free_emulator_port) || exit 1
  serial="emulator-$port"
  mkdir -p .build
  log="$PWD/.build/emulator-$avd.log"
  echo "Booting $avd..." >&2
  nohup "$EMULATOR" -avd "$avd" -port "$port" >"$log" 2>&1 &
  pid=$!
  until booted "$serial"; do
    kill -0 "$pid" 2>/dev/null || fail "$avd stopped while booting. See $log"
    [ "$waited" -lt "$BOOT_TIMEOUT" ] || fail "$avd didn't boot within $BOOT_TIMEOUT seconds. See $log"
    sleep 2
    waited=$((waited + 2))
  done
  echo "$serial"
}

emulator_target() {
  local serial
  serial=$(running_target)
  if [ -n "$serial" ]; then
    echo "$serial"
    return
  fi
  boot_emulator
}

existing_target() {
  local serial
  serial=$(running_target)
  [ -n "$serial" ] || fail "No emulator is running. Start one with scripts/run.sh, or set DEVICE_SERIAL."
  echo "$serial"
}

phone_target() {
  local requested="$1" phones count
  if [ -n "$requested" ]; then
    echo "$requested"
    return
  fi
  phones=$(connected phone)
  count=$(printf '%s' "$phones" | grep -c . || true)
  [ "$count" -gt 0 ] || fail "No phone is connected. Turn on USB or wireless debugging and check scripts/run.sh devices."
  [ "$count" -eq 1 ] || fail "Several phones are connected: $(echo "$phones" | tr '\n' ' '). Choose one with scripts/run.sh device <serial>."
  echo "$phones"
}

install_app() {
  local serial="$1" output
  if output=$("$ADB" -s "$serial" install -r -d "$APK" 2>&1); then
    return
  fi
  echo "$output" >&2
  case "$output" in
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*|*INSTALL_FAILED_VERSION_DOWNGRADE*)
      fail "Another Muxy build is installed on $serial. Uninstall it first, which deletes its data: $ADB -s $serial uninstall $APP_ID" ;;
  esac
  fail "Installation failed on $serial."
}

launch_app() {
  local output
  output=$("$ADB" -s "$1" shell am start -S -W -n "$ACTIVITY" 2>&1) || fail "Launch failed on $1: $output"
  case "$output" in
    *Error*) fail "Launch failed on $1: $output" ;;
  esac
}

connect_hint() {
  local local_ip
  if [[ $1 == emulator-* ]]; then
    echo "Muxy 1: connect to your Mac at 10.0.2.2:4865. Muxy 2: paste the link from Muxy > Settings > Mobile > Copy Link or muxy mobile pair."
    return
  fi
  local_ip=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "<your Mac's LAN IP>")
  echo "Muxy 1: connect to your Mac at $local_ip:4865 on the same network. Muxy 2: scan the code from Muxy > Settings > Mobile or muxy mobile pair."
}

run_on() {
  local serial="$1"
  build_app
  install_app "$serial"
  launch_app "$serial"
  echo "Muxy running on $serial. $(connect_hint "$serial")"
}

stop_on() {
  "$ADB" -s "$1" shell am force-stop "$APP_ID"
  echo "Muxy stopped on $1"
}

case "$ACTION" in
  build)
    build_app
    ;;
  test)
    require_muxy_sdk
    echo "Testing Muxy (unit tests)..."
    ./gradlew :app:testDebugUnitTest :ktlint-rules:test
    echo "Tests passed"
    ;;
  devices)
    require_adb
    require_emulator
    echo "Emulators:"
    "$EMULATOR" -list-avds 2>/dev/null || true
    "$ADB" devices -l
    ;;
  run)
    require_muxy_sdk
    require_adb
    serial=$(emulator_target)
    run_on "$serial"
    ;;
  device)
    require_muxy_sdk
    require_adb
    serial=$(phone_target "${2:-${DEVICE_SERIAL:-}}")
    run_on "$serial"
    ;;
  test-device)
    require_muxy_sdk
    require_adb
    serial=$(emulator_target)
    echo "Testing Muxy (instrumented tests on $serial)..."
    ANDROID_SERIAL="$serial" ./gradlew connectedDebugAndroidTest
    echo "Tests passed"
    ;;
  stop)
    require_adb
    serial=$(existing_target)
    stop_on "$serial"
    ;;
  restart)
    require_muxy_sdk
    require_adb
    serial=$(emulator_target)
    stop_on "$serial"
    run_on "$serial"
    ;;
  logs)
    require_adb
    serial=$(existing_target)
    echo "Following Muxy logs on $serial. Press Ctrl-C to stop."
    "$ADB" -s "$serial" logcat -v tag | grep --line-buffered -E '^[VDIWEF]/(Muxy/|AndroidRuntime)'
    ;;
esac
