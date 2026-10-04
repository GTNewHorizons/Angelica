#!/usr/bin/env bash
# usage: release-companion-jar.sh <jar> <notes>; TAG is <name>-v<version>
set -euo pipefail

: "${TAG:?TAG required}"
jar="${1:?jar required}"
notes="${2:?notes required}"

gh release create "$TAG" "$jar" --latest=false --title "angelica-${TAG%%-v*} ${TAG#*-v}" --notes "$notes"
