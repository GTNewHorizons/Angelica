#!/usr/bin/env bash
# Unattended flyby run of a test Angelica jar inside the real Prism GTNH instance, one launch per variant
#
# Usage: scripts/flyby-prism.sh <name> <variant>...
#   Variants: sdl-off | sdl-pack | gl-off | gl-pack   (SDL-GPU vs OpenGL x shader pack off vs on)
#   gl-pack writes <pack>.txt from the pack's profile.HIGH first (never Ultra on OpenGL) and fails before launching without it.
#   Output: tmp/prism/<name>/<variant>/ (fml-client-latest.log, flyby-shot-*.png, console from the checker).
#   Prints one PASS/FAIL line per variant; exits non-zero if any fails. A run that never reaches the world
#   (no 'Flyby: world saving is off' in the log) is FAILED and stops the batch.
#   Needs a hands-off machine: the game must keep focus (waitForFocus) and runs fullscreen.
#
#   PRISM_INSTANCE  - instance id (default: GTNH-daily-2026-10-01+766-mmcprism-java17-26).
#   PRISM_BIN       - launcher binary (default: /Applications/Prism Launcher.app/Contents/MacOS/prismlauncher).
#   PRISM_TIMEOUT   - seconds before a variant's game is killed (default: 900).
#   PRISM_KEEP_JAR  - 1 leaves the test jar installed and the old angelica jar(s) disabled after the script ends (default: 0).
#   PRISM_WORLD     - save for QuickPlay (default: FlybyGTNH). No spaces.
#   PRISM_ENTRIES   - id=status,... passed as -Dangelica.flyby.debugEntries on every variant and graded with
#                     --require-renderers (default: all nine world renderers inOverlay).
#
#   Sets fullscreen:true in the instance options.txt per variant (avoids vsync pacing).
#   Restores instance.cfg, options.txt, config/shaders.properties and the pack option file on exit (cmp-verified).
#   scripts/flyby-prism.sh --helper <jvmargs|profile> ... runs one pure step in isolation.

set -euo pipefail

# shellcheck source=flyby-lib.sh
. "$(dirname "$0")/flyby-lib.sh"

HELPER="$(cat <<'PY'
import re, sys


def read(path):
    with open(path, newline="") as f:
        return f.read()


def write(path, text):
    with open(path, "w", newline="") as f:
        f.write(text)


def unquote(raw):
    if len(raw) >= 2 and raw[0] == '"' and raw[-1] == '"':
        return True, raw[1:-1].replace('\\"', '"').replace("\\\\", "\\")
    return False, raw


def requote(value, quoted):
    if quoted or re.search(r"[\s,;\"]", value):
        return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return value


def key_of(token):
    return token.split("=", 1)[0]


def jvmargs(cfg, original, backend, extra):
    src = read(original)
    m = re.search(r"^JvmArgs=([^\r\n]*)", src, re.M)
    quoted, value = unquote(m.group(1)) if m else (True, "")
    new = ["-Dangelica.sdlgpu.enable=" + backend] + extra
    for t in new:
        if re.search(r"\s", t) or '"' in t:
            raise SystemExit("error: jvm arg contains whitespace or a quote: " + repr(t))
    drop = {key_of(t) for t in new}
    kept = [t for t in value.split() if key_of(t) not in drop]
    line = "JvmArgs=" + requote(" ".join(kept + new), quoted)
    text = read(cfg)
    if re.search(r"^JvmArgs=[^\r\n]*", text, re.M):
        text = re.sub(r"^JvmArgs=[^\r\n]*", lambda _: line, text, count=1, flags=re.M)
    elif re.search(r"^\[General\][^\r\n]*", text, re.M):
        text = re.sub(r"^(\[General\][^\r\n]*)(\r?\n)", lambda g: g.group(1) + g.group(2) + line + g.group(2), text, count=1, flags=re.M)
    else:
        text = line + "\n" + text
    if re.search(r"^OverrideJavaArgs=[^\r\n]*", text, re.M):
        text = re.sub(r"^OverrideJavaArgs=[^\r\n]*", "OverrideJavaArgs=true", text, count=1, flags=re.M)
    else:
        text = re.sub(r"^(JvmArgs=[^\r\n]*)(\r?\n|$)", lambda g: g.group(1) + (g.group(2) or "\n") + "OverrideJavaArgs=true" + g.group(2), text, count=1, flags=re.M)
    write(cfg, text)
    print(line)


