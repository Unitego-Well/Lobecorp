"""Regression checks for frame CSV and trace-session origin alignment."""
import csv
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("event_audit", Path(__file__).with_name("analyze_event_perf_20261102.py"))
audit_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit_module)


class OriginAlignmentTest(unittest.TestCase):
    def test_entry_window_includes_exact_end_and_excludes_later_interval(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            custom = root / "HNQ01/results/artifacts/custom"
            custom.mkdir(parents=True)
            frame_summary = {"started_ns": 1_100_000_000}
            report = {"frames": frame_summary, "label": "sample", "frame_artifacts": []}
            (custom / "sample_hybrid18.json").write_text(json.dumps(report), encoding="utf-8")
            session = {"started_ns": 1_000_000_000, "frame_origin_ns": 1_100_000_000,
                       "frame_summary": frame_summary}
            (custom / "sample_session.json").write_text(json.dumps(session), encoding="utf-8")
            with (custom / "sample_frames.csv").open("w", newline="", encoding="utf-8") as stream:
                writer = csv.writer(stream)
                writer.writerow(["frame_id", "head_offset_ns", "interval_ns", "render_thread_allocated_bytes"])
                writer.writerows([[0, 100_000_000, "", ""],
                                  [1, 1_000_000_000, 900_000_000, 10],
                                  [2, 25_100_000_000, 24_100_000_000, 20],
                                  [3, 25_200_000_000, 100_000_000, 30]])
            result = audit_module.audit(root, "HNQ01", "0" * 40)
            self.assertEqual(100_000_000, result["csv_origin_shift_ns"])
            self.assertEqual(2, result["metrics"]["full"]["count"])
            self.assertEqual(30, result["metrics"]["full"]["render_thread_allocation_bytes"])
            self.assertEqual(24_100.0, result["metrics"]["full"]["worst_ms"])

    def test_missing_clock_anchor_is_explicitly_invalid(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            custom = root / "HNQ01/results/artifacts/custom"
            custom.mkdir(parents=True)
            (custom / "sample_hybrid18.json").write_text(json.dumps({"frames": {}, "label": "sample"}), encoding="utf-8")
            result = audit_module.audit(root, "HNQ01", "0" * 40)
            self.assertIn("unknown:csv_clock_origins", result["issues"])
            self.assertIsNone(result["csv_origin_shift_ns"])
            self.assertFalse(result["eligible"])


if __name__ == "__main__":
    unittest.main()
