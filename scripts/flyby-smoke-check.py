#!/usr/bin/env python3
import argparse
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
    r"flyby scene command did not execute|Grouped debug screen failed|Failed to set up the debug screen|"
    r"Debug screen entry \S+ failed and is disabled|needs the batching font renderer|"
    r"Flyby debugEntries:|Flyby: debug entry list is not available|Flyby: unknown debugProfile|"
    r"Debug screen compat entry for \S+ failed to register|"
    r"Failed to (?:load|save|set aside the unreadable) debug screen profile|toggle listeners kept changing"
)
OVERLAY_RE = re.compile(r"Flyby debug overlay shot=(\d+) mode=(\w+)(?:.* renderers=(\S*))?")
LINE_RE = re.compile(r"Flyby debug line shot=(\d+) id=(\S+) text=(.*)")
GROUP_RE = re.compile(r"Flyby debug group shot=(\d+) id=(\S+) ")
DUP_IDS = ("angelica:other_left", "angelica:other_right")
DUP_PREFIXES = ("Allocation rate:", "Java:", "GPU:", "OpenGL:", "CPU Cores:", "OS:", "LWJGL:", "IntCache:",
                "[Botania]", "[JM]", "[ChunkGen]", "hunger:", "HydroEnergy GPU RAM:", "renderDebugMode:")
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


def check_overlay(lines, opts, reasons):
    overlays = [m for m in (OVERLAY_RE.search(l) for l in lines) if m]
    mode = opts.get("expect_mode")
    if mode:
        if not overlays:
            reasons.append("no 'Flyby debug overlay' line")
        else:
            wrong = sorted({m.group(2) for m in overlays if m.group(2) != mode})
            if wrong:
                reasons.append("overlay mode " + ",".join(wrong) + " != expected " + mode)

    dbg_lines = [m for m in (LINE_RE.search(l) for l in lines) if m]
    groups = [m for m in (GROUP_RE.search(l) for l in lines) if m]
    for ident in opts.get("require_group") or []:
        if not any(m.group(2) == ident for m in groups):
            reasons.append("missing debug group " + ident)

    wanted = [r for r in (opts.get("require_renderers") or "").split(",") if r]
    if wanted:
        if not any(m.group(3) is not None and set(wanted) <= set(m.group(3).split(",")) for m in overlays):
            reasons.append("no overlay line with renderers " + ",".join(wanted))

    for name in opts.get("require_mod") or []:
        if not any(name.lower() in l.lower() for l in lines):
            reasons.append("mod " + name + " not in log")

    if opts.get("forbid_dup"):
        if not dbg_lines:
            reasons.append("no debug lines dumped")
        dups = sorted({m.group(3).lstrip().split(":")[0] + ":" for m in dbg_lines
                       if m.group(2) in DUP_IDS and m.group(3).lstrip().startswith(DUP_PREFIXES)})
        if dups:
            reasons.append("duplicate vanilla lines in " + "/".join(DUP_IDS) + ": " + ", ".join(dups))


