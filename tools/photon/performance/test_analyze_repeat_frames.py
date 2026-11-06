"""Synthetic tests only; fixtures and CLI outputs live in a temporary directory."""
import copy
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

import analyze_repeat_frames as analysis


def timing():
    return analysis.trigger_analysis({"triggers": [
        {"index": i, "requested_ns": t - 10, "observed_ns": t, "started": True}
        for i, t in enumerate((1_000_000_000, 2_000_000_000, 3_000_000_000))]})


def capture():
    heads = [0, 500_000_000, 1_500_000_000, 2_500_000_000,
             3_500_000_000, 8_500_000_000, 9_000_000_000]
    n = len(heads)
    return {"summary": {"started_ns": 0, "frame_count": n, "state": "DURATION", "error": None},
            "frames": {"head_offset_ns": heads,
                       "interval_ns": [-1] + [b - a for a, b in zip(heads, heads[1:])],
                       "render_cpu_ns": list(range(10, 10 + n)),
                       "render_gpu_ns": [-1, 100, None, 200, -1, 300, 400],
                       "render_thread_allocated_bytes": [-1] + [50] * (n - 1),
                       "previous_minecraft_ns": [-1] + [20] * (n - 1),
                       "phase_id": [0, 0, 1, 2, 3, 3, 3],
                       "interval_phase_id": [0, 0, 0, 1, 2, 3, 3]}}


