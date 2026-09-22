#!/usr/bin/env python3
import csv
import io
import os
import subprocess
import sys

CSVEXPORT = os.environ.get("TRACY_CSVEXPORT", os.path.expanduser("~/dev/mc/tracy/csvexport/build/tracy-csvexport"))

PLOTS = [
    "gl.drawCalls", "gl.listPlaybacks", "gl.streamDraws", "entitiesRendered", "blockEntitiesRendered", "tesr.modelParts", "tesr.retainedDraws", "tesr.cacheMisses", "tesr.instancedDraws", "tesr.instancedInstances", "tesr.cubeInstances",
    "tesr.streamedInstances", "tesr.texMatrixRuns", "tesr.liveFallbacks", "tesr.bail.foreignProgram",
    "tesr.bail.material", "tesr.bail.texture", "entity.shadowQuads", "entity.shadowDraws", "items.instanced", "items.fallback", "items.glintInstanced",
    "items.bail.ineligible", "items.bail.material", "items.bail.isbrh", "items.bail.blockState", "items.bail.notAllowed", "items.bail.template", "items.bail.queue",
    "particles.direct", "particles.captured", "particles.spilled", "particles.undecodable", "particles.draws", "sdl.renderPasses", "sdl.submits", "sdl.gpuWaitUs",
    "sdl.acquireWaitUs",
    "weather.strength", "weather.quads", "weather.drawCalls", "weather.micros",
]
ZONES = ["entityModelParts", "tesrBatch", "tesrInstanced", "entityLayerLoop", "tesrOpaque", "particlePass", "cl:weather"]
CLOCK = "gl.drawCalls"


def run(args):
    out = subprocess.run([CSVEXPORT] + args, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(out.stderr.strip() or f"csvexport failed: {' '.join(args)}")
    return list(csv.DictReader(io.StringIO(out.stdout)))


def windows(path):
    marks = []
    for row in run(["-m", path]):
        name = row["MessageName"]
        if name.startswith("flyby leg ") or name.startswith("flyby turn ") or name.startswith("flyby end"):
            marks.append((int(row["total_ns"]), name))
    marks.sort()
    result = {}
    for i, (t, name) in enumerate(marks):
        if not name.startswith("flyby leg "):
            continue
        leg = int(name.split()[-1])
        if leg in result:
            continue
        end = marks[i + 1][0] if i + 1 < len(marks) else None
        result[leg] = (t, end)
    return result


def inside(t, window):
    start, end = window
    return t >= start and (end is None or t < end)


def stats(values):
    if not values:
        return None
    s = sorted(values)
    n = len(s)
    return (sum(s) / n, s[n // 2], s[min(n - 1, int(n * 0.99))], s[-1], n)


def analyze(path):
    legs = windows(path)
    plots = {name: {leg: [] for leg in legs} for name in PLOTS + ["Frame"]}
    last_clock = None
    for row in run(["-u", "-p", "-f", "zzz-no-such-zone", path]):
        name = row["name"]
        if name not in plots:
            continue
        t = int(row["ns_since_start"])
        if name == CLOCK:
            if last_clock is not None:
                for leg, window in legs.items():
                    if inside(t, window):
                        plots["Frame"][leg].append((t - last_clock) / 1e6)
                        break
            last_clock = t
        for leg, window in legs.items():
            if inside(t, window):
                plots[name][leg].append(float(row["value"]))
                break
    zones = {name: {leg: [] for leg in legs} for name in ZONES}
    for name in ZONES:
        for row in run(["-u", "-c", "-f", name, path]):
            if row["name"] != name:
                continue
            t = int(row["ns_since_start"])
            for leg, window in legs.items():
                if inside(t, window):
                    zones[name][leg].append(int(row["exec_time_ns"]))
                    break
    return legs, plots, zones


def fmt(v, digits=1):
    if v is None:
        return "-"
    if isinstance(v, float):
        return f"{v:.{digits}f}"
    return str(v)


def main(paths):
    labels = [os.path.splitext(os.path.basename(p))[0] for p in paths]
    data = [analyze(p) for p in paths]
    all_legs = sorted({leg for legs, _, _ in data for leg in legs})
    width = max(12, max(len(l) for l in labels) + 2)
    for leg in all_legs:
        print(f"\n== leg {leg}")
        print("metric".ljust(28) + "".join(l.rjust(width) for l in labels))

        def line(metric, cells):
            print(metric.ljust(28) + "".join(fmt(c).rjust(width) for c in cells))

        frame = [stats(plots["Frame"].get(leg, [])) for _, plots, _ in data]
        line("frames", [f[4] if f else None for f in frame])
        line("frame avg ms", [f[0] if f else None for f in frame])
        line("frame p50 ms", [f[1] if f else None for f in frame])
        line("frame p99 ms", [f[2] if f else None for f in frame])
        line("frame max ms", [f[3] if f else None for f in frame])
        busy = []
        for (_, plots, _), f in zip(data, frame):
            acquire = plots["sdl.acquireWaitUs"].get(leg, [])
            gpu = plots["sdl.gpuWaitUs"].get(leg, [])
            if f and acquire:
                busy.append(f[0] - sum(acquire) / len(acquire) / 1000.0 - (sum(gpu) / len(gpu) / 1000.0 if gpu else 0.0))
            else:
                busy.append(None)
        line("busy avg ms", busy)
        for name in PLOTS:
            per = []
            for _, plots, _ in data:
                v = plots[name].get(leg, [])
                per.append(sum(v) / len(v) if v else None)
            if all(c is None for c in per):
                continue
            line(name + " /frame", per)
        for name in ZONES:
            per = []
            for legs, _, zones in data:
                v = zones[name].get(leg, [])
                frames = len(plots_frames(data, legs, leg))
                per.append((sum(v) / 1000.0 / frames) if v and frames else None)
            if all(c is None for c in per):
                continue
            line(name + " us/frame", per)


def plots_frames(data, legs, leg):
    for l, plots, _ in data:
        if l is legs:
            return plots["Frame"].get(leg, [])
    return []


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: flyby-compare.py <capture.tracy> [more captures...]")
    main(sys.argv[1:])
