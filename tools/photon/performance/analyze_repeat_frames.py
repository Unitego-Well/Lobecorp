#!/usr/bin/env python3
"""Offline Photon repeat matrix analysis (stdlib only).

Usage: python analyze_repeat_frames.py --manifest matrix.json --output-dir result
       python analyze_repeat_frames.py --self-test
Quantiles use nearest rank; slow frames are strictly greater than each threshold.
"""
import argparse
import collections
import json
import math
import pathlib
import sys

SCHEMA = "photon-repeat-matrix-v1"
NS_PER_MS = 1_000_000
TAIL_NS = 5_000_000_000
THRESHOLDS_NS = (NS_PER_MS * 1000 / 60, NS_PER_MS * 1000 / 30,
                 50 * NS_PER_MS, 100 * NS_PER_MS, 250 * NS_PER_MS)
METRICS = ("interval_ns", "previous_minecraft_ns", "render_cpu_ns",
           "render_gpu_ns", "render_thread_allocated_bytes")
ARTIFACTS = ("frames", "prewarm_frames", "trial_report", "metadata", "console",
             "trace_directory", "jfr", "spark")
WINDOWS = ("before_first", "trigger0_to_trigger1", "trigger1_to_trigger2",
           "trigger2_plus_5s", "tail")


def number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def measured(value):
    return number(value) and value >= 0


def integer(value):
    return isinstance(value, int) and not isinstance(value, bool)


def stats(values, signed=False):
    valid = sorted(v for v in values if (number(v) if signed else measured(v)))
    count = len(valid)
    return {
        "count": count, "unavailable": len(values) - count,
        "status": "measured" if count else "unknown",
        "min": valid[0] if count else None,
        "max": valid[-1] if count else None,
        "mean": sum(valid) / count if count else None,
        "sum": sum(valid) if count else None,
        **{name: valid[max(0, math.ceil(count * q) - 1)] if count else None
           for name, q in (("p50", .5), ("p90", .9), ("p95", .95), ("p99", .99), ("p999", .999))},
    }


def distribution(records):
    result = {key: stats([r.get(key) for r in records]) for key in METRICS}
    slow = {}
    for threshold in THRESHOLDS_NS:
        sequences = []
        current = []
        for record in records:
            value = record.get("interval_ns")
            if measured(value) and value > threshold:
                if current and (record["frame_id"] != current[-1]["frame_id"] + 1
                                or record.get("run_id") != current[-1].get("run_id")):
                    sequences.append(current)
                    current = []
                current.append(record)
            elif current:
                sequences.append(current)
                current = []
        if current:
            sequences.append(current)
        valid_count = result["interval_ns"]["count"]
        slow[str(threshold / NS_PER_MS)] = {
            "operator": ">", "threshold_ms": threshold / NS_PER_MS,
            "count": sum(len(s) for s in sequences) if valid_count else None,
            "fraction": sum(len(s) for s in sequences) / valid_count if valid_count else None,
            "cumulative_over_budget_ns": sum(
                r["interval_ns"] - threshold for s in sequences for r in s) if valid_count else None,
            "longest_consecutive": max((len(s) for s in sequences), default=0) if valid_count else None,
            "sequences": [{"run_id": s[0].get("run_id"),
                           "first_frame": s[0]["frame_id"], "last_frame": s[-1]["frame_id"],
                           "count": len(s), "total_interval_ns": sum(r["interval_ns"] for r in s)}
                          for s in sequences],
        }
    result["slow_frames"] = slow
    result["frame_count"] = len(records)
    return result


def read_json(path):
    with pathlib.Path(path).open(encoding="utf-8-sig") as stream:
        return json.load(stream)


def artifact(base, run, key):
    value = run.get(key)
    if not isinstance(value, str) or not value:
        return {"status": "unknown", "path": None}
    path = pathlib.Path(value)
    if not path.is_absolute():
        path = base / path
    exists = path.is_dir() if key == "trace_directory" else path.is_file()
    return {"status": "present" if exists else "missing", "path": str(path.resolve())}