def logical_lines(text):
    out = []
    buf = ""
    for raw in text.splitlines():
        if buf:
            piece = raw.strip()
        else:
            piece = raw.rstrip()
        if piece.endswith("\\"):
            buf += piece[:-1] + " "
            continue
        out.append(buf + piece)
        buf = ""
    if buf:
        out.append(buf)
    return out


def profile_tokens(defs, name, seen):
    if name in seen:
        raise SystemExit("error: profile cycle at " + name)
    if name not in defs:
        raise SystemExit("error: profile." + name + " not defined")
    out = []
    for tok in defs[name].split():
        if tok.startswith("profile."):
            out.extend(profile_tokens(defs, tok[len("profile."):], seen | {name}))
        else:
            out.append(tok)
    return out


def profile(props, out_txt, name):
    defs = {}
    for line in logical_lines(read(props)):
        s = line.strip()
        if s.startswith("#") or s.startswith("!"):
            continue
        m = re.match(r"profile\.(\w+)\s*=(.*)$", s)
        if m:
            defs[m.group(1)] = m.group(2)
    if name not in defs:
        raise SystemExit("error: profile." + name + " not found in " + props)
    options = {}
    for tok in profile_tokens(defs, name, frozenset()):
        if "=" in tok:
            k, v = tok.split("=", 1)
        elif tok.startswith("!"):
            k, v = tok[1:], "false"
        else:
            k, v = tok, "true"
        if not re.match(r"^[A-Za-z_][A-Za-z0-9_.]*$", k) or v == "":
            raise SystemExit("error: cannot parse profile token " + repr(tok))
        options[k] = v
    if not options:
        raise SystemExit("error: profile." + name + " is empty")
    write(out_txt, "".join(k + "=" + v + "\n" for k, v in options.items()))
    print(len(options))


def main(argv):
    cmd = argv[1]
    if cmd == "jvmargs":
        jvmargs(argv[2], argv[3], argv[4], argv[5:])
    elif cmd == "profile":
        profile(argv[2], argv[3], argv[4] if len(argv) > 4 else "HIGH")
    else:
        raise SystemExit("error: unknown helper " + cmd)


main(sys.argv)
PY
)"

helper() {
    python3 -c "$HELPER" "$@"
}

if [ "${1:-}" = "--helper" ]; then
    shift
    helper "$@"
    exit $?
fi

NAME="${1:?usage: flyby-prism.sh <name> <variant>...}"
shift
if [ $# -eq 0 ]; then
    echo "usage: flyby-prism.sh <name> <sdl-off|sdl-pack|gl-off|gl-pack>..." >&2
    exit 2
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PRISM_INSTANCE="${PRISM_INSTANCE:-GTNH-daily-2026-10-01+766-mmcprism-java17-26}"
PRISM_BIN="${PRISM_BIN:-/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher}"
PRISM_TIMEOUT="${PRISM_TIMEOUT:-900}"
PRISM_KEEP_JAR="${PRISM_KEEP_JAR:-0}"
PRISM_WORLD="${PRISM_WORLD:-FlybyGTNH}"
PRISM_ENTRIES="${PRISM_ENTRIES:-minecraft:chunk_borders=inOverlay,minecraft:chunk_section_visibility=inOverlay,minecraft:visualize_block_light_levels=inOverlay,minecraft:visualize_sky_light_levels=inOverlay,minecraft:visualize_heightmap=inOverlay,minecraft:visualize_collision_boxes=inOverlay,minecraft:visualize_solid_faces=inOverlay,minecraft:visualize_water_levels=inOverlay,minecraft:visualize_entity_supporting_block=inOverlay}"

for v in "$@"; do
    case "$v" in
        sdl-off|sdl-pack|gl-off|gl-pack) ;;
        *) echo "error: variant must be sdl-off, sdl-pack, gl-off or gl-pack, got: $v" >&2; exit 2 ;;
    esac
