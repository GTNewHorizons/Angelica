#!/usr/bin/env bash
# Run a flyby benchmark under tracy-capture and/or an in-process JFR profile
#
# * Launches the game with the flyby properties set and tracy-capture
# * Game waits for the tracy connection, runs the route, returns to the original coordinates and exits.
# * tracy-capture finalizes
#
# Usage: scripts/flyby-capture.sh <output.tracy|output.jfr> [route] [length] [extra gradle args...]
#   route  - straight | pan | circuit | static
#   length - Units: blocks (straight), blocks per leg (circuit), degrees (pan), ticks (static).
#   extra  - passed through to gradle; -Dangelica.* reaches the client (e.g. -Dangelica.flyby.timeOfDay=1500).
#
#   FLYBY_SPEED     - blocks per tick. (0.5 ~= creative flight).
#   FLYBY_PACING    - UNCAPPED | CONFIGURED. Passed through when set.
#   FLYBY_ORIGIN    - x,z[,yaw] flight start. Unset or empty starts where the player is.
#   QUICKPLAY_WORLD - save to auto-load (default: latest).
#   FLYBY_SCENE     - server command file run at flyby start.
#   SDLGPU=0|1      - force OpenGL (0) or SDL-GPU (1); unset uses the build default.
#   ALSO_JFR=1      - with a .tracy output, also record a JFR alongside it (timings inflated, correlation only).
#   ASPROF_OPTS     - comma separated async-profiler agent opts, passed through when a JFR is recorded.
#
#   The output extension selects the profiler: .tracy for Tracy, .jfr for an in-process async-profiler
#   JFR that starts near the end of warmup and stops when the measured run ends.
#
#   Requires tracy-capture on PATH (TRACY_CAPTURE=/path/to/tracy-capture) for .tracy output.
#   QuickPlay loads QUICKPLAY_WORLD on launch; the run starts once the world is up.
#
#   Ensure the chunks in the route have been generated before capturing

set -euo pipefail

OUT="${1:?usage: flyby-capture.sh <output.tracy|output.jfr> [route] [length] [extra gradle args...]}"
ROUTE="${2:-straight}"
LENGTH="${3:-0}"
if [ $# -ge 3 ]; then
    shift 3
else
    shift $#
fi
WARMUP="${FLYBY_WARMUP:-400}"
TRACY_CAPTURE="${TRACY_CAPTURE:-tracy-capture}"

case "$OUT" in
    *.tracy) MODE="tracy" ;;
    *.jfr) MODE="jfr" ;;
    *)
        echo "usage: flyby-capture.sh <output.tracy|output.jfr> [route] [length] [extra gradle args...]" >&2
        exit 2
        ;;
esac

BACKEND_ARGS=()
if [ "${SDLGPU:-}" = "1" ]; then
    BACKEND_ARGS+=(-Dangelica.sdlgpu.enable=true)
elif [ "${SDLGPU:-}" = "0" ]; then
    BACKEND_ARGS+=(-Dangelica.sdlgpu.enable=false)
fi
if [ -n "${FLYBY_PACING:-}" ]; then
    BACKEND_ARGS+=(-Dangelica.flyby.pacing="$FLYBY_PACING")
fi
if [ -n "${FLYBY_ORIGIN:-}" ]; then
    BACKEND_ARGS+=(-Dangelica.flyby.origin="$FLYBY_ORIGIN")
fi

