#!/usr/bin/env python3
"""对已有 Photon 配对帧和 JFR JSON 做离线分析，不连接 JVM。"""
import argparse
from bisect import bisect_right
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal
import json
import math
from pathlib import Path
import re

NS_PER_SECOND = 1_000_000_000
SLOW_NS = 50_000_000
CATEGORIES = {
    "collision": ("collid", "collision", "VoxelShape", ".clip"),
    "curve": ("Curve", "curve", "Bezier", "bezier"),
    "transparent_sort": ("PhotonViewDepthSortUtil.reorder", "MeshData.sortQuads", "VertexSorting."),
    "vertex_submission": ("BufferBuilder", "VertexConsumer", "MeshData",
                          "drawElements", "glDraw", "glBuffer", "BufferUploader"),
    "particle_simulation": ("TileParticle.update", "TileParticle.tick",
                            "ParticleEmitter.update", "ParticleEmitter.emitParticle", "ParticlePhysics"),
    "emitter_tick_inclusive": ("Emitter.updateTick", "FXObject.tick"),
    "child_spawn_inclusive": ("PhotonChildSpawner.emitNow", "PhotonSpawnTemplateUtil.create"),
}


def epoch_ns(value):
    """保留 ISO 时间的纳秒小数，不经浮点 epoch。"""
    match = re.fullmatch(r"(.*T\d\d:\d\d:\d\d)(?:\.(\d+))?(Z|[+-]\d\d:\d\d)", value)
    if not match:
        raise ValueError("需要带时区的 ISO 时间: " + value)
    base, fraction, zone = match.groups()
    stamp = datetime.fromisoformat(base + ("+00:00" if zone == "Z" else zone))
    return int(stamp.timestamp()) * NS_PER_SECOND + int((fraction or "").ljust(9, "0")[:9])


def duration_ns(value):
    match = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?", value)
    if not match:
        raise ValueError("不支持的 JFR duration: " + value)
    hours, minutes, seconds = match.groups()
    return int((Decimal(hours or 0) * 3600 + Decimal(minutes or 0) * 60
                + Decimal(seconds or 0)) * NS_PER_SECOND)


def iter_jfr(path):
    """流式读取 jfr print --json，或已压缩的逐事件 JSONL。"""
    with Path(path).open(encoding="utf-8-sig") as stream:
        first = stream.readline()
        if '"type":' in first and '"recording"' not in first:
            yield json.loads(first)
            for line in stream:
                if line.strip():
                    yield json.loads(line)
            return
        buffer = first
        marker = re.compile(r'"events"\s*:\s*\[')
        while not (match := marker.search(buffer)):
            chunk = stream.read(65536)
            if not chunk:
                raise ValueError("缺少 recording.events")
            buffer += chunk
        buffer = buffer[match.end():]
        decoder = json.JSONDecoder()
        while True:
            buffer = buffer.lstrip(" \r\n\t,")
            if buffer.startswith("]"):
                return
            try:
                event, end = decoder.raw_decode(buffer)
            except json.JSONDecodeError:
                chunk = stream.read(1048576)
                if not chunk:
                    raise ValueError("JFR JSON 未完整结束") from None
                buffer += chunk
                continue
            yield event
            buffer = buffer[end:]


def stack_names(values):
    if "frames" in values:
        return values["frames"]
    return [frame["method"]["type"]["name"] + "." + frame["method"]["name"]
            for frame in (values.get("stackTrace") or {}).get("frames", [])]


def overlap(start, end, other_start, other_end):
    return max(0, min(end, other_end) - max(start, other_start))


def union_ns(intervals):
    total = 0
    end = None
    for left, right in sorted(intervals):
        if end is None or left > end:
            total += right - left
            end = right
        elif right > end:
            total += right - end
            end = right
    return total


def summary(values):
    values = sorted(v for v in values if isinstance(v, (int, float))
                    and not isinstance(v, bool) and math.isfinite(v) and v >= 0)
    if not values:
        return {"count": 0}
    return {"count": len(values), "sum": sum(values), "mean": sum(values) / len(values),
            **{name: values[math.ceil(len(values) * q) - 1]
               for name, q in (("p50", .5), ("p99", .99), ("max", 1))}}