done
case "$PRISM_WORLD" in
    *[[:space:]\"]*|"") echo "error: PRISM_WORLD must be non-empty without spaces or quotes: '$PRISM_WORLD'" >&2; exit 2 ;;
esac
case "$PRISM_ENTRIES" in
    *[[:space:]\"]*) echo "error: PRISM_ENTRIES must not contain spaces or quotes" >&2; exit 2 ;;
esac

INST="$HOME/Library/Application Support/PrismLauncher/instances/$PRISM_INSTANCE"
MC="$INST/.minecraft"
CFG="$INST/instance.cfg"
OPTS="$MC/options.txt"
SHADERS="$MC/config/shaders.properties"
for f in "$CFG" "$OPTS" "$SHADERS"; do
    if [ ! -f "$f" ]; then
        echo "error: missing $f" >&2
        exit 1
    fi
done
if [ ! -x "$PRISM_BIN" ]; then
    echo "error: launcher not executable: $PRISM_BIN" >&2
    exit 1
fi

INST_TAIL="${PRISM_INSTANCE#*+}"
GAME_PAT="java.*$INST_TAIL"
if pgrep -x prismlauncher >/dev/null 2>&1; then
    echo "error: Prism Launcher is already running; quit it first" >&2
    exit 1
fi
if pgrep -f "$GAME_PAT" >/dev/null 2>&1; then
    echo "error: a process matching '$GAME_PAT' (the instance's game?) is running" >&2
    exit 1
fi
if pgrep -f 'GradleStart|retrofuturabootstrap' >/dev/null 2>&1; then
    echo "error: a dev client is running" >&2
    exit 1
fi

./gradlew assemble
JAR=""
for f in "$ROOT"/build/libs/angelica-*.jar; do
    case "$f" in
        *-dev.jar|*-sources.jar|*-api.jar|*-dev-preshadow.jar|*-predowngrade.jar|*shadow-stripped.jar) continue ;;
    esac
    if [ -f "$f" ] && { [ -z "$JAR" ] || [ "$f" -nt "$JAR" ]; }; then
        JAR="$f"
    fi
done
if [ -z "$JAR" ]; then
    echo "error: no production angelica jar in build/libs" >&2
    exit 1
fi
JAR_NAME="$(basename "$JAR")"
echo "==> test jar: $JAR"

OUT="$ROOT/tmp/prism/$NAME"
BAK="$OUT/backup"

PACK="$(awk '/^shaderPack=/ { sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit }' "$SHADERS")"
PACK_TXT=""
if [ -n "$PACK" ]; then
    PACK_TXT="$MC/shaderpacks/$PACK.txt"
fi
for v in "$@"; do
    if [ "$v" = "gl-pack" ] || [ "$v" = "sdl-pack" ]; then
        if [ -z "$PACK" ] || [ ! -d "$MC/shaderpacks/$PACK" ]; then
            echo "error: shaderPack '$PACK' missing from $MC/shaderpacks" >&2
            exit 1
        fi
    fi
done

STALE_BAK=""
if [ -d "$BAK" ]; then
    STALE_BAK="$(find "$BAK" -mindepth 1 -maxdepth 1 | head -n 1)"
fi
STALE_DISABLED=""
for f in "$MC"/mods/angelica-*.jar.disabled; do
    if [ -e "$f" ]; then
        STALE_DISABLED="$STALE_DISABLED $f"
    fi
done
if [ -n "$STALE_BAK" ] || [ -n "$STALE_DISABLED" ]; then
    echo "error: leftovers from an interrupted run; nothing was touched" >&2
    echo "undo steps:" >&2
    echo "  1. copy back from $BAK (if present): instance.cfg -> $CFG, options.txt -> $OPTS, shaders.properties -> $SHADERS, pack.txt -> ${PACK_TXT:-n/a}" >&2
    echo "  2. remove the installed test jar from $MC/mods (originals are listed in $BAK/renamed.list)" >&2
    echo "  3. drop the .disabled suffix from:${STALE_DISABLED:- (none found)}" >&2
    echo "  4. rm -rf '$BAK'" >&2
    exit 1
fi
mkdir -p "$BAK"

flyby_copy "$CFG" "$BAK/instance.cfg"
flyby_copy "$OPTS" "$BAK/options.txt"
flyby_copy "$SHADERS" "$BAK/shaders.properties"
if [ -n "$PACK_TXT" ]; then
    flyby_backup_file "$PACK_TXT" "$BAK/pack.txt"
fi

RENAMED="$BAK/renamed.list"
: >| "$RENAMED"

PRISM_PID=""
GAME_PID=""

kill_game() {
    local pids
    pids="$(pgrep -f "$GAME_PAT" || true)"
    if [ -n "$pids" ]; then
        # shellcheck disable=SC2086
        kill $pids 2>/dev/null || true
        sleep 3
        pids="$(pgrep -f "$GAME_PAT" || true)"
        if [ -n "$pids" ]; then
            # shellcheck disable=SC2086
            kill -9 $pids 2>/dev/null || true
        fi
    fi
    if [ -n "$PRISM_PID" ] && kill -0 "$PRISM_PID" 2>/dev/null; then
        kill "$PRISM_PID" 2>/dev/null || true
        sleep 1
        kill -9 "$PRISM_PID" 2>/dev/null || true
    fi
}

RESTORED=0
restore() {
    set +e
    if [ "$RESTORED" = "1" ]; then
        return 0
    fi
    RESTORED=1
    trap - EXIT INT TERM HUP
    kill_game
    local ok=1
    flyby_restore_file "$BAK/instance.cfg" "$CFG" || ok=0
    flyby_restore_file "$BAK/options.txt" "$OPTS" || ok=0
    flyby_restore_file "$BAK/shaders.properties" "$SHADERS" || ok=0
    if [ -n "$PACK_TXT" ]; then
        flyby_restore_file "$BAK/pack.txt" "$PACK_TXT" || ok=0
    fi
    if [ "$PRISM_KEEP_JAR" != "1" ]; then
        rm -f "$MC/mods/$JAR_NAME" || { echo "warning: could not remove $MC/mods/$JAR_NAME" >&2; ok=0; }
        while IFS= read -r f; do
            if [ -n "$f" ] && [ -f "$f.disabled" ]; then
                mv -f "$f.disabled" "$f" || { echo "warning: could not re-enable $f" >&2; ok=0; }
            fi
        done < "$RENAMED"
        if [ "$ok" = "1" ]; then
            rm -rf "$BAK"
        fi
        echo "==> test jar removed, original angelica jar(s) re-enabled"
    else
        echo "==> test jar LEFT installed: $MC/mods/$JAR_NAME"
        echo "==> to undo: rm -f '$MC/mods/$JAR_NAME'; then drop the .disabled suffix from: $(tr '\n' ' ' < "$RENAMED")"
    fi
}
trap restore EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

for f in "$MC"/mods/angelica-*.jar; do
    if [ -f "$f" ]; then
        printf '%s\n' "$f" >> "$RENAMED"
        mv -n "$f" "$f.disabled"
    fi
done
flyby_copy "$JAR" "$MC/mods/$JAR_NAME"
echo "==> installed $JAR_NAME; disabled: $(tr '\n' ' ' < "$RENAMED")"

game_pid() {
    pgrep -f "$GAME_PAT" | head -n 1 || true
}

FAILED=0
for VARIANT in "$@"; do
    VDIR="$OUT/$VARIANT"
    flyby_fresh_dir "$VDIR"

    case "$VARIANT" in
        sdl-*) BACKEND=true ;;
        *) BACKEND=false ;;
    esac
    case "$VARIANT" in
        *-pack) SHADERS_ON=true ;;
        *) SHADERS_ON=false ;;
    esac

    EXTRA=(
        -Dangelica.debug.tooling=true
        -Dangelica.flyby.route=static
        -Dangelica.flyby.length=400
        -Dangelica.flyby.warmupTicks=200
        -Dangelica.flyby.exitWhenDone=true
        -Dangelica.flyby.waitForFocus=true
        -Dangelica.flyby.screenshots=4
        -Dangelica.flyby.debugHud=true
        -Dangelica.flyby.debugDump=true
        "-DquickPlaySingleplayer=$PRISM_WORLD"
    )
    CHECK=(--modpack --shots 4 --expect-mode OVERLAY --require-group botania:botania --require-group journeymap:journeymap --forbid-dup --require-mod etfuturum)
    EXTRA+=("-Dangelica.flyby.debugEntries=$PRISM_ENTRIES")
    RENDERERS="$(printf '%s\n' "$PRISM_ENTRIES" | tr ',' '\n' | sed -e '/^$/d' -e 's/=[^=]*$//' | paste -sd, -)"
    CHECK+=(--require-renderers "$RENDERERS")

    flyby_restore_file "$BAK/instance.cfg" "$CFG"
    echo "==> $NAME/$VARIANT: JvmArgs"
    helper jvmargs "$CFG" "$BAK/instance.cfg" "$BACKEND" "${EXTRA[@]}" | tee "$VDIR/jvmargs.txt"

    flyby_restore_file "$BAK/shaders.properties" "$SHADERS"
    flyby_set_key "$SHADERS" enableShaders "$SHADERS_ON" =

    if [ -n "$PACK_TXT" ]; then
        flyby_restore_file "$BAK/pack.txt" "$PACK_TXT"
    fi
    if [ "$VARIANT" = "gl-pack" ]; then
        PROPS="$MC/shaderpacks/$PACK/shaders/shaders.properties"
        if [ ! -f "$PROPS" ] || ! COUNT="$(helper profile "$PROPS" "$PACK_TXT" HIGH)"; then
            echo "FAIL $VARIANT: pack profile.HIGH could not be read from $PROPS; not launching"
            FAILED=1
            continue
        fi
        echo "==> $NAME/$VARIANT: wrote $COUNT HIGH options to $PACK_TXT"
    fi

    flyby_restore_file "$BAK/options.txt" "$OPTS"
    flyby_set_key "$OPTS" fullscreen true :

    mkdir -p "$MC/screenshots"
    rm -f "$MC"/screenshots/flyby-shot-*.png "$MC/logs/fml-client-latest.log"
    flyby_crash_list "$MC/crash-reports" >| "$VDIR/crashes.before"

    echo "==> $NAME/$VARIANT: launching (timeout ${PRISM_TIMEOUT}s)"
    "$PRISM_BIN" -l "$PRISM_INSTANCE" >| "$VDIR/launcher.log" 2>&1 &
    PRISM_PID=$!

    GAME_PID=""
    WAITED=0
    while [ "$WAITED" -lt 240 ]; do
        GAME_PID="$(game_pid)"
        if [ -n "$GAME_PID" ]; then
            break
        fi
        if ! kill -0 "$PRISM_PID" 2>/dev/null; then
            break
        fi
        sleep 2
        WAITED=$((WAITED + 2))
    done

    STATUS="0"
    if [ -z "$GAME_PID" ]; then
        STATUS="nogame"
        echo "==> $NAME/$VARIANT: no game process appeared within ${WAITED}s"
    else
        DEADLINE=$(( $(date +%s) + PRISM_TIMEOUT ))
        while kill -0 "$GAME_PID" 2>/dev/null; do
            if [ "$(date +%s)" -ge "$DEADLINE" ]; then
                echo "==> $NAME/$VARIANT: timeout, killing game"
                STATUS="timeout"
                break
            fi
            sleep 2
        done
    fi
    kill_game
    PRISM_PID=""
    sleep 2

    NEW_CRASHES="$(flyby_new_crashes "$MC/crash-reports" "$VDIR/crashes.before" "$VDIR/crashes.after")"
    flyby_collect "$MC" "$VDIR" fml-client-latest.log

    if ! grep -qF "Flyby: world saving is off" "$VDIR/fml-client-latest.log" 2>/dev/null; then
        echo "FAIL $VARIANT: never entered the world (no 'Flyby: world saving is off' in the log; QuickPlay did not fire?); stopping the batch"
        FAILED=1
        break
    fi

    if [ "$STATUS" = "nogame" ]; then
        STATUS="1"
    fi
    if ! python3 "$ROOT/scripts/flyby-smoke-check.py" "$VDIR" "$STATUS" "$NEW_CRASHES" "${CHECK[@]}"; then
        FAILED=1
    fi
done

exit "$FAILED"
