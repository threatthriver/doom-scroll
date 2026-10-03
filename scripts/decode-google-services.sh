#!/usr/bin/env bash
#
# Materialises app/google-services.json from the GOOGLE_SERVICES_JSON_BASE64 environment
# variable. Use this in CI (GitHub Actions, etc.) where the config is stored as an encrypted
# secret rather than committed to the repo.
#
#   export GOOGLE_SERVICES_JSON_BASE64="<base64 of app/google-services.json>"
#   ./scripts/decode-google-services.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$REPO_ROOT/app/google-services.json"

if [[ -z "${GOOGLE_SERVICES_JSON_BASE64:-}" ]]; then
  echo "ERROR: GOOGLE_SERVICES_JSON_BASE64 is not set." >&2
  echo "Set it to the base64 of your app/google-services.json (see scripts/encode-google-services.sh)." >&2
  exit 1
fi

# base64 -d (GNU) / base64 -D (BSD/macOS): try both.
if echo "$GOOGLE_SERVICES_JSON_BASE64" | base64 -d > "$OUT" 2>/dev/null; then
  :
elif echo "$GOOGLE_SERVICES_JSON_BASE64" | base64 -D > "$OUT" 2>/dev/null; then
  :
else
  echo "ERROR: failed to base64-decode GOOGLE_SERVICES_JSON_BASE64." >&2
  exit 1
fi

echo "Wrote $OUT"
