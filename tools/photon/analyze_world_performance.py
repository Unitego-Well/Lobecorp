"""对相同协议的世界特效帧记录生成前后稳定性比较；不启动游戏或修改资源。"""

import argparse
import bisect
import collections
import hashlib
import json
import math
import statistics
from pathlib import Path

LABELS = (
    "world_v1_before_one_20261018",
    "world_v2_after_one_20261018",
    "world_v1_before_four_20261018",
    "world_v2_after_four_20261018",
)
WINDOWS = {
    "warm_steady": ((55, 74), (95, 114)),
    "warm_full": ((40.5, 79.8), (80.5, 118.8)),
    "warm_release": ((50, 51.9), (90, 91.9)),
    "first_release": ((10, 11.9),),
    "warm_end": ((75, 78), (115, 118)),
    "warm_empty": ((78.2, 79.8), (118.2, 118.8)),
}
MIB = 1024 * 1024
NS_PER_SECOND = 1_000_000_000
NS_PER_MS = 1_000_000


def quantile(values, fraction):
    position = (len(values) - 1) * fraction
    left = math.floor(position)
    right = min(left + 1, len(values) - 1)
    return values[left] + (values[right] - values[left]) * (position - left)


def load_capture(directory, label):
    candidates = []
    rejected = []
    for path in directory.glob(label + "-*.json"):
        capture = json.loads(path.read_text(encoding="utf-8"))
        summary = capture["summary"]
        if (
            summary["state"] == "DURATION"
            and summary["frame_count"] > 1000
            and 119 <= summary["elapsed_seconds"] <= 125
        ):
            candidates.append((path.stat().st_mtime_ns, path, capture))
        else:
            rejected.append({
                "path": str(path),
                "state": summary["state"],
                "frames": summary["frame_count"],
                "seconds": summary["elapsed_seconds"],
            })
    if not candidates:
        raise RuntimeError("No complete valid capture for " + label)
    _, path, capture = max(candidates, key=lambda entry: entry[0])
    return path, capture, rejected


def minimum_one_second(heads, windows):
    counts = []
    for start, end in windows:
        for tenth in range(math.ceil(start * 10), math.floor((end - 1) * 10) + 1):
            window_start = tenth / 10
            counts.append(
                bisect.bisect_right(heads, window_start + 1)
                - bisect.bisect_right(heads, window_start)
            )
    return min(counts) if counts else None


def describe(frames, windows):
    heads = frames["head_offset_ns"]
    indices = [
        index
        for index in range(1, len(heads))
        if frames["interval_ns"][index] > 0
        and any(
            heads[index - 1] >= start * NS_PER_SECOND
            and heads[index] <= end * NS_PER_SECOND
            for start, end in windows
        )
    ]
    if not indices:
        raise RuntimeError("No complete intervals in comparison window")
    intervals = sorted(frames["interval_ns"][index] / NS_PER_MS for index in indices)
    duration = sum(intervals) / 1000
    worst_one_percent = math.ceil(len(intervals) * 0.01)
    worst_tenth_percent = math.ceil(len(intervals) * 0.001)
    allocations = [
        frames["render_thread_allocated_bytes"][index]
        for index in indices
        if frames["render_thread_allocated_bytes"][index] >= 0
    ]
    result = {
        "windows": windows,
        "frames": len(indices),
        "measured_seconds": duration,
        "mean_fps": len(indices) / duration,
        "one_percent_low_fps": 1000 / statistics.mean(intervals[-worst_one_percent:]),
        "point_one_percent_low_fps": 1000 / statistics.mean(intervals[-worst_tenth_percent:]),
        "min_one_second_fps": minimum_one_second(
            [value / NS_PER_SECOND for value in heads], windows
        ),
        "worst_frame_equivalent_fps": 1000 / intervals[-1],
        "interval_ms": {
            "mean": statistics.mean(intervals),
            "p50": quantile(intervals, 0.5),
            "p95": quantile(intervals, 0.95),
            "p99": quantile(intervals, 0.99),
            "p999": quantile(intervals, 0.999),
            "max": intervals[-1],
            "standard_deviation": statistics.pstdev(intervals),
        },
        "over_10_ms_percent": sum(value > 10 for value in intervals) * 100 / len(intervals),
        "over_16_7_ms_percent": sum(value > 1000 / 60 for value in intervals) * 100 / len(intervals),
        "over_33_3_ms_percent": sum(value > 1000 / 30 for value in intervals) * 100 / len(intervals),
        "allocation_mib_per_frame": statistics.mean(allocations) / MIB if allocations else None,
        "allocation_mib_per_second": sum(allocations) / MIB / duration if allocations else None,
        "flags": dict(collections.Counter(frames["flags"][index] for index in indices)),
        "throttle": dict(collections.Counter(frames["throttle_reason"][index] for index in indices)),
        "actual_limits": dict(collections.Counter(frames["actual_framerate_limit"][index] for index in indices)),
        "framebuffer_sizes": sorted({
            (frames["framebuffer_width"][index], frames["framebuffer_height"][index])
            for index in indices
        }),
    }
    for field in ("previous_minecraft_ns", "render_cpu_ns", "render_gpu_ns"):
        values = sorted(
            frames[field][index] / NS_PER_MS
            for index in indices
            if frames[field][index] >= 0
        )
        result[field.replace("_ns", "_ms")] = {
            "count": len(values),
            "mean": statistics.mean(values) if values else None,
            "p50": quantile(values, 0.5) if values else None,
            "p99": quantile(values, 0.99) if values else None,
        }
    return result


