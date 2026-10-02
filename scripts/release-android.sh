#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
source "$REPO_ROOT/android/scripts/release-inputs.sh"

usage() {
  cat <<'USAGE'
Usage: scripts/release-android.sh [--upload] [--track internal|alpha|production] <version_name> [version_code]
       scripts/release-android.sh --upload-only [--track internal|alpha|production] [path/to/file.aab]

Builds the native Kotlin app. Reads signing and Play credentials from .env.
Version codes default to a Unix timestamp and must exceed 1788620580.
The default track is production for 1.x or later, otherwise alpha. Uploads are drafts.
Upload-only reads the version from the AAB with bundletool, not from the checkout.
R8 mapping and native symbols are uploaded from the AAB's embedded metadata.
USAGE
}

AUTO_UPLOAD=false
UPLOAD_ONLY=false
TRACK=auto
POSITIONAL=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --upload) AUTO_UPLOAD=true; shift ;;
    --upload-only) UPLOAD_ONLY=true; AUTO_UPLOAD=true; shift ;;
    --track) [[ $# -ge 2 ]] || die "--track requires a value"; TRACK="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    --*) die "Unknown option: $1" ;;
    *) POSITIONAL+=("$1"); shift ;;
  esac
done
if [[ ${#POSITIONAL[@]} -eq 0 ]]; then
  set --
else
  set -- "${POSITIONAL[@]}"
fi
cd "$REPO_ROOT"
run_started

PACKAGE_NAME="com.muxy.app"
AAB_PATH="android/app/build/outputs/bundle/release/app-release.aab"

if [[ "$UPLOAD_ONLY" == true ]]; then
  [[ $# -le 1 ]] || die "Upload-only accepts at most one AAB path"
  AAB_PATH="${1:-$AAB_PATH}"
  [[ -f "$AAB_PATH" ]] || die "AAB not found: $AAB_PATH"
  command -v bundletool >/dev/null || die "Upload-only requires bundletool (brew install bundletool)"
  VERSION_NAME=$(bundletool dump manifest --bundle="$AAB_PATH" --xpath='/manifest/@android:versionName')
  VERSION_CODE=$(bundletool dump manifest --bundle="$AAB_PATH" --xpath='/manifest/@android:versionCode')
  BUNDLE_PACKAGE=$(bundletool dump manifest --bundle="$AAB_PATH" --xpath='/manifest/@package')
  [[ "$BUNDLE_PACKAGE" == "$PACKAGE_NAME" ]] || die "AAB package does not match $PACKAGE_NAME"
else
  [[ $# -ge 1 && $# -le 2 ]] || { usage; exit 1; }
  VERSION_NAME="$1"
  VERSION_CODE="${2:-}"
fi
validate_release_inputs
load_env

if [[ "$UPLOAD_ONLY" != true ]]; then
  require_file ANDROID_SIGNING_KEY_PATH
  require_var ANDROID_KEY_STORE_PASSWORD
  require_var ANDROID_KEY_ALIAS
  require_var ANDROID_KEY_PASSWORD

  step "Installing pinned Muxy SDK"
  android/scripts/sdk.sh install

  step "Building signed native Release AAB"
  (
    cd android
    ANDROID_SIGNING_KEY_PATH="$ANDROID_SIGNING_KEY_PATH" \
      ANDROID_KEY_STORE_PASSWORD="$ANDROID_KEY_STORE_PASSWORD" \
      ANDROID_KEY_ALIAS="$ANDROID_KEY_ALIAS" \
      ANDROID_KEY_PASSWORD="$ANDROID_KEY_PASSWORD" \
      ./gradlew :app:bundleRelease -PversionName="$VERSION_NAME" -PversionCode="$VERSION_CODE"
  )
  [[ -s "$AAB_PATH" ]] || die "AAB not found"
  [[ -s android/app/build/outputs/mapping/release/mapping.txt ]] || die "R8 mapping not found"
  [[ -s android/app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip ]] || die "Native symbols not found"
fi

UPLOAD_STATUS=skipped
if [[ "$AUTO_UPLOAD" == true || -n "${PLAY_SERVICE_ACCOUNT_JSON_PATH:-}" ]]; then
  require_file PLAY_SERVICE_ACCOUNT_JSON_PATH
  if [[ "$AUTO_UPLOAD" == true ]] || confirm "Upload AAB to Play Store ($TRACK track, draft status)?"; then
    step "Uploading AAB, R8 mapping and native symbols to Play"
    VENV_DIR="$REPO_ROOT/.venv-play-upload"
    if [[ ! -d "$VENV_DIR" ]]; then
      python3 -m venv "$VENV_DIR"
      "$VENV_DIR/bin/pip" install --quiet google-api-python-client google-auth
    fi
    "$VENV_DIR/bin/python" "$SCRIPT_DIR/lib/play_upload.py" \
      --aab "$AAB_PATH" \
      --package-name "$PACKAGE_NAME" \
      --track "$TRACK" \
      --json-key "$PLAY_SERVICE_ACCOUNT_JSON_PATH"
    UPLOAD_STATUS="Play Store ($TRACK draft)"
  fi
fi

print_summary "Android Release Summary" \
  "Version" "$VERSION_NAME ($VERSION_CODE)" \
  "Package" "$PACKAGE_NAME" \
  "AAB" "$AAB_PATH" \
  "Size" "$(du -h "$AAB_PATH" | cut -f1 | tr -d '[:space:]')" \
  "Track" "$TRACK" \
  "Upload" "$UPLOAD_STATUS"
