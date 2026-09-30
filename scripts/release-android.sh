#!/usr/bin/env bash
# Local Android release script. Mirrors .github/workflows/android-release.yml.
#
# Usage:
#   scripts/release-android.sh [--upload] <version_name> [version_code]
#   scripts/release-android.sh --upload-only [path/to/file.aab]
#
# Flags:
#   --upload        Skip the confirmation prompt and upload to Play Store.
#   --upload-only   Skip build; upload an existing AAB. Defaults to the most
#                   recent file in android/app/build/outputs/bundle/release/.
#
# Examples:
#   scripts/release-android.sh 0.3.0
#   scripts/release-android.sh 0.3.0 42
#   scripts/release-android.sh --upload 0.3.0 42
#   scripts/release-android.sh --upload-only
#   scripts/release-android.sh --upload-only path/to/app.aab
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
    -h|--help) die "usage: scripts/release-android.sh [--upload | --upload-only] <version_name> [version_code]" ;;
    *) POSITIONAL+=("$1"); shift ;;
  esac
done
set -- "${POSITIONAL[@]}"

cd "$REPO_ROOT"

if [[ -f android/settings.gradle.kts ]]; then
  die "android/ holds the native Kotlin app, and expo prebuild would replace it. Release React Native with the Release workflow, or run this script from a worktree of the last React Native release."
fi

PACKAGE_NAME="com.muxy.app"
KEYSTORE_DEST="android/app/upload-keystore.jks"

