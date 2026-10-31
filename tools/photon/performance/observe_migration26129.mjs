import {registerHooks, createRequire} from "node:module";
import {pathToFileURL} from "node:url";
import {readFile, readdir} from "node:fs/promises";
import {resolve, basename, dirname} from "node:path";
import {createHash} from "node:crypto";

/// 纯离线验收入口；导入模块不发现实例、不连接游戏。
export function validateMigrationResult(summary, report, contracts, identity, runId) {
    if (!/^(MN01|MS01|MHN01|MHS01)$/.test(runId ?? "")) throw Error("migration26129_run_id_required");
    const expectedMode = runId.startsWith("MH") ? "HYBRID" : "NATIVE";
    const expectedShader = runId === "MS01" || runId === "MHS01";
    const selected = summary.scenarios.filter(item => item.status !== "SKIPPED");
    if (summary.summary.status !== "PASSED" || selected.length !== 1 ||
        selected[0].id !== "lobecorp.photon-06-migration-laser3" || selected[0].status !== "PASSED") {
        throw Error("migration26129_failed:" + JSON.stringify(selected));
    }
    if (report.schema !== "photon-migration26129-v1" || report.run_id !== runId ||
        report.pid !== identity.pid || report.archived !== true || report.finished !== true ||
        report.failure !== null || report.verified !== true || report.native_contracts_verified !== true ||
        report.raw_archived !== true || report.raw_archive_error !== null ||
        report.cleanup_complete !== true || report.cleanup_failed !== false ||
        report.frames?.state !== "DURATION" || report.frames.label !== report.label ||
        report.frames.started_ns !== report.entry_t0_ns || !Number.isFinite(report.frames.frame_count) ||
        report.frames.frame_count <= 0 ||
        report.frames.error !== null || report.capture_seconds !== 45 || report.variant !== "laser3" ||
        report.shader_in_use !== expectedShader || report.creation_mode !== expectedMode ||
        !Array.isArray(report.frame_artifacts) || report.frame_artifacts.length === 0) {
        throw Error("migration26129_report_identity_or_completion");
    }
    if (contracts.passed !== true || contracts.coverage_complete !== true ||
        contracts.main_window_sample !== false || contracts.project_reset_verified !== true ||
        contracts.natural_drain_verified !== true) {
        throw Error("migration_contract_failure");
    }
    const versions = report.actual_mod_versions;
    if (versions?.photon !== "26.1.2.9" || versions.ldlib2 !== "26.1.2.43" ||
        versions.kilagraph !== "26.1.0.16.a") throw Error("migration_versions");
    if (expectedMode === "HYBRID") {
        const d = report.factory_delta;
        const counters = ["prepared_source_cache_hits", "prepared_source_compilations",
            "pool_hit", "pool_reset", "reset_failures"];
        if (!counters.every(key => Number.isFinite(d?.[key]) && d[key] >= 0) ||
            d.prepared_source_cache_hits < 45 || d.prepared_source_compilations !== 0 ||
            d.pool_hit <= 0 || d.pool_reset <= 0 || d.reset_failures !== 0) {
            throw Error("migration_factory_usage");
        }
    }
}


export async function verifyMigrationArtifacts(custom, report) {
    if (!Array.isArray(report.frame_artifacts) || report.frame_artifacts.length === 0) {
        throw Error("migration_raw_manifest_missing");
    }
    for (const suffix of ["_frames.csv", "_session.json"]) {
        if (!report.frame_artifacts.some(item => typeof item.artifact === "string" &&
            basename(item.artifact) === report.label + suffix)) throw Error("migration_raw_required_missing");
    }
    for (const item of report.frame_artifacts) {
        if (typeof item.artifact !== "string" || typeof item.sha256 !== "string") {
            throw Error("migration_raw_manifest_invalid");
        }
        const target = resolve(custom, item.artifact);
        if (dirname(target) !== resolve(custom) || !basename(target).startsWith(report.label + "_")) {
            throw Error("migration_raw_artifact_owner");
        }
        const bytes = await readFile(target);
        if (createHash("sha256").update(bytes).digest("hex") !== item.sha256) {
            throw Error("migration_raw_artifact_hash");
        }
    }
}

