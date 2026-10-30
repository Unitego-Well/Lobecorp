"""Read-only audit of photon-repeat-matrix-v1 inputs; JSON report goes to stdout.

Run with python -B audit_repeat_inputs.py MANIFEST. Exit 0 means complete audit
pass, 1 means failed constraints, 2 means unknown/incomplete/not_run or input error.
Runtime snapshots are evidence, static argument templates are never substitutes.
metadata must reference a same-PID runtimeStatus snapshot (directly or under
runtime_status), containing test_environment or env. BASE in trial maps to run
in manifest. Warnings preserve lossy quality/configuration differences and exit 2.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import shlex

SCHEMA = "photon-repeat-matrix-v1"
CONFIG = "src/main/java/org/unitego/lobecorp/config/photon/PhotonRepeatedEffectConfig.java"
INTERVAL_TOLERANCE_MS = 250.0
QUALITY_FIELDS = (
    "deniedNodeRequests", "deniedThreadRequests", "droppedImportantMarks",
    "droppedRawSpans", "droppedDepthSpans", "deniedAggregateRequests",
    "deniedCounterRequests",
)
ENV_KEYS = (
    "width", "height", "focused", "maximized", "iconified", "fullscreen",
    "vsync", "fps_limit", "render_distance", "fov", "shader_pack", "graphics",
    "particles_setting", "sodium", "spark", "cast_requested",
    "submission_requested", "resource_packs",
)


def number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def timestamp(value):
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def test_vm_argument(argument):
    """Mirror PhotonRepeatedEffectConfig.isTestVmArgument, including false-valued flags."""
    lower = argument.lower()
    if lower.startswith("-d"):
        key = lower[2:].split("=", 1)[0]
        return (key.startswith(("modbench.", "lobecorp.photon.", "mc_debug_",
                                "minecraft.debug.", "spark.", "jdk.jfr."))
                or "gametest" in key or "hotswap" in key)
    return any(part in lower for part in
               ("jdwp", "flightrecord", "hotswap", "enhancedclassredefinition"))


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def file_hash(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_policy(root):
    text = (root / CONFIG).read_text(encoding="utf-8-sig")
    constants = dict(re.findall(r'public static final (?:String|int) (\w+) = ("[^"]*"|\d+);', text))
    needed = ("COUNT", "INTERVAL_SECONDS", "WORLD_PREFIX", "FX_SHA256",
              "MATERIAL_SHA256", "TEXTURE_SHA256")
    return {key: json.loads(constants[key]) for key in needed}


def outcome(checks):
    states = {item["status"] for item in checks}
    return ("fail" if "fail" in states else "unknown" if "unknown" in states
            else "warn" if "warn" in states else "pass")


def add(checks, name, status, evidence):
    checks.append({"check": name, "status": status, "evidence": evidence})


def verify(checks, name, actual, expected):
    add(checks, name, "unknown" if actual is None or expected is None else
        "pass" if actual == expected else "fail", {"actual": actual, "expected": expected})


def resolve(base, value):
    if not isinstance(value, str) or not value.strip():
        raise ValueError("missing path")
    path = Path(value)
    return path if path.is_absolute() else base / path


def get_object(base, value, loader, checks, name):
    try:
        data = loader(resolve(base, value))
        if not isinstance(data, dict):
            raise ValueError("expected JSON object")
        return data
    except (OSError, ValueError, TypeError) as error:
        add(checks, name, "unknown", str(error))
        return {}


def audit_triggers(run, trial, policy, tolerance_ms, checks):
    count = policy["COUNT"]
    events = trial.get("triggers")
    manifest_events = run.get("triggers")
    for name, items in (("trial", events), ("manifest", manifest_events)):
        valid = (isinstance(items, list) and len(items) == count
                 and all(isinstance(item, dict) for item in items)
                 and all(type(item.get("index")) is int for item in items)
                 and sorted(item["index"] for item in items) == list(range(count)))
        add(checks, name + "_three_triggers", "pass" if valid else
            "unknown" if items is None else "fail", items)
        if not valid:
            return []
    events = sorted(events, key=lambda item: item["index"])
    manifest_events = sorted(manifest_events, key=lambda item: item["index"])
    timing = []
    for event, mapped in zip(events, manifest_events):
        index = event["index"]
        verify(checks, f"trigger_{index}_started", event.get("started"), True)
        verify(checks, f"manifest_trigger_{index}_started", mapped.get("started"), True)
        for key in ("requested_ns", "first_active_ns"):
            if key == "first_active_ns" and key not in mapped:
                continue
            verify(checks, f"trigger_{index}_{key}_mapping", mapped.get(key), event.get(key))
        for key in ("requested_ns", "dispatch_ns", "actual_ns", "first_active_ns"):
            add(checks, f"trigger_{index}_{key}", "pass" if timestamp(event.get(key)) else
                "unknown", event.get(key))
        observed = mapped.get("observed_ns")
        scope = run.get("trigger_time_scope")
        add(checks, f"trigger_{index}_observed_scope", "pass" if
            timestamp(observed) and isinstance(scope, str) and scope.strip() else "unknown",
            {"observed_ns": observed, "scope": scope,
             "matches_trial_fields": [key for key in ("dispatch_ns", "actual_ns", "first_active_ns")
                                      if timestamp(observed) and observed == event.get(key)]})
        if timestamp(observed) and observed not in (
                event.get("dispatch_ns"), event.get("actual_ns"), event.get("first_active_ns")):
            add(checks, f"trigger_{index}_observed_mapping", "unknown",
                "observed timestamp does not match a recorded trial clock field")
        values = [event.get(key) for key in ("requested_ns", "dispatch_ns", "actual_ns", "first_active_ns")]
        if all(timestamp(value) for value in values):
            requested, dispatch, actual, active = values
            add(checks, f"trigger_{index}_clock_order", "pass" if
                requested <= dispatch <= actual <= active else "fail", values)
            timing.append({"index": index, "dispatch_lateness_ms": (dispatch - requested) / 1e6,
                           "actual_lateness_ms": (actual - requested) / 1e6,
                           "activation_latency_ms": (active - actual) / 1e6,
                           "actual_scope": "native start invocation" if trial.get("kind") == "NATIVE"
                           else "server cast invocation; not effect first frame"})
    intervals = []
    for left, right in zip(events, events[1:]):
        row = {"from_index": left["index"], "to_index": right["index"]}
        for key in ("requested_ns", "dispatch_ns", "actual_ns", "first_active_ns"):
            if timestamp(left.get(key)) and timestamp(right.get(key)):
                seconds = (right[key] - left[key]) / 1e9
                jitter = (seconds - policy["INTERVAL_SECONDS"]) * 1000
                row[key] = {"interval_seconds": seconds, "jitter_ms": jitter}
                if key != "first_active_ns":
                    add(checks, f"interval_{left['index']}_{key}",
                        "pass" if abs(jitter) <= tolerance_ms else "fail", row[key])
        intervals.append(row)
    return {"triggers": timing, "intervals": intervals, "tolerance_ms": tolerance_ms,
            "scope": "adjacent start invocations; overlapping instances allowed"}


def audit_quality(trial, session, mode, checks):
    if mode == "run":
        if not trial.get("main_trace") and not session:
            add(checks, "detailed_trace_quality", "not_enabled",
                "BASE does not enable detailed trace; no counters inferred as zero")
            return
    trace = trial.get("main_trace", {})
    retention = trace.get("retention", {}) if isinstance(trace, dict) else {}
    if not isinstance(retention, dict):
        retention = {}
    quality = retention.get("denied_dropped_quality", {})
    if not isinstance(quality, dict):
        quality = {}
    session_quality = session.get("collector_quality", {})
    if not isinstance(session_quality, dict):
        session_quality = {}
    for source, values in (("trial", quality), ("session", session_quality)):
        if source == "session" and not session:
            continue
        for key in QUALITY_FIELDS:
            value = values.get(key)
            add(checks, source + "_quality_" + key,
                "unknown" if not number(value) or value < 0 else "pass" if value == 0 else "warn", value)
        add(checks, source + "_quality_limit", "unknown" if "limit" not in values else
            "pass" if values["limit"] is None else "warn", values.get("limit"))
    for key in ("raw_truncated", "aggregate_truncated"):
        value = retention.get(key)
        add(checks, key, "unknown" if not isinstance(value, bool) else
            "warn" if value else "pass", value)
    for key in ("dropped_records", "overwritten_events", "incomplete_spans"):
        value = session.get(key)
        add(checks, key, "unknown" if not number(value) or value < 0 else
            "warn" if value else "pass", value)
    add(checks, "raw_coverage_claim", "info",
        "bounded retained events are not proof of complete event coverage")


def audit_assets(trial, policy, checks, hasher):
    assets = trial.get("assets")
    if not isinstance(assets, list) or not assets:
        add(checks, "asset_hashes", "unknown", "runtime assets missing")
        return {}
    stable = {}
    found = set()
    fx_sources = []
    for item in assets:
        if not isinstance(item, dict):
            add(checks, "asset_record", "fail", item)
            continue
        source, digest = item.get("source"), item.get("sha256")
        valid = (isinstance(source, str) and isinstance(digest, str)
                 and re.fullmatch(r"[0-9a-fA-F]{64}", digest) is not None)
        add(checks, "asset_hash_record", "pass" if valid else "unknown", item)
        if not valid:
            continue
        normalized = source.replace("\\", "/")
        world_path = trial.get("world_path")
        world_snapshot = (isinstance(world_path, str) and
                          normalized == world_path.replace("\\", "/").rstrip("/") + "/level.dat")
        if normalized.endswith(".fx"):
            fx_sources.append(source)
        expected = item.get("expected_sha256")
        for suffix, key in (("first_effect2.fx", "FX_SHA256"),
                            ("particle.material.nbt", "MATERIAL_SHA256"),
                            ("particle.png", "TEXTURE_SHA256")):
            if normalized.endswith(suffix):
                found.add(key)
                expected = policy[key]
        if expected is not None:
            verify(checks, "asset_expected:" + source, digest.lower(), str(expected).lower())
        game_directory = str(trial.get("game_directory", "")).replace("\\", "/").rstrip("/")
        if source != "world_name_seed_dimension" and not world_snapshot:
            stable[normalized[len(game_directory):] if game_directory and
                   normalized.startswith(game_directory + "/") else normalized] = digest.lower()
        if item.get("source_pack") == "file":
            try:
                file_path = Path(normalized)
                if not file_path.is_absolute():
                    if not game_directory:
                        raise ValueError("relative asset has no recorded game directory")
                    file_path = Path(game_directory) / file_path
                actual = hasher(file_path)
                if world_snapshot:
                    add(checks, "world_snapshot:" + source, "info",
                        {"snapshot_sha256": digest.lower(), "current_sha256": actual.lower(),
                         "snapshot_phase": "trial initial world snapshot",
                         "current_phase": "offline audit after trial; world saves may change level.dat",
                         "classification": "mutable_world_snapshot",
                         "snapshot_matches_current": actual.lower() == digest.lower()})
                else:
                    verify(checks, "asset_current_file:" + source, actual.lower(), digest.lower())
            except (OSError, ValueError) as error:
                add(checks, "asset_current_file:" + source, "unknown", str(error))
    if trial.get("kind") == "NATIVE":
        for key in ("FX_SHA256", "MATERIAL_SHA256", "TEXTURE_SHA256"):
            add(checks, "native_asset:" + key, "pass" if key in found else "unknown", policy[key])
    add(checks, "workload_fx_hashes", "pass" if fx_sources else "unknown", fx_sources)
    return stable


def audit_run(run, base, policy, tolerance_ms, loader, hasher):
    checks = []
    trial = get_object(base, run.get("trial_report"), loader, checks, "trial_report")
    metadata = get_object(base, run.get("metadata"), loader, checks, "runtime_metadata")
    runtime = metadata.get("runtime_status", metadata)
    if not isinstance(runtime, dict):
        runtime = {}
    mode, kind = run.get("mode"), run.get("kind")
    verify(checks, "kind", trial.get("kind"), str(kind).upper())
    verify(checks, "mode", trial.get("mode"), "BASE" if mode == "run" else "FULL")
    verify(checks, "state", trial.get("state"), "COMPLETE")
    verify(checks, "workload_verified", trial.get("workload_verified"), True)
    if trial.get("error") or trial.get("cancelled") or trial.get("cleanup_unconfirmed"):
        add(checks, "trial_failure", "fail", {key: trial.get(key) for key in
                                           ("error", "cancelled", "cleanup_unconfirmed")})
    pid = trial.get("pid")
    add(checks, "pid", "pass" if type(pid) is int and pid > 0 else "unknown", pid)
    verify(checks, "manifest_pid", run.get("game_pid"), pid)
    verify(checks, "metadata_pid", runtime.get("pid"), pid)
    world, world_path = trial.get("world"), trial.get("world_path")
    add(checks, "owned_world", "unknown" if not isinstance(world, str) or
        not isinstance(world_path, str) else "pass" if world.startswith(policy["WORLD_PREFIX"])
        and world_path.replace("\\", "/").rstrip("/").split("/")[-1] == world else "fail",
        {"world": world, "world_path": world_path, "seed": trial.get("world_seed")})
    args = trial.get("vm_arguments")
    if not isinstance(args, list) or not all(isinstance(arg, str) for arg in args):
        add(checks, "real_vm_arguments", "unknown", "trial runtime vm_arguments missing")
        args = None
    else:
        effective_properties = {}
        for arg in args:
            if arg.startswith("-D"):
                key, separator, value = arg[2:].partition("=")
                effective_properties[key] = value if separator else ""
        flags = [arg for arg in args if test_vm_argument(arg)]
        add(checks, "real_vm_arguments", "fail" if mode == "run" and flags else "pass",
            {"source": "trial runtime MXBean snapshot", "arguments": args, "test_arguments": flags,
             "effective_system_properties": effective_properties})
        if "vm_arguments" in runtime:
            verify(checks, "metadata_vm_arguments", runtime["vm_arguments"], args)
        if mode == "full":
            add(checks, "full_debug", "pass" if any("jdwp" in arg.lower() for arg in args) else "fail", args)
            verify(checks, "full_scenario", effective_properties.get("modBench.scenarios"),
                   "lobecorp.photon-03-repeat-performance")
    env = runtime.get("test_environment", runtime.get("env"))
    if not isinstance(env, dict) or runtime.get("pid") != pid or not timestamp(pid):
        add(checks, "process_environment", "unknown",
            "requires same-PID runtime metadata test_environment (filtered snapshot) or env")
    else:
        flags = {key: value for key, value in env.items()
                 if key.startswith(("LOBECORP_LASER_PROFILE", "MC_DEBUG_"))}
        injected = {key: env[key] for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")
                    if env.get(key)}
        add(checks, "process_environment", "fail" if mode == "run" and flags else "pass",
            {"test_environment": flags, "java_injection_environment": injected,
             "scope": "filtered runtime snapshot" if "test_environment" in runtime else "runtime env"})
        if injected and mode == "run":
            add(checks, "java_environment_injection", "unknown",
                "nonempty launcher injection variables require reconciliation with actual JVM arguments")
    environment = trial.get("initial_environment")
    if not isinstance(environment, dict):
        environment = {}
    for key in ENV_KEYS:
        add(checks, "environment:" + key, "unknown" if environment.get(key) is None else "pass", environment.get(key))
    mods = trial.get("mod_versions")
    add(checks, "mod_versions", "pass" if isinstance(mods, dict) and mods else "unknown", mods)
    add(checks, "probe_availability", "info", run.get("probe_availability", "unknown"))
    main_window = trial.get("main_frames")
    duration_window = isinstance(main_window, dict) and main_window.get("state") == "DURATION"
    add(checks, "window_environment_sources", "info",
        {"initial": environment, "runtime_status": runtime.get("environment"),
         "scope": "trial.main_frames DURATION main window" if duration_window else "initial environment",
         "environment_first": main_window.get("environment_first") if duration_window else None,
         "environment_last": main_window.get("environment_last") if duration_window else None})
    for key, expected in (("maximized", True), ("iconified", False), ("shader_pack", False), ("sodium", True)):
        if duration_window and key in ("maximized", "iconified"):
            values = []
            for phase in ("environment_first", "environment_last"):
                snapshot = main_window.get(phase)
                value = snapshot.get(key) if isinstance(snapshot, dict) else None
                values.append(value)
                verify(checks, "main_window:" + phase + ":" + key, value, expected)
            add(checks, "window_or_renderer:" + key,
                "fail" if any(value is not None and value != expected for value in values) else
                "unknown" if None in values else "pass",
                {"actual": values, "expected": expected, "scope": "DURATION main window endpoints"})
        else:
            verify(checks, "window_or_renderer:" + key, environment.get(key), expected)
    changes = []
    samples = trial.get("samples")
    if not isinstance(samples, list) or not samples:
        add(checks, "sampled_environment", "unknown", "no runtime environment samples")
    else:
        for sample in samples:
            if not isinstance(sample, dict) or not isinstance(sample.get("environment"), dict):
                add(checks, "sampled_environment", "unknown", sample)
                continue
            sampled = sample["environment"]
            delta = {key: {"initial": environment.get(key), "sample": sampled.get(key)}
                     for key in ENV_KEYS if sampled.get(key) != environment.get(key)}
            if delta:
                changes.append({"observed_ns": sample.get("observed_ns"), "differences": delta})
            if sampled.get("iconified") is True or sampled.get("maximized") is False:
                add(checks, "sampled_window", "fail", sample)
            missing = [key for key in ENV_KEYS if key not in sampled]
            if missing:
                add(checks, "sampled_environment", "unknown", missing)
    add(checks, "environment_changes", "warn" if changes else "info", changes)
    frames = get_object(base, run.get("frames"), loader, checks, "frames")
    columns = frames.get("frames")
    summary = frames.get("summary")
    valid_frames = (isinstance(columns, dict) and isinstance(summary, dict)
                    and isinstance(columns.get("interval_ns"), list) and bool(columns["interval_ns"])
                    and summary.get("frame_count") == len(columns["interval_ns"]))
    add(checks, "frame_input", "pass" if valid_frames else "unknown",
        "requires actual PhotonFrameCapture column arrays and matching summary.frame_count")
    if not isinstance(summary, dict):
        summary = {}
    main_frames = trial.get("main_frames", {})
    if not isinstance(main_frames, dict):
        main_frames = {}
    for key in ("label", "started_ns", "frame_count"):
        verify(checks, "frame_trial_mapping:" + key, summary.get(key), main_frames.get(key))
    verify(checks, "frame_trial_label", summary.get("label"),
           str(trial["label"]) + "_main" if trial.get("label") else None)
    session = {}
    if mode == "full":
        session = get_object(base, str(resolve(base, run["trace_directory"]) / "session.json")
                             if run.get("trace_directory") else None, loader, checks, "trace_session")
        for key in ("jfr", "spark"):
            try:
                digest = hasher(resolve(base, run.get(key)))
                add(checks, key + "_artifact", "unknown" if digest == hashlib.sha256(b"").hexdigest()
                    else "pass", {"path": run[key], "sha256": digest})
            except (OSError, ValueError) as error:
                add(checks, key + "_artifact", "unknown", str(error))
        for key in ("spark", "cast_requested", "submission_requested"):
            verify(checks, "full_probe:" + key, environment.get(key), True)
    timing = audit_triggers(run, trial, policy, tolerance_ms, checks)
    hashes = audit_assets(trial, policy, checks, hasher)
    audit_quality(trial, session, mode, checks)
    return {"id": run.get("id"), "kind": kind, "mode": mode, "iteration": run.get("iteration"),
            "runtime_evidenced": timestamp(pid) and args is not None and bool(trial.get("world_path")),
            "status": outcome(checks), "checks": checks, "timing": timing,
            "quality_snapshots": {key: trial.get(key) for key in
                                  ("main_trace", "prewarm_trace", "main_frames", "prewarm_frames",
                                   "main_submissions", "prewarm_submissions")},
            "identity": {"pid": pid, "world": world, "world_path": world_path},
            "comparison": {"environment": environment, "settings": run.get("settings"),
                           "vm_arguments": args, "process_environment": env,
                           "metadata_settings": metadata.get("settings"),
                           "mod_versions": trial.get("mod_versions"), "asset_hashes": hashes}}


def audit_manifest(manifest, base, policy, tolerance_ms=INTERVAL_TOLERANCE_MS,
                   loader=read_json, hasher=file_hash):
    checks = []
    if not isinstance(manifest, dict):
        return {"status": "fail", "error": "manifest must be a JSON object", "runs": []}
    verify(checks, "schema", manifest.get("schema"), SCHEMA)
    runs = manifest.get("runs")
    if not isinstance(runs, list):
        add(checks, "runs", "fail", "runs must be an array")
        runs = []
    if not runs:
        return {"status": "not_run" if outcome(checks) == "pass" else outcome(checks),
                "executed_runs": 0, "expected_runs": 12, "checks": checks, "runs": [],
                "message": "未运行；空矩阵仅可检查工具与静态配置，不能证明12局/36次成功"}
    expected = {(kind, mode, iteration) for kind in ("laser", "native")
                for mode in ("full", "run") for iteration in range(1, 4)}
    actual, ids, results = [], set(), []
    for run in runs:
        if not isinstance(run, dict):
            add(checks, "run_object", "fail", run)
            continue
        cell = (run.get("kind"), run.get("mode"), run.get("iteration"))
        valid = (isinstance(cell[0], str) and isinstance(cell[1], str)
                 and type(cell[2]) is int and cell in expected)
        add(checks, "matrix_cell", "pass" if valid else "fail", list(cell))
        if valid:
            actual.append(cell)
        identifier = run.get("id")
        unique = isinstance(identifier, str) and bool(identifier) and identifier not in ids
        add(checks, "unique_run_id", "pass" if unique else "fail", identifier)
        if isinstance(identifier, str):
            ids.add(identifier)
        effective_run = dict(run)
        if "trigger_time_scope" not in effective_run and "trigger_time_scope" in manifest:
            effective_run["trigger_time_scope"] = manifest["trigger_time_scope"]
        results.append(audit_run(effective_run, base, policy, tolerance_ms, loader, hasher))
    add(checks, "matrix_coverage", "pass" if set(actual) == expected and len(actual) == 12 else "unknown",
        {"missing": [list(cell) for cell in sorted(expected - set(actual))], "run_count": len(results)})
    if len(actual) != len(set(actual)):
        add(checks, "duplicate_matrix_cell", "fail", [list(cell) for cell in actual])
    for key in ("pid", "world", "world_path"):
        values = [result["identity"][key] for result in results]
        known = [json.dumps(value, sort_keys=True) for value in values if value is not None]
        add(checks, "new_" + key, "fail" if len(known) != len(set(known)) else
            "unknown" if len(known) != len(values) else "pass", values)
    differences = []
    if results:
        reference = results[0]
        for result in results[1:]:
            delta = {key: {"reference": reference["comparison"][key], "actual": value}
                     for key, value in result["comparison"].items()
                     if value != reference["comparison"][key]}
            if delta:
                differences.append({"reference_id": reference["id"], "id": result["id"], "differences": delta})
    add(checks, "between_run_configuration_differences", "warn" if differences else "info", differences)
    for result in results:
        add(checks, "run:" + str(result["id"]), result["status"], "see per-run checks")
    return {"status": outcome(checks), "declared_runs": len(results),
            "executed_runs": sum(result["runtime_evidenced"] for result in results), "expected_runs": 12,
            "checks": checks, "runs": results,
            "interpretation": "input audit only; environment/probe differences are not pure probe overhead or visual acceptance"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--interval-tolerance-ms", type=float, default=INTERVAL_TOLERANCE_MS)
    args = parser.parse_args(argv)
    try:
        if not math.isfinite(args.interval_tolerance_ms) or args.interval_tolerance_ms < 0:
            raise ValueError("tolerance must be finite and nonnegative")
        root = Path(__file__).resolve().parents[3]
        policy = load_policy(root)
        report = audit_manifest(read_json(args.manifest), args.manifest.resolve().parent,
                                policy, args.interval_tolerance_ms)
        template = root / "tools/photon/performance/config/run-control-vmargs.txt"
        tokens = shlex.split(template.read_text(encoding="utf-8-sig"), comments=True)
        report["static_template"] = {"path": str(template), "test_arguments":
                                     [token for token in tokens if test_vm_argument(token)],
                                     "scope": "static template only; not runtime JVM evidence"}
    except (OSError, ValueError, TypeError, KeyError) as error:
        report = {"status": "unknown", "error": str(error)}
    print(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False))
    return 0 if report["status"] == "pass" else 1 if report["status"] == "fail" else 2


if __name__ == "__main__":
    raise SystemExit(main())
