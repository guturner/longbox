#!/usr/bin/env bash
# Build a Docker image and save it as a .tar.gz ready to import into
# Synology Container Manager (Image → Add → Add from file).
#
# Usage: ./scripts/synology-package.sh [version]
#   version  defaults to the latest git tag (strip leading v), or "dev" if no tags exist.
#
# Output: longbox-server-<version>.tar.gz in the repo root.
set -euo pipefail

VERSION="${1:-$(git describe --tags --abbrev=0 2>/dev/null | sed 's/^v//' || echo "dev")}"
IMAGE="longbox-server:${VERSION}"
ARCHIVE="longbox-server-${VERSION}.tar.gz"

echo "=== Longbox — Synology package (${VERSION}) ==="
echo

echo "Building server fat jar + web distribution…"
./gradlew :server:buildFatJar :webApp:wasmJsBrowserDistribution

echo
echo "Building Docker image ${IMAGE}…"
docker build -f server/Dockerfile -t "$IMAGE" .

echo
echo "Saving to ${ARCHIVE}…"
docker save "$IMAGE" | gzip > "$ARCHIVE"

echo
echo "Done: ${ARCHIVE}"
echo
echo "Next steps:"
echo "  1. Copy ${ARCHIVE} to the NAS (File Station or scp)."
echo "  2. Container Manager → Image → Add → Add from file → select ${ARCHIVE}."
echo "  3. If this is a new install, follow docs/SYNOLOGY.md sections 2–5."
echo "  4. If upgrading, restart the 'longbox' project in Container Manager."