export async function observeMigration(runId, attempt = "") {

    if (!/^(MN01|MS01|MHN01|MHS01)$/.test(runId ?? "")) throw Error("migration26129_run_id_required");
    if (attempt && !/^r[1-9][0-9]*$/.test(attempt)) throw Error("migration_attempt_invalid");
    const expectedScenario = "lobecorp.photon-06-migration-laser3";
    const root = "E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon";
    const directory = root + "/build/modBench/raw-results/photon-migration26129-" + runId.toLowerCase() + "-20261031" + (attempt ? "-" + attempt : "") + "/client";
    const shared = createRequire("E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/package.json");
    registerHooks({
        resolve(specifier, context, nextResolve) {
            if (context.parentURL?.includes("/lib/minecraft-mcp/bridge/") &&
                (specifier.startsWith("@modelcontextprotocol/sdk/") || specifier === "zod")) {
                return nextResolve(pathToFileURL(shared.resolve(specifier)).href, context);
            }
            return nextResolve(specifier, context);
        }
    });
    const {ModClient} = await import(pathToFileURL(root + "/lib/minecraft-mcp/bridge/dist/index.js").href);
    const mod = new ModClient();
    try {
        let identity;
        const discoveryDeadline = Date.now() + 180000;
        while (Date.now() < discoveryDeadline) {
            identity = await mod.discover(19877, 19877);
            if (identity) break;
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
        if (!identity || identity.port !== 19877) throw Error("migration26129_wrong_instance");
        console.log(JSON.stringify({runId, identity}));
        const deadline = Date.now() + 300000;
        let previous = "";
        let complete = false;
        let finalSummary = false;
        while (Date.now() < deadline) {
            try {
                const summary = JSON.parse(await readFile(directory + "/summary.json", "utf8"));
                const selected = summary.scenarios.filter(item => item.status !== "SKIPPED");
                const state = summary.summary.status;
                if (state !== previous) {
                    console.log(JSON.stringify({
                        runId,
                        state,
                        scenarios: selected.map(x => ({id: x.id, status: x.status, failure: x.failure}))
                    }));
                    previous = state;
                }
                if (selected.length !== 1 || selected[0].id !== expectedScenario) throw Error("migration26129_wrong_scenario");
                if (state !== "PASSED") throw Error("migration26129_failed:" + JSON.stringify(selected));
                if (selected[0].status !== "PASSED") throw Error("migration26129_scenario_not_passed");
                finalSummary = true;
                const custom = directory + "/artifacts/custom";
                const names = (await readdir(custom)).filter(x => x.endsWith("_migration26129.json"));
                if (names.length !== 1) throw Error("migration26129_exactly_one_owned_report_required");
                const report = JSON.parse(await readFile(custom + "/" + names[0], "utf8"));
                if (typeof report.native_contract_artifact !== "string" ||
                    report.native_contract_artifact !== report.label + "_migration-contracts.json" ||
                    basename(report.native_contract_artifact) !== report.native_contract_artifact) {
                    throw Error("migration_contract_artifact_identity");
                }
                const contracts = JSON.parse(await readFile(custom + "/" + report.native_contract_artifact, "utf8"));
                validateMigrationResult(summary, report, contracts, identity, runId);
                await verifyMigrationArtifacts(custom, report);
                complete = true;
                console.log(JSON.stringify({
                    runId, state: "COMPLETE", pid: report.pid, label: report.label,
                    variant: report.variant, shader: report.shader_in_use, t0: report.entry_t0_ns,
                    addedNs: report.server_entity_added_ns, report: custom + "/" + names[0]
                }));
                break;
            } catch (error) {
                if (error.code !== "ENOENT" || finalSummary) throw error;
            }
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
        if (!complete) throw Error("migration26129_no_final_result");
    } finally {
        mod.disconnect();
    }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    await observeMigration(process.argv[2], process.argv[3] ?? "");
}
