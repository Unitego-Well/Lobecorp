"""Synthetic, in-memory checks only. Run: python -B test_audit_repeat_inputs.py."""
import copy
from pathlib import Path
import unittest

import audit_repeat_inputs as audit

BASE = Path("/synthetic")
POLICY = {
    "COUNT": 3, "INTERVAL_SECONDS": 5, "WORLD_PREFIX": "photon_repeat_",
    "FX_SHA256": "a" * 64, "MATERIAL_SHA256": "b" * 64, "TEXTURE_SHA256": "c" * 64,
}


def fixture(mode="run", kind="native", pid=100):
    world = "photon_repeat_" + str(pid)
    environment = {key: False for key in audit.ENV_KEYS}
    environment.update(width=2560, height=1440, focused=False, maximized=True,
                       iconified=False, fullscreen=False, vsync=True, fps_limit=260,
                       render_distance=16, fov=70, shader_pack=False, graphics="fancy",
                       particles_setting="all", sodium=True, spark=True,
                       cast_requested=mode == "full", submission_requested=mode == "full",
                       resource_packs=[])
    actions = []
    for index in range(3):
        requested = 10_000_000_000 + (2 + index * 5) * 1_000_000_000
        actions.append({"index": index, "requested_ns": requested,
                        "dispatch_ns": requested + (index + 1) * 10_000_000,
                        "actual_ns": requested + (index + 1) * 10_000_000 + 1_000_000,
                        "first_active_ns": requested + (index + 1) * 10_000_000 + 20_000_000,
                        "started": True})
    args = ["-Dmcp.port=19877", "--enable-native-access=ALL-UNNAMED"]
    if mode == "full":
        args += ["-agentlib:jdwp=transport=dt_socket",
                 "-DmodBench.scenarios=lobecorp.photon-03-repeat-performance"]
    assets = [{"source": name, "source_pack": "runtime_pack", "sha256": POLICY[key],
               "expected_sha256": POLICY[key], "bytes": 100}
              for name, key in (("photon:fx/first_effect2.fx", "FX_SHA256"),
                                ("particle.material.nbt", "MATERIAL_SHA256"),
                                ("lobecorp:the_queen_of_hatred/particle.png", "TEXTURE_SHA256"))]
    trial = {"pid": pid, "label": str(pid), "kind": kind.upper(), "mode": "BASE" if mode == "run" else "FULL",
             "state": "COMPLETE", "workload_verified": True, "world": world,
             "world_path": "/game/saves/" + world, "game_directory": "/game",
             "world_seed": 20261029, "vm_arguments": args, "initial_environment": environment,
             "mod_versions": {"sodium": "synthetic", "spark": "synthetic"},
             "assets": assets, "triggers": actions,
             "main_frames": {"frame_count": 1, "started_ns": 10_000_000_000, "label": str(pid) + "_main"},
             "samples": [{"observed_ns": 15_000_000_000, "environment": copy.deepcopy(environment)}]}
    quality = {key: 0 for key in audit.QUALITY_FIELDS}
    quality["limit"] = None
    session = {"collector_quality": copy.deepcopy(quality), "dropped_records": 0,
               "overwritten_events": 0, "incomplete_spans": 0, "mode": "GPU"}
    if mode == "full":
        trial["main_trace"] = {"retention": {"denied_dropped_quality": quality,
                                            "raw_truncated": False, "aggregate_truncated": False}}
    run = {"id": str(pid), "kind": kind, "mode": mode, "iteration": 1, "game_pid": pid,
           "trial_report": str(pid) + "/trial.json", "metadata": str(pid) + "/metadata.json",
           "frames": str(pid) + "/frames.json", "trace_directory": str(pid) + "/trace",
           "spark": str(pid) + "/client.sparkprofile", "jfr": str(pid) + "/client.jfr",
           "trigger_time_scope": "trial dispatch_ns; not server start or effect first frame",
           "triggers": [{"index": item["index"], "requested_ns": item["requested_ns"],
                         "observed_ns": item["dispatch_ns"], "started": True,
                         "first_active_ns": item["first_active_ns"]} for item in actions]}
    documents = {str(BASE / run["trial_report"]): trial,
                 str(BASE / run["metadata"]): {"pid": pid, "vm_arguments": args, "test_environment": {}},
                 str(BASE / run["frames"]): {"summary": copy.deepcopy(trial["main_frames"]),
                                           "frames": {"interval_ns": [16_000_000]}},
                 str(BASE / run["trace_directory"] / "session.json"): session}
    return run, trial, documents


