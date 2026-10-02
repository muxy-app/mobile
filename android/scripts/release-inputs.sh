#!/usr/bin/env bash

validate_release_inputs() {
  if ! [[ "${VERSION_NAME:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: version_name must be X.Y.Z" >&2
    return 1
  fi
  VERSION_CODE="${VERSION_CODE:-$(date +%s)}"
  if ! [[ "$VERSION_CODE" =~ ^[1-9][0-9]{9}$ ]] || (( VERSION_CODE <= 1788620580 || VERSION_CODE > 2100000000 )); then
    echo "Error: version_code must be above the last RN upload (1788620580) and at most 2100000000" >&2
    return 1
  fi
  TRACK="${TRACK:-auto}"
  if [[ "$TRACK" == auto ]]; then
    TRACK=production
    if [[ "$VERSION_NAME" =~ ^0+\. ]]; then
      TRACK=alpha
    fi
  fi
  case "$TRACK" in
    internal|alpha|production) ;;
    *) echo "Error: track must be auto, internal, alpha or production" >&2; return 1 ;;
  esac
}
