#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
  cat <<'USAGE'
Usage: scripts/render.sh [target...]

Renders store images from the captures in raw/<target>/ into app-store/ and google-play/.
Targets: iphone iphone-duo ipad android-phone android-tablet feature-graphic app-store-header app-store-search. All are rendered by default.
USAGE
}

case "${1:-}" in
  help|-h|--help) usage; exit 0 ;;
esac

swift build -c release --package-path composer >/dev/null
COMPOSER="$(swift build -c release --package-path composer --show-bin-path)/StoreComposer"

output() {
  case "$1" in
    iphone) echo "app-store/iphone-6.9" ;;
    iphone-duo) echo "app-store/iphone-duo" ;;
    ipad) echo "app-store/ipad-13" ;;
    android-phone) echo "google-play/phone" ;;
    android-tablet) echo "google-play/tablet" ;;
  esac
}

targets=("$@")
[ "${#targets[@]}" -gt 0 ] || targets=(iphone iphone-duo ipad android-phone android-tablet feature-graphic app-store-header app-store-search)

for target in "${targets[@]}"; do
  if [ "$target" = "feature-graphic" ]; then
    mkdir -p google-play
    "$COMPOSER" feature-graphic raw/android-phone ../ios/Muxy/Assets.xcassets/AppIcon.appiconset/icon-1024.png google-play/feature-graphic.png
    continue
  fi
  case "$target" in
    app-store-header) "$COMPOSER" "$target" raw/iphone raw/ipad app-store/header.png; continue ;;
    app-store-search) "$COMPOSER" "$target" raw/iphone raw/ipad app-store/search-results.png; continue ;;
  esac
  [ -d "raw/$target" ] || { echo "No captures in raw/$target. Run the capture script first." >&2; exit 1; }
  "$COMPOSER" "$target" "raw/$target" "$(output "$target")"
done
