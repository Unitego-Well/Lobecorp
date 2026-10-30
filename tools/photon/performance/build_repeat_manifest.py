"""Build offline repeat inputs only after the main operator ends the matrix.
python -B build_repeat_manifest.py --matrix-ended --output-dir NEW_DIRECTORY
Exit 0: complete input shape (not audit/performance acceptance); 2: incomplete/error.
Never invokes analyzers, game, build or debugger. Existing output directories are refused.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re

from audit_repeat_inputs import test_vm_argument

SCHEMA = "photon-repeat-matrix-v1"
EXPECTED = {(k, m, i) for k in ("native", "laser") for m in ("run", "full") for i in range(1, 4)}
INTEGER_COLUMNS = {
    "frame_id", "head_offset_ns", "interval_ns", "previous_minecraft_ns",
    "render_cpu_ns", "render_gpu_ns", "render_thread_allocated_bytes",
    "framebuffer_width", "framebuffer_height", "window_width", "window_height",
    "flags", "phase_id", "interval_phase_id", "limit", "actual_framerate_limit",
}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def evidence(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return {"path": str(path.resolve()), "sha256": digest.hexdigest(), "bytes": path.stat().st_size}


def write_json(path, value):
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")


def normalize_csv(path, summary):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        names = reader.fieldnames
        if not names or len(names) != len(set(names)) or "head_offset_ns" not in names:
            raise ValueError("invalid frame CSV header: " + str(path))
        columns = {name: [] for name in names}
        for row in reader:
            if None in row or any(value is None for value in row.values()):
                raise ValueError("ragged frame CSV: " + str(path))
            for name in names:
                value = row[name]
                columns[name].append(None if value == "" else int(value)
                                     if name in INTEGER_COLUMNS else value)
    count = len(columns["head_offset_ns"])
    if not count or summary.get("frame_count") != count:
        raise ValueError("trial/CSV frame count mismatch: " + str(path))
    if "limit" in columns and "actual_framerate_limit" not in columns:
        columns["actual_framerate_limit"] = list(columns["limit"])
    return {"summary": summary, "frames": columns, "provenance": evidence(path),
            "normalization": {"limit": "actual_framerate_limit alias; original retained",
                              "missing": "empty CSV cell becomes null; no zero imputation"}}


def cell_for(trial, path, full_version="v3"):
    if trial.get("mode") == "BASE":
        match = re.fullmatch(r"photon_repeat_run_(native|laser)_v2_0([123])", path.parent.name)
        return (match[1], "run", int(match[2])) if match else None
    if trial.get("mode") == "FULL":
        match = re.fullmatch(r"photon_repeat_f([nl])" + full_version[1:] + r"(0[123])-flat",
                             str(trial.get("world")))
        return ({"n": "native", "l": "laser"}[match[1]], "full", int(match[2])) if match else None
    return None


def owned_files(trial, stage):
    label = str(trial.get("label")) + "_" + stage
    return [item for output in trial.get("outputs", []) if output.get("label") == label
            for item in output.get("files", [])]


def stage_file(trial, stage, suffix):
    found = [item for item in owned_files(trial, stage)
             if str(item.get("owned_copy", "")).endswith(suffix)]
    if len(found) != 1:
        raise ValueError("expected one owned " + stage + " " + suffix)
    item = found[0]
    path = Path(item["owned_copy"])
    proof = evidence(path)
    if not item.get("sha256") or proof["sha256"].lower() != item["sha256"].lower():
        raise ValueError("owned copy hash mismatch: " + str(path))
    return path, proof


def map_triggers(trial):
    return [{**event, "requested_ns": event.get("requested_ns"),
             "observed_ns": event.get("actual_ns"), "dispatch_ns": event.get("dispatch_ns"),
             "started": event.get("started"), "first_active_ns": event.get("first_active_ns")}
            for event in trial.get("triggers", [])]


def runtime_metadata(trial):
    args = trial.get("vm_arguments")
    runtime = {key: trial.get(key) for key in (
        "pid", "vm_arguments", "mod_versions", "assets", "initial_environment", "created_ns")}
    runtime["environment"] = trial.get("initial_environment")
    runtime["environment_phases"] = environment_phases(trial)
    runtime["base_vm_arguments_clean"] = (not any(test_vm_argument(arg) for arg in args)
                                         if isinstance(args, list) and all(isinstance(a, str) for a in args)
                                         else None)
    # A trial does not contain a process-environment snapshot. Do not invent an empty one.
    runtime["process_environment_status"] = "unknown"
    return {"runtime_status": runtime, "settings": settings(trial),
            "source_scope": "generated projection of authoritative trial; not a live runtimeStatus call"}


def environment_phases(trial):
    phases = {}
    if "initial_environment" in trial:
        phases["initial"] = trial["initial_environment"]
    main = trial.get("main_frames", {})
    for key in ("environment_first", "environment_last"):
        if key in main:
            phases["main_" + key] = main[key]
    return phases


def settings(trial):
    env = dict(trial.get("initial_environment") or {})
    # Last measured value wins; absent fields retain earlier observed values.
    for key in ("environment_first", "environment_last"):
        measured = trial.get("main_frames", {}).get(key)
        if isinstance(measured, dict):
            env.update(measured)
    keys = ("width", "height", "fullscreen", "vsync", "fps_limit", "render_distance",
            "fov", "shader_pack", "graphics", "particles_setting", "resource_packs",
            "maximized", "focused", "framebuffer_width", "framebuffer_height",
            "window_width", "window_height")
    return {key: env.get(key) for key in keys}


def one_file(paths, description, issues):
    paths = sorted(set(p.resolve() for p in paths if p.is_file()))
    if len(paths) != 1:
        issues.append(description + "_missing_or_ambiguous")
        return None
    return paths[0]


def build_run(root, output, trial_path, trial, cell, head, full_version="v3"):
    kind, mode, iteration = cell
    rid = f"{mode}_{kind}_{iteration:02d}"
    issues, sources = [], [evidence(trial_path)]
    run = {"id": rid, "kind": kind, "mode": mode, "iteration": iteration,
           "game_pid": trial.get("pid"), "world": trial.get("world"),
           "world_path": trial.get("world_path"), "trial_report": str(trial_path.resolve()),
           "triggers": map_triggers(trial), "settings": settings(trial),
           "trigger_time_scope": "render-before-native-emit" if kind == "native"
           else "server-before-public-cast", "console": None, "jfr": None, "spark": None,
           "trace_directory": None, "probe_availability": {
               "source": "trial.initial_environment",
               **{key: trial.get("initial_environment", {}).get(key) for key in
                  ("spark", "cast_requested", "submission_requested")}}}
    for stage, key in (("main", "frames"), ("prewarm", "prewarm_frames")):
        path, proof = stage_file(trial, stage, ".json" if mode == "run" else "frames.csv")
        sources.append(proof)
        if mode == "full":
            normalized = output / (rid + "_" + stage + ".json")
            write_json(normalized, normalize_csv(path, trial.get(stage + "_frames", {})))
            run[key] = str(normalized.resolve())
            sources.append(evidence(normalized))
        else:
            run[key] = str(path.resolve())
    metadata = runtime_metadata(trial)
    metadata["provenance"] = sources[0]
    metadata_path = output / (rid + "_metadata.json")
    write_json(metadata_path, metadata)
    run["metadata"] = str(metadata_path.resolve())
    sources.append(evidence(metadata_path))
    provenance = {"current_head_at_generation": head, "runtime_assets": trial.get("assets"),
                  "build_identity_scope": "loaded_class_bytes SHA in trial validates loaded byte identity; "
                  "Git metadata alone does not establish executed code"}
    provenance["trial_version"] = full_version if mode == "full" else "v2"
    if mode == "full":
        raw = root / f"build/modBench/raw-results/photon-repeat-full-{kind}-{full_version}-{iteration:02d}/client"
        summary_path = raw / "summary.json"
        provenance["raw_summary_path"] = str(summary_path.resolve())
        if summary_path.is_file():
            raw_summary = read_json(summary_path)
            sources.append(evidence(summary_path))
            provenance["raw_summary"] = raw_summary.get("git", raw_summary.get("provenance"))
            provenance["raw_summary_identity_fields"] = {
                key: value for key, value in raw_summary.items()
                if any(token in key.lower() for token in ("git", "commit", "version", "build"))}
            provenance["git_distinction"] = (
                "raw generated metadata may retain 12e330; current HEAD may be fe96ab5 or later; "
                "preserve both, neither substitutes for runtime loaded-class hashes")
            artifacts = raw / "artifacts"
            run["jfr"] = one_file(artifacts.rglob("*.jfr"), "jfr", issues)
            run["spark"] = one_file(
                artifacts.rglob(str(trial["label"]) + "_*_client.sparkprofile"), "spark", issues)
            for key in ("jfr", "spark"):
                if run[key]:
                    sources.append(evidence(run[key]))
                    run[key] = str(run[key])
            console = one_file(artifacts.rglob("*console*.log"), "console", [])
            run["console"] = str(console) if console else None
        else:
            issues.append("raw_summary_missing")
        trace, proof = stage_file(trial, "main", "session.json")
        run["trace_directory"] = str(trace.resolve().parent)
        sources.append(proof)
        provenance["trace_directory_scope"] = "owned main session directory; session quality preserved verbatim"
    for asset in trial.get("assets", []):
        if asset.get("source_pack") == "loaded_class_bytes":
            class_path = root / "build/classes/java/main" / (asset["source"].replace(".", "/") + ".class")
            if class_path.is_file():
                proof = evidence(class_path)
                proof["runtime_sha256"] = asset.get("sha256")
                proof["matches_loaded_class"] = proof["sha256"] == asset.get("sha256")
                sources.append(proof)
    run.update(source_hashes=sources, provenance=provenance, input_issues=issues)
    return run


def generate(root, output, head, full_version="v3", full_retry_version=None):
    for version in (full_version, full_retry_version):
        if version is not None and not re.fullmatch(r"v[1-9][0-9]*", version):
            raise ValueError("invalid FULL version")
    if full_retry_version == full_version:
        raise ValueError("FULL retry version must differ from primary version")
    if output.exists():
        raise FileExistsError("output directory already exists: " + str(output))
    candidates = list((root / "run/client/performance").glob("photon_repeat_run_*_v2_0[123]/trial.json"))
    candidates += list((root / "build/modBench/runs/default/client/performance").glob("photon_repeat_full_*/trial.json"))
    selected, issues, versions, excluded = {}, [], {}, []
    grouped = {}
    for path in sorted(candidates):
        trial = read_json(path)
        version = full_version
        cell = cell_for(trial, path, version)
        if cell is None and full_retry_version is not None:
            version = full_retry_version
            cell = cell_for(trial, path, version)
        if cell is None:
            excluded.append({"path": str(path.resolve()), "reason": "outside_selected_versions"})
            continue
        identity = (cell, version)
        if identity in grouped:
            raise ValueError("duplicate matrix cell: " + str(identity))
        grouped[identity] = (path, trial)
    for cell in sorted(EXPECTED):
        primary = (cell, full_version)
        retry = (cell, full_retry_version)
        identity = retry if cell[1] == "full" and retry in grouped else primary
        if identity not in grouped:
            continue
        path, trial = grouped[identity]
        versions[cell] = identity[1]
        if identity == retry and primary in grouped:
            old_path, old_trial = grouped[primary]
            excluded.append({"path": str(old_path.resolve()), "reason": "superseded_by_explicit_retry",
                             "replacement": str(path.resolve()), "state": old_trial.get("state"),
                             "error": old_trial.get("error")})
        if trial.get("kind") != cell[0].upper() or trial.get("state") != "COMPLETE":
            issues.append("not_complete:" + str(path))
        events = trial.get("triggers", [])
        if (len(events) != 3 or {e.get("index") for e in events} != {0, 1, 2}
                or any(e.get("started") is not True or type(e.get("actual_ns")) is not int
                       or type(e.get("requested_ns")) is not int for e in events)):
            issues.append("incomplete_triggers:" + str(path))
        if trial.get("workload_verified") is not True or any(
                trial.get(key) for key in ("error", "cancelled", "cleanup_unconfirmed")):
            issues.append("trial_failure_or_unverified:" + str(path) + ":error=" + str(trial.get("error")))
        if trial.get("main_frames", {}).get("state") != "DURATION":
            issues.append("main_capture_not_duration:" + str(path))
        selected[cell] = (path, trial)
    if set(selected) != EXPECTED:
        issues.append("expected_12_cells")
    for key in ("pid", "world", "world_path"):
        values = [trial.get(key) for _, trial in selected.values()]
        if any(v is None for v in values) or len(set(values)) != len(values):
            issues.append("missing_or_duplicate_" + key)
    # Refuse generation before completion, including empty matrices.
    if issues:
        raise ValueError("; ".join(issues))
    output.mkdir(parents=True, exist_ok=False)
    runs = [build_run(root, output, *selected[cell], cell, head, versions[cell]) for cell in sorted(EXPECTED)]
    issues = [f"{run['id']}:{issue}" for run in runs for issue in run["input_issues"]]
    manifest = {"schema": SCHEMA, "runs": runs, "expected_runs": 12, "full_version": full_version,
                "matrix_complete": False, "performance_acceptance": "unknown",
                "generation_status": "incomplete" if issues else "input_shape_complete",
                "issues": issues, "scope": "input assembly only; audit and analysis must run separately"}
    if full_retry_version is not None:
        manifest["full_retry_version"] = full_retry_version
        manifest["excluded_sources"] = excluded
    path = output / "matrix.json"
    write_json(path, manifest)
    return path, manifest


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matrix-ended", action="store_true", help="main operator confirms all game matrix work ended")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--git-head", required=True, help="HEAD verified through IDEA background Git")
    parser.add_argument("--full-version", default="v3", help="FULL world/raw-results version; BASE stays v2")
    parser.add_argument("--full-retry-version", help="explicit per-cell FULL retry overrides primary; failed retry refuses generation")
    args = parser.parse_args(argv)
    if not args.matrix_ended:
        parser.error("--matrix-ended is required; do not run during the game matrix")
    try:
        root = args.root.resolve()
        head = args.git_head
        if not re.fullmatch(r"[0-9a-f]{40}", head):
            raise ValueError("invalid verified Git HEAD")
        path, manifest = generate(root, args.output_dir.resolve(), head, args.full_version,
                                  args.full_retry_version)
        print(json.dumps({"manifest": str(path), "status": manifest["generation_status"],
                          "run_count": len(manifest["runs"]), "issues": manifest["issues"]}))
        return 2 if manifest["issues"] else 0
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(json.dumps({"status": "incomplete", "error": str(error)}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