def trigger_analysis(run):
    raw = run.get("triggers")
    errors = []
    if not isinstance(raw, list):
        raw = []
        errors.append("triggers_missing")
    if len(raw) != 3 or any(not isinstance(t, dict) for t in raw):
        errors.append("expected_three_triggers")
    by_index = {}
    for t in raw:
        if not isinstance(t, dict):
            continue
        index = t.get("index")
        if not integer(index) or index not in range(3) or index in by_index:
            errors.append("invalid_or_duplicate_trigger_index")
        else:
            by_index[index] = t
    triggers = []
    for index in range(3):
        t = by_index.get(index, {})
        requested, observed = t.get("requested_ns"), t.get("observed_ns")
        # Absolute nanoTime can be negative; only durations use negative sentinels.
        delta = observed - requested if number(observed) and number(requested) else None
        triggers.append({
            "index": index, "requested_ns": requested if number(requested) else None,
            "observed_ns": observed if number(observed) else None,
            "started": t.get("started") if isinstance(t.get("started"), bool) else None,
            "dispatch_jitter_ns": delta,
            "ack_delay_ns": t["ack_ns"] - observed
            if number(t.get("ack_ns")) and number(observed) else None,
            "first_active_delay_ns": t["first_active_ns"] - observed
            if number(t.get("first_active_ns")) and number(observed) else None,
            "first_active_ns": t.get("first_active_ns") if number(t.get("first_active_ns")) else None,
            "dispatch_ns": t.get("dispatch_ns"),
            "last_active_ns": t.get("last_active_ns"),
            "overlap_at_dispatch": t.get("overlap_at_dispatch"),
        })
    observed = [t["observed_ns"] for t in triggers]
    ordered = all(number(t) for t in observed) and observed[0] < observed[1] < observed[2]
    if not ordered:
        errors.append("observed_trigger_times_missing_or_not_increasing")
    if any(t["started"] is not True for t in triggers):
        errors.append("trigger_start_not_confirmed")
    gaps = []
    for a, b in zip(triggers, triggers[1:]):
        actual = b["observed_ns"] - a["observed_ns"] if ordered else None
        requested = (b["requested_ns"] - a["requested_ns"]
                     if number(a["requested_ns"]) and number(b["requested_ns"]) else None)
        gaps.append({"actual_ns": actual, "requested_ns": requested,
                     "error_ns": actual - requested if actual is not None and requested is not None else None})
    return {"triggers": triggers, "gaps": gaps, "errors": errors,
            "ordered": ordered, "time_scope": run.get("trigger_time_scope", "unknown")}


