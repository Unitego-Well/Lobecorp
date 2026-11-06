import {registerHooks, createRequire} from "node:module";
import {pathToFileURL} from "node:url";
import {readFile, readdir} from "node:fs/promises";
import {spawn} from "node:child_process";

const root = "E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon";
const runId = process.argv[2];
if (!/^H(NQ|NL|NB|SQ|SL|SB)0[1-3]$/.test(runId ?? "")) throw Error("invalid_run_id");
const base = root + "/build/agent-backups/20261101-photon-event-perf-r2/" + runId;
const directory = base + "/results";
const expectedVariant = runId[2] === "Q" ? "queen25" : runId[2] === "L" ? "laser3" : "blank25";
const shared = createRequire("E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/package.json");
registerHooks({resolve(specifier, context, nextResolve) {
    if (context.parentURL?.includes("/lib/minecraft-mcp/bridge/") &&
        (specifier.startsWith("@modelcontextprotocol/sdk/") || specifier === "zod")) {
        return nextResolve(pathToFileURL(shared.resolve(specifier)).href, context);
    }
    return nextResolve(specifier, context);
}});
const {ModClient} = await import(pathToFileURL(root + "/lib/minecraft-mcp/bridge/dist/index.js").href);
const mod = new ModClient();
let focus;
let screenshotDone = false;
let entryObserved = 0;
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const emit = value => console.log(JSON.stringify({runId, utc: new Date().toISOString(), ...value}));
async function screenshot() {
    screenshotDone = true;
    const child = spawn(process.execPath, [root + "/lib/minecraft-mcp/bridge/dist/cli.js", "mcp", "--no-discover"],
        {env: {...process.env, MC_MCP_PORT: "19877"}, windowsHide: true, stdio: ["pipe", "pipe", "pipe"]});
    let buffer = "", sequence = 0;
    const pending = new Map();
    child.stdout.on("data", data => {
        buffer += data;
        const lines = buffer.split("\n");
        buffer = lines.pop();
        for (const line of lines) {
            try {
                const message = JSON.parse(line);
                const value = pending.get(message.id);
                if (value) { clearTimeout(value.timer); pending.delete(message.id); value.resolve(message); }
            } catch {}
        }
    });
    const rpc = (method, params) => new Promise((resolve, reject) => {
        const id = ++sequence;
        const timer = setTimeout(() => { pending.delete(id); reject(Error("screenshot_rpc_timeout")); }, 12000);
        pending.set(id, {resolve, timer});
        child.stdin.write(JSON.stringify({jsonrpc: "2.0", id, method, params}) + "\n");
    });
    try {
        await rpc("initialize", {protocolVersion: "2024-11-05", capabilities: {}, clientInfo: {name: "photon-perf", version: "1"}});
        child.stdin.write(JSON.stringify({jsonrpc: "2.0", method: "notifications/initialized"}) + "\n");
        await rpc("tools/call", {name: "ping", arguments: {}});
        const result = await rpc("tools/call", {name: "screenshot_to_file", arguments: {path: base + "/scene.png"}});
        emit({event: "screenshot", childPid: child.pid, result: result.result});
    } finally {
        for (const value of pending.values()) clearTimeout(value.timer);
        pending.clear();
        child.stdin.end();
        await Promise.race([new Promise(resolve => child.once("exit", resolve)), pause(1000)]);
        if (child.exitCode === null) child.kill();
    }
}
emit({event: "observer_started", pid: process.pid});
try {
    let identity;
    const discoveryDeadline = Date.now() + 180000;
    while (Date.now() < discoveryDeadline) {
        identity = await mod.discover(19877, 19877);
        if (identity) break;
        await pause(1000);
    }
    if (!identity || identity.port !== 19877 || !Number.isInteger(identity.pid)) throw Error("wrong_instance");
    emit({event: "connected", identity});
    focus = spawn("powershell.exe", ["-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-File",
        root + "/tools/photon/performance/focus_event_perf_20261101.ps1", "-GamePid", String(identity.pid),
        "-ObserverPid", String(process.pid), "-LogPath", base + "/focus.jsonl"],
        {windowsHide: true, stdio: ["ignore", "pipe", "pipe"]});
    emit({event: "window_observer_started", mode: "observe_only", pid: focus.pid, gamePid: identity.pid});
    focus.stdout.on("data", data => emit({event: "focus_stdout", text: String(data).slice(0, 1000)}));
    focus.stderr.on("data", data => emit({event: "focus_error", text: String(data).slice(0, 1000)}));
    const deadline = Date.now() + 360000;
    let complete = false;
    while (Date.now() < deadline) {
        try {
            const log = await readFile(base + "/isolated/build/modBench/runs/default/client/logs/latest.log", "utf8");
            if (!entryObserved && log.includes("HYBRID_MATRIX runId=" + runId + " phase=world_entry")) {
                entryObserved = Date.now();
                emit({event: "world_entry_observed"});
            }
            if (!screenshotDone && entryObserved && Date.now() - entryObserved >= 8000) {
                await screenshot();
            }
        } catch (error) {
            if (error.code !== "ENOENT" && !(error instanceof SyntaxError)) throw error;
        }
        try {
            const summary = JSON.parse(await readFile(directory + "/summary.json", "utf8"));
            const selected = summary.scenarios.filter(item => item.status !== "SKIPPED");
            if (summary.summary.status !== "PASSED" || selected.length !== 1 ||
                selected[0].id !== "lobecorp.photon-05-hybrid-" + expectedVariant || selected[0].status !== "PASSED") {
                throw Error("scenario_failed_or_mismatch:" + JSON.stringify(selected));
            }
            const custom = directory + "/artifacts/custom";
            const names = (await readdir(custom)).filter(name => name.endsWith("_hybrid18.json"));
            if (names.length !== 1) throw Error("owned_report_count");
            const report = JSON.parse(await readFile(custom + "/" + names[0], "utf8"));
            if (report.run_id !== runId || report.pid !== identity.pid || !report.archived || !report.finished ||
                report.failure !== null || !report.verified || report.frames.state !== "DURATION" ||
                report.capture_seconds !== (expectedVariant === "queen25" ? 25 : 45) ||
                report.variant !== expectedVariant || report.shader_in_use !== (runId[1] === "S")) {
                throw Error("report_identity_or_completion");
            }
            if (!screenshotDone) await screenshot();
            emit({event: "COMPLETE", pid: report.pid, report: custom + "/" + names[0], variant: expectedVariant,
                shader: report.shader_in_use, spark: report.spark});
            complete = true;
            break;
        } catch (error) {
            if (error.code !== "ENOENT" && !(error instanceof SyntaxError)) throw error;
        }
        await pause(1000);
    }
    if (!complete) throw Error("result_deadline");
} finally {
    mod.disconnect();
    if (focus) {
        await Promise.race([new Promise(resolve => focus.once("exit", resolve)), pause(1500)]);
        if (focus.exitCode === null) focus.kill();
        emit({event: "window_observer_released", pid: focus.pid});
    }
    emit({event: "observer_released", pid: process.pid});
}
