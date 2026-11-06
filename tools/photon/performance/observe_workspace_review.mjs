import {registerHooks, createRequire} from "node:module";
import {pathToFileURL} from "node:url";
import {readFile, readdir} from "node:fs/promises";

const id = process.argv[2];
const attempt = process.argv[3] ?? "r1";
if (!/^[ps]01$/.test(id ?? "") || !/^r[1-9][0-9]*$/.test(attempt)) throw Error("review_identity");
const root = "E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon";
const directory = root + "/build/modBench/raw-results/photon-workspace-review-" + id + "-20261031-" + attempt + "/client";
const shared = createRequire("E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/package.json");
registerHooks({
    resolve(specifier, context, next) {
        if (context.parentURL?.includes("/lib/minecraft-mcp/bridge/") &&
            (specifier.startsWith("@modelcontextprotocol/sdk/") || specifier === "zod")) {
            return next(pathToFileURL(shared.resolve(specifier)).href, context);
        }
        return next(specifier, context);
    }
});
const {ModClient} = await import(pathToFileURL(root + "/lib/minecraft-mcp/bridge/dist/index.js").href);
const client = new ModClient();
const delay = () => new Promise(resolve => setTimeout(resolve, 1000));
try {
    const deadline = Date.now() + 300000;
    let identity;
    while (Date.now() < deadline && !identity) {
        identity = await client.discover(19877, 19877);
        if (!identity) await delay();
    }
    if (!identity || identity.port !== 19877) throw Error("review_instance_missing");
    console.log(JSON.stringify({id, identity}));
    let complete = false;
    while (Date.now() < deadline) {
        let summary;
        try {
            summary = JSON.parse(await readFile(directory + "/summary.json", "utf8"));
        } catch (error) {
            if (error.code !== "ENOENT" && !(error instanceof SyntaxError)) throw error;
            await delay();
            continue;
        }
        const selected = summary.scenarios.filter(item => item.status !== "SKIPPED");
        if (summary.summary.status !== "PASSED" || selected.length !== 1 ||
            selected[0].id !== "lobecorp.photon-07-workspace-review" || selected[0].status !== "PASSED") {
            throw Error("review_failed:" + JSON.stringify(selected));
        }
        const custom = directory + "/artifacts/custom";
        const names = (await readdir(custom)).filter(name => name.endsWith("_workspace_review.json"));
        if (names.length !== 1) throw Error("review_report_count");
        const report = JSON.parse(await readFile(custom + "/" + names[0], "utf8"));
        if (report.passed !== true || report.pid !== identity.pid || report.performance_acceptance !== false ||
            report.child_stopped_completion_rounds !== 3 || report.project_pool_bounded !== true ||
            report.native_pack_walk_file_snapshot !== true || report.unknown_fields_preserved !== true ||
            report.creation_mode !== "HYBRID" || report.shader !== (id === "s01") ||
            names[0] !== report.label + "_workspace_review.json") throw Error("review_report_invalid");
        const contracts = JSON.parse(await readFile(custom + "/" + report.label + "_migration-contracts.json", "utf8"));
        if (contracts.passed !== true || contracts.project_reset_verified !== true ||
            contracts.natural_drain_verified !== true || contracts.main_window_sample !== false) {
            throw Error("review_contracts_invalid");
        }
        complete = true;
        console.log(JSON.stringify({id, state: "COMPLETE", pid: report.pid, report: custom + "/" + names[0]}));
        break;
    }
    if (!complete) throw Error("review_timeout");
} finally {
    client.disconnect();
}
