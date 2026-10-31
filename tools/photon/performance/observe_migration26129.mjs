import {registerHooks, createRequire} from "node:module";
import {pathToFileURL} from "node:url";
import {readFile, readdir} from "node:fs/promises";

const runId = process.argv[2];
if (!/^(MN01|MS01|MHN01|MHS01)$/.test(runId ?? "")) throw Error("migration26129_run_id_required");
const attempt = process.argv[3] ?? "";
if (attempt && !/^r[1-9][0-9]*$/.test(attempt)) throw Error("migration_attempt_invalid");
const variant = "laser3";
const expectedSeconds = variant === "queen25" ? 25 : 45;
const expectedScenario = "lobecorp.photon-06-migration-laser3";
const expectedMode = runId.startsWith("MH") ? "HYBRID" : "NATIVE";
const expectedShader = runId === "MS01" || runId === "MHS01";
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
            const custom = directory + "/artifacts/custom";
            const names = (await readdir(custom)).filter(x => x.endsWith("_migration26129.json"));
            if (names.length !== 1) throw Error("migration26129_exactly_one_owned_report_required");
            const report = JSON.parse(await readFile(custom + "/" + names[0], "utf8"));
            if (report.run_id !== runId || report.pid !== identity.pid || !report.archived || !report.finished ||
                report.failure !== null || !report.verified || report.frames.state !== "DURATION" || report.capture_seconds !== expectedSeconds || report.variant !== variant ||
                report.shader_in_use !== expectedShader || report.creation_mode !== expectedMode) {
                throw Error("migration26129_report_identity_or_completion");
            }
            const contracts = JSON.parse(await readFile(custom + "/" + report.native_contract_artifact, "utf8"));
            if (!contracts.passed) throw Error("migration_contract_failure");
            const versions = report.actual_mod_versions;
            if (versions.photon !== "26.1.2.9" || versions.ldlib2 !== "26.1.2.43" || versions.kilagraph !== "26.1.0.16.a") throw Error("migration_versions");
            if (expectedMode === "HYBRID") {
                const d = report.factory_delta;
                if (d.prepared_source_cache_hits < 45 || d.prepared_source_compilations !== 0 ||
                    d.pool_hit <= 0 || d.pool_reset <= 0 || d.reset_failures !== 0) throw Error("migration_factory_usage");
            }
            complete = true;
            console.log(JSON.stringify({
                runId, state: "COMPLETE", pid: report.pid, label: report.label,
                variant: report.variant, shader: report.shader_in_use, t0: report.entry_t0_ns,
                addedNs: report.server_entity_added_ns, report: custom + "/" + names[0]
            }));
            break;
        } catch (error) {
            if (error.code !== "ENOENT" && !(error instanceof SyntaxError)) throw error;
        }
        await new Promise(resolve => setTimeout(resolve, 1000));
    }
    if (!complete) throw Error("migration26129_no_final_result");
} finally {
    mod.disconnect();
}