def frame_records(capture):
    summary = capture.get("summary")
    frames = capture.get("frames")
    if not isinstance(summary, dict) or not isinstance(frames, dict):
        raise ValueError("Expected native summary + frames column arrays")
    heads = frames.get("head_offset_ns")
    if not isinstance(heads, list):
        raise ValueError("frames.head_offset_ns must be an array")
    count = len(heads)
    issues = []
    if summary.get("frame_count") != count:
        issues.append("summary_frame_count_mismatch_or_missing")
    for key, values in frames.items():
        if not isinstance(values, list) or len(values) != count:
            issues.append("column_length_mismatch:" + key)
    origin = summary.get("started_ns")

    def value(key, index):
        values = frames.get(key)
        return values[index] if isinstance(values, list) and 0 <= index < len(values) else None

    records = []
    for i in range(count):
        head, previous = value("head_offset_ns", i), value("head_offset_ns", i - 1)
        start = origin + previous if number(origin) and number(previous) else None
        end = origin + head if number(origin) and number(head) else None
        interval = value("interval_ns", i)
        valid_bounds = i > 0 and number(start) and number(end) and end > start
        if i > 0 and not valid_bounds:
            issues.append("invalid_head_bounds:" + str(i))
        if i > 0 and (not measured(interval) or interval <= 0):
            issues.append("invalid_interval:" + str(i))
        mismatch = valid_bounds and measured(interval) and interval != end - start
        if mismatch:
            issues.append("interval_head_mismatch:" + str(i))
        phase = value("interval_phase_id", i)
        method = "interval_phase_id"
        if not integer(phase) or phase < 0:
            phase = value("phase_id", i - 1)
            method = "previous_render_phase_id"
        if not integer(phase) or phase < 0 or i == 0:
            phase = None
            method = "unknown"
        record = {
            "frame_id": i, "render_frame_id": i - 1 if i else None,
            "start_ns": start if valid_bounds else None, "end_ns": end if valid_bounds else None,
            "interval_ns": interval if i > 0 else None,
            "phase_id": phase, "phase_attribution": method,
            "current_phase_id": value("phase_id", i),
            "phase_transition": (value("phase_id", i) != phase
                                 if phase is not None and integer(value("phase_id", i))
                                 and value("phase_id", i) >= 0 else None),
            "interval_head_mismatch": bool(mismatch),
            "previous_minecraft_ns": value("previous_minecraft_ns", i) if i else None,
            "render_cpu_ns": value("render_cpu_ns", i - 1) if i else None,
            "render_gpu_ns": value("render_gpu_ns", i - 1) if i else None,
            "render_thread_allocated_bytes": value("render_thread_allocated_bytes", i) if i else None,
            "window": "unknown", "straddle": [],
            "environment": {key: value(key, i - 1) if i else None for key in (
                "flags", "framebuffer_width", "framebuffer_height", "window_width",
                "window_height", "actual_framerate_limit", "throttle_reason")},
        }
        records.append(record)
    return records, issues


def analyze_capture(capture, timing):
    records, issues = frame_records(capture)
    observed = [t["observed_ns"] for t in timing["triggers"]]
    boundaries = observed + [observed[2] + TAIL_NS] if timing["ordered"] else None
    for r in records:
        if boundaries and r["start_ns"] is not None:
            r["window"] = WINDOWS[sum(r["start_ns"] >= b for b in boundaries)]
            r["straddle"] = [
                {"boundary": ("trigger" + str(i)) if i < 3 else "trigger2_plus_5s_end", "time_ns": b}
                for i, b in enumerate(boundaries) if r["start_ns"] < b < r["end_ns"]]
    groups = {}
    for r in records:
        groups.setdefault(str(r["phase_id"]) if r["phase_id"] is not None else "unknown", []).append(r)
    windows = {}
    for name in (*WINDOWS, "unknown"):
        subset = [r for r in records if r["window"] == name]
        windows[name] = distribution(subset)
        windows[name]["straddle_count"] = sum(bool(r["straddle"]) for r in subset)
    coverage = {}
    for i, name in enumerate(WINDOWS):
        lo = boundaries[i - 1] if boundaries and i else None
        hi = boundaries[i] if boundaries and i < len(boundaries) else None
        starts = [r["start_ns"] for r in records if r["start_ns"] is not None]
        ends = [r["end_ns"] for r in records if r["end_ns"] is not None]
        coverage[name] = {"requested_start_ns": lo, "requested_end_ns": hi,
                          "capture_start_ns": min(starts) if starts else None,
                          "capture_end_ns": max(ends) if ends else None,
                          "bounded_window_covered": (
                              min(starts) <= lo and max(ends) >= hi
                              if starts and ends and lo is not None and hi is not None else None)}
    environment = collections.Counter(json.dumps(r["environment"], sort_keys=True) for r in records[1:])
    return {"summary": capture["summary"], "distribution": distribution(records),
            "windows": windows, "window_coverage": coverage,
            "phases": {key: distribution(rows) for key, rows in groups.items()},
            "environment_distribution": [{"environment": json.loads(key), "count": n}
                                         for key, n in environment.items()],
            "issues": issues, "records": records,
            "unpaired_last_render": {
                key: capture["frames"].get(key, [])[-1] if capture["frames"].get(key) else None
                for key in ("render_cpu_ns", "render_gpu_ns")}}


