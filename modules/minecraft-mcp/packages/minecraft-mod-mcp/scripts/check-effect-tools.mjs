import {Client} from "@modelcontextprotocol/sdk/client";
import {StdioClientTransport} from "@modelcontextprotocol/sdk/client/stdio.js";
import {fileURLToPath} from "node:url";
import {dirname, isAbsolute, relative, resolve, sep} from "node:path";
import process from "node:process";
import {isDeepStrictEqual} from "node:util";
import {randomUUID} from "node:crypto";
import {Buffer} from "node:buffer";

const effectToolNames = [
    "effect_capabilities", "effect_state", "effect_create", "effect_edit_batch",
    "effect_preview", "effect_save", "effect_export", "effect_history",
    "effect_open", "effect_resources", "effect_resource_read", "effect_resource_write",
    "effect_texture_import", "effect_view_state", "effect_view",
];
const nativeToolCount = 46;
const readOnlyEffectToolNames = new Set([
    "effect_capabilities", "effect_state", "effect_resources", "effect_resource_read", "effect_view_state",
]);
const runtimeMode = process.argv.slice(2).includes("--runtime");
const authoringMode = process.argv.slice(2).includes("--authoring");
const authoringExample = {
    name: "MCP模型接口示例",
    playbackSpeed: 0.75,
    seekTime: 5.5,
    projectFile: "model.mfxproj",
    definitionFile: "model.mfx",
    packFile: "model.mfxpack",
    fxName: "MCP粒子接口示例",
    modelEmitterType: "lobecorp_model_emitter",
    fxSeekTime: 5,
    fxProjectFile: "effect.fxproj",
    fxDefinitionFile: "effect.fx",
};
let authoringProgress = null;
const bridgeDirectory = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const client = new Client({name: "lobecorp-effect-tool-discovery", version: "1.0.0"}, {capabilities: {}});
const transport = new StdioClientTransport({
    command: process.execPath,
    args: [resolve(bridgeDirectory, "dist/cli.js")],
    cwd: bridgeDirectory,
    stderr: "pipe",
});

try {
    await client.connect(transport);
    const {tools} = await client.listTools();
    validateToolMetadata(tools);
    if (authoringMode) {
        console.log(JSON.stringify(await checkAuthoring()));
    } else if (runtimeMode) {
        console.log(JSON.stringify(await checkRuntime()));
    } else {
        console.log(JSON.stringify({ok: true, toolCount: tools.length, effectTools: effectToolNames}));
    }
} catch (error) {
    console.error(JSON.stringify({
        ok: false,
        ...(authoringProgress ?? {}),
        error: error instanceof Error ? error.message : String(error),
    }));
    process.exitCode = 1;
} finally {
    await client.close();
}

function validateToolMetadata(tools) {
    const names = new Set(tools.map(tool => tool.name));
    if (tools.length !== nativeToolCount + effectToolNames.length || names.size !== tools.length) {
        throw new Error(`The bridge must expose ${nativeToolCount} native tools and ${effectToolNames.length} distinct effect tools.`);
    }
    for (const name of effectToolNames) {
        const tool = tools.find(candidate => candidate.name === name);
        if (!tool || !tool.outputSchema || !tool.inputSchema || !tool.annotations) {
            throw new Error("Effect tool metadata is incomplete: " + name);
        }
        const readOnly = readOnlyEffectToolNames.has(name);
        if (tool.annotations.readOnlyHint !== readOnly) {
            throw new Error("Effect tool mutability metadata is invalid: " + name);
        }
    }
}

async function checkRuntime() {
    const capabilities = await callReadOnlyEffectTool("effect_capabilities");
    const state = await callReadOnlyEffectTool("effect_state");
    if (!Number.isInteger(capabilities.protocolVersion) || capabilities.protocolVersion <= 0
        || !Array.isArray(capabilities.emitterTypes) || !Array.isArray(state.projects)) {
        throw new Error("The effect runtime returned invalid capabilities or project state.");
    }

    return {
        ok: true,
        protocolVersion: capabilities.protocolVersion,
        emitterTypeCount: capabilities.emitterTypes.length,
        projectCount: state.projects.length,
    };
}

