#!/usr/bin/env bash
set -euo pipefail

[ "$#" -ge 2 ] && OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
cd "$(dirname "$0")/.."

usage() {
  cat <<'USAGE'
Usage: scripts/capture-android.sh <serial> <output-dir> [shots]

Captures demo-mode screenshots from a booted emulator with the debug build installed.
Run android/scripts/run.sh build first. Shots are comma separated; all are captured by default.
Set PORTRAIT=1 to lock a landscape-first device such as a tablet in portrait.

  projects agent editor git diff files preview dev
  theme-editor-<theme slug>   for example theme-editor-tokyonight
USAGE
}

[ "$#" -ge 2 ] || { usage >&2; exit 1; }
[ -n "${ANDROID_HOME:-}" ] || { echo "Set ANDROID_HOME to your Android SDK." >&2; exit 1; }

SERIAL="$1"
ONLY="${3:-}"
APP_ID="com.muxy.app"
ACTIVITY="$APP_ID/.app.MainActivity"
APK="../android/app/build/outputs/apk/debug/app-debug.apk"
ADB=("$ANDROID_HOME/platform-tools/adb" -s "$SERIAL")
HERE="$(pwd)"
mkdir -p "$OUT"

adb() { "${ADB[@]}" "$@"; }

want() { [ -z "$ONLY" ] || [[ ",$ONLY," == *",$1,"* ]]; }

locate() {
  adb shell uiautomator dump /sdcard/muxy-ui.xml >/dev/null 2>&1 || return 1
  adb exec-out cat /sdcard/muxy-ui.xml | python3 -I "$HERE/scripts/ui_locate.py" "$1" "${2:-center}" "${OCCURRENCE:-first}"
}

tap() {
  local point
  for _ in $(seq 1 20); do
    if point=$(locate "$1" "${2:-center}"); then
      adb shell input tap $point
      sleep "${3:-1.5}"
      return 0
    fi
    sleep 0.5
  done
  echo "Could not find '$1' on screen" >&2
  return 1
}

tap_scrolling() {
  local point size width height
  size=$(adb shell wm size | awk '{ print $NF }' | tr -d '\r')
  width=${size%x*}
  height=${size#*x}
  for _ in $(seq 1 8); do
    if point=$(locate "$1" center); then
      adb shell input tap $point
      sleep 1.5
      return 0
    fi
    adb shell input swipe "$((width / 2))" "$((height * 3 / 4))" "$((width / 2))" "$((height / 3))" 400
    sleep 1
  done
  echo "Could not find '$1' after scrolling" >&2
  return 1
}

shot() {
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "captured $1"
}

status_bar() {
  adb shell settings put global sysui_demo_allowed 1
  local demo=(shell am broadcast -a com.android.systemui.demo)
  adb "${demo[@]}" -e command enter >/dev/null
  adb "${demo[@]}" -e command clock -e hhmm 0941 >/dev/null
  adb "${demo[@]}" -e command battery -e level 100 -e plugged false >/dev/null
  adb "${demo[@]}" -e command network -e wifi show -e level 4 -e fully true >/dev/null
  adb "${demo[@]}" -e command network -e mobile hide >/dev/null
  adb "${demo[@]}" -e command notifications -e visible false >/dev/null
}

orientation() {
  [ "${PORTRAIT:-}" = "1" ] || return 0
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
  sleep 3
}

launch() {
  adb shell am force-stop "$APP_ID"
  adb shell am start -n "$ACTIVITY" >/dev/null
  sleep 3
}

prepare() {
  adb install -r "$APK" >/dev/null
  adb shell pm clear "$APP_ID" >/dev/null
  adb shell cmd uimode night yes >/dev/null
  orientation
  status_bar
  launch
  tap "Skip"
  tap "Settings"
  tap "Demo Mode" start
  tap "Close"
}

select_theme() {
  launch
  tap "Settings"
  tap "Terminal theme"
  tap_scrolling "$1"
  tap "Back"
  tap "Close"
}

open_home_tab() { launch; tap "$1" center 3; }
open_muxy() { open_home_tab agent; }
open_tab() { tap "$1" left 3; }

prepare

if want projects; then launch; shot projects; fi
if want agent; then open_muxy; shot agent; fi
if want editor; then open_muxy; open_tab nvim; shot editor; fi
if want git; then open_muxy; tap "Git" center 2.5; shot git; fi
if want diff; then open_muxy; tap "Git" center 2.5; tap "Sources/Muxy/Tabs/TabStore.swift" center 2.5; shot diff; fi
if want files; then open_muxy; tap "Files" center 2.5; shot files; fi
if want preview; then
  open_muxy; tap "Files" center 2.5; tap "Sources"; OCCURRENCE=last tap "Muxy"; tap "Tabs"; tap "TabStore.swift" center 2.5; shot preview
fi
if want dev; then open_home_tab dev; shot dev; fi

for theme in "TokyoNight" "Gruvbox Dark" "Catppuccin Latte"; do
  slug=$(echo "$theme" | tr '[:upper:] ' '[:lower:]-')
  if want "theme-editor-$slug"; then select_theme "$theme"; open_muxy; open_tab nvim; shot "theme-editor-$slug"; fi
done

select_theme "Muxy"
