"""Read-only Photon redesign matrix planner; emits JSON to stdout only.

Future use: python -B tools/photon/performance/runtime_redesign_matrix.py
  --root WORKTREE --git-head VERIFIED_HEAD [--observations IDEA_SAVED_JSON]
Do not redirect stdout into the project. Persist returned JSON through IDEA.
No subprocess, project writes, imports from project modules, build or game.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import re
import xml.etree.ElementTree as ET

BASELINE = "ca2b926b20f485aed73a3c8142a02cc4725b7c52"
ORDER = "N01 A01 B01 C01 C02 B02 A02 N02 N03 A03 B03 C03".split()
ATTEMPTS = {"N01": "r1"}
MODES = {"N": "NATIVE", "A": "COMPILED", "B": "POOLED", "C": "INDEXED"}
EVIDENCE = "build/agent-backups/20261031-runtime-redesign"
VARIABLE_KEYS = {
    "modBench.client.worldId", "modBench.resultDirectory",
    "lobecorp.photon.creation.mode",
}
REQUIRED = {
    "modBench.scenarios": "lobecorp.photon-03-repeat-performance",
    "mcp.port": "19877",
    "lobecorp.photon.bench.controlFx": "",
    "lobecorp.photon.diag.cast": "true",
    "lobecorp.photon.diag.material": "true",
    "lobecorp.photon.bench.probes": "true",
    "lobecorp.photon.copyLedger.enabled": "true",
    "lobecorp.photon.bench.retentionProbe": "true",
    "lobecorp.photon.bench.materialReloadCheck": "true",
    "lobecorp.photon.bench.nativeModelCopy": "false",
    "lobecorp.photon.bench.eagerShaderBuiltin": "false",
    "lobecorp.photon.bench.preserveWindow": "true",
    "modBench.jfr": "true",
    "modBench.git.commit": BASELINE,
    "modBench.git.dirty": "true",
    "modBench.client.vsync": "true",
    "modBench.client.renderDistance": "16",
    "modBench.client.simulationDistance": "12",
    "modBench.client.requireWindowFocus": "false",
    "modBench.phaseTimeoutTicks": "12000",
    "lobecorp.photon.trace.maxAggregatesPerThread": "65536",
    "lobecorp.photon.trace.maxNodes": "16384",
    "lobecorp.photon.trace.maxCountersPerThread": "16384",
}
SOURCE_DIRS = (
    "src/main/java/org/unitego/lobecorp/client/photon",
    "src/main/java/org/unitego/lobecorp/util/photon",
    "src/main/java/org/unitego/lobecorp/config/photon",
    "src/main/java/org/unitego/lobecorp/mixin",
    "src/main/java/org/unitego/lobecorp/events/client",
    "src/bench/java",
    "src/main/resources",
)
FIXED_INPUTS = (
    "build.gradle", "settings.gradle", "gradle.properties",
    "gradle/libs.versions.toml", "gradle.lockfile",
    ".agent/plans/20261031-runtime-redesign.md",
    ".agent/plans/20261031-runtime-redesign-scheme-a.md",
    ".agent/plans/20261031-runtime-redesign-scheme-b.md",
    ".agent/plans/20261031-runtime-redesign-scheme-c.md",
    ".agent/reports/20261031-runtime-redesign-research.md",
    ".idea/runConfigurations/Photon_Copy_Material_B1.xml",
    "tools/photon/performance/config/run-control-vmargs.txt",
    "tools/photon/performance/runtime_redesign_matrix.py",
    "build/moddev/benchClientRunVmArgs.txt",
    "build/moddev/benchClientRunProgramArgs.txt",
)
ARTIFACT_ROLES = {
    "trial", "frames", "trace", "jfr", "spark", "console",
    "correctness", "creation_counters", "loaded_classes", "assets",
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_record(root, relative):
    path = root / relative
    if not path.is_file():
        return {"path": relative, "state": "missing", "sha256": None}
    data = path.read_bytes()
    return {"path": relative, "state": "present",
            "bytes": len(data), "sha256": sha(data)}


def fingerprint(records):
    canonical = json.dumps(records, sort_keys=True, separators=(",", ":"))
    return sha(canonical.encode("utf-8"))


def parameters(path):
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    values = {}
    for line in lines:
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if not line.startswith("-D") or "=" not in line:
            raise ValueError("unsupported matrix argument: " + line)
        key, value = line[2:].split("=", 1)
        if key in values:
            raise ValueError("duplicate matrix property: " + key)
        values[key] = value
    return values


def classify(root, run, observation):
    if observation is None:
        return {"classification": "NOT_RUN", "performance_acceptance": "unknown"}
    issues = []
    if observation.get("correctness") == "FAILED":
        category = "CORRECTNESS_FAILED"
    elif observation.get("state") == "FAILED":
        category = "EXECUTION_FAILED"
    else:
        category = "INCOMPLETE"
    for key in ("pid", "process_started_utc", "world_path", "trial_label"):
        if not observation.get(key):
            issues.append("missing_identity:" + key)
    if observation.get("world") != run["world"]:
        issues.append("world_mismatch")
    if observation.get("effective_creation_mode") != run["creation_mode"]:
        issues.append("effective_mode_mismatch")
    if observation.get("effective_properties") != run["properties"]:
        issues.append("effective_properties_mismatch")
    if observation.get("state") != "COMPLETE":
        issues.append("not_complete")
    if observation.get("correctness") != "PASSED":
        issues.append("correctness_unverified")
    if observation.get("primary_seconds") != 40:
        issues.append("capture_duration")
    if observation.get("main_frames_state") != "DURATION":
        issues.append("capture_not_duration")
    triggers = observation.get("triggers", [])
    if (len(triggers) != 3
            or [event.get("index") for event in triggers] != [0, 1, 2]
            or any(event.get("started") is not True for event in triggers)
            or [event.get("requested_offset_seconds") for event in triggers] != [2, 7, 12]):
        issues.append("triggers_incomplete")
    for key in ("workload_verified", "source_trace_verified", "environment_verified",
                "loaded_class_hashes_verified", "source_snapshot_matches",
                "process_closed", "debug_closed", "observer_closed"):
        if observation.get(key) is not True:
            issues.append("unverified:" + key)
    for key in ("error", "cancelled", "cleanup_unconfirmed"):
        if observation.get(key):
            issues.append(key)
    artifacts = observation.get("artifacts", [])
    roles = set()
    for artifact in artifacts:
        relative = artifact["path"]
        path = (root / relative).resolve()
        if not path.is_relative_to(root / "build"):
            raise ValueError("artifact outside owned build evidence: " + relative)
        actual = file_record(root, relative)
        if actual["sha256"] is None or actual["sha256"] != artifact.get("sha256"):
            issues.append("artifact_hash:" + relative)
        roles.add(artifact["role"])
    issues.extend("missing_artifact:" + role for role in sorted(ARTIFACT_ROLES - roles))
    if not issues:
        category = "COMPARABLE_CANDIDATE"
    return {"classification": category, "issues": issues, "artifacts": artifacts,
            "performance_acceptance": "unknown", "observation": observation}


def generate(root, head, observations):
    if not re.fullmatch(r"[0-9a-f]{40}", head):
        raise ValueError("--git-head must be a verified full Git SHA")
    if set(observations) - set(ORDER):
        raise ValueError("unknown observation IDs")
    runs = []
    common = None
    for position, identity in enumerate(ORDER, 1):
        suffix = identity.lower()
        arg = "tools/photon/performance/config/runtime-redesign-" + suffix + ".txt"
        xml = ".idea/runConfigurations/Photon_Redesign_" + identity + ".xml"
        values = parameters(root / arg)
        attempt_suffix = suffix + ATTEMPTS.get(identity, "")
        world = "photon_repeat_rd_" + attempt_suffix + "_1031"
        result = (root / ("build/modBench/raw-results/photon-runtime-redesign-"
                          + attempt_suffix + "-20261031/client")).as_posix()
        expected = dict(REQUIRED, **{
            "modBench.client.worldId": world, "modBench.resultDirectory": result,
            "lobecorp.photon.creation.mode": MODES[identity[0]],
        })
        if values != expected or len(world) > 30:
            raise ValueError("parameter contract mismatch: " + identity)
        shared = {key: value for key, value in values.items() if key not in VARIABLE_KEYS}
        if common is not None and common != shared:
            raise ValueError("common probes differ: " + identity)
        common = shared
        config = ET.parse(root / xml).getroot().find("configuration")
        if config is None or config.get("name") != "Photon Redesign " + identity:
            raise ValueError("run configuration identity: " + identity)
        vm = config.find("option[@name='VM_PARAMETERS']")
        if vm is None or not vm.get("value", "").endswith("@$PROJECT_DIR$/" + arg):
            raise ValueError("matrix argfile must be last: " + identity)
        make = config.find("method/option[@name='Make']")
        if make is None or make.get("enabled") != "false":
            raise ValueError("automatic Make must be disabled: " + identity)
        run = {
            "id": identity, "order": position, "creation_mode": MODES[identity[0]],
            "workload": "LASER", "capture_mode": "FULL", "primary_seconds": 40,
            "trigger_offsets_seconds": [2, 7, 12], "world_id": world, "world": world + "-flat",
            "result_directory": result, "properties": values,
            "configuration_files": [file_record(root, xml), file_record(root, arg)],
        }
        run["result"] = classify(root, run, observations.get(identity))
        runs.append(run)
    source_paths = set(FIXED_INPUTS)
    for directory in SOURCE_DIRS:
        source_paths.update(path.relative_to(root).as_posix()
                            for path in (root / directory).rglob("*") if path.is_file())
    sources = [file_record(root, path) for path in sorted(source_paths)]
    classes = [file_record(root, path.relative_to(root).as_posix())
               for path in sorted((root / "build/classes/java").rglob("*.class"))]
    libs = [file_record(root, path.relative_to(root).as_posix())
            for path in sorted((root / "lib").rglob("*.jar"))]
    source_sha = fingerprint(sources)
    schemes = {}
    for letter, mode in MODES.items():
        selected = [run for run in runs if run["creation_mode"] == mode]
        args = [record for run in selected for record in run["configuration_files"]
                if record["path"].endswith(".txt")]
        snapshot = {
            "schema": "photon-runtime-redesign-scheme-snapshot-v1",
            "baseHEAD": BASELINE, "observedHEAD": head, "mode": mode,
            "sourceSHA": source_sha, "source_files": sources,
            "argsSHA": fingerprint(args), "args_files": args,
            "state": "preparation_snapshot_not_runtime_frozen",
            "source_backup_directory": EVIDENCE + "/scheme-" + mode.lower() + "/source",
            "restore": "IDEA byte backups only; do not overwrite concurrent WIP",
        }
        schemes[letter] = {
            "snapshot": snapshot, "snapshotSHA": fingerprint(snapshot),
            "manifest": {
                "schema": "photon-runtime-redesign-scheme-manifest-v1",
                "baseHEAD": BASELINE, "observedHEAD": head, "mode": mode,
                "sourceSHA": source_sha, "argsSHA": fingerprint(args),
                "snapshotSHA": fingerprint(snapshot),
                "run_ids": [run["id"] for run in selected],
                "runtime_loaded_source_verified": False,
            },
        }
    return {
        "scheme_snapshots": schemes,
        "schema": "photon-runtime-redesign-matrix-v1",
        "baseline_commit": BASELINE, "operator_verified_head": head,
        "snapshot_kind": "current_disk_not_frozen_or_loaded_runtime",
        "expected_runs": 12, "expected_triggers": 36, "order": ORDER,
        "common_properties": common, "runs": runs,
        "source_files": sources, "source_manifest_sha256": fingerprint(sources),
        "disk_classes": classes, "disk_class_manifest_sha256": fingerprint(classes),
        "local_jars": libs, "local_jar_manifest_sha256": fingerprint(libs),
        "evidence_root": EVIDENCE, "matrix_complete": False,
        "performance_acceptance": "unknown",
        "pending": [
            "Freeze source/resource snapshots through IDEA after main implementation.",
            "Audit existing maintrace/source stack output controlled by cast/material/bench.probes.",
            "Verify trial callback statistics boundaries, generation and window-local maxima.",
            "Disk class hashes do not prove loaded classes; preserve runtime class hashes.",
            "Record actual JVM args, JDK/mod/classpath hashes and environment each run.",
            "Audit unique JVM/world/result identity, common source hashes and all 36 triggers.",
            "Correctness, lifecycle, visual and performance acceptance remain separate.",
        ],
        "retention": "Keep every failed/cancelled/incomplete attempt; retry uses new IDs and paths.",
    }


def missing(reason):
    return {"status": "missing", "reason": reason}


def read_artifact(root, observation, role):
    matches = [item for item in observation.get("artifacts", []) if item.get("role") == role]
    if len(matches) != 1:
        return None, missing("expected exactly one explicit artifact role: " + role)
    item = matches[0]
    relative = item["path"]
    path = (root / relative).resolve()
    if not path.is_relative_to(root / "build"):
        return None, missing("artifact outside worktree build evidence")
    record = file_record(root, relative)
    if record["sha256"] is None or record["sha256"] != item.get("sha256"):
        return None, missing("artifact absent or SHA mismatch: " + relative)
    return path, record


def numeric(value):
    return type(value) in (int, float) and math.isfinite(value)


def distribution_ns(values):
    if not values:
        return missing("no complete adjacent-HEAD intervals inside main window")
    ordered = sorted(values)
    return {
        "status": "observed", "count": len(values),
        "mean_ms": sum(values) / len(values) / 1_000_000,
        "p95_ms": ordered[math.ceil(len(values) * .95) - 1] / 1_000_000,
        "p99_ms": ordered[math.ceil(len(values) * .99) - 1] / 1_000_000,
        "max_ms": ordered[-1] / 1_000_000,
        "over_50ms": sum(value > 50_000_000 for value in values),
        "over_100ms": sum(value > 100_000_000 for value in values),
        "over_250ms": sum(value > 250_000_000 for value in values),
    }


def main_intervals(path, summary):
    if path.suffix.lower() == ".csv":
        with path.open(encoding="utf-8-sig", newline="") as stream:
            rows = list(csv.DictReader(stream))
        if not rows or not {"head_offset_ns", "interval_ns"} <= rows[0].keys():
            return missing("unknown frames CSV columns")
        pairs = [(int(row["head_offset_ns"]), int(row["interval_ns"]) if row["interval_ns"] else None)
                 for row in rows]
    elif path.suffix.lower() == ".json":
        capture = json.loads(path.read_text(encoding="utf-8-sig"))
        columns = capture.get("frames", {})
        heads, intervals = columns.get("head_offset_ns"), columns.get("interval_ns")
        if (capture.get("summary", {}).get("label") != summary.get("label")
                or not isinstance(heads, list) or not isinstance(intervals, list)
                or len(heads) != len(intervals)):
            return missing("unknown native frame arrays or owner mismatch")
        pairs = list(zip(heads, intervals))
    else:
        return missing("unsupported explicit frames format")
    if len(pairs) != summary.get("frame_count"):
        return missing("main summary/frame row count mismatch")
    if any(type(head) is not int or (type(interval) is not int and not (index == 0 and interval is None))
           for index, (head, interval) in enumerate(pairs)):
        return missing("noninteger frame timestamps")
    if any(right[0] <= left[0] for left, right in zip(pairs, pairs[1:])):
        return missing("unordered/duplicate frame timestamps")
    selected = [(head, interval) for head, interval in pairs
                if interval is not None and interval > 0 and head - interval >= 0 and head <= 40_000_000_000]
    result = distribution_ns([interval for _, interval in selected])
    result.update(
        raw_rows=len(pairs), excluded_rows=len(pairs) - len(selected),
        scope="both adjacent render HEADs inside [0,40s]; no phase/body substitution",
        first_interval_start_offset_ns=selected[0][0] - selected[0][1] if selected else None,
        last_interval_end_offset_ns=selected[-1][0] if selected else None,
    )
    return result


CREATION_COUNTERS = (
    "definition_ns", "instance_ns", "reset_ns", "pool_hit", "pool_miss", "pool_reset",
    "pool_created", "pool_retired", "prepared_definitions", "compiled_nodes",
    "created_runtimes", "initialized_nodes", "native_node_fallbacks",
    "project_requests", "project_compilations", "indexed_runtimes",
    "unmanaged_creates", "native_creates", "reset_failures",
)
CREATION_SCOPE = "host_callbacks_enclosing_main_capture"


def trial_creation_window(trial):
    before = trial.get("creation_statistics_before_main")
    after = trial.get("creation_statistics_after_main")
    scope = trial.get("creation_statistics_scope")
    raw = {"before": before, "after": after, "scope": scope}
    if (scope != CREATION_SCOPE or not isinstance(before, dict)
            or not isinstance(after, dict)):
        return dict(missing("main capture callback boundary contract absent"), **raw)
    generation = before.get("generation")
    if (not numeric(generation) or generation < 0
            or not numeric(after.get("generation"))
            or after["generation"] != generation):
        return dict(missing("invalid or changed statistics generation"), **raw)
    counters = {}
    for key in CREATION_COUNTERS:
        start, end = before.get(key), after.get(key)
        if (not numeric(start) or not numeric(end) or start < 0 or end < start):
            return dict(missing("missing, negative or nonmonotonic counter: " + key), **raw)
        counters[key] = end - start
    maxima = {}
    for key in ("window_max_reset_ns", "window_max_instance_ns"):
        start, end = before.get(key), after.get(key)
        if (not numeric(start) or start != 0 or not numeric(end) or end < 0):
            return dict(missing("invalid window-local maximum: " + key), **raw)
        maxima[key] = end
    return {
        **raw, "status": "observed", "generation": generation,
        "counters": counters, "reset_ns": counters["reset_ns"],
        "max_reset_ns": maxima["window_max_reset_ns"], **maxima,
        "boundary": "host callbacks enclosing main capture; includes callback polling margin",
    }


def creation_window(root, observation, trial, mode):
    window = trial_creation_window(trial)
    result = {"main_window": window,
              "cumulative_diagnostic": missing("no post-main diagnostic artifact")}
    _, trial_proof = read_artifact(root, observation, "trial")
    result["source_artifact"] = trial_proof
    result["artifactSHA"] = trial_proof.get("sha256")
    path, proof = read_artifact(root, observation, "creation_counters")
    result["artifact"] = proof
    if path is None:
        return result
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    # Post-main statistics remain cumulative, including warmup/export/cleanup.
    if (document.get("phase") == "first_observed_post_main_before_native_checks"
            and document.get("post_check_included") is False
            and document.get("mode") == mode):
        counters = document.get("factory_statistics")
        if isinstance(counters, dict):
            result["cumulative_diagnostic"] = {
                "status": "observed", "scope": document.get("scope"),
                "phase": document["phase"], "counters": counters,
                "reset_ns": counters.get("reset_ns", missing("reset_ns absent")),
                "max_reset_ns": counters.get("max_reset_ns", missing("max_reset_ns absent")),
                "comparable_main_window": False,
            }
    # Only legacy trials without callback fields may use the explicit old schema.
    if (not any(key in trial for key in (
            "creation_statistics_before_main", "creation_statistics_after_main",
            "creation_statistics_scope"))
            and document.get("schema") == "photon-creation-window-v1"):
        summary = trial.get("main_frames", {})
        start, end = document.get("started_ns"), document.get("ended_ns")
        counters = document.get("window_counters")
        if (type(start) is int and start == summary.get("started_ns")
                and type(end) is int and end >= start
                and document.get("mode") == mode
                and document.get("scope") == "isolated_main_window"
                and document.get("post_check_included") is False
                and document.get("warmup_included") is False
                and isinstance(counters, dict)
                and all(numeric(value) and value >= 0 for value in counters.values())
                and all(numeric(counters.get(key)) for key in ("reset_ns", "max_reset_ns"))):
            result["main_window"] = {
                "status": "observed", "counters": counters,
                "reset_ns": counters["reset_ns"], "max_reset_ns": counters["max_reset_ns"],
                "scope": "legacy explicit isolated window counters",
                "started_ns": start, "ended_ns": end,
            }
            result["source_artifact"] = proof
            result["artifactSHA"] = proof["sha256"]
    return result


def results(root, manifest, observations):
    if manifest.get("schema") != "photon-runtime-redesign-matrix-v1":
        raise ValueError("unsupported matrix manifest schema")
    if [run.get("id") for run in manifest.get("runs", [])] != ORDER:
        raise ValueError("manifest must preserve the exact 12-run order")
    rows = []
    for run in manifest["runs"]:
        observation = observations.get(run["id"], {})
        row = {
            "id": run["id"], "mode": run["creation_mode"],
            "classification": "NOT_RUN" if not observation else "INCOMPLETE",
            "main_summary": missing("trial not supplied"),
            "frame_intervals": missing("main frames not supplied"),
            "creation": {"main_window": missing("main counters not supplied")},
            "failures": [], "performance_acceptance": "unknown",
        }
        try:
            row["audit"] = classify(root, run, observation or None)
            row["classification"] = row["audit"]["classification"]
            path, proof = read_artifact(root, observation, "trial")
            if path:
                trial = json.loads(path.read_text(encoding="utf-8-sig"))
                summary = trial.get("main_frames")
                row["trial_artifact"] = proof
                row["trial_state"] = trial.get("state", "missing")
                events = trial.get("triggers", [])
                if trial.get("state") != "COMPLETE":
                    row["classification"] = ("EXECUTION_FAILED"
                                             if trial.get("state") == "FAILED" else "INCOMPLETE")
                if any(trial.get(key) for key in ("error", "adapter_failure", "cancelled", "cleanup_unconfirmed")):
                    row["classification"] = "EXECUTION_FAILED"
                if (trial.get("workload_verified") is not True or len(events) != 3
                        or [event.get("index") for event in events] != [0, 1, 2]
                        or any(event.get("started") is not True for event in events)):
                    if row["classification"] == "COMPARABLE_CANDIDATE":
                        row["classification"] = "INCOMPLETE"
                row["failures"] = {key: trial.get(key, "missing")
                                   for key in ("error", "adapter_failure", "cancelled", "cleanup_unconfirmed")}
                if (trial.get("world") == run["world"] and trial.get("kind") == "LASER"
                        and trial.get("mode") == "FULL" and isinstance(summary, dict)
                        and summary.get("schema_version") == 1
                        and summary.get("requested_seconds") == 40
                        and summary.get("state") == "DURATION"):
                    row["main_summary"] = {"status": "observed", "value": summary}
                    frame_path, frame_proof = read_artifact(root, observation, "frames")
                    row["frame_intervals"] = (main_intervals(frame_path, summary)
                                               if frame_path else frame_proof)
                    row["creation"] = creation_window(root, observation, trial, run["creation_mode"])
                else:
                    row["main_summary"] = missing("trial identity or native main summary contract mismatch")
            else:
                row["main_summary"] = proof
        except (OSError, ValueError, TypeError, KeyError, AttributeError) as error:
            row["input_error"] = str(error)
            row["classification"] = "INCOMPLETE"
        if observation.get("correctness") == "FAILED":
            row["classification"] = "CORRECTNESS_FAILED"
        row["comparable"] = (
            row["classification"] == "COMPARABLE_CANDIDATE"
            and row["frame_intervals"].get("status") == "observed"
            and row["creation"]["main_window"].get("status") == "observed"
            and all(numeric(row["creation"]["main_window"].get(key))
                    for key in ("reset_ns", "max_reset_ns"))
        )
        if row["classification"] == "COMPARABLE_CANDIDATE" and not row["comparable"]:
            row["classification"] = "INCOMPLETE"
        rows.append(row)
    return {
        "schema": "photon-runtime-redesign-results-v1", "runs": rows,
        "matrix_complete": False, "performance_acceptance": "unknown",
        "scope": "main frames and enclosing callback counters; cumulative diagnostics remain separate",
        "missing_policy": "unknown/missing fields never become zero; no post-nativecheck counters",
    }


def markdown_results(result):
    lines = [
        "| ID | Mode | State | Main intervals | P95 ms | P99 ms | Max ms | Reset ns | Max reset ns | Comparable |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    def cell(value):
        return str(round(value, 3) if isinstance(value, float) else value) if numeric(value) else "missing"
    for run in result["runs"]:
        frame = run["frame_intervals"]
        counters = run["creation"]["main_window"]
        values = [
            run["id"], run["mode"], run["classification"], cell(frame.get("count")),
            cell(frame.get("p95_ms")), cell(frame.get("p99_ms")), cell(frame.get("max_ms")),
            cell(counters.get("reset_ns")), cell(counters.get("max_reset_ns")),
            str(run["comparable"]).lower(),
        ]
        lines.append("| " + " | ".join(values) + " |")
    lines.append("")
    lines.append("missing means unavailable, not zero. Cumulative reset costs remain in JSON diagnostics.")
    lines.append("No visual, correctness or performance acceptance is implied.")
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", nargs="?", choices=("manifest", "results"), default="manifest")
    parser.add_argument("--manifest", type=Path, help="IDEA-saved matrix manifest for results")
    parser.add_argument("--format", choices=("json", "markdown", "both"), default="json")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--git-head", help="verified full HEAD; required for manifest")
    parser.add_argument("--observations", type=Path,
                        help="optional IDEA-saved object keyed by matrix run ID")
    args = parser.parse_args(argv)
    try:
        observations = (json.loads(args.observations.read_text(encoding="utf-8-sig"))
                        if args.observations else {})
        if not isinstance(observations, dict):
            raise ValueError("observations must be an object")
        if args.command == "results":
            if args.manifest is None:
                raise ValueError("results requires --manifest")
            manifest = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
            result = results(args.root.resolve(), manifest, observations)
            if args.format in ("json", "both"):
                print(json.dumps(result, ensure_ascii=False, indent=2))
            if args.format == "both":
                print("\\n--- Markdown ---\\n")
            if args.format in ("markdown", "both"):
                print(markdown_results(result))
        else:
            if args.git_head is None:
                raise ValueError("manifest requires --git-head")
            manifest = generate(args.root.resolve(), args.git_head, observations)
            print(json.dumps(manifest, ensure_ascii=False, indent=2))
        return 0
    except (OSError, ValueError, TypeError, KeyError, AttributeError, ET.ParseError) as error:
        print(json.dumps({"status": "INPUT_ERROR", "error": str(error)}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