class AuditTests(unittest.TestCase):
    def setUp(self):
        self.run, self.trial, self.documents = fixture()

    def load(self, path):
        if str(path) not in self.documents:
            raise FileNotFoundError(str(path))
        return self.documents[str(path)]

    def result(self):
        return audit.audit_run(self.run, BASE, POLICY, 250, self.load, lambda path: "f" * 64)

    def check(self, result, name):
        return next(item for item in result["checks"] if item["check"] == name)

    def test_current_base_fields_and_focus_false(self):
        result = self.result()
        self.assertEqual("pass", result["status"])
        self.assertEqual("not_enabled", self.check(result, "detailed_trace_quality")["status"])
        self.assertAlmostEqual(5.01, result["timing"]["intervals"][0]["actual_ns"]["interval_seconds"])
        self.assertAlmostEqual(10, result["timing"]["intervals"][0]["actual_ns"]["jitter_ms"])

    def test_full_current_quality_fields(self):
        self.run, self.trial, self.documents = fixture("full", "laser")
        self.assertEqual("pass", self.result()["status"])

    def test_java_parameter_classification(self):
        flagged = ["-DmodBench.enabled=false", "-Dlobecorp.photon.diag.cast=false",
                   "-DMC_DEBUG_FOO=false", "-Dminecraft.debug.foo=1", "-DfooGameTest=false",
                   "-DfooHotSwap=0", "-Dspark.enabled=false", "-Djdk.jfr.foo=1",
                   "-agentlib:jdwp=foo", "-XX:StartFlightRecording=x",
                   "-XX:+AllowEnhancedClassRedefinition", "-javaagent:hotswap.jar"]
        clean = ["-Dmcp.port=19877", "-Dterminal.ansi=true", "-Dlog4j2.configurationFile=x",
                 "-Dfoo=/mods/spark.jar", "-cp", "mods/spark.jar", "--enable-native-access=ALL-UNNAMED"]
        for argument in flagged:
            self.assertTrue(audit.test_vm_argument(argument), argument)
        for argument in clean:
            self.assertFalse(audit.test_vm_argument(argument), argument)

    def test_false_flag_still_contaminates_run(self):
        self.trial["vm_arguments"].append("-DmodBench.enabled=false")
        self.assertEqual("fail", self.check(self.result(), "real_vm_arguments")["status"])

    def test_static_file_cannot_replace_runtime_args(self):
        del self.trial["vm_arguments"]
        self.run["vm_arguments_file"] = "config/run-control-vmargs.txt"
        self.assertEqual("unknown", self.check(self.result(), "real_vm_arguments")["status"])

    def test_missing_env_is_unknown(self):
        del self.documents[str(BASE / self.run["metadata"])]["test_environment"]
        self.assertEqual("unknown", self.result()["status"])

    def test_wrong_pid_env_is_not_evidence(self):
        self.documents[str(BASE / self.run["metadata"])]["pid"] = 999
        self.assertEqual("fail", self.result()["status"])
        self.assertEqual("unknown", self.check(self.result(), "process_environment")["status"])

    def test_environment_contamination(self):
        self.documents[str(BASE / self.run["metadata"])]["test_environment"] = {"MC_DEBUG_FOO": "false"}
        self.assertEqual("fail", self.result()["status"])

    def test_java_environment_requires_reconciliation(self):
        runtime = self.documents[str(BASE / self.run["metadata"])]
        runtime.pop("test_environment")
        runtime["env"] = {"JAVA_TOOL_OPTIONS": "-DmodBench.enabled=true"}
        self.assertEqual("unknown", self.result()["status"])

    def test_missing_report_and_files_are_unknown(self):
        self.documents.clear()
        self.assertEqual("unknown", self.result()["status"])
        report = audit.audit_manifest({"schema": audit.SCHEMA, "runs": [self.run]},
                                      BASE, POLICY, loader=self.load)
        self.assertEqual(0, report["executed_runs"])
        self.assertEqual(1, report["declared_runs"])

    def test_column_array_frame_format_required(self):
        frames = self.documents[str(BASE / self.run["frames"])]
        frames["frames"] = [{"interval_ns": 16_000_000}]
        self.assertEqual("unknown", self.check(self.result(), "frame_input")["status"])

    def test_frame_count_mismatch_is_not_pass(self):
        self.documents[str(BASE / self.run["frames"])]["summary"]["frame_count"] = 2
        self.assertEqual("unknown", self.check(self.result(), "frame_input")["status"])

    def test_null_environment_is_unknown(self):
        self.trial["initial_environment"]["width"] = None
        self.assertEqual("unknown", self.result()["status"])

    def test_missing_full_artifact_is_unknown(self):
        self.run, self.trial, self.documents = fixture("full")
        self.run.pop("jfr")
        self.assertEqual("unknown", self.result()["status"])

    def test_missing_full_scenario_is_unknown(self):
        self.run, self.trial, self.documents = fixture("full")
        self.trial["vm_arguments"].pop()
        self.assertEqual("unknown", self.result()["status"])

    def test_missing_trigger_time_and_scope(self):
        self.run.pop("trigger_time_scope")
        self.trial["triggers"][0].pop("actual_ns")
        self.assertEqual("unknown", self.result()["status"])

    def test_manifest_trial_trigger_mismatch(self):
        self.run["triggers"][0]["first_active_ns"] += 1
        self.assertEqual("fail", self.result()["status"])

    def test_optional_manifest_first_active_uses_trial_evidence(self):
        for event in self.run["triggers"]:
            event.pop("first_active_ns")
        self.assertEqual("pass", self.result()["status"])

    def test_manifest_level_trigger_scope_is_supported(self):
        scope = self.run.pop("trigger_time_scope")
        report = audit.audit_manifest({"schema": audit.SCHEMA, "runs": [self.run],
                                       "trigger_time_scope": scope}, BASE, POLICY,
                                      loader=self.load, hasher=lambda path: "f" * 64)
        self.assertEqual("pass", report["runs"][0]["status"])

    def test_duplicate_trigger_indices(self):
        self.trial["triggers"][1]["index"] = 0
        self.assertEqual("fail", self.result()["status"])

    def test_dispatch_and_server_jitter(self):
        self.trial["triggers"][1]["actual_ns"] += 500_000_000
        result = self.result()
        self.assertEqual("fail", result["status"])
        self.assertAlmostEqual(510, result["timing"]["intervals"][0]["actual_ns"]["jitter_ms"])

    def test_not_started(self):
        self.trial["triggers"][1]["started"] = False
        self.assertEqual("fail", self.result()["status"])

    def test_bad_asset_hash(self):
        self.trial["assets"][0]["sha256"] = "f" * 64
        self.assertEqual("fail", self.result()["status"])

    def test_missing_native_asset(self):
        self.trial["assets"].pop()
        self.assertEqual("unknown", self.result()["status"])

    def test_file_asset_is_rehashed(self):
        self.trial["assets"][0]["source_pack"] = "file"
        self.assertEqual("fail", self.result()["status"])

    def test_missing_quality_counter(self):
        self.run, self.trial, self.documents = fixture("full")
        del self.trial["main_trace"]["retention"]["denied_dropped_quality"]["deniedNodeRequests"]
        self.assertEqual("unknown", self.result()["status"])

    def test_denied_dropped_and_overwritten_are_preserved(self):
        self.run, self.trial, self.documents = fixture("full")
        quality = self.trial["main_trace"]["retention"]["denied_dropped_quality"]
        quality["droppedRawSpans"] = 19
        quality["deniedAggregateRequests"] = 2
        self.documents[str(BASE / self.run["trace_directory"] / "session.json")]["overwritten_events"] = 23
        result = self.result()
        self.assertEqual("warn", result["status"])
        self.assertEqual(19, self.check(result, "trial_quality_droppedRawSpans")["evidence"])
        self.assertEqual(23, self.check(result, "overwritten_events")["evidence"])

    def test_window_changes_and_iconification(self):
        self.trial["samples"][0]["environment"]["iconified"] = True
        self.assertEqual("fail", self.result()["status"])

    def test_focus_changes_are_reported_without_rejection(self):
        self.trial["samples"][0]["environment"]["focused"] = True
        self.assertEqual("warn", self.result()["status"])

    def test_empty_matrix_is_explicitly_not_run(self):
        result = audit.audit_manifest({"schema": audit.SCHEMA, "runs": []}, BASE, POLICY)
        self.assertEqual("not_run", result["status"])
        self.assertEqual([], result["runs"])
        self.assertEqual(0, result["executed_runs"])

    def test_matrix_uniqueness_and_coverage(self):
        run2 = copy.deepcopy(self.run)
        run2["id"] = "second"
        run2["iteration"] = 2
        result = audit.audit_manifest({"schema": audit.SCHEMA, "runs": [self.run, run2]},
                                      BASE, POLICY, loader=self.load, hasher=lambda path: "f" * 64)
        self.assertEqual("fail", result["status"])
        self.assertEqual("fail", self.check(result, "new_pid")["status"])
        self.assertEqual("unknown", self.check(result, "matrix_coverage")["status"])

    def test_full_matrix_and_configuration_differences(self):
        runs = []
        for kind in ("laser", "native"):
            for mode in ("full", "run"):
                for iteration in range(1, 4):
                    run, trial, documents = fixture(mode, kind, 200 + len(runs))
                    run["iteration"] = iteration
                    runs.append(run)
                    self.documents.update(documents)
        result = audit.audit_manifest({"schema": audit.SCHEMA, "runs": runs}, BASE, POLICY,
                                      loader=self.load, hasher=lambda path: "f" * 64)
        self.assertEqual(12, result["executed_runs"])
        self.assertEqual("pass", self.check(result, "matrix_coverage")["status"])
        self.assertTrue(all(item["status"] == "pass" for item in result["runs"]))
        self.assertEqual("warn", result["status"])
        self.assertTrue(self.check(result, "between_run_configuration_differences")["evidence"])

    def test_malformed_inputs_never_pass(self):
        for data in ([], {}, {"schema": audit.SCHEMA, "runs": "wrong"},
                     {"schema": audit.SCHEMA, "runs": [None]}):
            self.assertNotEqual("pass", audit.audit_manifest(data, BASE, POLICY)["status"])

    def test_duplicate_scenario_last_wins(self):
        good = "-DmodBench.scenarios=lobecorp.photon-03-repeat-performance"
        bad = "-DmodBench.scenarios=lobecorp.photon-02-first-cast-performance"
        for arguments, status in (([bad, good], "pass"), ([good, bad], "fail"),
                                  ([good, "-DmodBench.scenarios"], "fail")):
            with self.subTest(arguments=arguments):
                self.run, self.trial, self.documents = fixture("full")
                self.trial["vm_arguments"][:] = ["-agentlib:jdwp=foo"] + arguments
                result = self.result()
                self.assertEqual(status, self.check(result, "full_scenario")["status"])
                evidence = self.check(result, "real_vm_arguments")["evidence"]
                self.assertEqual(self.trial["vm_arguments"], evidence["arguments"])
                self.assertEqual(arguments[-1].partition("=")[2],
                                 evidence["effective_system_properties"]["modBench.scenarios"])

    def duration_window(self):
        self.trial["initial_environment"].update(width=854, height=480, maximized=False)
        window = {"maximized": True, "iconified": False, "window_width": 2560,
                  "window_height": 1440, "framebuffer_width": 2560, "framebuffer_height": 1440}
        self.trial["main_frames"].update(state="DURATION", environment_first=copy.deepcopy(window),
                                         environment_last=copy.deepcopy(window))
        runtime = self.documents[str(BASE / self.run["metadata"])]
        runtime["environment"] = copy.deepcopy(self.trial["initial_environment"])

    def test_duration_main_window_overrides_initial_snapshot(self):
        self.duration_window()
        result = self.result()
        self.assertEqual("pass", self.check(result, "window_or_renderer:maximized")["status"])
        self.assertEqual(854, result["comparison"]["environment"]["width"])
        sources = self.check(result, "window_environment_sources")["evidence"]
        self.assertEqual(2560, sources["environment_first"]["window_width"])
        self.assertFalse(sources["runtime_status"]["maximized"])

    def test_duration_main_window_false_or_missing_never_passes(self):
        for phase in ("environment_first", "environment_last"):
            for value, status in ((False, "fail"), (None, "unknown")):
                with self.subTest(phase=phase, value=value):
                    self.setUp()
                    self.duration_window()
                    self.trial["main_frames"][phase]["maximized"] = value
                    self.assertEqual(status, self.check(
                        self.result(), "window_or_renderer:maximized")["status"])

    def test_duration_does_not_hide_bad_sample_or_iconification(self):
        self.duration_window()
        self.trial["samples"][0]["environment"]["maximized"] = False
        self.assertEqual("fail", self.check(self.result(), "sampled_window")["status"])
        self.trial["main_frames"]["environment_last"]["iconified"] = True
        self.assertEqual("fail", self.check(self.result(), "window_or_renderer:iconified")["status"])

    def test_non_duration_does_not_override_initial_window(self):
        self.duration_window()
        self.trial["main_frames"]["state"] = "RUNNING"
        self.assertEqual("fail", self.check(self.result(), "window_or_renderer:maximized")["status"])

    def test_owned_level_dat_is_mutable_snapshot_only(self):
        source = self.trial["world_path"] + "/level.dat"
        self.trial["assets"].append({"source": source, "source_pack": "file", "sha256": "a" * 64})
        result = self.result()
        snapshot = self.check(result, "world_snapshot:" + source)
        self.assertEqual("info", snapshot["status"])
        self.assertEqual("a" * 64, snapshot["evidence"]["snapshot_sha256"])
        self.assertEqual("f" * 64, snapshot["evidence"]["current_sha256"])
        self.assertEqual("trial initial world snapshot", snapshot["evidence"]["snapshot_phase"])
        self.assertNotIn(source, result["comparison"]["asset_hashes"])
        self.assertEqual("pass", result["status"])

    def test_other_files_and_other_world_level_dat_remain_strict(self):
        for source in ("/other/level.dat", self.trial["world_path"] + "/level.dat_old",
                       self.trial["world_path"] + "/region/r.0.0.mca"):
            with self.subTest(source=source):
                self.trial["assets"] = fixture()[1]["assets"] + [
                    {"source": source, "source_pack": "file", "sha256": "a" * 64}]
                result = self.result()
                self.assertEqual("fail", self.check(result, "asset_current_file:" + source)["status"])
                comparison_key = source[len("/game"):] if source.startswith("/game/") else source
                self.assertIn(comparison_key, result["comparison"]["asset_hashes"])

    def test_static_fx_material_texture_rehash_stays_strict(self):
        for index in range(3):
            with self.subTest(index=index):
                self.setUp()
                self.trial["assets"][index]["source_pack"] = "file"
                result = self.result()
                source = self.trial["assets"][index]["source"]
                self.assertEqual("fail", self.check(result, "asset_current_file:" + source)["status"])

    def test_actual_policy_and_static_template(self):
        root = Path(__file__).resolve().parents[3]
        policy = audit.load_policy(root)
        self.assertEqual(3, policy["COUNT"])
        self.assertEqual(5, policy["INTERVAL_SECONDS"])
        tokens = audit.shlex.split((root / "tools/photon/performance/config/run-control-vmargs.txt")
                                   .read_text(encoding="utf-8-sig"), comments=True)
        self.assertEqual([], [arg for arg in tokens if audit.test_vm_argument(arg)])



