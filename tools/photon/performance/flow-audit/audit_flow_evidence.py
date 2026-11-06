#!/usr/bin/env python3
"""Offline evidence audit for the Photon hybrid-18 capture set.

Reads only existing frames.csv, session.json, outliers.trace.json, and
JFR JSON printed from startup/*.jfr. It does not run Minecraft or a build.

The frame interval is the wall interval from the previous render HEAD to
the current render HEAD. render_cpu_ns and render_gpu_ns overlap and are
not additive. A GC collection duration is a period, not a stop-the-world
budget. Only pause events are treated as directly elapsed STW evidence.
"""

from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
import json
import re
import subprocess
from typing import Any, Iterable

NS = 1_000_000_000
MS = 1_000_000


def epoch_ns(value: str) -> int:
    match = re.fullmatch(r"(.*T\d\d:\d\d:\d\d)(?:\.(\d+))?(Z|[+-]\d\d:\d\d)", value)
    if not match:
        raise ValueError(f"expected ISO timestamp with zone: {value}")
    base, fraction, zone = match.groups()
    stamp = datetime.fromisoformat(base + ("+00:00" if zone == "Z" else zone)).astimezone(timezone.utc)
    return int(stamp.timestamp()) * NS + int((fraction or "").ljust(9, "0")[:9])


def parse_jfr_time(value: str) -> int:
    if value.endswith("Z") or re.search(r"[+-]\d\d:\d\d$", value):
        return epoch_ns(value)
    # Text form from `jfr print`: 23:04:31.673 (2026-10-31)
    match = re.fullmatch(r"(\d\d:\d\d:\d\d(?:\.\d+)?)\s+\((\d{4}-\d\d-\d\d)\)", value)
    if not match:
        raise ValueError(f"unsupported JFR time: {value}")
    clock = match.group(1)
    second, _, fraction = clock.partition(".")
    # `jfr print` text time is already UTC, not the local capture zone.
    local = datetime.fromisoformat(match.group(2) + "T" + second).replace(tzinfo=timezone.utc)
    return int(local.timestamp()) * NS + (int(fraction.ljust(9, "0")[:9]) if fraction else 0)


