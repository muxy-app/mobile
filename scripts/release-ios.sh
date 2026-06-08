#!/usr/bin/env bash
# Local iOS release script. Mirrors .github/workflows/ios-release.yml.
#
# Builds the native app in ios/ (Muxy.xcodeproj, SwiftPM).
#
# Usage:
#   scripts/release-ios.sh [--upload] <version> [build_number]
#   scripts/release-ios.sh --upload-only [path/to/file.ipa]
#
# Flags:
#   --upload        Skip the confirmation prompt and upload to App Store Connect.
#   --upload-only   Skip build; upload an existing IPA. Defaults to the most
#                   recent file in ios/build/export/.
#
# Examples:
#   scripts/release-ios.sh 0.3.0 42
#   scripts/release-ios.sh --upload 0.3.0 42
#   scripts/release-ios.sh --upload-only
#   scripts/release-ios.sh --upload-only path/to/Muxy.ipa
#
# Reads secrets from .env at repo root. See .env.example.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

AUTO_UPLOAD=false
UPLOAD_ONLY=false
POSITIONAL=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --upload) AUTO_UPLOAD=true; shift ;;
    --upload-only) UPLOAD_ONLY=true; AUTO_UPLOAD=true; shift ;;
    -h|--help) die "usage: scripts/release-ios.sh [--upload | --upload-only] <version> [build_number]" ;;
    *) POSITIONAL+=("$1"); shift ;;
  esac
done
set -- "${POSITIONAL[@]}"

cd "$REPO_ROOT"

APP_EXPORT_PATH="ios/build/export"

