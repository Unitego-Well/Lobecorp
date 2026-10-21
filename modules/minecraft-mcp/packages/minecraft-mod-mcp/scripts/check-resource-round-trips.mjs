import {Client} from "@modelcontextprotocol/sdk/client/index.js";
import {StdioClientTransport} from "@modelcontextprotocol/sdk/client/stdio.js";
import {readFile, writeFile, mkdir} from "node:fs/promises";
import {resolve, dirname, basename, join} from "node:path";
import {createHash} from "node:crypto";
import {isDeepStrictEqual} from "node:util";
import {fileURLToPath} from "node:url";

const manifestPath = resolve(process.argv[2]);
const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
const game = manifest.gameDirectory;
const runId = new Date().toISOString().replace(/[^0-9]/g, "") + "_" + process.pid;
const output = join(game, "ldlib2/assets/g1_outputs", runId);
const reportPath = join(output, "project_round_trips.json");
const client = new Client({name: "g1-full-resource-acceptance", version: "1.0.0"}, {capabilities: {}});
const bridge = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const transport = new StdioClientTransport({
    command: process.execPath,
    args: [join(bridge, "dist/cli.js"), "mcp", "--no-discover"],
    cwd: bridge,
    env: {MC_MCP_PORT: process.env.MC_MCP_PORT || "19877"},
    stderr: "pipe"
});
const ownedProjects = new Set();
const report = {
    version: 1, runId, pid: process.pid, startedAt: new Date().toISOString(),
    manifest: manifestPath, gameDirectory: game, nativeRoundTripOnly: true,
    independentRuntimeValidated: false, rows: []
};

async function api(name, arguments_ = {}) {
    const response = await client.callTool({name, arguments: arguments_});
    const text = response.content?.find(item => item.type === "text")?.text;
    const value = JSON.parse(text);
    if (response.isError || value.ok !== true) {
        throw new Error(JSON.stringify({tool: name, response: value}));
    }
    if (["effect_open", "effect_create"].includes(name) && value.data?.projectId) {
        ownedProjects.add(value.data.projectId);
    }
    return value.data;
}

function requireThat(value, label) {
    if (!value) {
        throw new Error(label);
    }
}

function ids(state) {
    return (state.objects || []).map(object => object.objectId).sort();
}

async function flush() {
    await mkdir(dirname(reportPath), {recursive: true});
    await writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
}

