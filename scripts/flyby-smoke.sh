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
#   SMOKE_PACK      - shaderpack name for the pack variant (default: ComplementaryUnbound_r5.7.1 + EuphoriaPatches_1.8.1).
#
#   Runs from this script's own checkout. Restores run/client/config/shaders.properties and angelica-options.json on exit.

set -euo pipefail

NAME="${1:?usage: flyby-smoke.sh <name> [ffp|pack|vao]...}"
shift
if [ $# -eq 0 ]; then
    set -- ffp pack vao
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

QUICKPLAY_WORLD="${QUICKPLAY_WORLD:-Flat World - No Mods}"
FLYBY_ORIGIN="${FLYBY_ORIGIN:-1509.5,-924.5,0}"
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-600}"
SMOKE_PACK="${SMOKE_PACK:-ComplementaryUnbound_r5.7.1 + EuphoriaPatches_1.8.1}"

SCENE="$ROOT/scripts/flyby-scenes/surround-smoke.txt"
RUN="$ROOT/run/client"
SHADERS="$RUN/config/shaders.properties"
OUT="$ROOT/tmp/smoke/$NAME"
BACKUP="$OUT/shaders.properties.bak"
OPTIONS="$RUN/config/angelica-options.json"
OPTIONS_BACKUP="$OUT/angelica-options.json.bak"

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
python3 - "$SHADERS" "$BACKUP" <<'PY'
import shutil, sys
shutil.copyfile(sys.argv[1], sys.argv[2])
PY
python3 - "$OPTIONS" "$OPTIONS_BACKUP" <<'PY'
import shutil, sys
shutil.copyfile(sys.argv[1], sys.argv[2])
PY

restore_options() {
    python3 - "$OPTIONS_BACKUP" "$OPTIONS" <<'PY' || true
import shutil, sys
shutil.copyfile(sys.argv[1], sys.argv[2])
PY
}

restore() {
    python3 - "$BACKUP" "$SHADERS" <<'PY' || true
import shutil, sys
shutil.copyfile(sys.argv[1], sys.argv[2])
PY
    restore_options
}
trap restore EXIT

set_shaders() {
    python3 - "$SHADERS" "$1" "$2" <<'PY'
import re, sys
path, enable, pack = sys.argv[1:4]
text = open(path).read()
def put(text, key, value):
    pat = re.compile(r'^' + key + r'=.*$', re.M)
    if pat.search(text):
        return pat.sub(lambda m: key + '=' + value, text)
    if text and not text.endswith('\n'):
        text += '\n'
    return text + key + '=' + value + '\n'
text = put(text, 'enableShaders', enable)
if pack:
    text = put(text, 'shaderPack', pack)
open(path, 'w').write(text)
PY
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

crash_list() {
    if [ -d "$RUN/crash-reports" ]; then
        ls "$RUN/crash-reports" | sort
    fi
}

FAILED=0
for VARIANT in "$@"; do
    VDIR="$OUT/$VARIANT"
    python3 - "$VDIR" <<'PY'
import shutil, sys
shutil.rmtree(sys.argv[1], ignore_errors=True)
PY
    mkdir -p "$VDIR"

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
    crash_list >| "$VDIR/crashes.before"

    echo "==> $NAME/$VARIANT: launching (timeout ${SMOKE_TIMEOUT}s)"
    ./gradlew runClient25 --console=plain \
        -Dangelica.flyby.route=pan \
        -Dangelica.flyby.length=360 \
        -Dangelica.flyby.warmupTicks=100 \
        -Dangelica.flyby.exitWhenDone=true \
        -Dangelica.flyby.commands="$SCENE" \
        -Dangelica.flyby.origin="$FLYBY_ORIGIN" \
        -Dangelica.flyby.pitch=15 \
        -Dangelica.flyby.screenshots=8 \
        -Dangelica.flyby.debugHud=true \
        -Dangelica.flyby.crashTest=isbrh \
        -Dangelica.debug.testBlocks=true \
        -DquickPlaySingleplayer="$QUICKPLAY_WORLD" \
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

    crash_list >| "$VDIR/crashes.after"
    NEW_CRASHES="$(comm -13 "$VDIR/crashes.before" "$VDIR/crashes.after" | wc -l | tr -d ' ')"

    for f in fml-client-latest.log latest.log; do
        if [ -f "$RUN/logs/$f" ]; then
            cp -f "$RUN/logs/$f" "$VDIR/$f"
        fi
    done
    for f in "$RUN"/screenshots/flyby-shot-*.png; do
        if [ -f "$f" ]; then
            cp -f "$f" "$VDIR/"
        fi
    done

    if ! python3 "$ROOT/scripts/flyby-smoke-check.py" "$VDIR" "$STATUS" "$NEW_CRASHES"; then
        FAILED=1
    fi
done

exit "$FAILED"
