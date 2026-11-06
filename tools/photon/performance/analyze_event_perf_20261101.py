"""Read-only event performance audit. JSON stdout; no build, game or file writes."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import re
import statistics

GROUPS = ("HNB", "HNQ", "HNL", "HSB", "HSQ", "HSL")
IDS = tuple(g + f"{i:02}" for g in GROUPS for i in range(1, 4))


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def metrics(rows, lo, hi):
    selected = [r for r in rows if r[0] >= lo and r[1] <= hi]
    values = sorted(r[2] / 1e6 for r in selected)
    allocations = [r[3] for r in selected]
    return {
        "count": len(values), "p99_ms": values[math.ceil(len(values)*.99)-1] if values else None,
        "worst_ms": max(values) if values else None,
        **{f"over_{n}_ms": sum(v > n for v in values) if values else None for n in (50, 100, 250)},
        "render_thread_allocation_bytes": sum(allocations) if allocations and all(v is not None for v in allocations) else None,
        "window_seconds": [lo/1e9, hi/1e9],
        "selection": "both adjacent HEAD endpoints inside window; no interval clipping",
    }


def audit(root, identity, head):
    result = {"run_id": identity, "issues": [], "metrics": None}
    issues = result["issues"]

    def check(condition, message):
        if not condition:
            issues.append(message)

    folder = root / identity / "results"
    reports = list((folder / "artifacts/custom").glob("*_hybrid18.json"))
    check(len(reports) == 1, "unknown: exactly one owned hybrid18 report required")
    if len(reports) != 1:
        return result
    report_path = reports[0]
    d = read(report_path)
    f = d.get("frames", {})
    seconds = 25 if identity[2] == "Q" else 45
    variant = {"B": "blank25", "Q": "queen25", "L": "laser3"}[identity[2]]
    for key, expected in {"run_id": identity, "variant": variant,
                          "shader_in_use": identity[1] == "S", "capture_seconds": seconds,
                          "archived": True, "verified": True, "finished": True, "failure": None}.items():
        check(key in d and d[key] == expected, "report:" + key)
    check(type(d.get("pid")) is int and d["pid"] > 0, "unknown:pid")
    result.update(pid=d.get("pid"), report=str(report_path))
    args = d.get("vm_arguments", [])
    commits = [a.split("=", 1)[1] for a in args if a.startswith("-DmodBench.git.commit=")]
    check(commits == [head], "unknown_or_mismatched:expected_head")
    check(f.get("state") == "DURATION", "frames:not_DURATION")
    check(f.get("requested_seconds") == seconds, "frames:requested_seconds")
    check(f.get("started_ns") == d.get("entry_t0_ns") and f.get("started_ns") is not None, "frames:origin")
    label = d.get("label")
    check(bool(label) and f.get("label") == label, "frames:label")

    def artifact(suffix):
        records = [a for a in d.get("frame_artifacts", []) if str(a.get("source", "")).endswith(suffix)]
        local = list(report_path.parent.glob("*_" + suffix))
        if len(records) == 1:
            a = records[0]
            p = report_path.parent / Path(a.get("artifact", "")).name
            if not p.is_file():
                p = Path(a.get("artifact", ""))
            if p.is_file():
                check(hashlib.sha256(p.read_bytes()).hexdigest() == a.get("sha256"), "artifact:hash:" + suffix)
                return p
        if len(local) == 1:
            issues.append("unknown:artifact_hash:" + suffix)
            return local[0]
        issues.append("unknown:artifact:" + suffix)
        return None

    fp, sp = artifact("frames.csv"), artifact("session.json")
    session = read(sp) if sp else {}
    for key, expected in {"label": label, "frame_origin_ns": f.get("started_ns"), "end_reason": "DURATION"}.items():
        check(expected is not None and session.get(key) == expected, "session:" + key)
    check(session.get("frame_summary") == f, "session:frame_summary")
    result["span_coverage"] = {"overwritten_events": session.get("overwritten_events"),
                               "raw_trace_truncated": d.get("raw_trace_truncated"),
                               "scope": "retained spans only; never used as full-window frame population"}
    spark = d.get("spark", {})
    check(spark.get("state") == "COMPLETE" and spark.get("archived") is True and spark.get("failure") == "", "spark:completion")
    check(spark.get("run_id") == label, "spark:identity")
    candidates = list(folder.rglob("*.sparkprofile"))
    original = Path(spark.get("profile_path", ""))
    if not candidates and original.is_file():
        candidates = [original]
    matching = [p for p in candidates if p.stat().st_size == spark.get("profile_bytes") and
                hashlib.sha256(p.read_bytes()).hexdigest() == spark.get("profile_sha256")]
    check(len(matching) == 1, "unknown_or_mismatched:spark_file")
    check(isinstance(spark.get("started_ack_ns"), int) and spark["started_ack_ns"] <= d.get("entry_t0_ns", 0), "spark:start_coverage")
    check(isinstance(spark.get("stop_request_ns"), int) and spark["stop_request_ns"] >= d.get("stop_observed_ns", float("inf")), "spark:end_coverage")
    rows = []
    if fp:
        with fp.open(encoding="utf-8-sig", newline="") as stream:
            raw = list(csv.DictReader(stream))
        check(len(raw) == f.get("frame_count"), "frames:row_count")
        previous = None
        for i, row in enumerate(raw):
            check(int(row["frame_id"]) == i, "frames:sequence")
            end = int(row["head_offset_ns"])
            interval = int(row["interval_ns"]) if row["interval_ns"] else None
            if interval is not None:
                check(interval >= 0 and previous is not None and end-previous == interval, "frames:adjacency")
                allocation = int(row["render_thread_allocated_bytes"]) if row["render_thread_allocated_bytes"] else None
                rows.append((end-interval, end, interval, allocation))
            previous = end
        check(len(rows) == f.get("frame_interval_ms", {}).get("count"), "frames:interval_count")
        result["raw_frame_coverage"] = {"first_offset_ns": int(raw[0]["head_offset_ns"]) if raw else None,
                                       "last_offset_ns": previous, "interval_count": len(rows),
                                       "scope": "adjacent HEAD intervals; initial and terminal unpaired edges excluded"}
        result["metrics"] = {"full": metrics(rows, 0, seconds*1e9),
                             "first25": metrics(rows, 0, 25e9),
                             "5to25": metrics(rows, 5e9, 25e9)}
    # Map monotonic entry time to epoch using the contemporaneous observer anchor.
    anchors = ("entry_t0_ns", "observer_installed_ns", "observer_installed_epoch_ms", "jvm_started_epoch_ms")
    result["loading_wall_seconds"] = ((d[anchors[0]]-d[anchors[1]])/1e9 +
                                      (d[anchors[2]]-d[anchors[3]])/1000) if all(type(d.get(k)) is int for k in anchors) else None
    check(result["loading_wall_seconds"] is not None, "unknown:loading_wall")
    result["eligible"] = not issues
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--expected-head", required=True)
    args = parser.parse_args()
    if not re.fullmatch("[0-9a-f]{40}", args.expected_head):
        parser.error("--expected-head requires full lowercase SHA")
    runs = []
    for identity in IDS:
        try:
            runs.append(audit(args.root.resolve(), identity, args.expected_head))
        except (OSError, ValueError, KeyError, TypeError) as error:
            runs.append({"run_id": identity, "eligible": False, "issues": [str(error)], "metrics": None})
    pids = [r.get("pid") for r in runs if r.get("pid") is not None]
    duplicates = {p for p in pids if pids.count(p) > 1}
    for run in runs:
        if run.get("pid") in duplicates:
            run["issues"].append("duplicate_PID")
            run["eligible"] = False
    groups = {}
    for group in GROUPS:
        selected = [r for r in runs if r["run_id"].startswith(group)]
        valid = all(r.get("eligible") for r in selected)
        entry = {"eligible": valid, "runs": [r["run_id"] for r in selected], "aggregates": None}
        if valid:
            entry["aggregates"] = {
                window: {key: {"each": [r["metrics"][window][key] for r in selected],
                               "median": statistics.median([r["metrics"][window][key] for r in selected])
                               if all(r["metrics"][window][key] is not None for r in selected) else None}
                         for key in ("p99_ms", "worst_ms", "over_50_ms", "over_100_ms", "over_250_ms", "render_thread_allocation_bytes")}
                for window in ("full", "first25", "5to25")}
            entry["loading_wall_seconds_median"] = statistics.median(r["loading_wall_seconds"] for r in selected)
        groups[group] = entry
    print(json.dumps({"schema": "photon-event-perf-20261101-v1", "expected_head": args.expected_head,
                      "status": "COMPLETE" if all(r.get("eligible") for r in runs) else "INCOMPLETE",
                      "unknown": None, "runs": runs, "groups": groups,
                      "historical_baseline": "eaf6155 is descriptive reference only; no causal optimization ratio",
                      "quantile": "nearest rank ceil(n*.99)-1 per JVM; median across three JVMs; no pooled quantile"},
                     ensure_ascii=True, allow_nan=False))


if __name__ == "__main__":
    main()
