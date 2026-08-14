#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. Install Gradle 8.13 or use the GitHub Actions build." >&2
exit 1