def comparison_details(results, field):
    variants = collections.defaultdict(list)
    values = {}
    for run in results:
        value = run.get(field)
        key = json.dumps(value, sort_keys=True)
        variants[key].append(run["id"])
        values[key] = value
    def known_value(value):
        if isinstance(value, dict):
            return all(known_value(v) for v in value.values())
        if isinstance(value, list):
            return all(known_value(v) for v in value)
        return value is not None and value != "unknown"
    known = all(isinstance(r.get(field), dict) and bool(r[field]) and known_value(r[field])
                for r in results)
    return {"comparable": bool(results) and known and len(variants) == 1,
            "variants": [{"value": values[key], "run_ids": ids} for key, ids in variants.items()]}


def grouped_distributions(results):
    groups = {}
    for kind in ("laser", "native"):
        for mode in ("full", "run"):
            runs = [r for r in results if r["kind"] == kind and r["mode"] == mode]
            rows = [dict(row, run_id=r["id"]) for r in runs if r["capture"]
                    for row in r["capture"]["records"]]
            groups[kind + "_" + mode] = {
                "run_ids": [r["id"] for r in runs],
                "distribution": distribution(rows),
                "windows": {name: distribution([r for r in rows if r["window"] == name])
                            for name in (*WINDOWS, "unknown")},
                "straddles": [{"run_id": r["run_id"], "frame_id": r["frame_id"],
                               "interval_ns": r["interval_ns"], "window": r["window"],
                               "boundaries": r["straddle"]} for r in rows if r["straddle"]],
                "trigger_timing": {
                    label: {metric: stats([t[metric] for r in runs for t in r["timing"]["triggers"]
                                           if (t["index"] == 0) == (label == "first")], signed=True)
                            for metric in ("dispatch_jitter_ns", "ack_delay_ns", "first_active_delay_ns")}
                    for label in ("first", "subsequent")},
                "overlap_at_dispatch": [
                    {"run_id": r["id"], "trigger_index": t["index"],
                     "snapshot": t["overlap_at_dispatch"]} for r in runs for t in r["timing"]["triggers"]],
            }
    return groups