def gc_summary(directory, label, frames):
    path = directory / (label + "_analysis.json")
    if not path.is_file():
        return None
    analysis = json.loads(path.read_text(encoding="utf-8"))
    pauses = analysis["pauses"]
    long_frames = []
    for index in range(1, len(frames["head_offset_ns"])):
        start = frames["head_offset_ns"][index - 1] / NS_PER_SECOND
        end = frames["head_offset_ns"][index] / NS_PER_SECOND
        interval_ms = frames["interval_ns"][index] / NS_PER_MS
        if interval_ms <= 10 or not any(
            start >= left and end <= right for left, right in WINDOWS["warm_steady"]
        ):
            continue
        overlap_ms = sum(
            max(0, min(end, pause["seconds"] + pause["duration_ms"] / 1000)
                - max(start, pause["seconds"])) * 1000
            for pause in pauses
        )
        long_frames.append({
            "start_seconds": start,
            "interval_ms": interval_ms,
            "gc_overlap_ms": overlap_ms,
        })
    return {
        "path": str(path),
        "pause_count": len(pauses),
        "selected_total_pause_ms": sum(pause["selected_overlap_ms"] for pause in pauses),
        "max_pause_ms": max((pause["duration_ms"] for pause in pauses), default=0),
        "over_10_ms_frames": len(long_frames),
        "over_10_ms_frames_overlapping_gc": sum(
            frame["gc_overlap_ms"] > 0 for frame in long_frames
        ),
        "longest_frames": sorted(
            long_frames, key=lambda frame: frame["interval_ms"], reverse=True
        )[:10],
        "interpretation": "Temporal overlap is not proof that GC caused the whole frame.",
    }


def analyze(directory):
    result = {
        "method": {
            "one_percent_low": "1000 / mean(slowest ceil(frame_count * 0.01) interval_ms)",
            "point_one_percent_low": "1000 / mean(slowest ceil(frame_count * 0.001) interval_ms)",
            "minimum_one_second": "minimum render HEAD count in (start,start+1], sampled every 100 ms",
            "complete_interval_rule": "both interval HEAD timestamps must be inside the same window",
            "first_release": "first release in this run; not an equivalent cold-start A/B",
        },
        "cases": {},
        "rejected": [],
    }
    for label in LABELS:
        path, capture, rejected = load_capture(directory, label)
        summary = capture["summary"]
        start = summary["pool_start"]
        end = summary["pool_current"]
        borrows = end["borrows"] - start["borrows"]
        pool = {
            key: end[key] - start[key]
            for key in ("hits", "misses", "borrows", "recycled", "discarded", "createdInitialBytes")
        }
        pool.update({
            "hit_percent": pool["hits"] * 100 / borrows if borrows else None,
            "cached_count_end": end["cachedCount"],
            "cached_mib_end": end["cachedBytes"] / MIB,
            "borrowed_end": end["borrowedCount"],
        })
        result["cases"][label] = {
            "path": str(path),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "summary": summary,
            "pool_delta": pool,
            "gc": gc_summary(directory, label, capture["frames"]),
            "segments": {
                name: describe(capture["frames"], windows)
                for name, windows in WINDOWS.items()
            },
            "steady_cycles": [
                describe(capture["frames"], ((40 * cycle + 15, 40 * cycle + 34),))
                for cycle in range(3)
            ],
        }
        result["rejected"].extend(rejected)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--performance-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    result = analyze(arguments.performance_dir)
    arguments.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False), encoding="utf-8"
    )
    print(json.dumps({
        label: {
            key: case["segments"]["warm_steady"][key]
            for key in (
                "mean_fps", "one_percent_low_fps", "point_one_percent_low_fps",
                "min_one_second_fps", "worst_frame_equivalent_fps",
                "interval_ms", "over_10_ms_percent", "allocation_mib_per_frame",
            )
        }
        for label, case in result["cases"].items()
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
