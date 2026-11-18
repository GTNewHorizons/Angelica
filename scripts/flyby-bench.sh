#!/usr/bin/env bash
# Usage: scripts/flyby-bench.sh <name> ffp|pack [flyby-capture.sh args...]
# Env: FLYBY_SCENE, FLYBY_ORIGIN, TIME_OF_DAY, QUICKPLAY_WORLD, TRACY_CAPTURE, SDLGPU, RENDER_DISTANCE.
# BENCH_EXT=tracy|jfr - output extension, selects the profiler via flyby-capture.sh (default: tracy).
# Forces particles:0 for the run and restores it after exit; the game rewrites options.txt on quit.
# A worktree needs tracy-client/src/main/resources/natives copied in.

set -euo pipefail

NAME="${1:?usage: flyby-bench.sh <name> ffp|pack [extra gradle args...]}"
MODE="${2:?usage: flyby-bench.sh <name> ffp|pack [extra gradle args...]}"
shift 2

OUT_DIR="tmp/bench"
OPTIONS="run/client/options.txt"
SHADERS="run/client/config/shaders.properties"
mkdir -p "$OUT_DIR"

PARTICLES_BEFORE="$(grep -E '^particles:' "$OPTIONS" | cut -d: -f2)"
SHADERS_BEFORE="$(grep -E '^enableShaders=' "$SHADERS" | cut -d= -f2)"
if [ -n "${RENDER_DISTANCE:-}" ]; then RENDER_DISTANCE_BEFORE="$(grep -E '^renderDistance:' "$OPTIONS" | cut -d: -f2)"; fi

restore() {
    sed -i '' "s/^particles:.*/particles:${PARTICLES_BEFORE}/" "$OPTIONS"
    sed -i '' "s/^enableShaders=.*/enableShaders=${SHADERS_BEFORE}/" "$SHADERS"
    if [ -n "${RENDER_DISTANCE:-}" ]; then
        sed -i '' "s/^renderDistance:.*/renderDistance:${RENDER_DISTANCE_BEFORE}/" "$OPTIONS"
    fi
}
trap restore EXIT

sed -i '' 's/^particles:.*/particles:0/' "$OPTIONS"
case "$MODE" in
    ffp)  sed -i '' 's/^enableShaders=.*/enableShaders=false/' "$SHADERS" ;;
    pack) sed -i '' 's/^enableShaders=.*/enableShaders=true/' "$SHADERS" ;;
    *) echo "error: mode must be ffp or pack" >&2; exit 1 ;;
esac
if [ -n "${RENDER_DISTANCE:-}" ]; then
    sed -i '' "s/^renderDistance:.*/renderDistance:${RENDER_DISTANCE}/" "$OPTIONS"
fi

export FLYBY_SCENE="${FLYBY_SCENE:-scripts/flyby-scenes/entities-x8.txt}"
export QUICKPLAY_WORLD="${QUICKPLAY_WORLD:-Flat World - No Mods}"
if [ "$QUICKPLAY_WORLD" = "Flat World - No Mods" ]; then export FLYBY_ORIGIN="${FLYBY_ORIGIN-1509.5,-924.5,0}"; fi
export TRACY_CAPTURE="${TRACY_CAPTURE:-$HOME/dev/mc/tracy/capture/build/tracy-capture}"
export SDLGPU="${SDLGPU:-1}"
BENCH_EXT="${BENCH_EXT:-tracy}"

echo "=== $NAME ($MODE) start $(date '+%H:%M:%S')"
scripts/flyby-capture.sh "$OUT_DIR/$NAME.$BENCH_EXT" circuit 128 -Dangelica.flyby.timeOfDay="${TIME_OF_DAY:-6000}" "$@" >| "$OUT_DIR/$NAME.console.log" 2>&1
echo "=== $NAME exit=$? $(date '+%H:%M:%S'), $(grep -c 'did not execute' "$OUT_DIR/$NAME.console.log" || true) failed scene lines"