def check(variant_dir, exit_status, new_crashes, opts=None):
    opts = opts or {}
    shots_required = opts.get("shots") or SHOTS_REQUIRED
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

        if opts.get("modpack"):
            bad = [l for i, l in enumerate(lines) if BAD_LINE.search(l)
                   and any("angelica" in x.lower() for x in lines[i:i + 4])]
        else:
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
        if not caught and not opts.get("modpack"):
            reasons.append("no ISBRH catch line for crashtest")

        check_overlay(lines, opts, reasons)

    shots = sorted(glob.glob(os.path.join(variant_dir, "flyby-shot-*.png")))
    if len(shots) < shots_required:
        reasons.append("only " + str(len(shots)) + " of " + str(shots_required) + " screenshots")
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
            ("bad-entries", GOOD_LOG + ["[10:00:06] [Client thread/ERROR]: Flyby debugEntries: unknown entry id 'test:nope'"], {}, 1),
        ]
        for name, log, kw, want in cases:
            d = make_variant(root, name, log, **kw)
            got = check(d, 0, 0)
            if got != want:
                failures.append(name)
        no_isbrh = [l for l in GOOD_LOG if CATCH_MARK not in l]
        ov = [
            "[10:00:06] [Client thread/INFO]: Flyby debug overlay shot=0 mode=OVERLAY groups=2 renderers=vanilla,angelica",
            "[10:00:06] [Client thread/INFO]: Flyby debug group shot=0 id=angelica:other_left side=L x=0 y=0 w=1 h=1 facts= lines=2",
            "[10:00:06] [Client thread/INFO]: Flyby debug line shot=0 id=angelica:other_left text=Allocation rate: 1 MB/s",
            "[10:00:06] [Client thread/INFO]: Flyby debug line shot=0 id=angelica:other_left text=Second",
            "[10:00:06] [Client thread/INFO]: Flyby debug line shot=0 id=angelica:other_right text=Free",
            "[10:00:07] [main/INFO]: Loading mod Botania",
        ]
        ov_dup = [l for l in ov if "Allocation" not in l]
        ov_dup += ["[10:00:06] [Client thread/INFO]: Flyby debug line shot=1 id=angelica:other_right text=  GPU: foo"]
        flag_cases = [
            ("modpack-ok", no_isbrh, {"modpack": True}, 0),
            ("modpack-no-flag", no_isbrh, {}, 1),
            ("modpack-foreign-bad", GOOD_LOG + ["[10:00:05] [main/ERROR]: InvalidInjectionException: x", "other", "other", "other", "angelica late"], {"modpack": True}, 0),
            ("modpack-angelica-bad", no_isbrh + ["[10:00:05] [main/ERROR]: Grouped debug screen failed", "x", "angelica y"], {"modpack": True}, 1),
            ("grouped-failed", GOOD_LOG + ["[10:00:05] [main/ERROR]: Grouped debug screen failed"], {}, 1),
            ("setup-failed", GOOD_LOG + ["[10:00:05] [Client thread/ERROR] [Angelica]: Failed to set up the debug screen; it stays disabled for this session"], {}, 1),
            ("entry-failed", GOOD_LOG + ["[10:00:05] [Client thread/ERROR] [Angelica]: Debug screen entry botania:debug failed and is disabled for the rest of this session"], {}, 1),
            ("font-fallback", GOOD_LOG + ["[10:00:05] [Client thread/ERROR] [Angelica]: Grouped debug screen needs the batching font renderer on the HUD font; using the classic layout for the rest of this session"], {}, 1),
            ("shots-ok", GOOD_LOG, {"shots": 3}, 0),
            ("shots-short", GOOD_LOG, {"shots": 9}, 1),
            ("mode-ok", GOOD_LOG + ov, {"expect_mode": "OVERLAY"}, 0),
            ("mode-wrong", GOOD_LOG + ov, {"expect_mode": "FLAT"}, 1),
            ("mode-none", GOOD_LOG, {"expect_mode": "FLAT"}, 1),
            ("group-ok", GOOD_LOG + ov, {"require_group": ["angelica:other_left"]}, 0),
            ("group-missing", GOOD_LOG + ov, {"require_group": ["angelica:nope"]}, 1),
            ("renderers-ok", GOOD_LOG + ov, {"require_renderers": "angelica,vanilla"}, 0),
            ("renderers-missing", GOOD_LOG + ov, {"require_renderers": "angelica,sodium"}, 1),
            ("mod-ok", GOOD_LOG + ov, {"require_mod": ["botania"]}, 0),
            ("mod-missing", GOOD_LOG + ov, {"require_mod": ["thaumcraft"]}, 1),
            ("dup-fail", GOOD_LOG + ov, {"forbid_dup": True}, 1),
            ("dup-ok", GOOD_LOG + ov_dup[:3] + ov_dup[4:5], {"forbid_dup": True}, 0),
            ("dup-spaces-fail", GOOD_LOG + ov_dup, {"forbid_dup": True}, 1),
            ("dup-no-dump", GOOD_LOG, {"forbid_dup": True}, 1),
        ]
        line = "[10:00:06] [Client thread/INFO]: Flyby debug line shot=1 id=angelica:other_left text=  [Botania] x"
        flag_cases.append(("dup-prefix", GOOD_LOG + ov_dup[:3] + [line], {"forbid_dup": True}, 1))
        flag_cases.append(("dup-prefix-other-id", GOOD_LOG + ov_dup[:3] + [line.replace("other_left", "other_elsewhere")], {"forbid_dup": True}, 0))
        for name, log, opts, want in flag_cases:
            d = make_variant(root, name, log, shots=3 if name == "shots-ok" else 8)
            if check(d, 0, 0, opts) != want:
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
    ap = argparse.ArgumentParser(prog="flyby-smoke-check.py")
    ap.add_argument("variant_dir")
    ap.add_argument("exit_status")
    ap.add_argument("new_crashes", type=int)
    ap.add_argument("--modpack", action="store_true")
    ap.add_argument("--shots", type=int)
    ap.add_argument("--expect-mode")
    ap.add_argument("--require-group", action="append", metavar="ID")
    ap.add_argument("--require-renderers", metavar="a,b")
    ap.add_argument("--require-mod", action="append", metavar="NAME")
    ap.add_argument("--forbid-dup", action="store_true")
    a = ap.parse_args(argv[1:])
    return check(a.variant_dir, a.exit_status, a.new_crashes, vars(a))


if __name__ == "__main__":
    sys.exit(main(sys.argv))
