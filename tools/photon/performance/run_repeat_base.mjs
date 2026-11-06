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
    const discoveryDeadline = Date.now() + 90000;
    while (Date.now() < discoveryDeadline) {
        identity = await mod.discover(19877, 19877);
        if (identity) break;
        await new Promise(r => setTimeout(r, 1000));
    }
    if (!identity || identity.port !== 19877) throw Error("wrong_instance");
    const [kind, label, world] = process.argv.slice(2);
    if (!["laser", "native"].includes(kind) || !/^photon_repeat_[a-z0-9_]+$/.test(world) || world.length > 30) throw Error("invalid_arguments");

    async function call(name, args = {}) {
        const r = await mod.sendCommand(name, args);
        if (r.ok === false) throw Error(JSON.stringify(r));
        return r;
    }

    async function gui() {
        return (await call("gui_control", {action: "list"})).data;
    }

    async function button(s, label) {
        const w = s.widgets.find(w => w.label === label && w.active && w.visible);
        if (!w) throw Error("button_missing:" + label);
        return call("gui_control", {action: "click", mode: "direct", screenId: s.screenId, widgetId: w.widgetId});
    }

    async function waitFor(fn, ms = 90000) {
        const end = Date.now() + ms;
        while (Date.now() < end) {
            const r = await fn();
            if (r) return r;
            await new Promise(r => setTimeout(r, 1000));
        }
        throw Error("wait_timeout");
    }

    const initial = await waitFor(async () => {
        try {
            const r = await call("photon_repeat_status");
            return r.status ? r : null;
        } catch {
            return null;
        }
    });
    if (!initial.status.base_vm_arguments_clean || Object.keys(initial.status.test_environment).length) throw Error("dirty_vm");
    await waitFor(async () => {
        try {
            return (await gui()).screenClass.endsWith("TitleScreen");
        } catch {
            return false;
        }
    });
    await call("enter_control_mode");
    await call("photon_repeat_prepare");
    let s = await gui();
    await button(s, "Create Test World");
    s = await waitFor(async () => {
        const x = await gui();
        return x.screenClass.endsWith("CreateWorldScreen") ? x : null;
    });
    const name = s.widgets.find(w => w.label === "世界名称" && "value" in w);
    await call("gui_control", {
        action: "set_text",
        mode: "direct",
        screenId: s.screenId,
        widgetId: name.widgetId,
        text: world
    });
    const tab = s.widgets.find(w => w.label === "世界");
    await call("gui_control", {
        action: "click",
        mode: "simulate",
        screenId: s.screenId,
        x: tab.x + tab.width / 2,
        y: tab.y + tab.height / 2
    });
    s = await gui();
    const seed = s.widgets.find(w => w.label === "世界生成器的种子" && "value" in w);
    await call("gui_control", {
        action: "set_text",
        mode: "direct",
        screenId: s.screenId,
        widgetId: seed.widgetId,
        text: "7"
    });
    await button(s, "创建新的世界");
    await waitFor(async () => {
        const r = await call("photon_repeat_status");
        return r.status.world_loaded;
    });
    await call("photon_repeat_start", {kind, label});
    let last = "";
    let final;
    await waitFor(async () => {
        const r = await call("photon_repeat_status", {label});
        const t = r.status.trial;
        const state = t.state + ":" + t.triggers.length;
        if (state !== last) {
            console.log(JSON.stringify({pid: r.status.pid, label, state, error: t.error}));
            last = state;
        }
        if (t.terminal) {
            final = t;
            return true;
        }
        return false;
    }, 300000);
    console.log(JSON.stringify({
        label,
        state: final.state,
        report: final.owned_report,
        triggers: final.triggers,
        outputs: final.outputs,
        error: final.error
    }));
    if (final.state !== "COMPLETE") throw Error("trial_failed");
    await call("photon_repeat_close", {label});
} finally {
    mod.disconnect();
}
