import {registerHooks, createRequire} from "node:module";
import {pathToFileURL} from "node:url";

const shared = createRequire("E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/package.json");
registerHooks({
    resolve(specifier, context, nextResolve) {
        if (context.parentURL?.includes("/lib/minecraft-mcp/bridge/") && (specifier.startsWith("@modelcontextprotocol/sdk/") || specifier === "zod")) return nextResolve(pathToFileURL(shared.resolve(specifier)).href, context);
        return nextResolve(specifier, context);
    }
});
const {ModClient} = await import("file:///E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon/lib/minecraft-mcp/bridge/dist/index.js");
const mod = new ModClient();
try {
    let identity;
    const end = Date.now() + 90000;
    while (Date.now() < end) {
        identity = await mod.discover(19877, 19877);
        if (identity) break;
        await new Promise(r => setTimeout(r, 1000));
    }
    if (!identity || identity.port !== 19877) throw Error("wrong_instance");
    console.log(JSON.stringify({identity}));
    let label, last = "", terminal = false;
    const deadline = Date.now() + 360000;
    while (Date.now() < deadline) {
        try {
            const r = await mod.sendCommand("photon_repeat_status", label ? {label} : {});
            if (r.ok === false) throw Error(JSON.stringify(r));
            if (!label && r.status.active_label) {
                label = r.status.active_label;
                console.log(JSON.stringify({
                    label,
                    pid: r.status.pid,
                    vm: r.status.vm_arguments,
                    environment: r.status.environment
                }));
            }
            const t = r.status.trial;
            if (t) {
                const phase = t.state + ":" + t.triggers.length;
                if (phase !== last) {
                    console.log(JSON.stringify({
                        label,
                        pid: r.status.pid,
                        state: phase,
                        error: t.error,
                        report: t.owned_report
                    }));
                    last = phase;
                }
                if (t.terminal) {
                    terminal = true;
                    if (t.state !== "COMPLETE") throw Error("failed_trial:" + JSON.stringify(t));
                }
            }
        } catch (e) {
            if (terminal) {
                if (!last.startsWith("COMPLETE:")) throw e;
                console.log(JSON.stringify({label, process_disconnected_after_terminal: true}));
                break;
            }
            if (!(await mod.discover(19877, 19877))) throw e;
        }
        await new Promise(r => setTimeout(r, 1000));
    }
    if (!terminal) throw Error("full_not_complete");
} finally {
    mod.disconnect();
}