def analyze(run, events, threshold_ns=SLOW_NS, uncertainty_ns=2_000_000):
    capture = run["capture"]
    anchor = capture["summary"]
    offset = epoch_ns(anchor["started_at"]) - anchor["started_ns"]
    records = [row for row in capture["records"] if row.get("interval_ns") is not None
               and row.get("start_ns") is not None and row.get("end_ns") is not None]
    records.sort(key=lambda row: row["start_ns"])
    if not records:
        raise ValueError("没有有效配对帧")
    for before, after in zip(records, records[1:]):
        if before["end_ns"] > after["start_ns"]:
            raise ValueError("配对帧重叠")
    starts = [row["start_ns"] for row in records]
    window_start, window_end = starts[0], records[-1]["end_ns"]
    slow = [row for row in records if row["interval_ns"] > threshold_ns]
    counters = defaultdict(Counter)
    frame_counters = defaultdict(Counter)
    pauses = []
    gc_events = []
    loads = defaultdict(list)
    cpu_load = []
    seen_types = Counter()
    excluded = Counter()
    boundary = Counter()
    max_truncated = Counter()
    evidence = defaultdict(list)

    for event in events:
        kind, values = event["type"], event["values"]
        seen_types[kind] += 1
        if not values.get("startTime"):
            continue
        timestamp = epoch_ns(values["startTime"]) - offset
        if kind == "jdk.GCPhasePause":
            end = timestamp + duration_ns(values["duration"])
            if overlap(timestamp, end, window_start, window_end):
                pauses.append((max(timestamp, window_start), min(end, window_end)))
            continue
        if kind == "jdk.GarbageCollection":
            if window_start <= timestamp < window_end:
                gc_events.append({k: v for k, v in values.items() if k != "stackTrace"})
            continue
        if not window_start <= timestamp < window_end:
            excluded[kind] += 1
            continue
        thread = values.get("sampledThread") or values.get("eventThread") or {}
        name = thread.get("javaName") or thread.get("osName") or "unknown"
        if kind == "jdk.ThreadCPULoad":
            loads[name].append({"monotonic_ns": timestamp, "user": values["user"],
                                "system": values["system"]})
        if kind == "jdk.CPULoad":
            cpu_load.append(values)
        if kind not in ("jdk.ExecutionSample", "jdk.NativeMethodSample"):
            continue
        index = bisect_right(starts, timestamp) - 1
        if index < 0 or timestamp >= records[index]["end_ns"]:
            excluded["sample_in_frame_gap"] += 1
            continue
        row = records[index]
        phase = "slow" if row["interval_ns"] > threshold_ns else "stable"
        key = kind + "|" + name + "|" + phase
        frames = stack_names(values)
        counters[key]["samples"] += 1
        frame_counters[row["frame_id"]][kind + "|" + name] += 1
        if min(timestamp - row["start_ns"], row["end_ns"] - timestamp) <= uncertainty_ns:
            boundary[key] += 1
        truncated = values.get("truncated", (values.get("stackTrace") or {}).get("truncated"))
        if truncated:
            max_truncated[key] += 1
        if frames:
            counters[key + "|leaf"][frames[0]] += 1
        for method in set(frames):
            counters[key + "|inclusive"][method] += 1
        for category, needles in CATEGORIES.items():
            if any(any(needle in method for needle in needles) for method in frames):
                counters[key + "|categories"][category] += 1
                if len(evidence[category]) < 5 and name == "Render thread":
                    evidence[category].append({"time_ns": timestamp, "phase": phase,
                                               "frames": frames[:24]})

    frame_gc = {row["frame_id"]: union_ns(
        [(max(row["start_ns"], left), min(row["end_ns"], right))
         for left, right in pauses if overlap(left, right, row["start_ns"], row["end_ns"])])
        for row in slow}
    def frame_detail(row):
        return {**row, "gc_pause_overlap_ns": frame_gc[row["frame_id"]],
                "jfr_samples": dict(frame_counters[row["frame_id"]])}

    metrics = ("interval_ns", "render_cpu_ns", "render_gpu_ns",
               "previous_minecraft_ns", "render_thread_allocated_bytes")
    return {
        "run_id": run["id"], "pid": run["game_pid"],
        "alignment": {"anchor_epoch": anchor["started_at"], "anchor_monotonic_ns": anchor["started_ns"],
                      "epoch_minus_monotonic_ns": offset, "window_start_ns": window_start,
                      "window_end_ns": window_end, "uncertainty_ns_assumed": uncertainty_ns,
                      "boundary_samples": dict(boundary),
                      "limit": "single Instant/nanoTime anchor; no independent clock drift calibration"},
        "metrics": {phase: {metric: summary([r.get(metric) for r in rows]) for metric in metrics}
                    for phase, rows in (("all", records), ("slow", slow),
                                        ("stable", [r for r in records if r not in slow]))},
        "slow_frames": [frame_detail(row) for row in sorted(slow, key=lambda r: -r["interval_ns"])],
        "counts": {key: dict(counter.most_common(80)) for key, counter in counters.items()},
        "gc": {"pause_union_ns": union_ns(pauses), "pause_intervals": pauses,
               "slow_overlap_ns": sum(frame_gc.values()),
               "slow_frames_overlapping": sum(value > 0 for value in frame_gc.values()),
               "collections": gc_events},
        "thread_cpu_load": dict(loads), "machine_cpu_load": cpu_load,
        "event_counts": dict(seen_types), "excluded_counts": dict(excluded),
        "truncated_samples": dict(max_truncated), "category_evidence": dict(evidence),
        "limits": [
            "ExecutionSample fractions are sample counts, not exclusive wall time or CPU milliseconds.",
            "Native samples are separate; inclusive/category counts overlap and must not be added.",
            "Categories use literal method substrings, require stack review, and do not cover all implementations.",
            "Thread CPU load is periodic, not per-frame CPU attribution.",
            "GPU duration is paired to the previous render, not epoch-calibrated GPU execution.",
            "Stable means interval <= threshold, not isolated particle steady state.",
            "No aggregate reconstruction of missing raw spans; no native-minus-laser cost inference."
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--analysis", required=True, type=Path)
    parser.add_argument("--jfr-json", required=True, type=Path)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--alignment-uncertainty-ms", type=Decimal, default=Decimal(2))
    args = parser.parse_args()
    data = json.loads(args.analysis.read_text(encoding="utf-8-sig"))
    matches = [run for run in data["runs"] if run["id"] == args.run_id]
    if len(matches) != 1:
        parser.error("--run-id 必须唯一匹配")
    if args.alignment_uncertainty_ms < 0:
        parser.error("时间误差必须非负")
    result = analyze(matches[0], iter_jfr(args.jfr_json),
                     uncertainty_ns=int(args.alignment_uncertainty_ms * 1_000_000))
    result["sources"] = {"analysis": str(args.analysis.resolve()),
                         "jfr_json": str(args.jfr_json.resolve())}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2, allow_nan=False)


if __name__ == "__main__":
    main()
