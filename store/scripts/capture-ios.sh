#!/usr/bin/env bash
set -euo pipefail

[ "$#" -ge 2 ] && OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
cd "$(dirname "$0")/.."

usage() {
  cat <<'USAGE'
Usage: scripts/capture-ios.sh <simulator-name> <output-dir> [shots]

Captures demo-mode screenshots in an iOS simulator with AXe (brew install cameroncooke/axe/axe).
Run ios/scripts/run.sh build first. Shots are comma separated; all are captured by default.

  projects agent editor git diff files preview dev
  theme-editor-<theme slug>   for example theme-editor-tokyonight
USAGE
}

[ "$#" -ge 2 ] || { usage >&2; exit 1; }
command -v axe >/dev/null 2>&1 || { echo "AXe is required: brew install cameroncooke/axe/axe" >&2; exit 1; }

NAME="$1"
ONLY="${3:-}"
APP_ID="com.muxy.app"
APP="../ios/.build/xcode/Build/Products/Debug-iphonesimulator/Muxy.app"
SIM=$(xcrun simctl list devices available -j | python3 -I -c "
import json, sys
name = sys.argv[1]
devices = [d for runtime in json.load(sys.stdin)['devices'].values() for d in runtime if d['name'] == name]
print(devices[-1]['udid'] if devices else '')
" "$NAME")
[ -n "$SIM" ] || { echo "No simulator named '$NAME'." >&2; exit 1; }
mkdir -p "$OUT"

want() { [ -z "$ONLY" ] || [[ ",$ONLY," == *",$1,"* ]]; }

tap() { axe tap --label "$1" --udid "$SIM" --wait-timeout 8 >/dev/null; sleep "${2:-1.5}"; }

shot() {
  axe screenshot --udid "$SIM" --output "$OUT/$1.png" >/dev/null
  echo "captured $1"
}

screen_points() {
  local points
  for _ in $(seq 1 20); do
    points=$(axe describe-ui --udid "$SIM" 2>/dev/null | python3 -I -c "
import json, sys
root = json.load(sys.stdin)
frame = (root[0] if isinstance(root, list) else root)['frame']
print(int(frame['width']), int(frame['height']))
" 2>/dev/null) && { echo "$points"; return 0; }
    sleep 1
  done
  echo "Could not read the simulator screen size." >&2
  return 1
}

tap_scrolling() {
  local width height
  for _ in $(seq 1 5); do
    if axe tap --label "$1" --udid "$SIM" --wait-timeout 2 >/dev/null 2>&1; then
      sleep "${2:-1.5}"
      return 0
    fi
    read -r width height < <(screen_points)
    axe swipe --start-x "$((width / 2))" --start-y "$((height * 2 / 3))" --end-x "$((width / 2))" --end-y "$((height / 3))" --duration 0.6 --udid "$SIM" >/dev/null 2>&1
    sleep 1
  done
  echo "Could not find '$1' after scrolling" >&2
  return 1
}

prepare() {
  local locale
  xcrun simctl boot "$SIM" 2>/dev/null || true
  xcrun simctl bootstatus "$SIM" -b >/dev/null
  locale=$(xcrun simctl spawn "$SIM" defaults read -g AppleLocale 2>/dev/null || true)
  if [ "$locale" != "en_US" ]; then
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
  xcrun simctl install "$SIM" "$APP"
}

launch() {
  xcrun simctl terminate "$SIM" "$APP_ID" >/dev/null 2>&1 || true
  xcrun simctl spawn "$SIM" defaults write "$APP_ID" muxy.hasCompletedOnboarding -bool YES
  xcrun simctl spawn "$SIM" defaults write "$APP_ID" muxy.settings.demoMode -bool YES
  xcrun simctl spawn "$SIM" defaults write "$APP_ID" muxy.settings.theme -string "${1:-Muxy}"
  xcrun simctl launch "$SIM" "$APP_ID" >/dev/null
  sleep 2.5
}

open_tab() { tap "$1, Demo Desktop" 3; }
open_muxy() { open_tab "agent, Muxy"; }

prepare

if want projects; then launch; shot projects; fi
if want agent; then launch; open_muxy; shot agent; fi
if want editor; then launch; open_muxy; tap "nvim" 2.5; shot editor; fi
if want git; then launch; open_muxy; tap "Git" 2.5; shot git; fi
if want diff; then launch; open_muxy; tap "Git" 2.5; tap_scrolling "TabStore.swift, Sources/Muxy/Tabs/TabStore.swift, M" 2.5; shot diff; fi
if want files; then launch; open_muxy; tap "Files" 2.5; shot files; fi
if want preview; then
  launch; open_muxy; tap "Files" 2.5; tap "Sources, Folder"; tap "Muxy, Folder"; tap "Tabs, Folder"; tap "TabStore.swift, Swift source" 2.5
  shot preview
fi
if want dev; then launch; open_tab "dev, Web App"; shot dev; fi

for theme in "TokyoNight" "Gruvbox Dark" "Catppuccin Latte"; do
  slug=$(echo "$theme" | tr '[:upper:] ' '[:lower:]-')
  if want "theme-editor-$slug"; then launch "$theme"; open_muxy; tap "nvim" 2.5; shot "theme-editor-$slug"; fi
done

launch
