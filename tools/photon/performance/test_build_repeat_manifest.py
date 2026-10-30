"""Tiny synthetic tests only; never discovers real matrix data."""
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

import build_repeat_manifest as builder
from analyze_repeat_frames import frame_records


class ManifestBuilderTests(unittest.TestCase):
    def test_v3_selection_with_failed_v2_and_partial_matrix(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for index, (kind, mode, iteration) in enumerate(sorted(builder.EXPECTED)):
                label = (f"photon_repeat_run_{kind}_v2_{iteration:02d}" if mode == "run"
                         else f"photon_repeat_full_synthetic_{kind}_{iteration}")
                parent = root / ("run/client/performance" if mode == "run" else
                                 "build/modBench/runs/default/client/performance") / label
                parent.mkdir(parents=True)
                trial = {"label": label, "kind": kind.upper(), "mode": "BASE" if mode == "run" else "FULL",
                         "state": "COMPLETE", "pid": index + 1, "world_path": f"world-{index}",
                         "world": f"photon_repeat_f{kind[0]}3{iteration:02d}-flat" if mode == "full"
                         else f"base-{index}", "workload_verified": True,
                         "main_frames": {"state": "DURATION"}, "triggers": [
                             {"index": i, "requested_ns": i + 1, "actual_ns": i + 2, "started": True}
                             for i in range(3)]}
                builder.write_json(parent / "trial.json", trial)
            old = root / "build/modBench/runs/default/client/performance/photon_repeat_full_old"
            old.mkdir()
            builder.write_json(old / "trial.json", {"mode": "FULL", "world": "photon_repeat_fl201-flat",
                                                    "state": "FAILED", "error": "CAPACITY"})
            def tiny_run(_root, _output, path, trial, cell, head, version):
                return {"id": str(cell), "input_issues": [], "trial_report": str(path), "version": version}
            with patch.object(builder, "build_run", side_effect=tiny_run):
                path, manifest = builder.generate(root, root / "result", "synthetic-head")
            self.assertTrue(path.is_file())
            self.assertEqual(len(manifest["runs"]), 12)
            self.assertEqual(manifest["full_version"], "v3")
            self.assertEqual(manifest["generation_status"], "input_shape_complete")
            self.assertFalse(manifest["matrix_complete"])
            self.assertTrue(all("full_old" not in r["trial_report"] for r in manifest["runs"]))
            partial = next((root / "run/client/performance").glob("*/trial.json"))
            trial = builder.read_json(partial)
            trial["state"] = "RUNNING"
            partial.unlink()
            builder.write_json(partial, trial)
            with self.assertRaises(ValueError):
                builder.generate(root, root / "partial", "synthetic-head")
            self.assertFalse((root / "partial").exists())

    def synthetic_matrix(self, root):
        paths = {}
        for index, cell in enumerate(sorted(builder.EXPECTED)):
            kind, mode, iteration = cell
            label = (f"photon_repeat_run_{kind}_v2_{iteration:02d}" if mode == "run"
                     else f"photon_repeat_full_{kind}_v4_{iteration:02d}")
            parent = root / ("run/client/performance" if mode == "run" else
                             "build/modBench/runs/default/client/performance") / label
            parent.mkdir(parents=True)
            trial = {"label": label, "kind": kind.upper(), "mode": "BASE" if mode == "run" else "FULL",
                     "state": "COMPLETE", "pid": index + 1, "world_path": f"world-{index}",
                     "world": f"photon_repeat_f{kind[0]}4{iteration:02d}-flat" if mode == "full"
                     else f"base-{index}", "workload_verified": True,
                     "main_frames": {"state": "DURATION"}, "triggers": [
                         {"index": i, "requested_ns": i + 1, "actual_ns": i + 2, "started": True}
                         for i in range(3)]}
            paths[cell] = parent / "trial.json"
            builder.write_json(paths[cell], trial)
        return paths

    def add_retry(self, path, iteration, failed=False):
        trial = builder.read_json(path)
        trial.update(world=f"photon_repeat_fl5{iteration:02d}-flat", pid=100 + iteration,
                     world_path=f"retry-world-{iteration}", metric="worse")
        if failed:
            trial.update(state="FAILED", error="COUNTERS")
        parent = path.parent.with_name(f"photon_repeat_full_retry_v5_{iteration:02d}")
        parent.mkdir()
        retry = parent / "trial.json"
        builder.write_json(retry, trial)
        return retry

    def test_explicit_retry_selection_and_versions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = self.synthetic_matrix(root)
            failed = paths[("laser", "full", 2)]
            retry2 = self.add_retry(failed, 2)
            retry3 = self.add_retry(paths[("laser", "full", 3)], 3)
            trial = builder.read_json(failed)
            trial.update(state="FAILED", error="COUNTERS")
            failed.write_text(builder.json.dumps(trial), encoding="utf-8")

            def tiny_run(_root, _output, path, trial, cell, head, version):
                return {"id": str(cell), "input_issues": [], "trial_report": str(path),
                        "version": version}

            with patch.object(builder, "build_run", side_effect=tiny_run):
                _, manifest = builder.generate(root, root / "result", "head", "v4", "v5")
            self.assertEqual(len(manifest["runs"]), 12)
            for run in manifest["runs"]:
                self.assertEqual(run["version"], "v5" if run["trial_report"] in
                                 (str(retry2), str(retry3)) else "v4")
            self.assertEqual(len(manifest["excluded_sources"]), 2)
            self.assertTrue(any(item.get("error") == "COUNTERS" for item in manifest["excluded_sources"]))
            self.assertFalse(manifest["matrix_complete"])
            self.assertEqual(manifest["performance_acceptance"], "unknown")

    def test_failed_retry_refuses_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = self.synthetic_matrix(root)
            retry = self.add_retry(paths[("laser", "full", 2)], 2, failed=True)
            with self.assertRaisesRegex(ValueError, "not_complete:.*retry_v5"):
                builder.generate(root, root / "result", "head", "v4", "v5")
            self.assertFalse((root / "result").exists())
            self.assertEqual(builder.read_json(retry)["error"], "COUNTERS")

    def test_same_version_duplicates_and_missing_cells(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = self.synthetic_matrix(root)
            retry = self.add_retry(paths[("laser", "full", 2)], 2)
            duplicate = retry.parent.with_name("photon_repeat_full_duplicate")
            duplicate.mkdir()
            builder.write_json(duplicate / "trial.json", builder.read_json(retry))
            with self.assertRaisesRegex(ValueError, "duplicate matrix cell"):
                builder.generate(root, root / "result", "head", "v4", "v5")
            (duplicate / "trial.json").unlink()
            paths[("native", "full", 1)].unlink()
            with self.assertRaisesRegex(ValueError, "expected_12_cells"):
                builder.generate(root, root / "result", "head", "v4", "v5")
            self.assertFalse((root / "result").exists())

    def test_measured_settings_and_explicit_phases(self):
        initial = {"width": 854, "height": 480, "maximized": False, "focused": False,
                   "vsync": True, "fps_limit": 60, "render_distance": 12}
        measured = {"width": 2560, "height": 1494, "maximized": True, "focused": True,
                    "vsync": False, "fps_limit": 260}
        trial = {"initial_environment": initial,
                 "main_frames": {"environment_first": measured, "environment_last": dict(measured)}}
        effective = builder.settings(trial)
        for key, value in measured.items():
            self.assertEqual(effective[key], value)
        self.assertEqual(effective["render_distance"], 12)
        metadata = builder.runtime_metadata(trial)
        self.assertEqual(metadata["runtime_status"]["initial_environment"], initial)
        self.assertEqual(metadata["runtime_status"]["environment_phases"]["main_environment_first"], measured)
        self.assertEqual(initial["width"], 854)
        trial["main_frames"]["environment_last"] = {"focused": False, "vsync": None}
        self.assertFalse(builder.settings(trial)["focused"])
        self.assertIsNone(builder.settings(trial)["vsync"])
        self.assertEqual(builder.settings(trial)["width"], 2560)
        self.assertEqual(builder.environment_phases({}), {})
        self.assertIsNone(builder.settings({})["maximized"])
        self.assertEqual(builder.settings({"initial_environment": initial})["width"], 854)

    def test_retry_cli_forwarding(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(builder, "generate", return_value=(root / "matrix.json",
                              {"generation_status": "input_shape_complete", "runs": [], "issues": []})) as generate:
                self.assertEqual(builder.main(["--matrix-ended", "--root", str(root), "--output-dir",
                                              str(root / "result"), "--git-head", "a" * 40,
                                              "--full-version", "v4", "--full-retry-version", "v5"]), 0)
            self.assertEqual(generate.call_args.args[-2:], ("v4", "v5"))

    def test_csv_canonical_and_missing(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "frames.csv"
            path.write_text("frame_id,head_offset_ns,interval_ns,render_gpu_ns,phase_id,interval_phase_id,limit,throttle_reason,extra\n"
                            "0,10,,,0,0,260,NONE,kept\n"
                            "1,30,20,-1,1,0,260,NONE,also_kept\n", encoding="utf-8")
            capture = builder.normalize_csv(path, {"frame_count": 2, "started_ns": 100})
            self.assertIsNone(capture["frames"]["interval_ns"][0])
            self.assertEqual(capture["frames"]["render_gpu_ns"], [None, -1])
            self.assertEqual(capture["frames"]["actual_framerate_limit"], [260, 260])
            self.assertEqual(capture["frames"]["extra"], ["kept", "also_kept"])
            records, issues = frame_records(capture)
            self.assertEqual(issues, [])
            self.assertEqual(records[1]["start_ns"], 110)
            self.assertEqual(records[1]["phase_id"], 0)
            self.assertEqual(capture["provenance"]["sha256"], builder.evidence(path)["sha256"])
            with self.assertRaises(ValueError):
                builder.normalize_csv(path, {"frame_count": 3})

    def test_mapping_and_scopes(self):
        self.assertEqual(builder.cell_for({"mode": "FULL", "world": "photon_repeat_fl302-flat"},
                                         Path("trial.json")), ("laser", "full", 2))
        self.assertIsNone(builder.cell_for({"mode": "FULL", "world": "photon_repeat_fn201-flat"},
                                          Path("trial.json")))
        self.assertEqual(builder.cell_for({"mode": "FULL", "world": "photon_repeat_fn201-flat"},
                                         Path("trial.json"), "v2"), ("native", "full", 1))
        for name in ("pilot_native", "photon_repeat_run_native_01"):
            self.assertIsNone(builder.cell_for({"mode": "BASE"}, Path(name) / "trial.json"))
        event = {"index": 0, "requested_ns": 10, "dispatch_ns": 11, "actual_ns": 12,
                 "first_active_ns": 13, "started": True}
        mapped = builder.map_triggers({"triggers": [event]})[0]
        self.assertEqual(mapped["observed_ns"], 12)
        self.assertEqual(mapped["dispatch_ns"], 11)
        self.assertEqual(mapped["first_active_ns"], 13)
        self.assertIsNone(builder.runtime_metadata({})["runtime_status"]["base_vm_arguments_clean"])
        runtime = builder.runtime_metadata({"vm_arguments": ["-DmodBench.enabled=false"]})["runtime_status"]
        self.assertFalse(runtime["base_vm_arguments_clean"])
        self.assertNotIn("test_environment", runtime)

    def test_no_output_for_empty_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "new"
            with self.assertRaises(ValueError):
                builder.generate(root, output, "synthetic-head")
            self.assertFalse(output.exists())
            with self.assertRaises(FileExistsError):
                builder.generate(root, root, "synthetic-head")

    def test_owned_copy_hash_required(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "owned.json"
            path.write_text("{}", encoding="utf-8")
            trial = {"label": "tiny", "outputs": [{"label": "tiny_main", "files": [
                {"owned_copy": str(path), "sha256": "bad"}]}]}
            with self.assertRaises(ValueError):
                builder.stage_file(trial, "main", ".json")
            trial["outputs"][0]["files"][0]["sha256"] = builder.evidence(path)["sha256"]
            self.assertEqual(builder.stage_file(trial, "main", ".json")[0], path)


if __name__ == "__main__":
    unittest.main()
