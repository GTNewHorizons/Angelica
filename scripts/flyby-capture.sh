#!/usr/bin/env bash
# Run a flyby benchmark under using tracy-capture
#
# * Launches the game with the flyby properties set and tracy-capture
# * Game waits for the tracy connection, runs the route, returns to the original coordinates and exits.
# * tracy-capture finalizes
#
# Usage: scripts/flyby-capture.sh <output.tracy> [route] [length] [extra gradle args...]
#   route  - straight | pan | circuit | static
#   length - Units: blocks (straight), blocks per leg (circuit), degrees (pan), ticks (static).
#   extra  - passed through to gradle; -Dangelica.* reaches the client (e.g. -Dangelica.flyby.timeOfDay=1500).
#
#   FLYBY_SPEED     - blocks per tick. (0.5 ~= creative flight).
#   QUICKPLAY_WORLD - save to auto-load (default: latest).
#   SDLGPU=1        - launch on the SDL-GPU backend.
#
#   Requires tracy-capture on PATH (TRACY_CAPTURE=/path/to/tracy-capture).
#   QuickPlay loads QUICKPLAY_WORLD on launch; the run starts once the world is up.
#
#   Ensure the chunks in the route have been generated before capturing

set -euo pipefail

OUT="${1:?usage: flyby-capture.sh <output.tracy> [route] [length] [extra gradle args...]}"
ROUTE="${2:-straight}"
LENGTH="${3:-0}"
if [ $# -ge 3 ]; then
    shift 3
else
    shift $#
fi
WARMUP="${FLYBY_WARMUP:-400}"
TRACY_CAPTURE="${TRACY_CAPTURE:-tracy-capture}"

BACKEND_ARGS=()
if [ "${SDLGPU:-0}" = "1" ]; then
    BACKEND_ARGS+=(-Dangelica.sdlgpu.enable=true)
fi

if ! command -v "$TRACY_CAPTURE" >/dev/null 2>&1 && [ ! -x "$TRACY_CAPTURE" ]; then
    cat >&2 <<'EOF'
error: tracy-capture not found.

Build and launch with: TRACY_CAPTURE=/path/to/tracy/capture/build/tracy-capture scripts/flyby-capture.sh ...
EOF
    exit 1
fi

if [ -e "$OUT" ]; then
    echo "error: $OUT already exists; refusing to overwrite a capture" >&2
    exit 1
fi

echo "==> starting capture -> $OUT"
if [ -n "${CAPTURE_SECONDS:-}" ]; then
    "$TRACY_CAPTURE" -o "$OUT" -s "$CAPTURE_SECONDS" &
else
    "$TRACY_CAPTURE" -o "$OUT" &
fi
CAPTURE_PID=$!

cleanup() {
    if kill -0 "$CAPTURE_PID" 2>/dev/null; then
        echo "==> stopping capture"
        kill -INT "$CAPTURE_PID" 2>/dev/null || true
        wait "$CAPTURE_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo "==> launching game (route=$ROUTE warmup=$WARMUP length=${LENGTH:-default})"

./gradlew runClient25 --console=plain \
    -Dangelica.flyby.route="$ROUTE" \
    -Dangelica.flyby.length="$LENGTH" \
    -Dangelica.flyby.speed="${FLYBY_SPEED:-0}" \
    -Dangelica.flyby.warmupTicks="$WARMUP" \
    -Dangelica.flyby.waitForTracy=true \
    -Dangelica.flyby.exitWhenDone=true \
    -DquickPlaySingleplayer="${QUICKPLAY_WORLD:-latest}" \
    ${BACKEND_ARGS[@]+"${BACKEND_ARGS[@]}"} \
    "$@"

echo "==> game exited, finalising capture"
cleanup
trap - EXIT

if [ -s "$OUT" ]; then
    echo "==> wrote $OUT ($(du -h "$OUT" | cut -f1))"
else
    echo "error: $OUT is empty - did the game connect to Tracy?" >&2
    exit 1
fi
