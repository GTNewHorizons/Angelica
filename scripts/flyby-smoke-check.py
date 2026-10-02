#!/usr/bin/env python3
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile

MIN_SHOT_BYTES = 20000
SHOTS_REQUIRED = 8
CRASH_MARK = "angelica isbrh crashtest"
CATCH_MARK = "Caught an exception during ISBRH rendering"

BAD_LINE = re.compile(
    r"InvalidInjectionException|InjectionError|MixinApplyError|MixinTransformerError|"
    r"InvalidMixinException|MixinPrepareError|skipped: the surrounded call was removed|"
    r"flyby scene command did not execute"
)
ANGELICA_FRAME = re.compile(
    r"com\.gtnewhorizons\.angelica|net\.coderbot|org\.embeddedt|org\.taumc|com\.prupe|"
    r"jss\.notfine|\$surround|angelica\$"
)
FRAMES_RE = re.compile(r"Flyby \S+: (\d+) frames")


def split_traces(lines):
    traces = []
    cur = None
    for i, line in enumerate(lines):
        s = line.strip()
        if s.startswith("at ") and line[:1] in ("\t", " "):
            if cur is None:
                cur = [lines[i - 1] if i > 0 else ""]
            cur.append(line)
        elif cur is not None and (s.startswith("Caused by:") or s.startswith("... ") or s.startswith("Suppressed:")):
            cur.append(line)
        else:
            if cur is not None:
                traces.append(cur)
                cur = None
    if cur is not None:
        traces.append(cur)
    return traces


def check(variant_dir, exit_status, new_crashes):
    variant = os.path.basename(os.path.normpath(variant_dir))
    reasons = []
    details = []

    if str(exit_status) != "0":
        reasons.append("exit status " + str(exit_status))
    if new_crashes > 0:
        reasons.append(str(new_crashes) + " new crash report(s)")

    log_path = os.path.join(variant_dir, "fml-client-latest.log")
    lines = []
    if os.path.isfile(log_path):
        with open(log_path, errors="replace") as f:
            lines = f.read().splitlines()
    else:
        reasons.append("fml-client-latest.log missing")

    if lines:
        if not any("Flyby complete, shutting down." in l for l in lines):
            reasons.append("missing 'Flyby complete, shutting down.'")
        frames = [int(m.group(1)) for m in (FRAMES_RE.search(l) for l in lines) if m]
        if not frames:
            reasons.append("missing flyby frames line")
        elif max(frames) == 0:
            reasons.append("flyby frames is 0")

        bad = [l for l in lines if BAD_LINE.search(l)]
        if bad:
            reasons.append(str(len(bad)) + " mixin/injection/scene failure line(s)")
            details.extend(["  " + l.strip()[:200] for l in bad[:3]])

        offending = []
        for tr in split_traces(lines):
            text = "\n".join(tr)
            if CRASH_MARK in text:
                continue
            if any(ANGELICA_FRAME.search(l) for l in tr[1:] if l.strip().startswith("at ")):
                offending.append(tr)
        if offending:
            reasons.append(str(len(offending)) + " Angelica-attributed stack trace(s)")
            for tr in offending[:3]:
                details.append("  trace:")
                details.extend("    " + l.strip()[:200] for l in tr[:3])

        caught = False
        for i, l in enumerate(lines):
            if CATCH_MARK in l and any(CRASH_MARK in x for x in lines[i:i + 2]):
                caught = True
                break
        if not caught:
            reasons.append("no ISBRH catch line for crashtest")

    shots = sorted(glob.glob(os.path.join(variant_dir, "flyby-shot-*.png")))
    if len(shots) < SHOTS_REQUIRED:
        reasons.append("only " + str(len(shots)) + " of " + str(SHOTS_REQUIRED) + " screenshots")
    small = [os.path.basename(p) for p in shots if os.path.getsize(p) < MIN_SHOT_BYTES]
    if small:
        reasons.append("small screenshot(s): " + ", ".join(small))

    if reasons:
        print("FAIL " + variant + ": " + "; ".join(reasons))
        for d in details:
            print(d)
        return 1
    print("PASS " + variant)
    return 0


GOOD_LOG = [
    "[10:00:00] [Client thread/INFO]: Flyby scene done",
    "[10:00:01] [Client thread/INFO]: Flyby pan: 1234 frames",
    "[10:00:02] [Client thread/INFO]: Flyby complete, shutting down.",
    "[10:00:03] [Worker-Main-1/WARN]: Caught an exception during ISBRH rendering for block test_isbrh",
    "java.lang.IllegalStateException: angelica isbrh crashtest",
    "\tat com.gtnewhorizons.angelica.rendering.IsbrhTestRenderer.renderWorldBlock(IsbrhTestRenderer.java:20)",
    "[10:00:04] [Client thread/ERROR]: Botania noise",
    "java.lang.NullPointerException: boot",
    "\tat vazkii.botania.Foo.bar(Foo.java:1)",
]


def make_variant(root, name, log_lines, shots=8, shot_size=30000):
    d = os.path.join(root, name)
    os.makedirs(d)
    with open(os.path.join(d, "fml-client-latest.log"), "w") as f:
        f.write("\n".join(log_lines) + "\n")
    for i in range(shots):
        with open(os.path.join(d, "flyby-shot-" + str(i) + ".png"), "wb") as f:
            f.write(b"\0" * shot_size)
    return d


def self_test():
    root = tempfile.mkdtemp()
    failures = []
    try:
        bad_trace = GOOD_LOG + [
            "[10:00:05] [Client thread/ERROR]: oops",
            "java.lang.RuntimeException: boom",
            "\tat net.minecraft.client.Foo.bar(Foo.java:1)",
            "\tat com.gtnewhorizons.angelica.mixins.Baz.qux(Baz.java:2)",
        ]
        cases = [
            ("clean", GOOD_LOG, {}, 0),
            ("angelica-trace", bad_trace, {}, 1),
            ("crashtest-allowed", GOOD_LOG, {}, 0),
            ("no-isbrh", [l for l in GOOD_LOG if CATCH_MARK not in l], {}, 1),
            ("mixin-error", GOOD_LOG + ["[10:00:05] [main/ERROR]: InvalidInjectionException: bad"], {}, 1),
            ("small-shot", GOOD_LOG, {"shot_size": 100}, 1),
        ]
        for name, log, kw, want in cases:
            d = make_variant(root, name, log, **kw)
            got = check(d, 0, 0)
            if got != want:
                failures.append(name)
        d = make_variant(root, "bad-exit", GOOD_LOG)
        if check(d, "timeout", 1) != 1:
            failures.append("bad-exit")
    finally:
        shutil.rmtree(root, ignore_errors=True)
    if failures:
        print("SELF-TEST FAILED: " + ", ".join(failures))
        return 1
    print("SELF-TEST OK")
    return 0


def main(argv):
    if len(argv) == 2 and argv[1] == "--self-test":
        return self_test()
    if len(argv) != 4:
        print("usage: flyby-smoke-check.py <variant_dir> <exit_status|timeout> <new_crash_count>", file=sys.stderr)
        print("       flyby-smoke-check.py --self-test", file=sys.stderr)
        return 2
    return check(argv[1], argv[2], int(argv[3]))


if __name__ == "__main__":
    sys.exit(main(sys.argv))