def duration_ns(value: str) -> int:
    match = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?", value)
    if not match:
        raise ValueError(f"unsupported JFR duration: {value}")
    hours, minutes, seconds = match.groups()
    total = Decimal(hours or 0) * 3600 + Decimal(minutes or 0) * 60 + Decimal(seconds or 0)
    return int(total * NS)


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def frame_rows(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    result = []
    for row in rows:
        if not row.get("interval_ns"):
            continue
        item = {key: row.get(key) for key in (
            "frame_id", "head_offset_ns", "interval_ns", "previous_minecraft_ns",
            "render_cpu_ns", "render_gpu_ns", "render_thread_allocated_bytes",
            "framebuffer_width", "framebuffer_height", "phase_id", "interval_phase_id",
            "limit", "throttle_reason")}
        for key in ("frame_id", "head_offset_ns", "interval_ns", "previous_minecraft_ns",
                    "render_cpu_ns", "render_gpu_ns", "render_thread_allocated_bytes",
                    "phase_id", "interval_phase_id"):
            value = item.get(key)
            item[key] = int(value) if value not in (None, "") else None
        result.append(item)
    return result


def find_one(directory: Path, suffix: str) -> Path:
    matches = [path for path in directory.glob(f"*{suffix}") if "_loading_" not in path.name]
    if len(matches) != 1:
        raise ValueError(f"expected one non-loading *{suffix} under {directory}, found {len(matches)}")
    return matches[0]


def longest_span(spans: list[dict[str, Any]], name: str) -> dict[str, Any] | None:
    candidates = [event for event in spans if event.get("name") == name]
    return max(candidates, key=lambda event: float(event.get("dur", 0)), default=None)


def summarize_case(case_dir: Path, jfr_path: Path, jfr_events: list[dict[str, Any]]) -> dict[str, Any]:
    artifact_dir = case_dir / "client" / "artifacts" / "custom"
    session = read_json(find_one(artifact_dir, "_session.json"))
    frames = frame_rows(find_one(artifact_dir, "_frames.csv"))
    trace_path = find_one(artifact_dir, "_outliers.trace.json")
    trace = read_json(trace_path)
    spans = [event for event in trace.get("traceEvents", [])
             if event.get("ph") == "X" and event.get("dur") is not None]

    origin = int(session["frame_origin_ns"])
    started_ns = int(session["started_ns"])
    started_epoch_ns = epoch_ns(session["started_utc"])
    # The CSV head_offset_ns is relative to session.started_ns, not frame_origin_ns.
    # frame_origin_ns equals the first frame's absolute nanoTime and is only a
    # cross-check; the first row's offset equals its distance from started_ns.
    annotated = []
    for row in frames:
        head = started_epoch_ns + int(row["head_offset_ns"])
        start = head - int(row["interval_ns"])
        row["abs_head_ns"] = head
        row["abs_start_ns"] = start
        row["seconds_from_session"] = (int(row["head_offset_ns"])) / NS
        row["seconds_from_t0"] = (started_ns + int(row["head_offset_ns"]) - origin) / NS
        row["interval_ms"] = int(row["interval_ns"]) / MS
        row["previous_minecraft_ms"] = (int(row["previous_minecraft_ns"] or 0)) / MS
        row["render_cpu_ms"] = (int(row["render_cpu_ns"] or 0)) / MS
        row["render_gpu_ms"] = (int(row["render_gpu_ns"] or 0)) / MS
        row["render_alloc_mb"] = (int(row["render_thread_allocated_bytes"] or 0)) / (1024 * 1024)
        annotated.append(row)

    slow = [row for row in annotated if row["interval_ns"] > 100 * MS]
    slow.sort(key=lambda row: row["interval_ns"], reverse=True)

    gc_pauses = []
    gc_periods = []
    for event in jfr_events:
        event_type = event.get("type", "")
        values = event.get("values", {})
        if event_type == "jdk.GCPhasePause":
            gc_pauses.append({
                "start_ns": epoch_ns(values["startTime"]),
                "duration_ns": duration_ns(values["duration"]),
                "name": values.get("name"),
                "gc_id": values.get("gcId"),
            })
        elif event_type == "jdk.GarbageCollection":
            gc_periods.append({
                "start_ns": epoch_ns(values["startTime"]),
                "duration_ns": duration_ns(values["duration"]),
                "name": values.get("name"),
                "cause": values.get("cause"),
                "longest_pause_ns": duration_ns(values["longestPause"]) if values.get("longestPause") else 0,
                "gc_id": values.get("gcId"),
            })

    for row in annotated:
        pauses = [pause for pause in gc_pauses
                  if pause["start_ns"] < row["abs_head_ns"] and
                  pause["start_ns"] + pause["duration_ns"] > row["abs_start_ns"]]
        periods = [period for period in gc_periods
                   if period["start_ns"] < row["abs_head_ns"] and
                   period["start_ns"] + period["duration_ns"] > row["abs_start_ns"]]
        row["gc_pause_ms"] = sum(min(row["abs_head_ns"], pause["start_ns"] + pause["duration_ns"]) -
                                  max(row["abs_start_ns"], pause["start_ns"])
                                  for pause in pauses) / MS
        row["gc_period_names"] = sorted({period["name"] for period in periods})
        row["gc_period_ms"] = sum(min(row["abs_head_ns"], period["start_ns"] + period["duration_ns"]) -
                                   max(row["abs_start_ns"], period["start_ns"])
                                   for period in periods) / MS
        # The outlier trace keeps early slow-frame spans even after the bounded
        # full trace ring is overwritten. Match its frame id first; its ts is
        # relative microseconds, not epoch nanoseconds.
        frame_spans = [event for event in spans
                       if event.get("args", {}).get("frame_at_begin") == row["frame_id"]]
        if not frame_spans:
            frame_spans = [event for event in spans
                           if row["abs_start_ns"] <= started_epoch_ns + int(event["ts"] * 1000) <= row["abs_head_ns"]]
        render = longest_span(frame_spans, "unassigned/shared / RENDER")
        draw = longest_span(frame_spans, "unassigned/shared / DRAW")
        ui = longest_span(frame_spans, "unassigned/shared / UI_RENDER")
        extract = longest_span(frame_spans, "unassigned/shared / EXTRACT")
        row["span_counts"] = len(frame_spans)
        row["render_span_ms"] = float(render["dur"]) / 1000 if render else None
        row["draw_span_ms"] = float(draw["dur"]) / 1000 if draw else None
        row["ui_span_ms"] = float(ui["dur"]) / 1000 if ui else None
        row["extract_span_ms"] = float(extract["dur"]) / 1000 if extract else None
        row["classification"] = classify(row)

    return {
        "case": case_dir.name,
        "label": session.get("label"),
        "started_utc": session.get("started_utc"),
        "frame_origin_ns": origin,
        "started_epoch_ns": started_epoch_ns,
        "frames": len(annotated),
        "slow_frames": slow[:20],
        "gc_pause_count": len(gc_pauses),
        "gc_period_count": len(gc_periods),
        "trace_path": str(trace_path),
        "jfr_path": str(jfr_path),
    }


def classify(row: dict[str, Any]) -> str:
    """Pure observation labels; no coverage, dominance, or residual budget."""
    labels = ["slow_interval"]
    if row["gc_pause_ms"] > 0:
        labels.append("gc_pause_interval_overlap")
    if row["gc_period_names"]:
        labels.append("gc_collection_interval_overlap")
    if any(row[key] is not None for key in ("render_span_ms", "draw_span_ms", "ui_span_ms", "extract_span_ms")):
        labels.append("outlier_span_observed")
    return ";".join(labels)


def jfr_events(path: Path, events: str, jfr_exe: Path | None = None) -> list[dict[str, Any]]:
    command = [str(jfr_exe or find_jfr()), "print", "--json", "--events", events, "--stack-depth", "0", str(path)]
    completed = subprocess.run(command, check=True, text=True, encoding="utf-8", capture_output=True)
    return json.loads(completed.stdout)["recording"]["events"]


def find_jfr() -> Path:
    candidates = [
        Path(r"C:\Users\wang_\.jdks\jbr-25.0.4.1\bin\jfr.exe"),
        Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\jfr.exe"),
        Path(r"C:\Program Files\Eclipse Adoptium\jdk-25.0.2\bin\jfr.exe"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise FileNotFoundError("jfr.exe not found; pass --jfr <path to jfr.exe>")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, required=True,
                        help="build/modBench/raw-results containing photon-hybrid18-p2-*")
    parser.add_argument("--jfr-dir", type=Path, required=True,
                        help="build/agent-backups/.../startup")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--jfr", type=Path, default=None,
                        help="explicit jfr.exe; defaults to jbr-25.0.4.1 then JDK fallbacks")
    args = parser.parse_args()
    jfr_exe = args.jfr
    if jfr_exe is not None and not jfr_exe.is_file():
        parser.error(f"--jfr is not a file: {jfr_exe}")

    case_dirs = sorted(path for path in args.results.glob("photon-hybrid18-p2-*-20261031-r1") if path.is_dir())
    result = []
    for case_dir in case_dirs:
        suffix = case_dir.name.split("-")[3]
        jfr = args.jfr_dir / f"{suffix}.jfr"
        if not jfr.exists():
            raise FileNotFoundError(jfr)
        events = jfr_events(jfr, "jdk.GarbageCollection,jdk.GCPhasePause", jfr_exe)
        result.append(summarize_case(case_dir, jfr, events))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
