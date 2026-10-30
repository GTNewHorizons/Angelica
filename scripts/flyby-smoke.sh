#!/usr/bin/env bash
# Unattended flyby smoke test: scripted scene, 8 screenshots, log checks
#
# Usage: scripts/flyby-smoke.sh <name> [ffp|pack|vao]...
#   Default runs ffp, pack and vao, SDL-GPU backend. vao = ffp with quality.use_celeritas_smooth_lighting=false.
#   Output: tmp/smoke/<name>/<variant>/ (console.log, fml-client-latest.log, latest.log, flyby-shot-*.png)
#   Prints one PASS/FAIL line per variant; exits non-zero if any fails.
#
#   QUICKPLAY_WORLD - save to load (default: Flat World - No Mods).
#   FLYBY_ORIGIN    - x,z[,yaw] flight start (default: 1509.5,-924.5,0).
#   SMOKE_TIMEOUT   - seconds before the client is killed (default: 600).
#   SMOKE_SCENE     - scene command file (default: scripts/flyby-scenes/surround-smoke.txt).
#   SMOKE_PACK      - shaderpack name for the pack variant (default: ComplementaryUnbound_r5.7.1 + EuphoriaPatches_1.8.1).
#   SMOKE_DEBUG_HUD - true forces F3 on, false forces it off (default: true).
#   SMOKE_PITCH     - camera pitch in degrees, positive looks down (default: 15).
#   SMOKE_WARMUP_TICKS - ticks at the first pose before the pan (default: 100).
#   SMOKE_ARGS      - extra -D flags for the client, word-split (default: none).
#                     World-leak check: "-Dangelica.flyby.visits=3 -Dangelica.flyby.heapCheck=true" (FAIL/SKIPPED fails the variant).
#   SMOKE_CHECK_ARGS - extra flyby-smoke-check.py flags, word-split (default: none).
#   SMOKE_FULLSCREEN - 0 keeps options.txt's window mode (default: 1, fullscreen for the run).
#
#   Runs from this script's own checkout, fullscreen unless SMOKE_FULLSCREEN=0 (fullscreen avoids vsync pacing). Restores run/client/config/shaders.properties, angelica-options.json and options.txt on exit.

set -euo pipefail

NAME="${1:?usage: flyby-smoke.sh <name> [ffp|pack|vao]...}"
shift
if [ $# -eq 0 ]; then
    set -- ffp pack vao
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=flyby-lib.sh
. "$ROOT/scripts/flyby-lib.sh"

QUICKPLAY_WORLD="${QUICKPLAY_WORLD:-Flat World - No Mods}"
FLYBY_ORIGIN="${FLYBY_ORIGIN:-1509.5,-924.5,0}"
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-600}"
SMOKE_PACK="${SMOKE_PACK:-ComplementaryUnbound_r5.7.1 + EuphoriaPatches_1.8.1}"
SMOKE_DEBUG_HUD="${SMOKE_DEBUG_HUD:-true}"
SMOKE_PITCH="${SMOKE_PITCH:-15}"
SMOKE_WARMUP_TICKS="${SMOKE_WARMUP_TICKS:-100}"
SMOKE_ARGS="${SMOKE_ARGS:-}"
SMOKE_CHECK_ARGS="${SMOKE_CHECK_ARGS:-}"
SMOKE_FULLSCREEN="${SMOKE_FULLSCREEN:-1}"