SCENE_ABS=""
SCENE_ARGS=()
if [ -n "${FLYBY_SCENE:-}" ]; then
    case "$FLYBY_SCENE" in
        /*) SCENE_ABS="$FLYBY_SCENE" ;;
        *) SCENE_ABS="$PWD/$FLYBY_SCENE" ;;
    esac
    if [ ! -f "$SCENE_ABS" ]; then
        echo "error: FLYBY_SCENE file not found: $SCENE_ABS" >&2
        exit 1
    fi
    SCENE_ARGS+=(-Dangelica.flyby.commands="$SCENE_ABS")
fi

# JFR_OUT is set when a JFR is being recorded this run: always for MODE=jfr,
# additionally for MODE=tracy when ALSO_JFR=1 (both profilers active).
JFR_OUT=""
DISABLE_TRACY=0
if [ "$MODE" = "jfr" ]; then
    JFR_OUT="$OUT"
    DISABLE_TRACY=1
elif [ "${ALSO_JFR:-}" = "1" ]; then
    JFR_OUT="${OUT%.tracy}.jfr"
fi

PROFILE_ARGS=()
JFR_ABS=""
if [ -n "$JFR_OUT" ]; then
    JFR_DIR="$(dirname "$JFR_OUT")"
    if [ ! -d "$JFR_DIR" ]; then
        echo "error: directory for $JFR_OUT does not exist" >&2
        exit 1
    fi
    JFR_ABS="$(cd "$JFR_DIR" && pwd)/$(basename "$JFR_OUT")"
    if [ "$DISABLE_TRACY" = "1" ]; then
        PROFILE_ARGS+=(-Dangelica.tracy=false)
    fi
    PROFILE_ARGS+=(-Dangelica.flyby.jfr=true -Dangelica.profile.output="$JFR_ABS")
    if [ -n "${ASPROF_OPTS:-}" ]; then
        PROFILE_ARGS+=(-Dangelica.profile.opts="$ASPROF_OPTS")
    fi
fi

if [ "$MODE" = "tracy" ]; then
    if ! command -v "$TRACY_CAPTURE" >/dev/null 2>&1 && [ ! -x "$TRACY_CAPTURE" ]; then
        cat >&2 <<'EOF'
error: tracy-capture not found.

Build and launch with: TRACY_CAPTURE=/path/to/tracy/capture/build/tracy-capture scripts/flyby-capture.sh ...
EOF
        exit 1
    fi
fi

if [ -e "$OUT" ]; then
    echo "error: $OUT already exists; refusing to overwrite a capture" >&2
    exit 1
fi
if [ -n "$JFR_OUT" ] && [ "$JFR_OUT" != "$OUT" ] && [ -e "$JFR_OUT" ]; then
    echo "error: $JFR_OUT already exists; refusing to overwrite a capture" >&2
    exit 1
fi

WAIT_ARGS=()
CAPTURE_PID=""
if [ "$MODE" = "tracy" ]; then
    WAIT_ARGS+=(-Dangelica.flyby.waitForTracy=true)

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
fi

echo "==> launching game (route=$ROUTE warmup=$WARMUP length=${LENGTH:-default} scene=${SCENE_ABS:-none})"
if [ -n "$JFR_OUT" ]; then
    echo "==> recording JFR -> $JFR_ABS"
fi
if [ "$MODE" = "tracy" ] && [ -n "$JFR_OUT" ]; then
    echo "==> both profilers active: timings inflated, use for correlation only"
fi

./gradlew runClient25 --console=plain \
    -Dangelica.flyby.route="$ROUTE" \
    -Dangelica.flyby.length="$LENGTH" \
    -Dangelica.flyby.speed="${FLYBY_SPEED:-0}" \
    -Dangelica.flyby.warmupTicks="$WARMUP" \
    ${WAIT_ARGS[@]+"${WAIT_ARGS[@]}"} \
    -Dangelica.flyby.exitWhenDone=true \
    -DquickPlaySingleplayer="${QUICKPLAY_WORLD:-latest}" \
    ${BACKEND_ARGS[@]+"${BACKEND_ARGS[@]}"} \
    ${SCENE_ARGS[@]+"${SCENE_ARGS[@]}"} \
    ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} \
    "$@"

echo "==> game exited, finalising capture"
if [ "$MODE" = "tracy" ]; then
    cleanup
    trap - EXIT
fi

if [ "$MODE" = "jfr" ]; then
    if [ -s "$OUT" ]; then
        echo "==> wrote $OUT ($(du -h "$OUT" | cut -f1))"
    else
        echo "error: $OUT is empty - did the run reach warmup?" >&2
        exit 1
    fi
else
    if [ -s "$OUT" ]; then
        echo "==> wrote $OUT ($(du -h "$OUT" | cut -f1))"
    else
        echo "error: $OUT is empty - did the game connect to Tracy?" >&2
        exit 1
    fi
    if [ -n "$JFR_OUT" ]; then
        if [ -s "$JFR_OUT" ]; then
            echo "==> wrote $JFR_OUT ($(du -h "$JFR_OUT" | cut -f1))"
        else
            echo "error: $JFR_OUT is empty - did the run reach warmup?" >&2
            exit 1
        fi
    fi
fi
