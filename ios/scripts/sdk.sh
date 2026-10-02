#!/usr/bin/env bash
set -euo pipefail

IOS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK_DIR="$IOS_DIR/MuxyMobileSDK/Build"
PIN_FILE="$IOS_DIR/MuxyMobileSDK/SHA256SUMS"
RELEASES_URL="https://github.com/muxy-app/muxy/releases/download"
PIN_PATTERN='^([0-9a-f]{64})  muxy-mobile-([0-9A-Za-z.-]+)-ios\.zip$'
VERSION_PATTERN='^[0-9A-Za-z.-]+$'
ACTION="${1:-}"
WORK=""

usage() {
  cat <<'USAGE'
Usage: scripts/sdk.sh <command>

  install                Install the Muxy SDK release pinned in MuxyMobileSDK/SHA256SUMS
  pin <version>          Pin and install the SDK of a Muxy release, for example 2.0.0-beta-1077
  build <muxy checkout>  Build and install the SDK from a Muxy 2 checkout, for unreleased SDK changes
  check                  Fail when the SDK is missing, and tell when it isn't the pinned release
  help                   Show this help
USAGE
}

fail() {
  echo "Error: $*" >&2
  exit 1
}

parse_pin() {
  local line
  [ -s "$1" ] || fail "$1 is missing or empty"
  line=$(cat "$1")
  [[ $line =~ $PIN_PATTERN ]] || fail "$1 must hold one line: <sha256>  muxy-mobile-<version>-ios.zip"
  PIN_SHA256="${BASH_REMATCH[1]}"
  PIN_VERSION="${BASH_REMATCH[2]}"
}

prepare_work_dir() {
  if [ -n "$WORK" ]; then
    return
  fi
  mkdir -p "$IOS_DIR/.build"
  WORK=$(mktemp -d "$IOS_DIR/.build/sdk.XXXXXX")
  trap 'rm -rf "$WORK"' EXIT
}

download() {
  curl --fail --silent --show-error --location --proto '=https' --retry 3 --output "$2" "$1"
}

activate() {
  local framework="$1" bindings="$2" revision="$3"
  [ -f "$framework/Info.plist" ] || fail "The SDK has no MuxyMobile.xcframework"
  [ -f "$bindings" ] || fail "The SDK has no muxy_mobile.swift"
  mkdir -p "$WORK/Build/swift"
  mv "$framework" "$WORK/Build/MuxyMobile.xcframework"
  mv "$bindings" "$WORK/Build/swift/muxy_mobile.swift"
  printf '%s\n' "$revision" > "$WORK/Build/REVISION"
  if [ -e "$SDK_DIR" ]; then
    mv "$SDK_DIR" "$WORK/previous"
  fi
  mv "$WORK/Build" "$SDK_DIR"
  echo "Muxy SDK $revision installed in $SDK_DIR"
}

install_release() {
  local pin="$1" zip actual
  parse_pin "$pin"
  prepare_work_dir
  zip="muxy-mobile-$PIN_VERSION-ios.zip"
  download "$RELEASES_URL/v$PIN_VERSION/$zip" "$WORK/$zip" || fail "Couldn't download $zip from Muxy $PIN_VERSION"
  actual=$(shasum -a 256 "$WORK/$zip" | cut -d ' ' -f 1)
  [ "$actual" = "$PIN_SHA256" ] || fail "$zip has SHA-256 $actual, but the pin expects $PIN_SHA256"
  unzip -q "$WORK/$zip" -d "$WORK/release"
  activate "$WORK/release/MuxyMobile.xcframework" "$WORK/release/muxy_mobile.swift" "$PIN_VERSION"
}

pin_release() {
  local version="$1"
  [[ $version =~ $VERSION_PATTERN ]] || fail "Invalid version: $version"
  prepare_work_dir
  download "$RELEASES_URL/v$version/SHA256SUMS" "$WORK/SHA256SUMS" || fail "Muxy $version has no release checksums"
  awk -v name="muxy-mobile-$version-ios.zip" '$2 == name' "$WORK/SHA256SUMS" > "$WORK/pin"
  [ -s "$WORK/pin" ] || fail "Muxy $version has no iOS SDK"
  install_release "$WORK/pin"
  cp "$WORK/pin" "$PIN_FILE"
  echo "Pinned Muxy SDK $version in $PIN_FILE"
}

build_source() {
  local checkout="$1" revision
  [ -f "$checkout/scripts/build-mobile-sdk.sh" ] || fail "$checkout has no scripts/build-mobile-sdk.sh. Use a Muxy 2 checkout."
  revision=$(git -C "$checkout" describe --always --dirty)
  prepare_work_dir
  bash "$checkout/scripts/build-mobile-sdk.sh" "$WORK/source"
  activate "$WORK/source/MuxyMobile.xcframework" "$WORK/source/swift/muxy_mobile.swift" "$revision"
}

check_sdk() {
  local installed
  if [ ! -f "$SDK_DIR/MuxyMobile.xcframework/Info.plist" ] || [ ! -f "$SDK_DIR/swift/muxy_mobile.swift" ]; then
    fail "The Muxy SDK is missing. Install it with: scripts/sdk.sh install"
  fi
  parse_pin "$PIN_FILE"
  installed=$(cat "$SDK_DIR/REVISION" 2>/dev/null || echo "unknown")
  if [ "$installed" = "$PIN_VERSION" ]; then
    return
  fi
  echo "Note: Muxy SDK $installed is installed, but the app pins $PIN_VERSION. Run scripts/sdk.sh install to use it." >&2
}

case "$ACTION" in
  help|-h|--help) usage; exit 0 ;;
  install|check) [ "$#" -eq 1 ] || fail "$ACTION takes no arguments. Use scripts/sdk.sh help." ;;
  pin) [ "$#" -eq 2 ] || fail "Usage: scripts/sdk.sh pin <version>" ;;
  build) [ "$#" -eq 2 ] || fail "Usage: scripts/sdk.sh build <muxy checkout>" ;;
  "") usage >&2; exit 1 ;;
  *) usage >&2; fail "Unknown command: $ACTION" ;;
esac

case "$ACTION" in
  install) install_release "$PIN_FILE" ;;
  pin) pin_release "$2" ;;
  build) build_source "$2" ;;
  check) check_sdk ;;
esac