def analyze_manifest(manifest, base):
    if not isinstance(manifest, dict) or manifest.get("schema") != SCHEMA:
        raise ValueError("Expected schema " + SCHEMA)
    runs = manifest.get("runs")
    if not isinstance(runs, list):
        raise ValueError("runs must be an array")
    results = []
    cells = collections.defaultdict(list)
    ids = []
    pids = []
    for index, run in enumerate(runs):
        if not isinstance(run, dict):
            raise ValueError("Each run must be an object")
        errors = []
        kind, mode, iteration = run.get("kind"), run.get("mode"), run.get("iteration")
        valid_cell = kind in ("laser", "native") and mode in ("full", "run") and integer(iteration) and iteration in (1, 2, 3)
        if not valid_cell:
            errors.append("invalid_matrix_cell")
        else:
            cells[(kind, mode, iteration)].append(index)
        rid = run.get("id")
        if not isinstance(rid, str) or not rid:
            errors.append("run_id_missing")
        else:
            ids.append(rid)
        pid = run.get("game_pid")
        if not integer(pid) or pid <= 0:
            errors.append("game_pid_unknown")
        else:
            pids.append(pid)
        timing = trigger_analysis(run)
        errors.extend(timing["errors"])
        artifacts = {key: artifact(base, run, key) for key in ARTIFACTS}
        data = None
        if artifacts["frames"]["status"] == "present":
            try:
                data = analyze_capture(read_json(artifacts["frames"]["path"]), timing)
                errors.extend(data["issues"])
                if data["distribution"]["interval_ns"]["count"] == 0:
                    errors.append("no_valid_intervals")
                if data["summary"].get("error") is not None:
                    errors.append("capture_error")
                if data["summary"].get("state") != "DURATION":
                    errors.append("capture_not_full_duration")
                coverage = data["window_coverage"]
                if any(coverage[name]["bounded_window_covered"] is not True for name in WINDOWS[1:4]):
                    errors.append("trigger_windows_not_fully_captured")
            except (OSError, ValueError, TypeError, KeyError) as failure:
                errors.append("frames_invalid:" + str(failure))
        else:
            errors.append("frames_" + artifacts["frames"]["status"])
        prewarm = None
        if artifacts["prewarm_frames"]["status"] == "present":
            try:
                prewarm = analyze_capture(read_json(artifacts["prewarm_frames"]["path"]), trigger_analysis({}))
            except (OSError, ValueError, TypeError, KeyError) as failure:
                errors.append("prewarm_invalid:" + str(failure))
        results.append({"id": rid, "kind": kind, "mode": mode, "iteration": iteration,
                        "game_pid": pid, "errors": errors, "timing": timing,
                        "settings": run.get("settings"), "probe_availability": run.get("probe_availability", "unknown"),
                        "artifacts": artifacts, "capture": data, "prewarm": prewarm})
    expected = [(k, m, i) for k in ("laser", "native") for m in ("full", "run") for i in (1, 2, 3)]
    matrix = [{"kind": k, "mode": m, "iteration": i, "run_indices": cells[(k, m, i)],
               "status": "missing" if not cells[(k, m, i)] else
               "duplicate" if len(cells[(k, m, i)]) > 1 else
               "valid" if not results[cells[(k, m, i)][0]]["errors"] else "incomplete"}
              for k, m, i in expected]
    configuration = comparison_details(results, "settings")
    probes = comparison_details(results, "probe_availability")
    problems = []
    if len(runs) != 12:
        problems.append("expected_12_runs")
    if len(set(ids)) != len(ids):
        problems.append("duplicate_run_ids")
    if len(pids) != 12 or len(set(pids)) != 12:
        problems.append("12_distinct_jvm_pids_not_demonstrated")
    warnings = []
    if not configuration["comparable"]:
        warnings.append("settings_different_or_unknown")
    if not probes["comparable"]:
        warnings.append("probe_combinations_different_or_unknown")
    return {
        "schema": SCHEMA + "-analysis", "matrix_complete": (
            not problems and all(c["status"] == "valid" for c in matrix)),
        "validation_status": "incomplete" if problems or any(c["status"] != "valid" for c in matrix)
        else "matrix_data_complete",
        "configuration_comparable": configuration["comparable"],
        "probe_configuration_comparable": probes["comparable"],
        "strict_ab_comparable": False,
        "comparison_status": "strict_ab_not_established",
        "comparison_details": {"settings": configuration, "probe_availability": probes},
        "comparison_warnings": warnings,
        "performance_acceptance": "unknown",
        "run_count": len(runs), "confirmed_started_triggers": sum(
            t["started"] is True for r in results for t in r["timing"]["triggers"]),
        "matrix": matrix, "issues": problems + warnings, "execution_issues": problems,
        "groups": grouped_distributions(results), "runs": results,
        "method": {
            "quantiles": "nearest rank; unavailable values excluded and counted; groups pool frames, not run percentiles",
            "budget": "sum(max(interval - threshold, 0)); strict >; sequences never cross run boundaries",
            "completeness": "matrix_complete is execution/data integrity only; comparison warnings do not imply strict A/B",
            "interval": "interval[i] belongs to previous render; CPU/GPU[i-1], allocation[i]",
            "windows": "observed dispatch boundaries, ownership by interval start; no clipping; straddles retained",
            "timing": "same JVM monotonic ns required; observed dispatch is not first active frame",
            "jvm_identity": "distinct PIDs are evidence only; PID reuse/start identity must be checked externally",
            "limits": "overlapping activity/background may differ; not pure single-instance cold/warm A/B; "
                      "kind differences are not pure extension cost; CPU and GPU are never added",
            "missing": "null/unknown or explicit not_enabled retained; never imputed zero",
        },
    }