class RelativeAssetPathsTest(unittest.TestCase):
    def test_game_relative_asset_uses_recorded_game_directory(self):
        trial = {"game_directory": str(Path.cwd() / "synthetic-game"), "assets": [
            {"source": r".\ldlib2\assets\photon\fx\first_effect2.fx",
             "source_pack": "file", "sha256": "a" * 64}]}
        checks, seen = [], []
        def hasher(path):
            seen.append(path)
            return "a" * 64
        audit.audit_assets(trial, POLICY, checks, hasher)
        self.assertEqual([Path(trial["game_directory"]) / "ldlib2/assets/photon/fx/first_effect2.fx"], seen)
        self.assertTrue(any(c["check"].startswith("asset_current_file:") and c["status"] == "pass"
                            for c in checks))

    def test_game_relative_asset_mismatch_still_fails(self):
        trial = {"game_directory": str(Path.cwd() / "synthetic-game"), "assets": [
            {"source": "./ldlib2/assets/photon/fx/first_effect2.fx",
             "source_pack": "file", "sha256": "a" * 64}]}
        checks = []
        audit.audit_assets(trial, POLICY, checks, lambda _: "b" * 64)
        self.assertTrue(any(c["check"].startswith("asset_current_file:") and c["status"] == "fail"
                            for c in checks))

if __name__ == "__main__":
    unittest.main(verbosity=2)
