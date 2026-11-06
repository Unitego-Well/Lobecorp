import test from "node:test";
import assert from "node:assert/strict";
import {mkdtemp, writeFile, rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {createHash} from "node:crypto";
import {validateMigrationResult, verifyMigrationArtifacts} from "./observe_migration26129.mjs";

function fixture(runId = "MHN01") {
    const label = "review_fixture";
    const report = {
        schema: "photon-migration26129-v1", run_id: runId, pid: 7, label,
        archived: true, finished: true, failure: null, verified: true,
        native_contracts_verified: true, raw_archived: true, raw_archive_error: null,
        cleanup_complete: true, cleanup_failed: false, capture_seconds: 45, variant: "laser3",
        entry_t0_ns: 100, shader_in_use: runId === "MS01" || runId === "MHS01",
        creation_mode: runId.startsWith("MH") ? "HYBRID" : "NATIVE",
        frames: {state: "DURATION", label, started_ns: 100, frame_count: 2, error: null},
        actual_mod_versions: {photon: "26.1.2.9", ldlib2: "26.1.2.43", kilagraph: "26.1.0.16.a"},
        frame_artifacts: [{artifact: label + "_frames.csv", sha256: "placeholder"}],
        factory_delta: {
            prepared_source_cache_hits: 45, prepared_source_compilations: 0,
            pool_hit: 1, pool_reset: 1, reset_failures: 0
        }
    };
    const contracts = {
        passed: true, coverage_complete: true, main_window_sample: false,
        project_reset_verified: true, natural_drain_verified: true
    };
    const summary = {
        summary: {status: "PASSED"},
        scenarios: [{id: "lobecorp.photon-06-migration-laser3", status: "PASSED"}]
    };
    return {report, contracts, summary, identity: {pid: 7}, runId};
}

function validate(value) {
    return validateMigrationResult(value.summary, value.report, value.contracts, value.identity, value.runId);
}

test("four declared paths accept complete explicit contracts without discovering a game", () => {
    for (const id of ["MN01", "MS01", "MHN01", "MHS01"]) assert.doesNotThrow(() => validate(fixture(id)));
});
test("incomplete counters and false contracts cannot pass by coercion", () => {
    const corruptions = [
        v => delete v.report.factory_delta.pool_hit,
        v => v.report.factory_delta.pool_reset = NaN,
        v => v.report.factory_delta.reset_failures = Infinity,
        v => v.report.factory_delta.pool_hit = "1",
        v => v.contracts.passed = "true",
        v => v.contracts.project_reset_verified = false,
        v => v.contracts.natural_drain_verified = false,
        v => v.report.native_contracts_verified = undefined,
        v => v.summary.scenarios[0].status = "FAILED",
        v => v.report.frames.label = "foreign",
        v => v.report.raw_archived = false,
        v => v.report.frame_artifacts = []
    ];
    for (const corrupt of corruptions) {
        const value = fixture();
        corrupt(value);
        assert.throws(() => validate(value));
    }
});
test("raw artifacts require both frame data and session and detect corruption", async () => {
    const directory = await mkdtemp(join(tmpdir(), "photon-observer-review-"));
    try {
        const report = fixture().report;
        report.frame_artifacts = [];
        for (const [suffix, content] of [["_frames.csv", "frame_id,interval_ns\n0,\n1,100\n"], ["_session.json", "{}"]]) {
            const name = report.label + suffix;
            await writeFile(join(directory, name), content);
            report.frame_artifacts.push({artifact: name, sha256: createHash("sha256").update(content).digest("hex")});
        }
        await assert.doesNotReject(() => verifyMigrationArtifacts(directory, report));
        const missingSession = {...report, frame_artifacts: report.frame_artifacts.slice(0, 1)};
        await assert.rejects(() => verifyMigrationArtifacts(directory, missingSession), /migration_raw_required_missing/);
        await writeFile(join(directory, report.label + "_frames.csv"), "changed");
        await assert.rejects(() => verifyMigrationArtifacts(directory, report), /migration_raw_artifact_hash/);
    } finally {
        await rm(directory, {recursive: true, force: true});
    }
});