def markdown(report):
    lines = ["# Photon repeat frame analysis", "",
             "Validation: " + report["validation_status"],
             "Matrix complete: " + str(report["matrix_complete"]).lower(),
             "Configuration comparable: " + str(report["configuration_comparable"]).lower(),
             "Probe configuration comparable: " + str(report["probe_configuration_comparable"]).lower(),
             "Strict A/B comparable: false (not established)",
             "Performance acceptance: unknown", "",
             "| Kind | Mode | Iteration | Status |", "|---|---|---|---|"]
    lines += ["| {kind} | {mode} | {iteration} | {status} |".format(**c) for c in report["matrix"]]
    lines += ["", "Issues: " + (", ".join(report["issues"]) or "none"), ""]
    lines += ["Comparison warnings: " + (", ".join(report["comparison_warnings"]) or "none"), ""]
    for name, group in report["groups"].items():
        lines += ["## Group " + name, "",
                  "| Window | N | P50 ms | P95 ms | P99 ms | Max ms |",
                  "|---|---:|---:|---:|---:|---:|"]
        for window, data in {"all": group["distribution"], **group["windows"]}.items():
            s = data["interval_ns"]
            values = ["unknown" if s[k] is None else format(s[k] / NS_PER_MS, ".6f")
                      for k in ("p50", "p95", "p99", "max")]
            lines.append("| " + " | ".join([window, str(s["count"]), *values]) + " |")
        lines += ["", "| Threshold ms | Count | Longest consecutive | Over budget ms |",
                  "|---:|---:|---:|---:|"]
        for s in group["distribution"]["slow_frames"].values():
            excess = s["cumulative_over_budget_ns"]
            lines.append(f"| {s['threshold_ms']} | {s['count']} | {s['longest_consecutive']} | "
                         + ("unknown" if excess is None else format(excess / NS_PER_MS, ".6f")) + " |")
        lines.append("")
    for run in report["runs"]:
        lines += ["## " + str(run["id"]), "", "Issues: " + (", ".join(run["errors"]) or "none")]
        if run["capture"]:
            lines += ["", "| Window | Valid intervals | P95 ms | P99 ms | Max ms |",
                      "|---|---:|---:|---:|---:|"]
            for name, window in run["capture"]["windows"].items():
                s = window["interval_ns"]
                fmt = lambda x: "unknown" if x is None else format(x / NS_PER_MS, ".6f")
                lines.append(f"| {name} | {s['count']} | {fmt(s['p95'])} | {fmt(s['p99'])} | {fmt(s['max'])} |")
        lines.append("")
    lines += [v for v in report["method"].values()]
    return "\n".join(lines) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=pathlib.Path)
    parser.add_argument("--output-dir", type=pathlib.Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        import unittest
        suite = unittest.defaultTestLoader.discover(str(pathlib.Path(__file__).parent),
                                                  pattern="test_analyze_repeat_frames.py")
        if suite.countTestCases() == 0:
            parser.error("Synthetic test file not found")
        return 0 if unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() else 1
    if args.manifest is None or args.output_dir is None:
        parser.error("--manifest and --output-dir are required")
    try:
        report = analyze_manifest(read_json(args.manifest), args.manifest.resolve().parent)
        args.output_dir.mkdir(parents=True, exist_ok=True)
        with (args.output_dir / "analysis.json").open("x", encoding="utf-8") as stream:
            json.dump(report, stream, ensure_ascii=False, indent=2, allow_nan=False)
        with (args.output_dir / "report.md").open("x", encoding="utf-8") as stream:
            stream.write(markdown(report))
    except (OSError, ValueError, TypeError) as failure:
        print(str(failure), file=sys.stderr)
        return 1
    print(json.dumps({"matrix_complete": report["matrix_complete"],
                      "validation_status": report["validation_status"],
                      "output_dir": str(args.output_dir.resolve())}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
