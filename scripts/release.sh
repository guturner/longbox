#!/usr/bin/env bash
# Create a semver release tag and push it to trigger the GitHub Actions release workflow.
# Usage: ./scripts/release.sh 0.1.1
set -euo pipefail

VERSION="${1:-}"
if [[ ! "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Usage: $0 <major.minor.patch>  (e.g. 0.1.1)" >&2
    exit 1
fi

TAG="v${VERSION}"
git tag -a "${TAG}" -m "Release ${VERSION}"
git push origin "${TAG}"
echo "Tagged ${TAG} and pushed — GitHub Actions release workflow is now running."