async function callReadOnlyEffectTool(name) {
    return callEffectTool(name, {});
}

async function callEffectTool(name, args) {
    const response = await callEffectResponse(name, args);
    if (!response.ok) {
        throw new Error("Effect runtime request failed: " + name + " (" + response.error.code + "): " + response.error.message);
    }

    return response.data;
}

async function expectEffectFailure(name, args, code) {
    const response = await callEffectResponse(name, args);
    if (response.ok || response.error.code !== code) {
        throw new Error("Expected effect failure " + code + ": " + name);
    }
}

async function callEffectResponse(name, args) {
    const result = await client.callTool({name, arguments: args});
    const response = result.structuredContent;
    validateEffectResponse(response, name);
    if (!Array.isArray(result.content) || result.content.length !== 1
        || result.content[0].type !== "text" || typeof result.content[0].text !== "string"
        || !isDeepStrictEqual(response, JSON.parse(result.content[0].text))) {
        throw new Error("Effect structuredContent and text JSON differ: " + name);
    }
    if (result.isError !== !response.ok) {
        throw new Error("Effect MCP error metadata is inconsistent: " + name);
    }
    return response;
}

function validateEffectResponse(response, name) {
    if (!isObject(response) || Object.keys(response).sort().join(",") !== "data,error,ok"
        || typeof response.ok !== "boolean") {
        throw new Error("Effect runtime envelope is invalid: " + name);
    }

    const validPayload = response.ok
        ? isObject(response.data) && response.error === null
        : response.data === null && isObject(response.error)
        && Object.keys(response.error).sort().join(",") === "code,message"
        && typeof response.error.code === "string" && typeof response.error.message === "string";
    if (!validPayload) {
        throw new Error("Effect runtime payload is invalid: " + name);
    }
}