if [[ "$UPLOAD_ONLY" == "true" ]]; then
  run_started
  load_env
  require_file APP_STORE_CONNECT_API_KEY_PATH
  require_var APP_STORE_CONNECT_KEY_ID
  require_var APP_STORE_CONNECT_ISSUER_ID

  IPA_PATH="${1:-}"
  if [[ -z "$IPA_PATH" ]]; then
    IPA_PATH=$(ls -t "$APP_EXPORT_PATH"/*.ipa 2>/dev/null | head -1 || true)
    [[ -n "$IPA_PATH" ]] || die "No IPA found in $APP_EXPORT_PATH/. Pass a path explicitly or run a full build first."
  fi
  [[ -f "$IPA_PATH" ]] || die "IPA not found at $IPA_PATH"

  step "Uploading existing IPA"
  mkdir -p "$HOME/.appstoreconnect/private_keys"
  cp "$APP_STORE_CONNECT_API_KEY_PATH" \
    "$HOME/.appstoreconnect/private_keys/AuthKey_${APP_STORE_CONNECT_KEY_ID}.p8"

  xcrun altool \
    --upload-app \
    --type ios \
    --file "$IPA_PATH" \
    --apiKey "$APP_STORE_CONNECT_KEY_ID" \
    --apiIssuer "$APP_STORE_CONNECT_ISSUER_ID"

  IPA_SIZE=$(du -h "$IPA_PATH" | cut -f1 | tr -d '[:space:]')
  print_summary "iOS Upload Summary" \
    "Mode" "upload-only" \
    "IPA" "$IPA_PATH" \
    "Size" "$IPA_SIZE" \
    "Destination" "App Store Connect"
  exit 0
fi

if [[ $# -lt 1 ]]; then
  die "usage: scripts/release-ios.sh [--upload | --upload-only] <version> [build_number]"
fi

VERSION="$1"
BUILD_NUMBER="${2:-$(date +%s)}"

validate_version "$VERSION"
validate_numeric "build_number" "$BUILD_NUMBER"

load_env
require_file APPLE_DISTRIBUTION_CERTIFICATE_P12_PATH
require_var APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD
require_var APPLE_TEAM_ID
require_file APP_STORE_CONNECT_API_KEY_PATH
require_var APP_STORE_CONNECT_KEY_ID
require_var APP_STORE_CONNECT_ISSUER_ID
require_file APP_STORE_PROVISIONING_PROFILE_PATH
require_var KEYCHAIN_PASSWORD

APP_SCHEME="Muxy"
APP_PROJECT="ios/Muxy.xcodeproj"
APP_ARCHIVE_PATH="ios/build/Muxy.xcarchive"
BUNDLE_ID="com.muxy.app"
KEYCHAIN_PATH="$HOME/Library/Keychains/muxy-build.keychain-db"

cleanup() {
  if security list-keychains -d user | grep -q "muxy-build.keychain"; then
    security delete-keychain "$KEYCHAIN_PATH" || true
  fi
}
trap cleanup EXIT
run_started

step "Creating temporary build keychain"
cleanup
security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security set-keychain-settings -lut 21600 "$KEYCHAIN_PATH"
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"

USER_KEYCHAINS=$(security list-keychains -d user | sed -e 's/"//g')
security list-keychains -d user -s "$KEYCHAIN_PATH" $USER_KEYCHAINS

step "Importing distribution certificate"
security import "$APPLE_DISTRIBUTION_CERTIFICATE_P12_PATH" \
  -k "$KEYCHAIN_PATH" \
  -P "$APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD" \
  -T /usr/bin/codesign
security set-key-partition-list \
  -S apple-tool:,apple:,codesign: \
  -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"

step "Validating provisioning profile"
PROFILE_PLIST="$(mktemp)"
security cms -D -i "$APP_STORE_PROVISIONING_PROFILE_PATH" > "$PROFILE_PLIST"
PROFILE_UUID=$(/usr/libexec/PlistBuddy -c "Print :UUID" "$PROFILE_PLIST")
PROFILE_NAME=$(/usr/libexec/PlistBuddy -c "Print :Name" "$PROFILE_PLIST")
PROFILE_APP_ID=$(/usr/libexec/PlistBuddy -c "Print :Entitlements:application-identifier" "$PROFILE_PLIST")
PROFILE_GET_TASK_ALLOW=$(/usr/libexec/PlistBuddy -c "Print :Entitlements:get-task-allow" "$PROFILE_PLIST")

if [[ "$PROFILE_GET_TASK_ALLOW" == "true" ]]; then
  die "Profile '$PROFILE_NAME' is a development profile. Use an App Store profile for $BUNDLE_ID."
fi
if [[ "$PROFILE_APP_ID" != *.$BUNDLE_ID ]]; then
  die "Profile app id '$PROFILE_APP_ID' does not match $BUNDLE_ID"
fi

mkdir -p "$HOME/Library/MobileDevice/Provisioning Profiles"
cp "$APP_STORE_PROVISIONING_PROFILE_PATH" \
  "$HOME/Library/MobileDevice/Provisioning Profiles/$PROFILE_UUID.mobileprovision"
rm -f "$PROFILE_PLIST"

step "Archiving"
xcodebuild \
  -project "$APP_PROJECT" \
  -scheme "$APP_SCHEME" \
  -configuration Release \
  -destination "generic/platform=iOS" \
  -archivePath "$APP_ARCHIVE_PATH" \
  CODE_SIGN_STYLE=Manual \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" \
  PROVISIONING_PROFILE_SPECIFIER="$PROFILE_UUID" \
  CODE_SIGN_IDENTITY="Apple Distribution" \
  PRODUCT_BUNDLE_IDENTIFIER="$BUNDLE_ID" \
  MARKETING_VERSION="$VERSION" \
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER" \
  OTHER_CODE_SIGN_FLAGS="--keychain $KEYCHAIN_PATH" \
  clean archive

step "Exporting IPA"
EXPORT_OPTIONS="$(mktemp -t ExportOptions).plist"
cat > "$EXPORT_OPTIONS" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key>
  <string>app-store-connect</string>
  <key>signingStyle</key>
  <string>manual</string>
  <key>signingCertificate</key>
  <string>Apple Distribution</string>
  <key>teamID</key>
  <string>$APPLE_TEAM_ID</string>
  <key>provisioningProfiles</key>
  <dict>
    <key>$BUNDLE_ID</key>
    <string>$PROFILE_UUID</string>
  </dict>
  <key>uploadSymbols</key>
  <true/>
</dict>
</plist>
EOF

xcodebuild \
  -exportArchive \
  -archivePath "$APP_ARCHIVE_PATH" \
  -exportPath "$APP_EXPORT_PATH" \
  -exportOptionsPlist "$EXPORT_OPTIONS" \
  -authenticationKeyPath "$APP_STORE_CONNECT_API_KEY_PATH" \
  -authenticationKeyID "$APP_STORE_CONNECT_KEY_ID" \
  -authenticationKeyIssuerID "$APP_STORE_CONNECT_ISSUER_ID"

IPA_PATH=$(ls "$APP_EXPORT_PATH"/*.ipa | head -1)
[[ -f "$IPA_PATH" ]] || die "IPA not found in $APP_EXPORT_PATH"

log "Built IPA: $IPA_PATH"
IPA_SIZE=$(du -h "$IPA_PATH" | cut -f1 | tr -d '[:space:]')
UPLOAD_STATUS="skipped"

if [[ "$AUTO_UPLOAD" == "true" ]] || confirm "Upload to App Store Connect?"; then
  step "Uploading to App Store Connect"
  mkdir -p "$HOME/.appstoreconnect/private_keys"
  cp "$APP_STORE_CONNECT_API_KEY_PATH" \
    "$HOME/.appstoreconnect/private_keys/AuthKey_${APP_STORE_CONNECT_KEY_ID}.p8"
  xcrun altool \
    --upload-app \
    --type ios \
    --file "$IPA_PATH" \
    --apiKey "$APP_STORE_CONNECT_KEY_ID" \
    --apiIssuer "$APP_STORE_CONNECT_ISSUER_ID"
  UPLOAD_STATUS="App Store Connect"
else
  log "Skipped upload. IPA stays at $IPA_PATH"
fi

print_summary "iOS Release Summary" \
  "Version" "$VERSION ($BUILD_NUMBER)" \
  "Bundle ID" "$BUNDLE_ID" \
  "IPA" "$IPA_PATH" \
  "Size" "$IPA_SIZE" \
  "Upload" "$UPLOAD_STATUS"
