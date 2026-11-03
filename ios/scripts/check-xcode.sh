#!/usr/bin/env bash
set -euo pipefail

SDK_VERSION=$(xcrun --sdk iphoneos --show-sdk-version) || {
  echo "Error: Select Xcode 27.1 or newer with xcode-select or DEVELOPER_DIR." >&2
  exit 1
}

if [[ ! "$SDK_VERSION" =~ ^([0-9]+)\.([0-9]+)(\.[0-9]+)?$ ]]; then
  echo "Error: Cannot determine the iOS SDK version (found: $SDK_VERSION)." >&2
  exit 1
fi

if (( 10#${BASH_REMATCH[1]} < 27 || (10#${BASH_REMATCH[1]} == 27 && 10#${BASH_REMATCH[2]} < 1) )); then
  echo "Error: iOS SDK 27.1 or newer is required for full-screen iPhone Duo support (found: $SDK_VERSION). Select Xcode 27.1 or newer with xcode-select or DEVELOPER_DIR." >&2
  exit 1
fi

echo "Using iOS SDK $SDK_VERSION"
