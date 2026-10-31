import {registerHooks, createRequire} from "node:module";
import {pathToFileURL} from "node:url";
import {readFile, readdir} from "node:fs/promises";

const runId = process.argv[2];
if (!/^H(NQ|NL|NB|SQ|SL|SB)0[1-3]$/.test(runId ?? "")) throw Error("hybrid18_run_id_required");
const variant = runId[2] === "Q" ? "queen25" : runId[2] === "L" ? "laser3" : "blank25";
const expectedSeconds = variant === "queen25" ? 25 : 45;
const expectedScenario = "lobecorp.photon-05-hybrid-" + variant;
const root = "E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon";
const directory = root + "/build/modBench/raw-results/photon-hybrid18-" + runId.toLowerCase() + "-20261031/client";
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
    if (!identity || identity.port !== 19877) throw Error("hybrid18_wrong_instance");
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
            if (selected.length !== 1 || selected[0].id !== expectedScenario) throw Error("hybrid18_wrong_scenario");
            if (state !== "PASSED") throw Error("hybrid18_failed:" + JSON.stringify(selected));
            const custom = directory + "/artifacts/custom";
            const names = (await readdir(custom)).filter(x => x.endsWith("_hybrid18.json"));
            if (names.length !== 1) throw Error("hybrid18_exactly_one_owned_report_required");
            const report = JSON.parse(await readFile(custom + "/" + names[0], "utf8"));
            if (report.run_id !== runId || report.pid !== identity.pid || !report.archived || !report.finished ||
                report.failure !== null || !report.verified || report.frames.state !== "DURATION" || report.capture_seconds !== expectedSeconds || report.variant !== variant ||
                report.shader_in_use !== (runId[1] === "S")) {
                throw Error("hybrid18_report_identity_or_completion");
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
    if (!complete) throw Error("hybrid18_no_final_result");
} finally {
    mod.disconnect();
}
