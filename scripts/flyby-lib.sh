#!/usr/bin/env bash
# Shared file helpers for the flyby-*.sh scripts. Sourced, not executed: . "$(dirname "$0")/flyby-lib.sh"
#
#   flyby_set_key <file> <key> <value> [sep]   replace the first line starting with <key><sep> (default sep ":"), else append; creates the file
#   flyby_copy <src> <dst>                     copy bytes and verify with a compare
#   flyby_backup_file <file> <backup>          copy to <backup>; if <file> is absent, remove any stale <backup> (records absence)
#   flyby_restore_file <backup> <file>         flyby_backup_file with swapped arguments; prints one error line and returns 1 on failure.
#                                              Trap callers append '|| true'. Removes <file> if <backup> is absent: only call it after the backup was made.
#   flyby_fullscreen_on <options.txt> <backup>       backs up, sets fullscreen:true
#   flyby_fullscreen_restore <options.txt> <backup>  restores the backup; idempotent, call only after flyby_fullscreen_on
#   flyby_fresh_dir <dir>                      remove <dir> and recreate it empty
#   flyby_collect <run_dir> <variant_dir> <log>...   copy <run_dir>/logs/<log> and <run_dir>/screenshots/flyby-shot-*.png into <variant_dir>
#   flyby_crash_list <dir>                     sorted names in <dir>, nothing if it is absent
#   flyby_new_crashes <dir> <before> <after>   write the current crash list to <after>, print how many names are not in <before>

flyby_set_key() {
    python3 - "$1" "$2" "$3" "${4:-:}" <<'PY'
import os, re, sys
path, key, value, sep = sys.argv[1:5]
text = ""
if os.path.isfile(path):
    with open(path, newline="") as f:
        text = f.read()
pat = re.compile(r"^" + re.escape(key + sep) + r"[^\r\n]*", re.M)
line = key + sep + value
if pat.search(text):
    text = pat.sub(lambda _: line, text, count=1)
else:
    if text and not text.endswith("\n"):
        text += "\n"
    text += line + "\n"
with open(path, "w", newline="") as f:
    f.write(text)
PY
}

flyby_copy() {
    python3 - "$1" "$2" <<'PY'
import filecmp, shutil, sys
src, dst = sys.argv[1:3]
shutil.copyfile(src, dst)
if not filecmp.cmp(src, dst, shallow=False):
    sys.exit("error: copy differs: " + dst)
PY
}

flyby_backup_file() {
    python3 - "$1" "$2" <<'PY'
import filecmp, os, shutil, sys
path, backup = sys.argv[1:3]
if os.path.isfile(path):
    shutil.copyfile(path, backup)
    if not filecmp.cmp(path, backup, shallow=False):
        sys.exit("error: backup differs: " + backup)
elif os.path.isfile(backup):
    os.remove(backup)
PY
}

flyby_restore_file() {
    flyby_backup_file "$1" "$2" || { echo "error: restore failed or differs: $2" >&2; return 1; }
}

flyby_fullscreen_on() {
    flyby_backup_file "$1" "$2"
    flyby_set_key "$1" fullscreen true :
}

flyby_fullscreen_restore() {
    flyby_restore_file "$2" "$1"
}

flyby_fresh_dir() {
    python3 -c 'import shutil, sys; shutil.rmtree(sys.argv[1], ignore_errors=True)' "$1"
    mkdir -p "$1"
}

flyby_collect() {
    python3 - "$@" <<'PY'
import glob, os, shutil, sys
run, vdir = sys.argv[1:3]
for name in sys.argv[3:]:
    src = os.path.join(run, "logs", name)
    if os.path.isfile(src):
        shutil.copyfile(src, os.path.join(vdir, name))
for src in glob.glob(os.path.join(run, "screenshots", "flyby-shot-*.png")):
    shutil.copyfile(src, os.path.join(vdir, os.path.basename(src)))
PY
}

flyby_crash_list() {
    if [ -d "$1" ]; then
        ls -1 "$1" | sort
    fi
}

flyby_new_crashes() {
    flyby_crash_list "$1" >| "$3"
    comm -13 "$2" "$3" | wc -l | tr -d ' '
}