SCENE="${SMOKE_SCENE:-$ROOT/scripts/flyby-scenes/surround-smoke.txt}"
case "$SCENE" in
    /*) ;;
    *) SCENE="$ROOT/$SCENE" ;;
esac
RUN="$ROOT/run/client"
SHADERS="$RUN/config/shaders.properties"
OUT="$ROOT/tmp/smoke/$NAME"
BACKUP="$OUT/shaders.properties.bak"
OPTIONS="$RUN/config/angelica-options.json"
OPTIONS_BACKUP="$OUT/angelica-options.json.bak"
OPTIONS_TXT="$RUN/options.txt"

for v in "$@"; do
    case "$v" in
        ffp|pack|vao) ;;
        *) echo "error: variant must be ffp, pack or vao, got: $v" >&2; exit 2 ;;
    esac
done
if [ ! -f "$SCENE" ]; then
    echo "error: scene not found: $SCENE" >&2
    exit 1
fi
for v in "$@"; do
    if [ "$v" = "pack" ] && [ ! -d "$RUN/shaderpacks/$SMOKE_PACK" ]; then
        echo "error: shaderpack missing: $RUN/shaderpacks/$SMOKE_PACK" >&2
        exit 1
    fi
done

mkdir -p "$OUT"
flyby_copy "$SHADERS" "$BACKUP"
flyby_copy "$OPTIONS" "$OPTIONS_BACKUP"

restore_options() {
    flyby_restore_file "$OPTIONS_BACKUP" "$OPTIONS" || true
}

restore() {
    flyby_restore_file "$BACKUP" "$SHADERS" || true
    restore_options
    if [ "$SMOKE_FULLSCREEN" = 1 ]; then flyby_fullscreen_restore "$OPTIONS_TXT" "$OUT/options.txt.bak" || true; fi
}
if [ "$SMOKE_FULLSCREEN" = 1 ]; then flyby_fullscreen_on "$OPTIONS_TXT" "$OUT/options.txt.bak"; fi
trap restore EXIT

set_shaders() {
    flyby_set_key "$SHADERS" enableShaders "$1" =
    if [ -n "$2" ]; then
        flyby_set_key "$SHADERS" shaderPack "$2" =
    fi
}

set_vao() {
    python3 - "$OPTIONS" <<'PY'
import json, sys
path = sys.argv[1]
with open(path) as f:
    data = json.load(f)
data["quality"]["use_celeritas_smooth_lighting"] = False
with open(path, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
PY
}

FAILED=0
for VARIANT in "$@"; do
    VDIR="$OUT/$VARIANT"
    flyby_fresh_dir "$VDIR"

    restore_options
    if [ "$VARIANT" = "vao" ]; then
        set_vao
    fi

    if [ "$VARIANT" = "pack" ]; then
        set_shaders true "$SMOKE_PACK"
    else
        set_shaders false ""
    fi

    mkdir -p "$RUN/screenshots"
    rm -f "$RUN"/screenshots/flyby-shot-*.png
    flyby_crash_list "$RUN/crash-reports" >| "$VDIR/crashes.before"

    echo "==> $NAME/$VARIANT: launching (timeout ${SMOKE_TIMEOUT}s)"
    ./gradlew runClient25 --console=plain \
        -Dangelica.flyby.route=pan \
        -Dangelica.flyby.length=360 \
        -Dangelica.flyby.warmupTicks="$SMOKE_WARMUP_TICKS" \
        -Dangelica.flyby.exitWhenDone=true \
        -Dangelica.flyby.commands="$SCENE" \
        -Dangelica.flyby.origin="$FLYBY_ORIGIN" \
        -Dangelica.flyby.pitch="$SMOKE_PITCH" \
        -Dangelica.flyby.screenshots=8 \
        -Dangelica.flyby.debugHud="$SMOKE_DEBUG_HUD" \
        -Dangelica.flyby.crashTest=isbrh \
        -Dangelica.debug.testBlocks=true \
        -DquickPlaySingleplayer="$QUICKPLAY_WORLD" \
        $SMOKE_ARGS \
        >| "$VDIR/console.log" 2>&1 &
    PID=$!

    DEADLINE=$(( $(date +%s) + SMOKE_TIMEOUT ))
    STATUS=""
    while kill -0 "$PID" 2>/dev/null; do
        if [ "$(date +%s)" -ge "$DEADLINE" ]; then
            echo "==> $NAME/$VARIANT: timeout, killing client"
            pkill -f GradleStart || true
            kill "$PID" 2>/dev/null || true
            sleep 2
            kill -9 "$PID" 2>/dev/null || true
            STATUS="timeout"
            break
        fi
        sleep 2
    done
    RC=0
    wait "$PID" 2>/dev/null || RC=$?
    if [ -z "$STATUS" ]; then
        STATUS="$RC"
    fi

    NEW_CRASHES="$(flyby_new_crashes "$RUN/crash-reports" "$VDIR/crashes.before" "$VDIR/crashes.after")"
    flyby_collect "$RUN" "$VDIR" fml-client-latest.log latest.log

    if ! python3 "$ROOT/scripts/flyby-smoke-check.py" "$VDIR" "$STATUS" "$NEW_CRASHES" $SMOKE_CHECK_ARGS; then
        FAILED=1
    fi
done

exit "$FAILED"