function isObject(value) {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

async function checkAuthoring() {
    const capabilities = await callReadOnlyEffectTool("effect_capabilities");
    const previous = await callReadOnlyEffectTool("effect_state");
    requireCheck(Array.isArray(capabilities.allowedRoots) && capabilities.allowedRoots.length > 0
        && capabilities.allowedRoots.every(root => typeof root === "string" && isAbsolute(root))
        && Array.isArray(previous.projects), "authoring_capabilities");
    const directory = resolve(capabilities.allowedRoots[0], "mcp-authoring-" + randomUUID());
    const created = await callEffectTool("effect_create", {kind: "model", name: authoringExample.name});
    requireCheck(typeof created.projectId === "string" && created.projectId.length > 0
        && created.kind === "model" && created.name === authoringExample.name
        && !previous.projects.some(project => project.projectId === created.projectId), "create_model");
    const progress = {projectId: created.projectId, directory, checks: ["create_model"]};
    authoringProgress = progress;
    const paused = await callEffectTool("effect_preview", {projectId: progress.projectId, action: "pause"});
    recordCheck(progress.checks, paused.playing === false, "initial_pause");
    await checkModelEditing(progress.projectId, progress.checks);
    await checkModelPreview(progress.projectId, progress.checks);
    const packWarnings = await checkModelFiles(progress, capabilities.allowedRoots);
    await checkFxAuthoring(progress, capabilities);
    const current = await callReadOnlyEffectTool("effect_state");
    recordCheck(progress.checks, Array.isArray(current.projects)
        && previous.projects.every(project => current.projects.some(candidate => candidate.projectId === project.projectId))
        && current.projects.some(project => project.projectId === progress.projectId)
        && current.projects.some(project => project.projectId === progress.fxProjectId), "preserve_open_projects");
    return {ok: true, ...progress, packWarnings};
}

async function getModelState(projectId) {
    const state = await callEffectTool("effect_state", {projectId});
    requireCheck(state.projectId === projectId && state.kind === "model"
        && typeof state.revision === "string" && state.revision.length > 0
        && Number.isInteger(state.undoCount) && Number.isInteger(state.redoCount)
        && isObject(state.playback) && Number.isFinite(state.playback.playbackSpeed), "model_state");
    return state;
}

async function checkModelEditing(projectId, checks) {
    const operations = [{op: "set_model_playback", playback: {playbackSpeed: authoringExample.playbackSpeed}}];
    const original = await getModelState(projectId);
    requireCheck(original.playback.playbackSpeed !== authoringExample.playbackSpeed, "distinct_playback_edit");
    const dry = await callEffectTool("effect_edit_batch", {
        projectId, expectedRevision: original.revision, operations, dryRun: true,
    });
    const afterDry = await getModelState(projectId);
    recordCheck(checks, dry.dryRun === true && dry.changed === true
        && samePersistentState(original, dry) && samePersistentState(original, afterDry), "dry_run_unchanged");

    const beforeCommit = await getModelState(projectId);
    const committed = await callEffectTool("effect_edit_batch", {
        projectId, expectedRevision: beforeCommit.revision, operations,
    });
    const afterCommit = await getModelState(projectId);
    recordCheck(checks, committed.changed === true && committed.dryRun === false
        && samePersistentState(committed, afterCommit)
        && afterCommit.revision !== beforeCommit.revision
        && afterCommit.undoCount === beforeCommit.undoCount + 1 && afterCommit.redoCount === 0
        && afterCommit.playback.playbackSpeed === authoringExample.playbackSpeed, "single_history_edit");

    const beforeConflict = await getModelState(projectId);
    await expectEffectFailure("effect_edit_batch", {
        projectId, expectedRevision: beforeCommit.revision, operations,
    }, "REVISION_CONFLICT");
    recordCheck(checks, samePersistentState(beforeConflict, await getModelState(projectId)), "revision_conflict");

    const beforeUndo = await getModelState(projectId);
    const undone = await callEffectTool("effect_history", {projectId, action: "undo"});
    recordCheck(checks, samePersistentState(undone, await getModelState(projectId))
        && undone.revision === beforeCommit.revision
        && undone.playback.playbackSpeed === original.playback.playbackSpeed
        && undone.undoCount === beforeUndo.undoCount - 1
        && undone.redoCount === beforeUndo.redoCount + 1, "undo");

    const beforeRedo = await getModelState(projectId);
    const redone = await callEffectTool("effect_history", {projectId, action: "redo"});
    recordCheck(checks, samePersistentState(redone, await getModelState(projectId))
        && redone.revision === afterCommit.revision
        && redone.playback.playbackSpeed === authoringExample.playbackSpeed
        && redone.undoCount === beforeRedo.undoCount + 1
        && redone.redoCount === beforeRedo.redoCount - 1, "redo");
}

function samePersistentState(before, after) {
    return before.revision === after.revision && before.undoCount === after.undoCount
        && before.redoCount === after.redoCount
        && before.playback.playbackSpeed === after.playback.playbackSpeed;
}

async function checkModelPreview(projectId, checks) {
    for (const action of ["play", "pause", "stop", "seek"]) {
        const parameters = action === "seek" ? {time: authoringExample.seekTime} : {};
        const result = await callEffectTool("effect_preview", {projectId, action, ...parameters});
        recordCheck(checks, result.playing === (action === "play")
            && (action !== "stop" || result.time === 0)
            && (action !== "seek" || result.time === authoringExample.seekTime), "preview_" + action);
    }

    const state = await getModelState(projectId);
    recordCheck(checks, state.playing === false && state.time === authoringExample.seekTime, "fractional_model_time");
}

async function checkModelFiles(progress, allowedRoots) {
    const {projectId, directory, checks} = progress;
    const projectPath = resolve(directory, authoringExample.projectFile);
    const definitionPath = resolve(directory, authoringExample.definitionFile);
    const packPath = resolve(directory, authoringExample.packFile);
    await getModelState(projectId);
    const saved = await callEffectTool("effect_save", {projectId, path: projectPath});
    const savedState = await getModelState(projectId);
    recordCheck(checks, saved.dirty === false && savedState.dirty === false
        && saved.savedPath === projectPath && savedState.editorPath === projectPath, "save_clean_project");

    const definition = await callEffectTool("effect_export", {projectId, path: definitionPath, kind: "definition"});
    recordCheck(checks, definition.path === definitionPath && definition.kind === "definition"
        && Array.isArray(definition.warnings) && definition.warnings.length === 0, "export_definition");
    const pack = await callEffectTool("effect_export", {projectId, path: packPath, kind: "pack"});
    recordCheck(checks, pack.path === packPath && pack.kind === "pack"
        && Array.isArray(pack.warnings) && pack.warnings.every(warning => typeof warning === "string"), "export_pack");

    await expectEffectFailure("effect_save", {projectId, path: projectPath}, "FILE_EXISTS");
    checks.push("save_file_exists");
    await expectEffectFailure("effect_export", {projectId, path: definitionPath, kind: "definition"}, "FILE_EXISTS");
    checks.push("definition_file_exists");
    await expectEffectFailure("effect_export", {projectId, path: packPath, kind: "pack"}, "FILE_EXISTS");
    checks.push("pack_file_exists");
    await expectEffectFailure("effect_save", {projectId, path: forbiddenOutputPath(allowedRoots)}, "PATH_FORBIDDEN");
    checks.push("path_forbidden");
    return pack.warnings;
}


async function checkFxAuthoring(progress, capabilities) {
    recordCheck(progress.checks, Array.isArray(capabilities.emitterTypes)
        && capabilities.emitterTypes.some(type => type.type === authoringExample.modelEmitterType), "fx_registered_model_type");
    const previous = await callReadOnlyEffectTool("effect_state");
    const created = await callEffectTool("effect_create", {kind: "fx", name: authoringExample.fxName});
    requireCheck(typeof created.projectId === "string" && created.projectId.length > 0
        && created.kind === "fx" && Array.isArray(previous.projects)
        && !previous.projects.some(project => project.projectId === created.projectId), "create_fx");
    progress.fxProjectId = created.projectId;
    progress.checks.push("create_fx");
    const paused = await callEffectTool("effect_preview", {projectId: progress.fxProjectId, action: "pause"});
    recordCheck(progress.checks, paused.playing === false, "fx_initial_pause");
    const objectId = await addFxModelEmitter(progress);
    const sourceModel = await getModelState(progress.projectId);
    await setFxModelAsset(progress, objectId, sourceModel.asset);
    await checkFxPreview(progress);
    await checkFxFiles(progress);
}

async function getFxState(projectId) {
    const state = await callEffectTool("effect_state", {projectId});
    requireCheck(state.projectId === projectId && state.kind === "fx"
        && typeof state.revision === "string" && state.revision.length > 0
        && Number.isInteger(state.undoCount) && Number.isInteger(state.redoCount)
        && Array.isArray(state.objects) && typeof state.playing === "boolean", "fx_state");
    return state;
}

async function addFxModelEmitter(progress) {
    const projectId = progress.fxProjectId;
    const before = await getFxState(projectId);
    const previousIds = new Set(before.objects.map(object => object.objectId));
    const edited = await callEffectTool("effect_edit_batch", {
        projectId,
        expectedRevision: before.revision,
        operations: [{op: "add_emitter", type: authoringExample.modelEmitterType}],
    });
    const after = await getFxState(projectId);
    const added = after.objects.filter(object => !previousIds.has(object.objectId));
    recordCheck(progress.checks, edited.changed === true && added.length === 1
        && after.objects.length === before.objects.length + 1
        && before.objects.every(object => after.objects.some(candidate => candidate.objectId === object.objectId))
        && new Set(after.objects.map(object => object.objectId)).size === after.objects.length
        && added[0].type === authoringExample.modelEmitterType
        && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(added[0].objectId)
        && after.undoCount === before.undoCount + 1 && after.redoCount === 0
        && edited.revision === after.revision && after.revision !== before.revision
        && edited.playing === false && after.playing === false, "fx_add_model_emitter");
    return added[0].objectId;
}

async function setFxModelAsset(progress, objectId, asset) {
    const projectId = progress.fxProjectId;
    requireCheck(isObject(asset), "fx_model_asset_source");
    const before = await getFxState(projectId);
    const edited = await callEffectTool("effect_edit_batch", {
        projectId,
        expectedRevision: before.revision,
        operations: [{op: "set_model_asset", objectId, asset}],
    });
    const after = await getFxState(projectId);
    const target = after.objects.find(object => object.objectId === objectId);
    const historyDelta = edited.changed === true ? 1 : 0;
    recordCheck(progress.checks, typeof edited.changed === "boolean" && target
        && target.type === authoringExample.modelEmitterType && target.parameters?.config
        && Array.isArray(target.parameters.config["assetJson"])
        && isDeepStrictEqual(asset, JSON.parse(Buffer.from(target.parameters.config["assetJson"]).toString("utf8")))
        && after.objects.length === before.objects.length
        && after.undoCount === before.undoCount + historyDelta
        && after.redoCount === (edited.changed ? 0 : before.redoCount)
        && edited.revision === after.revision
        && (edited.changed || after.revision === before.revision)
        && edited.playing === false && after.playing === false, "fx_set_model_asset");
}

async function checkFxPreview(progress) {
    const projectId = progress.fxProjectId;
    const sought = await callEffectTool("effect_preview", {
        projectId,
        action: "seek",
        time: authoringExample.fxSeekTime
    });
    recordCheck(progress.checks, sought.playing === false && sought.time === authoringExample.fxSeekTime, "fx_preview_seek");
    const stopped = await callEffectTool("effect_preview", {projectId, action: "stop"});
    recordCheck(progress.checks, stopped.playing === false && stopped.time === 0, "fx_preview_stop");
}

async function checkFxFiles(progress) {
    const projectId = progress.fxProjectId;
    const projectPath = resolve(progress.directory, authoringExample.fxProjectFile);
    const definitionPath = resolve(progress.directory, authoringExample.fxDefinitionFile);
    await getFxState(projectId);
    const saved = await callEffectTool("effect_save", {projectId, path: projectPath});
    const state = await getFxState(projectId);
    recordCheck(progress.checks, saved.dirty === false && state.dirty === false
        && saved.savedPath === projectPath && state.editorPath === projectPath, "fx_save_clean_project");
    const definition = await callEffectTool("effect_export", {projectId, path: definitionPath, kind: "definition"});
    recordCheck(progress.checks, definition.path === definitionPath && definition.kind === "definition"
        && Array.isArray(definition.warnings) && definition.warnings.length === 0, "fx_export_definition");
}

function forbiddenOutputPath(allowedRoots) {
    let parent = dirname(resolve(allowedRoots[0]));
    while (allowedRoots.some(root => isWithin(root, parent))) {
        const ancestor = dirname(parent);
        requireCheck(ancestor !== parent, "forbidden_path_boundary");
        parent = ancestor;
    }

    const path = resolve(parent, "mcp-forbidden-" + randomUUID(), authoringExample.projectFile);
    requireCheck(!allowedRoots.some(root => isWithin(root, path)), "forbidden_path_boundary");
    return path;
}

function isWithin(root, path) {
    const pathFromRoot = relative(resolve(root), resolve(path));
    return pathFromRoot === "" || pathFromRoot !== ".."
        && !pathFromRoot.startsWith(".." + sep) && !isAbsolute(pathFromRoot);
}

function recordCheck(checks, condition, name) {
    requireCheck(condition, name);
    checks.push(name);
}

function requireCheck(condition, name) {
    if (!condition) {
        throw new Error("Effect authoring check failed: " + name);
    }
}