class RepeatAnalysisTests(unittest.TestCase):
    def test_nearest_rank_and_unavailable(self):
        s = analysis.stats([0, 10, 20, None, -1, float("nan"), True, "3"])
        self.assertEqual((s["count"], s["unavailable"], s["p50"], s["p99"]), (3, 5, 10, 20))
        self.assertIsNone(analysis.stats([])["mean"])
        self.assertIsNone(analysis.stats([-1])["sum"])

    def test_strict_thresholds_and_sequences(self):
        for threshold in analysis.THRESHOLDS_NS:
            values = [threshold, threshold + 1, threshold + 2, None, threshold + 1]
            rows = [{"frame_id": i, "interval_ns": x} for i, x in enumerate(values)]
            s = analysis.distribution(rows)["slow_frames"][str(threshold / analysis.NS_PER_MS)]
            self.assertEqual((s["count"], s["longest_consecutive"]), (3, 2))
        self.assertIsNone(analysis.distribution([])["slow_frames"]["50.0"]["count"])

    def test_nonadjacent_frames_break_sequences(self):
        d = analysis.distribution([{"frame_id": i, "interval_ns": 1_000_000_000} for i in (1, 3)])
        self.assertEqual(d["slow_frames"]["50.0"]["longest_consecutive"], 1)

    def test_previous_render_alignment(self):
        result = analysis.analyze_capture(capture(), timing())
        r = result["records"][2]
        self.assertEqual((r["phase_id"], r["current_phase_id"], r["render_cpu_ns"]), (0, 1, 11))
        self.assertEqual(r["render_gpu_ns"], 100)
        self.assertEqual(r["render_thread_allocated_bytes"], 50)
        self.assertEqual(result["unpaired_last_render"]["render_cpu_ns"], 16)

    def test_straddles_preserve_initial_blocking(self):
        result = analysis.analyze_capture(capture(), timing())
        r = result["records"][2]
        self.assertEqual(r["window"], "before_first")
        self.assertEqual(r["straddle"][0]["boundary"], "trigger0")
        self.assertEqual(r["interval_ns"], 1_000_000_000)
        self.assertEqual(result["records"][5]["straddle"][0]["boundary"], "trigger2_plus_5s_end")
        self.assertTrue(result["window_coverage"]["trigger2_plus_5s"]["bounded_window_covered"])

    def test_phase_fallback(self):
        c = capture()
        del c["frames"]["interval_phase_id"]
        r = analysis.analyze_capture(c, timing())["records"][3]
        self.assertEqual((r["phase_id"], r["phase_attribution"]), (1, "previous_render_phase_id"))

    def test_missing_current_phase_is_unknown(self):
        c = capture()
        del c["frames"]["phase_id"]
        r = analysis.analyze_capture(c, timing())["records"][2]
        self.assertEqual(r["phase_id"], 0)
        self.assertIsNone(r["phase_transition"])

    def test_bad_lengths_and_head_mismatch(self):
        c = capture()
        c["frames"]["interval_ns"][2] = 42
        c["frames"]["render_cpu_ns"] = []
        result = analysis.analyze_capture(c, timing())
        self.assertIn("interval_head_mismatch:2", result["issues"])
        self.assertIn("column_length_mismatch:render_cpu_ns", result["issues"])
        self.assertEqual(result["distribution"]["render_cpu_ns"]["count"], 0)

    def test_missing_trigger_no_invented_window(self):
        result = analysis.analyze_capture(capture(), analysis.trigger_analysis({}))
        self.assertEqual(result["windows"]["unknown"]["frame_count"], 7)
        self.assertEqual(result["windows"]["before_first"]["interval_ns"]["count"], 0)

    def test_real_jitter_and_gap_error(self):
        r = {"triggers": [{"index": i, "requested_ns": i * 100,
                           "observed_ns": i * 100 + i * 7, "started": True,
                           "first_active_ns": i * 100 + i * 7 + 50}
                          for i in range(3)]}
        t = analysis.trigger_analysis(r)
        self.assertEqual(t["triggers"][2]["dispatch_jitter_ns"], 14)
        self.assertEqual(t["gaps"][0]["error_ns"], 7)
        self.assertEqual(t["triggers"][0]["first_active_delay_ns"], 50)
        r["triggers"].append(r["triggers"][0])
        self.assertIn("expected_three_triggers", analysis.trigger_analysis(r)["errors"])

    def test_nonordered_times(self):
        r = {"triggers": [{"index": i, "observed_ns": 0, "started": True} for i in range(3)]}
        self.assertFalse(analysis.trigger_analysis(r)["ordered"])

    def test_empty_matrix(self):
        r = analysis.analyze_manifest({"schema": analysis.SCHEMA, "runs": []}, pathlib.Path("."))
        self.assertFalse(r["matrix_complete"])
        self.assertEqual(r["run_count"], 0)
        self.assertEqual(len(r["matrix"]), 12)
        self.assertTrue(all(c["status"] == "missing" for c in r["matrix"]))

    def test_full_matrix_and_mutations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "frames.json").write_text(json.dumps(capture()), encoding="utf-8")
            runs = []
            for kind in ("laser", "native"):
                for mode in ("full", "run"):
                    for iteration in (1, 2, 3):
                        runs.append({"id": str(len(runs)), "kind": kind, "mode": mode,
                                     "iteration": iteration, "game_pid": len(runs) + 100,
                                     "frames": "frames.json", "settings": {"limit": 260},
                                     "probe_availability": {"gpu": "not_enabled"},
                                     "triggers": [{"index": t["index"], "requested_ns": t["requested_ns"],
                                                   "observed_ns": t["observed_ns"], "started": True}
                                                  for t in timing()["triggers"]]})
            manifest = {"schema": analysis.SCHEMA, "runs": runs}
            result = analysis.analyze_manifest(manifest, root)
            self.assertTrue(result["matrix_complete"])
            self.assertEqual(result["confirmed_started_triggers"], 36)
            self.assertEqual(result["performance_acceptance"], "unknown")
            self.assertEqual(result["runs"][0]["artifacts"]["jfr"]["status"], "unknown")
            self.assertEqual(result["runs"][0]["probe_availability"]["gpu"], "not_enabled")
            for field, value in (("game_pid", 100),
                                 ("triggers", []), ("frames", "missing.json"),
                                 ("iteration", True), ("id", "0")):
                changed = copy.deepcopy(manifest)
                changed["runs"][1][field] = value
                self.assertFalse(analysis.analyze_manifest(changed, root)["matrix_complete"], field)

    def test_comparison_warnings_do_not_complete_bad_data(self):
        # Reuse the complete fixture through a local wrapper to inspect its mutations.
        original = analysis.analyze_manifest
        checked = []

        def inspect(manifest, root):
            result = original(manifest, root)
            if result["matrix_complete"] and not checked:
                checked.append(True)
                for field, value, flag in (
                        ("settings", {"limit": 60}, "configuration_comparable"),
                        ("settings", None, "configuration_comparable"),
                        ("settings", {}, "configuration_comparable"),
                        ("probe_availability", {"gpu": "enabled"}, "probe_configuration_comparable"),
                        ("probe_availability", None, "probe_configuration_comparable")):
                    changed = copy.deepcopy(manifest)
                    changed["runs"][1][field] = value
                    comparison = original(changed, root)
                    self.assertTrue(comparison["matrix_complete"])
                    self.assertEqual(comparison["validation_status"], "matrix_data_complete")
                    self.assertFalse(comparison[flag])
                    self.assertFalse(comparison["strict_ab_comparable"])
                    self.assertTrue(comparison["comparison_warnings"])
                    self.assertEqual(comparison["execution_issues"], [])
                    self.assertGreater(len(comparison["comparison_details"][field]["variants"]), 1)
                    changed["runs"][1]["triggers"] = []
                    self.assertFalse(original(changed, root)["matrix_complete"])
                for mutation in ("missing", "negative", "nan", "mismatch", "truncated", "empty"):
                    bad = capture()
                    if mutation == "missing":
                        del bad["frames"]["interval_ns"]
                    elif mutation in ("negative", "nan", "mismatch"):
                        bad["frames"]["interval_ns"][2] = {
                            "negative": -1, "nan": float("nan"), "mismatch": 42}[mutation]
                    elif mutation == "truncated":
                        for values in bad["frames"].values():
                            del values[-2:]
                        bad["summary"]["frame_count"] -= 2
                    else:
                        bad["frames"] = {"head_offset_ns": [], "interval_ns": []}
                        bad["summary"]["frame_count"] = 0
                    (root / "bad.json").write_text(json.dumps(bad), encoding="utf-8")
                    changed = copy.deepcopy(manifest)
                    changed["runs"][1]["frames"] = "bad.json"
                    self.assertFalse(original(changed, root)["matrix_complete"], mutation)
            return result

        from unittest.mock import patch
        with patch.object(analysis, "analyze_manifest", side_effect=inspect):
            self.test_full_matrix_and_mutations()
        self.assertEqual(checked, [True])

    def test_budget_and_sequences_do_not_cross_runs(self):
        rows = [{"run_id": run, "frame_id": frame, "interval_ns": 60 * analysis.NS_PER_MS}
                for run, frame in (("a", 1), ("a", 2), ("b", 3))]
        slow = analysis.distribution(rows)["slow_frames"]["50.0"]
        self.assertEqual(slow["longest_consecutive"], 2)
        self.assertEqual(slow["cumulative_over_budget_ns"], 30 * analysis.NS_PER_MS)

    def test_signed_timing_and_unknown_configuration(self):
        self.assertEqual(analysis.stats([-10, 0, 10], signed=True)["min"], -10)
        self.assertFalse(analysis.comparison_details(
            [{"id": "a", "settings": {"limit": None}}], "settings")["comparable"])
        self.assertFalse(analysis.comparison_details(
            [{"id": "a", "probe_availability": {"gpu": "unknown"}}],
            "probe_availability")["comparable"])

    def test_overlap_snapshot_preserved(self):
        source = {"triggers": [{"index": i, "observed_ns": i, "started": True,
                                "overlap_at_dispatch": {"active_count": i}}
                               for i in range(3)]}
        result = analysis.trigger_analysis(source)
        self.assertEqual([t["overlap_at_dispatch"]["active_count"] for t in result["triggers"]], [0, 1, 2])

    def test_variable_capture_coverage(self):
        c = capture()
        for values in c["frames"].values():
            del values[-2:]
        c["summary"]["frame_count"] -= 2
        result = analysis.analyze_capture(c, timing())
        self.assertFalse(result["window_coverage"]["trigger2_plus_5s"]["bounded_window_covered"])

    def test_invalid_native_format(self):
        with self.assertRaises(ValueError):
            analysis.frame_records({"summary": {}, "frames": []})
        with self.assertRaises(ValueError):
            analysis.analyze_manifest({"schema": "other", "runs": []}, pathlib.Path("."))

    def test_cli_empty_matrix_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            manifest = root / "matrix.json"
            manifest.write_text(json.dumps({"schema": analysis.SCHEMA, "runs": []}), encoding="utf-8-sig")
            command = [sys.executable, "-B", str(pathlib.Path(analysis.__file__)),
                       "--manifest", str(manifest), "--output-dir", str(root / "result")]
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            output = json.loads((root / "result" / "analysis.json").read_text(encoding="utf-8"))
            self.assertFalse(output["matrix_complete"])
            self.assertIn("incomplete", (root / "result" / "report.md").read_text(encoding="utf-8"))
            self.assertEqual(subprocess.run(command, capture_output=True).returncode, 1)


if __name__ == "__main__":
    unittest.main()