async function transact(row, create) {
    const result = {root: row.root, source: row.source, inputPath: row.inputPath, sha256: row.sha256, checks: []};
    report.rows.push(result);
    const previousOwned = new Set(ownedProjects);
    try {
        const input = await readFile(row.inputPath);
        requireThat(createHash("sha256").update(input).digest("hex") === row.sha256, "input_hash_before");
        let opened = create ? await create(input) : await api("effect_open", {path: row.inputPath});
        const projectId = opened.projectId;
        result.projectId = projectId;
        result.openReceipt = opened.sourceReceipt;
        result.checks.push("open");
        const initial = await api("effect_preview", {projectId, action: "pause"});
        const model = initial.kind === "model";
        const object = initial.objects?.find(item => typeof item.parameters?.config?.duration === "number");
        let operations;
        let expectedAsset;
        if (model) {
            expectedAsset = structuredClone(initial.modelAsset);
            requireThat(expectedAsset && Array.isArray(expectedAsset.vertices), "model_asset_present");
            expectedAsset.g1AcceptanceUnknown = "preserve";
            if (Array.isArray(expectedAsset.vertices[0])) {
                expectedAsset.vertices[0][0] += 0.001;
            }
            operations = [{op: "set_model_asset", asset: expectedAsset}];
        } else {
            requireThat(object, "editable_native_object");
            operations = [{
                op: "set_parameter",
                objectId: object.objectId,
                path: "/config/duration",
                value: object.parameters.config.duration + 1
            }];
        }
        const edited = await api("effect_edit_batch", {projectId, expectedRevision: initial.revision, operations});
        requireThat(edited.changed === true && edited.revision !== initial.revision, "legal_edit_changes_revision");
        const undo = await api("effect_history", {projectId, action: "undo"});
        requireThat(undo.revision === initial.revision, "undo_full_data");
        const redo = await api("effect_history", {projectId, action: "redo"});
        requireThat(redo.revision === edited.revision && isDeepStrictEqual(ids(initial), ids(redo)), "redo_identity");
        result.checks.push("legal_edit", "undo", "redo_identity");
        const identity = createHash("sha256").update(row.root + ":" + row.source).digest("hex").slice(0, 12);
        const stem = ("g1_" + runId + "_" + row.kind + "_" + identity + "_"
            + basename(row.source).replace(/\.(lcmodel\.json|fxproj|mfxproj|obj)$/, "")).toLowerCase().replace(/[^a-z0-9_]/g, "_");
        const projectPath = join(output, stem + (model ? ".mfxproj" : ".fxproj"));
        const saved = await api("effect_save", {projectId, path: projectPath});
        requireThat(saved.dirty === false && saved.revision === edited.revision, "save_complete_data");
        const reopened = await api("effect_open", {path: projectPath});
        requireThat(reopened.revision === edited.revision && isDeepStrictEqual(ids(reopened), ids(redo)), "reopen_full_revision_identity");
        if (model) {
            requireThat(isDeepStrictEqual(reopened.modelAsset, expectedAsset), "reopen_model_unknown_geometry");
        }
        result.checks.push("save", "reopen_full_revision_identity");
        const playing = await api("effect_preview", {projectId: reopened.projectId, action: "play"});
        requireThat(playing.playing === true, "preview_play");
        const pausedAgain = await api("effect_preview", {projectId: reopened.projectId, action: "pause"});
        requireThat(pausedAgain.playing === false, "preview_pause");
        const stopped = await api("effect_preview", {projectId: reopened.projectId, action: "stop"});
        requireThat(stopped.playing === false && stopped.time === 0, "preview_stop");
        result.checks.push("preview_play", "preview_pause", "preview_stop");
        result.savedPath = projectPath;
        const activeId = reopened.projectId;
        result.definition = await api("effect_export", {
            projectId: activeId, path: join(output, stem + (model ? ".mfx" : ".fx")), kind: "definition"
        });
        result.pack = await api("effect_export", {
            projectId: activeId,
            path: join(game, "photon/fxpacks", stem + (model ? ".mfxpack" : ".fxpack")),
            kind: "pack"
        });
        requireThat(result.pack.warnings?.length === 0, "pack_no_unresolved_dependencies");
        result.checks.push("export_definition", "export_pack");
        if (model) {
            result.json = await api("effect_export", {
                projectId: activeId,
                path: join(output, stem + ".json"),
                kind: "json"
            });
            result.obj = await api("effect_export", {
                projectId: activeId,
                path: join(output, stem + ".obj"),
                kind: "obj",
                flipV: false
            });
            result.checks.push("export_json", "export_obj");
        }
        requireThat(createHash("sha256").update(await readFile(row.inputPath)).digest("hex") === row.sha256, "original_unchanged");
        result.checks.push("original_unchanged");
        result.status = "passed_native_round_trip";
        result.runtime = "not_run";
    } catch (error) {
        result.status = "failed";
        result.error = String(error.stack || error);
    } finally {
        result.cleanup = [];
        for (const projectId of [...ownedProjects].filter(id => !previousOwned.has(id))) {
            try {
                const current = await api("effect_state", {projectId});
                const closed = await api("effect_close", {
                    projectId,
                    expectedRevision: current.revision,
                    discardUnsaved: true
                });
                requireThat(closed.closed === true, "project_close");
                ownedProjects.delete(projectId);
                result.cleanup.push({projectId, closed: true});
            } catch (error) {
                result.status = "failed";
                result.cleanup.push({projectId, closed: false, error: String(error)});
            }
        }
    }
    await flush();
    console.log(JSON.stringify({
        source: row.source,
        status: result.status,
        checks: result.checks,
        error: result.error
    }));
}

try {
    await client.connect(transport);
    const tools = await client.listTools();
    for (const name of ["effect_open", "effect_preview", "effect_edit_batch", "effect_history", "effect_save", "effect_export", "effect_close"]) {
        requireThat(tools.tools.some(tool => tool.name === name), "tool_missing:" + name);
    }
    const ping = await client.callTool({name: "ping", arguments: {}});
    requireThat(JSON.parse(ping.content.find(item => item.type === "text").text).result === "pong", "ping");
    const before = await api("effect_state");
    report.preexistingProjects = before.projects?.map(project => project.projectId) || [];
    for (const row of manifest.rows.filter(item => item.root === "source_assets" && ["FX_PROJECT", "MODEL_PROJECT"].includes(item.kind))) {
        await transact(row);
    }
    for (const row of manifest.rows.filter(item => item.root === "source_assets"
        && (item.source.endsWith("counterattack.lcmodel.json") || item.source.endsWith("counterattack.obj")))) {
        await transact(row, async input => {
            const created = await api("effect_create", {
                kind: "model",
                name: "g1_" + (row.kind === "OBJ" ? "obj_import" : "json_import")
            });
            const paused = await api("effect_preview", {projectId: created.projectId, action: "pause"});
            const operation = row.kind === "OBJ"
                ? {op: "import_model_obj", text: input.toString("utf8"), flipV: false}
                : {op: "set_model_asset", asset: JSON.parse(input.toString("utf8"))};
            return api("effect_edit_batch", {
                projectId: created.projectId,
                expectedRevision: paused.revision,
                operations: [operation]
            });
        });
    }
} finally {
    report.finishedAt = new Date().toISOString();
    try {
        await flush();
    } finally {
        await client.close();
    }
}
console.log(JSON.stringify({
    report: reportPath, passed: report.rows.filter(row => row.status === "passed_native_round_trip").length,
    failed: report.rows.filter(row => row.status === "failed").length
}));
process.exitCode = report.rows.some(row => row.status === "failed") ? 1 : 0;