if [[ "$UPLOAD_ONLY" == "true" ]]; then
  run_started
  load_env
  AAB_PATH="${1:-}"
  if [[ -z "$AAB_PATH" ]]; then
    AAB_PATH=$(ls -t android/app/build/outputs/bundle/release/*.aab 2>/dev/null | head -1 || true)
    [[ -n "$AAB_PATH" ]] || die "No AAB found in android/app/build/outputs/bundle/release/. Pass a path explicitly or run a full build first."
  fi
  [[ -f "$AAB_PATH" ]] || die "AAB not found at $AAB_PATH"

  if [[ -z "${PLAY_SERVICE_ACCOUNT_JSON_PATH:-}" ]]; then
    die "PLAY_SERVICE_ACCOUNT_JSON_PATH not set in .env; cannot upload."
  fi
  if [[ "$PLAY_SERVICE_ACCOUNT_JSON_PATH" != /* ]]; then
    PLAY_SERVICE_ACCOUNT_JSON_PATH="$REPO_ROOT/$PLAY_SERVICE_ACCOUNT_JSON_PATH"
  fi
  [[ -f "$PLAY_SERVICE_ACCOUNT_JSON_PATH" ]] || die "PLAY_SERVICE_ACCOUNT_JSON_PATH points to '$PLAY_SERVICE_ACCOUNT_JSON_PATH' but the file does not exist."

  VERSION_CODE_FROM_AAB=$(unzip -p "$AAB_PATH" base/manifest/AndroidManifest.xml 2>/dev/null \
    | strings | grep -oE 'versionCode[^0-9]*[0-9]+' | head -1 | grep -oE '[0-9]+' || true)
  VERSION_NAME_GUESS=$(node -e "try { console.log(JSON.parse(require('fs').readFileSync('app.json','utf8')).expo.version) } catch (e) {}")
  MAJOR="${VERSION_NAME_GUESS%%.*}"
  if [[ "${MAJOR:-0}" -ge 1 ]]; then
    TRACK="production"
  else
    TRACK="alpha"
  fi

  step "Uploading existing AAB"
  log "  versionCode (from AAB): ${VERSION_CODE_FROM_AAB:-unknown}"
  log "  track: $TRACK"

  VENV_DIR="$REPO_ROOT/.venv-play-upload"
  if [[ ! -d "$VENV_DIR" ]]; then
    log "Creating Python venv for Play upload (one-time)"
    python3 -m venv "$VENV_DIR"
    "$VENV_DIR/bin/pip" install --quiet --upgrade pip
    "$VENV_DIR/bin/pip" install --quiet google-api-python-client google-auth
  fi
  "$VENV_DIR/bin/python" "$SCRIPT_DIR/lib/play_upload.py" \
    --aab "$AAB_PATH" \
    --package-name "$PACKAGE_NAME" \
    --track "$TRACK" \
    --json-key "$PLAY_SERVICE_ACCOUNT_JSON_PATH"

  AAB_SIZE=$(du -h "$AAB_PATH" | cut -f1 | tr -d '[:space:]')
  print_summary "Android Upload Summary" \
    "Mode" "upload-only" \
    "Package" "$PACKAGE_NAME" \
    "AAB" "$AAB_PATH" \
    "versionCode" "${VERSION_CODE_FROM_AAB:-unknown}" \
    "Size" "$AAB_SIZE" \
    "Track" "$TRACK (draft)"
  exit 0
fi

if [[ $# -lt 1 ]]; then
  die "usage: scripts/release-android.sh [--upload | --upload-only] <version_name> [version_code]"
fi

VERSION_NAME="$1"
VERSION_CODE="${2:-$(date +%s)}"

validate_version "$VERSION_NAME"
validate_numeric "version_code" "$VERSION_CODE"

MAJOR="${VERSION_NAME%%.*}"
if [[ "$MAJOR" -ge 1 ]]; then
  TRACK="production"
else
  TRACK="alpha"
fi

load_env
require_file ANDROID_SIGNING_KEY_PATH
require_var ANDROID_KEY_STORE_PASSWORD
require_var ANDROID_KEY_ALIAS
require_var ANDROID_KEY_PASSWORD
APP_JSON_BACKUP="$(mktemp)"

cleanup() {
  if [[ -f "$APP_JSON_BACKUP" ]]; then
    cp "$APP_JSON_BACKUP" "$REPO_ROOT/app.json"
    rm -f "$APP_JSON_BACKUP"
  fi
}
trap cleanup EXIT
run_started

step "Installing JS deps"
npm ci

step "Writing version $VERSION_NAME ($VERSION_CODE) into app.json"
cp "$REPO_ROOT/app.json" "$APP_JSON_BACKUP"
node -e "
  const fs = require('fs');
  const cfg = JSON.parse(fs.readFileSync('app.json', 'utf8'));
  cfg.expo.version = '$VERSION_NAME';
  cfg.expo.android = cfg.expo.android || {};
  cfg.expo.android.versionCode = $VERSION_CODE;
  fs.writeFileSync('app.json', JSON.stringify(cfg, null, 2) + '\n');
"

step "expo prebuild (Android)"
npx expo prebuild --platform android --no-install --clean --non-interactive

step "Copying keystore into android/app/"
cp "$ANDROID_SIGNING_KEY_PATH" "$KEYSTORE_DEST"

step "Patching android/app/build.gradle with release signing config"
node "$SCRIPT_DIR/lib/patch_signing.js" android/app/build.gradle

step "Building signed Release AAB"
chmod +x android/gradlew
( cd android && \
    ANDROID_KEY_STORE_PASSWORD="$ANDROID_KEY_STORE_PASSWORD" \
    ANDROID_KEY_ALIAS="$ANDROID_KEY_ALIAS" \
    ANDROID_KEY_PASSWORD="$ANDROID_KEY_PASSWORD" \
    ./gradlew bundleRelease -PreactNativeArchitectures=arm64-v8a,armeabi-v7a )

AAB_PATH=$(ls android/app/build/outputs/bundle/release/*.aab | head -1)
[[ -f "$AAB_PATH" ]] || die "AAB not found"

log "Built AAB: $AAB_PATH (track: $TRACK)"
AAB_SIZE=$(du -h "$AAB_PATH" | cut -f1 | tr -d '[:space:]')
UPLOAD_STATUS="skipped"

if [[ -n "${PLAY_SERVICE_ACCOUNT_JSON_PATH:-}" ]]; then
  if [[ "$PLAY_SERVICE_ACCOUNT_JSON_PATH" != /* ]]; then
    PLAY_SERVICE_ACCOUNT_JSON_PATH="$REPO_ROOT/$PLAY_SERVICE_ACCOUNT_JSON_PATH"
  fi
fi
if [[ -n "${PLAY_SERVICE_ACCOUNT_JSON_PATH:-}" && -f "${PLAY_SERVICE_ACCOUNT_JSON_PATH:-}" ]]; then
  if [[ "$AUTO_UPLOAD" == "true" ]] || confirm "Upload AAB to Play Store ($TRACK track, draft status)?"; then
    step "Uploading to Play Store"
    VENV_DIR="$REPO_ROOT/.venv-play-upload"
    if [[ ! -d "$VENV_DIR" ]]; then
      log "Creating Python venv for Play upload (one-time)"
      python3 -m venv "$VENV_DIR"
      "$VENV_DIR/bin/pip" install --quiet --upgrade pip
      "$VENV_DIR/bin/pip" install --quiet google-api-python-client google-auth
    fi
    "$VENV_DIR/bin/python" "$SCRIPT_DIR/lib/play_upload.py" \
      --aab "$AAB_PATH" \
      --package-name "$PACKAGE_NAME" \
      --track "$TRACK" \
      --json-key "$PLAY_SERVICE_ACCOUNT_JSON_PATH"
    UPLOAD_STATUS="Play Store ($TRACK draft)"
  else
    log "Skipped upload. AAB stays at $AAB_PATH"
  fi
else
  log "PLAY_SERVICE_ACCOUNT_JSON_PATH not set or file missing; skipping Play upload."
fi

print_summary "Android Release Summary" \
  "Version" "$VERSION_NAME ($VERSION_CODE)" \
  "Package" "$PACKAGE_NAME" \
  "AAB" "$AAB_PATH" \
  "Size" "$AAB_SIZE" \
  "Track" "$TRACK" \
  "Upload" "$UPLOAD_STATUS"
