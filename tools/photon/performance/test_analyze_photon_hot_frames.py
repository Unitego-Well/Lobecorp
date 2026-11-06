"""纯分析边界用例；由主线统一执行。"""
import json
from pathlib import Path
import tempfile
import unittest

from analyze_photon_hot_frames import analyze, epoch_ns, iter_jfr, union_ns


class HotFrameAnalysisTest(unittest.TestCase):
    def run_data(self):
        return {"id": "fixture", "game_pid": 42, "capture": {
            "summary": {"started_at": "2026-10-30T00:00:00.123456789Z", "started_ns": 100},
            "records": [
                {"frame_id": 1, "start_ns": 100, "end_ns": 60_000_100, "interval_ns": 60_000_000},
                {"frame_id": 2, "start_ns": 60_000_100, "end_ns": 70_000_100, "interval_ns": 10_000_000}
            ]}}

    def event(self, stamp, kind="jdk.ExecutionSample", **values):
        return {"type": kind, "values": {
            "startTime": "2026-10-30T00:00:00." + stamp + "Z",
            "sampledThread": {"javaName": "Render thread"},
            "frames": ["Particle.tick", "Particle.tick"], **values}}

    def test_epoch_nanoseconds_and_timezone(self):
        self.assertEqual(epoch_ns("2026-10-30T00:00:00.123456789Z"),
                         epoch_ns("2026-10-30T08:00:00.123456789+08:00"))
        self.assertEqual(epoch_ns("2026-10-30T00:00:00.123456790Z")
                         - epoch_ns("2026-10-30T00:00:00.123456789Z"), 1)

    def test_half_open_window_and_boundary_ownership(self):
        result = analyze(self.run_data(), [
            self.event("123456788"), self.event("123456789"),
            self.event("183456789"), self.event("193456789")])
        counts = result["counts"]
        self.assertEqual(counts["jdk.ExecutionSample|Render thread|slow"]["samples"], 1)
        self.assertEqual(counts["jdk.ExecutionSample|Render thread|stable"]["samples"], 1)
        self.assertEqual(counts["jdk.ExecutionSample|Render thread|slow|inclusive"]["Particle.tick"], 1)
        self.assertEqual(result["excluded_counts"]["jdk.ExecutionSample"], 2)

    def test_gc_overlap_is_clipped_and_unioned(self):
        result = analyze(self.run_data(), [
            self.event("173456789", "jdk.GCPhasePause", duration="PT0.02S"),
            self.event("178456789", "jdk.GCPhasePause", duration="PT0.01S")])
        self.assertEqual(result["gc"]["pause_union_ns"], 20_000_000)
        self.assertEqual(result["gc"]["slow_overlap_ns"], 10_000_000)
        self.assertEqual(result["gc"]["slow_frames_overlapping"], 1)
        self.assertEqual(result["metrics"]["all"]["render_gpu_ns"], {"count": 0})

    def test_streaming_json_and_jsonl(self):
        events = [self.event("123456789"), self.event("183456789")]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "events.json"
            path.write_text(json.dumps({"recording": {"events": events}}, indent=2), encoding="utf-8")
            self.assertEqual(list(iter_jfr(path)), events)
            path.write_text("\n".join(json.dumps(e) for e in events), encoding="utf-8")
            self.assertEqual(list(iter_jfr(path)), events)

    def test_gui_sort_is_not_transparent_particle_sort(self):
        result = analyze(self.run_data(), [
            self.event("133456789", frames=["java/util/Arrays.sort",
                                            "net/minecraft/client/renderer/state/gui/GuiRenderState.sortElements"])])
        categories = result["counts"].get("jdk.ExecutionSample|Render thread|slow|categories", {})
        self.assertNotIn("transparent_sort", categories)

    def test_overlapping_frames_rejected(self):
        run = self.run_data()
        run["capture"]["records"][1]["start_ns"] -= 1
        with self.assertRaises(ValueError):
            analyze(run, [])

    def test_union_disjoint_and_nested(self):
        self.assertEqual(union_ns([(1, 8), (2, 3), (7, 10), (12, 14)]), 11)


if __name__ == "__main__":
    unittest.main()
