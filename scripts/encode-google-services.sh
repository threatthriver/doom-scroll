#!/usr/bin/env bash
#
# Prints the base64 of app/google-services.json so you can paste it into a CI secret
# (e.g. a GitHub Actions repository secret named GOOGLE_SERVICES_JSON_BASE64).
#
#   ./scripts/encode-google-services.sh | pbcopy    # macOS: copy to clipboard
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$REPO_ROOT/app/google-services.json"

if [[ ! -f "$SRC" ]]; then
  echo "ERROR: $SRC not found. Download it from the Firebase console first." >&2
  exit 1
fi

# GNU base64 supports -w0 (no wrapping); BSD/macOS base64 does not wrap and rejects -w.
# Feed via stdin and strip any newlines so the output is a single line on every platform.
if base64 --help 2>&1 | grep -q '\-w'; then
  base64 -w0 < "$SRC"
else
  base64 < "$SRC" | tr -d '\n'
fi
echo
