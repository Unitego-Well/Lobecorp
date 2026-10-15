import {Client} from "@modelcontextprotocol/sdk/client";
import {StdioClientTransport} from "@modelcontextprotocol/sdk/client/stdio.js";
import {mkdir, lstat, readFile, writeFile} from "node:fs/promises";
import {dirname, isAbsolute, relative, resolve, sep} from "node:path";
import process from "node:process";
import {fileURLToPath} from "node:url";
import {isDeepStrictEqual} from "node:util";
import {randomUUID} from "node:crypto";
import {buildStarTrailOperations} from "./effects/star-trail.mjs";
import {buildLaserDefinitionSnbt, buildLaserOperations, LASER_CONFIG} from "./effects/variable-laser.mjs";

const referenceEffects = {
    star: {
        name: "星星拖尾参考",
        fileName: "reference_star_trail",
        emitters: [
            {type: "trail_emitter", name: "star_trail_ribbon"},
            {type: "particle_emitter", name: "star_trail_pink"},
            {type: "particle_emitter", name: "star_trail_yellow"},
            {type: "particle_emitter", name: "star_trail_blue"},
        ],
    },
    laser: {
        name: "变量激光参考",
        fileName: "reference_variable_laser",
        emitters: [
            {type: "beam_emitter", name: "laser_core"},
            {type: "beam_emitter", name: "laser_body"},
            {type: "beam_emitter", name: "laser_halo"},
            {type: "lobecorp_animation_controller", name: "laser_controls"},
        ],
    },
};
const messages = {
    invalidPhase: "阶段必须为 inspect、prepare、apply 或 save。",
    invalidRecipe: "特效配方超过批次上限或返回无效操作。",
    invalidReference: "数值模板准备仅允许本轮两份独立参考 FX 工程。",
    inexactFloat: "整数 Constant 无法无损表示为原生 float，停止准备：",
    templateNotRetained: "原生工程未保留 float Constant 编码，停止应用配方。",
    invalidBatch: "批量编辑没有保持预期版本、暂停状态或历史：",
    invalidDefinition: "变量激光控制器定义缺少预期动画、变量或绑定。",
    invalidSnbt: "原生工程 SNBT 结构不符合预期，拒绝替换定义。",
    invalidRoots: "当前能力没有可用的绝对保存目录。",
    invalidSave: "原生保存或导出没有返回预期路径／状态：",
    invalidApplication: "请先执行完整 apply，生成真实应用诊断。",
    changedApplication: "工程在 apply 后发生持久修改；重新读取并应用配方后再保存。",
    invalidSnapshot: "诊断快照缺少两份参考工程的身份。",
    missingStarEmitter: "当前星星拖尾工程缺少唯一星点发射器：",
    invalidPreparation: "星点模块未正确启用：",
    forbiddenSnapshot: "诊断快照必须位于仓库 build/mcp 目录。",
    fileExists: "诊断快照已存在，拒绝覆盖。",
    invalidCapabilities: "特效能力响应缺少发射器注册信息。",
    missingEmitter: "特效注册表缺少需要的发射器类型：",
    invalidState: "FX 工程状态无效：",
    invalidEnvelope: "特效响应信封无效：",
    invalidPayload: "特效响应数据无效：",
    inconsistentContent: "特效 structuredContent 与 text JSON 不一致：",
    inconsistentError: "特效 MCP 错误元数据不一致：",
    invalidCreation: "新建参考工程返回了无效状态：",
    invalidPause: "参考工程未保持暂停：",
    invalidEdit: "参考发射器批量创建结果不符合请求：",
};
const bridgeDirectory = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = resolve(bridgeDirectory, "../../../..");
const diagnosticDirectory = resolve(repositoryRoot, "build/mcp");
const snapshotPath = resolve(process.argv[3] ?? resolve(diagnosticDirectory, "reference-effect-inspection.json"));
const progress = {starProjectId: null, laserProjectId: null};
const outputConfiguration = {
    directory: "reference-effects",
    reportFile: "reference-effect-delivery.json",
    failureFilePrefix: "reference-effect-failure-",
    projectSuffix: ".fxproj",
    definitionSuffix: ".fx",
    packSuffix: ".fxpack",
};
let stage = "initial";
const completedFiles = [];
let failureDiagnostic = null;
const client = new Client({name: "lobecorp-reference-effect-authoring", version: "1.0.0"}, {capabilities: {}});
const transport = new StdioClientTransport({
    command: process.execPath,
    args: [resolve(bridgeDirectory, "dist/cli.js")],
    cwd: bridgeDirectory,
    stderr: "pipe",
});

let summary;
try {
    summary = await runPhase();
} catch (error) {
    summary = failureSummary(error);
    process.exitCode = 1;
} finally {
    try {
        await client.close();
    } catch (error) {
        if (summary?.ok) {
            summary = failureSummary(error);
        } else {
            summary.closeError = errorDetails(error);
        }

        process.exitCode = 1;
    }
}

console.log(JSON.stringify(summary));

async function runPhase() {
    requireCondition(isWithin(diagnosticDirectory, snapshotPath) && snapshotPath !== diagnosticDirectory,
        "PATH_FORBIDDEN", messages.forbiddenSnapshot);

    if (process.argv[2] === "inspect") {
        return inspectReferences();
    }

    requireCondition(["prepare", "apply", "save"].includes(process.argv[2]), "INVALID_PHASE", messages.invalidPhase);
    const snapshot = await readReferenceSnapshot();
    await client.connect(transport);
    snapshot.capabilities = await callEffectTool("effect_capabilities", {});
    validateEmitterCapabilities(snapshot.capabilities);

    if (process.argv[2] === "prepare") {
        await prepareStarModules(snapshot);
        return {ok: true, phase: "prepare", ...progress, snapshotPath};
    }

    return process.argv[2] === "apply" ? applyReferences(snapshot) : saveReferences(snapshot);
}

async function readReferenceSnapshot() {
    stage = "read_snapshot";
    const snapshot = JSON.parse(String(await readFile(snapshotPath, {encoding: "utf8"})));
    requireCondition(typeof snapshot?.starState?.projectId === "string"
        && typeof snapshot?.laserState?.projectId === "string"
        && snapshot.starState.projectId !== snapshot.laserState.projectId,
        "INVALID_SNAPSHOT", messages.invalidSnapshot);
    progress.starProjectId = snapshot.starState.projectId;
    progress.laserProjectId = snapshot.laserState.projectId;
    return snapshot;
}

async function prepareStarModules(snapshot) {
    stage = "prepare_star_modules";
    await callEffectTool("effect_preview", {projectId: progress.starProjectId, action: "pause"});
    const before = await fxState(progress.starProjectId);
    const modules = ["/config/uvAnimation/_enable", "/config/colorOverLifetime/_enable"];
    const operations = referenceEffects.star.emitters.filter(emitter => emitter.type === "particle_emitter")
        .flatMap(emitter => {
            const targets = before.objects.filter(object => object.name === emitter.name && object.type === emitter.type);
            requireCondition(targets.length === 1, "STAR_EMITTER_MISSING", messages.missingStarEmitter + emitter.name);
            return modules.map(path => ({op: "set_parameter", objectId: targets[0].objectId, path, value: 1}));
        });
    const edited = await callEffectTool("effect_edit_batch", {
        projectId: progress.starProjectId,
        expectedRevision: before.revision,
        operations,
    });
    const after = await fxState(progress.starProjectId);
    requireCondition(edited.revision === after.revision && edited.playing === false
        && operations.every(operation => {
            const object = after.objects.find(candidate => candidate.objectId === operation.objectId);
            return object?.parameters?.config?.["uvAnimation"]?.["_enable"] === 1
                && object?.parameters?.config?.["colorOverLifetime"]?.["_enable"] === 1;
        }), "INVALID_PREPARATION", messages.invalidPreparation + progress.starProjectId);
    snapshot.starState = after;
    await updateSnapshot(snapshot);
    return after;
}

async function updateSnapshot(snapshot) {
    await writeFile(snapshotPath, JSON.stringify(snapshot, null, 2) + "\n", {encoding: "utf8", flag: "w"});
}

async function pauseFxState(projectId) {
    await callEffectTool("effect_preview", {projectId, action: "pause"});
    return fxState(projectId);
}

async function applyReferences(snapshot) {
    await prepareStarModules(snapshot);
    await prepareConstantTemplates(snapshot, "star");
    await prepareConstantTemplates(snapshot, "laser");
    stage = "apply_star_recipe";
    const starBefore = await pauseFxState(progress.starProjectId);
    const starOperations = buildStarTrailOperations(snapshot.capabilities, starBefore);
    snapshot.starState = await editRecipe(snapshot.capabilities, starBefore, starOperations);
    await updateSnapshot(snapshot);

    stage = "apply_laser_recipe";
    const laserBefore = await pauseFxState(progress.laserProjectId);
    const laserOperations = buildLaserOperations(snapshot.capabilities, laserBefore);
    const laserConfigured = await editRecipe(snapshot.capabilities, laserBefore, laserOperations);
    snapshot.laserState = laserConfigured;
    await updateSnapshot(snapshot);

    stage = "apply_laser_definition";
    const definition = buildLaserDefinitionSnbt(laserConfigured);
    const snbt = replaceControllerDefinition(laserConfigured, definition);
    snapshot.laserState = await editRecipe(snapshot.capabilities, laserConfigured, [{op: "set_project_data", snbt}]);
    const laserVariables = laserDefinitionMetadata(snapshot.laserState);
    snapshot.application = {
        starRevision: snapshot.starState["revision"],
        laserRevision: snapshot.laserState["revision"],
        starOperationCount: starOperations.length,
        laserOperationCount: laserOperations.length,
        laserVariables,
    };
    await updateSnapshot(snapshot);
    return {
        ok: true, phase: "apply", ...progress, snapshotPath,
        starOperationCount: starOperations.length, laserOperationCount: laserOperations.length,
        variables: laserVariables.variables.map(variable => variable.name)
    };
}

async function prepareConstantTemplates(snapshot, key) {
    stage = "prepare_" + key + "_constant_templates";
    const before = await pauseFxState(progress[key + "ProjectId"]);
    const reference = referenceEffects[key];
    requireCondition(before.projectId === snapshot[key + "State"].projectId && before.name === reference.name
        && before.objects.length === reference.emitters.length
        && reference.emitters.every(emitter => before.objects.filter(object =>
            object.name === emitter.name && object.type === emitter.type).length === 1),
        "INVALID_REFERENCE_PROJECT", messages.invalidReference);
    const template = floatConstantTemplates(before.snapshotSnbt);
    const after = template.count === 0 ? before
        : await editRecipe(snapshot.capabilities, before, [{op: "set_project_data", snbt: template.snbt}]);
    requireCondition(floatConstantTemplates(after["snapshotSnbt"]).count === 0,
        "FLOAT_TEMPLATE_NOT_RETAINED", messages.templateNotRetained);
    snapshot[key + "State"] = after;
    snapshot.templatePreparation ??= {};
    snapshot.templatePreparation[key] = {convertedConstants: template.count, revision: after.revision};
    await updateSnapshot(snapshot);
}

// 只接受恰有 type/data 且 data 仅含 number 的原生 Constant；其他 NBT 字段与编码保持原样。
function floatConstantTemplates(source) {
    const root = parseSnbt(source);
    const replacements = [];
    const visit = node => {
        if (node.fields instanceof Map) {
            const data = node.fields.get("data");
            const number = data?.fields?.get("number");
            if (node.fields.size === 2 && node.fields.get("type")?.value === "constant"
                && data?.fields?.size === 1 && number && /^[+-]?\d+$/.test(source.slice(number.start, number.end))) {
                const value = Number(source.slice(number.start, number.end));
                requireCondition(Number.isSafeInteger(value) && Math.fround(value) === value,
                    "INEXACT_FLOAT_TEMPLATE", messages.inexactFloat + source.slice(number.start, number.end));
                replacements.push({start: number.start, end: number.end});
            }

            for (const child of node.fields.values()) {
                visit(child);
            }
        }

        for (const child of node.items ?? []) {
            visit(child);
        }
    };
    visit(root);
    let snbt = source;
    for (const replacement of replacements.toSorted((left, right) => right.start - left.start)) {
        snbt = snbt.slice(0, replacement.end) + "f" + snbt.slice(replacement.end);
    }

    return {snbt, count: replacements.length};
}

async function editRecipe(capabilities, before, operations) {
    requireCondition(Array.isArray(operations) && operations.length <= capabilities.maxOperations,
        "INVALID_RECIPE", messages.invalidRecipe);

    if (operations.length === 0) {
        return before;
    }

    const argumentsForEdit = {projectId: before.projectId, expectedRevision: before.revision, operations};
    let dry;
    try {
        dry = await callEffectTool("effect_edit_batch", {...argumentsForEdit, dryRun: true});
    } catch (error) {
        await diagnoseDryRunFailure(argumentsForEdit, error);
        throw error;
    }
    const afterDry = await fxState(before.projectId);
    requireCondition(dry.dryRun === true && dry.revision === before.revision
        && typeof dry.proposedSnapshotSnbt === "string" && afterDry.revision === before.revision
        && afterDry.undoCount === before.undoCount && afterDry.redoCount === before.redoCount,
        "INVALID_DRY_RUN", messages.invalidBatch + stage);
    const edited = await callEffectTool("effect_edit_batch", argumentsForEdit);
    const after = await fxState(before.projectId);
    requireCondition(typeof edited.changed === "boolean" && edited.dryRun === false
        && edited.playing === false && edited.revision === after.revision
        && after.undoCount === before.undoCount + Number(edited.changed)
        && after.redoCount === (edited.changed ? 0 : before.redoCount)
        && (edited.changed ? after.revision !== before.revision : after.revision === before.revision),
        "INVALID_EDIT_RESPONSE", messages.invalidBatch + stage);
    return after;
}

async function diagnoseDryRunFailure(argumentsForEdit, originalError) {
    const diagnostic = {
        phase: process.argv[2], stage,
        projectId: argumentsForEdit.projectId, expectedRevision: argumentsForEdit.expectedRevision,
        originalError: errorDetails(originalError), dryRunCount: 0,
    };

    try {
        if (["INVALID_ARGUMENT", "INVALID_DATA", "UNSUPPORTED_OPERATION"].includes(originalError?.code)) {
            await findFailingOperation(argumentsForEdit, diagnostic);
        }

        const path = resolve(diagnosticDirectory, outputConfiguration.failureFilePrefix + randomUUID() + ".json");
        await mkdir(diagnosticDirectory, {recursive: true});
        await writeFile(path, JSON.stringify(diagnostic, null, 2) + "\n", {encoding: "utf8", flag: "wx"});
        failureDiagnostic = {path, operationIndex: diagnostic.operationIndex ?? null};
    } catch (error) {
        failureDiagnostic = {
            error: errorDetails(error), originalError: diagnostic.originalError,
            operationIndex: diagnostic.operationIndex ?? null
        };
    }
}

// 失败时仅对同一版本的独立副本做前缀二分与单项复核，不提交，也不重复读取状态。
async function findFailingOperation(argumentsForEdit, diagnostic) {
    const operations = argumentsForEdit.operations;
    let first = 0;
    let last = operations.length - 1;
    let prefixError = diagnostic.originalError;

    while (first < last) {
        const middle = Math.floor((first + last) / 2);
        const result = await probeDryOperations(argumentsForEdit, operations.slice(0, middle + 1));
        diagnostic.dryRunCount++;

        if (result.ok) {
            first = middle + 1;
        } else {
            requireCondition(result.error.code !== "REVISION_CONFLICT", "REVISION_CONFLICT", result.error.message);
            last = middle;
            prefixError = result.error;
        }
    }

    diagnostic.operationIndex = first;
    diagnostic.prefixLength = first + 1;
    diagnostic.operation = operations[first];
    diagnostic.prefixError = prefixError;
    const standalone = await probeDryOperations(argumentsForEdit, [operations[first]]);
    diagnostic.dryRunCount++;
    diagnostic.standaloneFailure = !standalone.ok;
    diagnostic.operationError = standalone.ok ? null : standalone.error;
}

async function probeDryOperations(argumentsForEdit, operations) {
    try {
        await callEffectTool("effect_edit_batch", {...argumentsForEdit, operations, dryRun: true});
        return {ok: true};
    } catch (error) {
        return {ok: false, error: errorDetails(error)};
    }
}

function laserController(state) {
    const controllers = state.objects.filter(object =>
        object.name === LASER_CONFIG.controller.name && object.type === LASER_CONFIG.controller.type);
    requireCondition(controllers.length === 1, "INVALID_LASER_DEFINITION", messages.invalidDefinition);
    return controllers[0];
}

function laserDefinitionMetadata(state) {
    const controller = laserController(state);
    const definition = controller.parameters?.["definition"];
    const animations = definition?.["animations"]?.filter(animation => animation.name === LASER_CONFIG.animation.name);
    requireCondition(Array.isArray(animations) && animations.length === 1,
        "INVALID_LASER_DEFINITION", messages.invalidDefinition);
    const animation = animations[0];
    const variables = Object.entries(LASER_CONFIG.variables).map(([name, config]) => {
        const matches = animation.parameters?.filter(parameter => parameter.name === name);
        requireCondition(Array.isArray(matches) && matches.length === 1 && matches[0].type === config.type,
            "INVALID_LASER_DEFINITION", messages.invalidDefinition);
        return {name, id: matches[0].id, ...config};
    });
    const beamIds = LASER_CONFIG.layers.map(layer => {
        const beams = state.objects.filter(object => object.name === layer.name && object.type === LASER_CONFIG.beam.type);
        requireCondition(beams.length === 1, "INVALID_LASER_DEFINITION", messages.invalidDefinition);
        return beams[0].objectId;
    });
    requireCondition(animation.parameters.length === variables.length
        && animation.tracks?.length === beamIds.length && animation.bindings?.length === beamIds.length * 3
        && animation.bindings.every(binding => beamIds.includes(binding.target)),
        "INVALID_LASER_DEFINITION", messages.invalidDefinition);
    return {
        controllerId: controller.objectId,
        animationId: animation.id,
        animationName: animation.name,
        beamIds,
        variables
    };
}

async function saveReferences(snapshot) {
    stage = "prepare_save";
    requireCondition(isObject(snapshot.application), "APPLY_REQUIRED", messages.invalidApplication);
    snapshot.starState = await pauseFxState(progress.starProjectId);
    snapshot.laserState = await pauseFxState(progress.laserProjectId);
    requireCondition(snapshot.application.starRevision === snapshot.starState.revision
        && snapshot.application.laserRevision === snapshot.laserState.revision,
        "APPLICATION_CHANGED", messages.changedApplication);
    const laserVariables = laserDefinitionMetadata(snapshot.laserState);
    const roots = snapshot.capabilities["allowedRoots"];
    requireCondition(Array.isArray(roots) && typeof roots[0] === "string" && isAbsolute(roots[0]),
        "INVALID_ALLOWED_ROOTS", messages.invalidRoots);
    const outputDirectory = resolve(roots[0], outputConfiguration.directory);
    const reportPath = resolve(diagnosticDirectory, outputConfiguration.reportFile);
    const destinations = Object.entries(referenceEffects).map(([key, definition]) => ({
        key,
        projectId: progress[key + "ProjectId"],
        projectPath: resolve(outputDirectory, definition.fileName + outputConfiguration.projectSuffix),
        definitionPath: resolve(outputDirectory, definition.fileName + outputConfiguration.definitionSuffix),
        packPath: resolve(outputDirectory, definition.fileName + outputConfiguration.packSuffix),
    }));

    for (const destination of destinations) {
        for (const path of [destination.projectPath, destination.definitionPath, destination.packPath]) {
            await requireNewFile(path);
        }
    }

    await requireNewFile(reportPath);
    const files = [];
    for (const destination of destinations) {
        files.push(await saveReference(destination));
    }

    snapshot.starState = await fxState(progress.starProjectId);
    snapshot.laserState = await fxState(progress.laserProjectId);
    await updateSnapshot(snapshot);
    await writeFile(reportPath, JSON.stringify({
        ...progress, outputDirectory, files, laserVariables,
        enabledSemantics: "enabled 控制激光透明度，实例模拟和销毁由原生生命周期负责。",
        snapshotPath,
    }, null, 2) + "\n", {encoding: "utf8", flag: "wx"});
    return {ok: true, phase: "save", ...progress, snapshotPath, reportPath, files};
}

async function saveReference(destination) {
    const {projectId, projectPath, definitionPath, packPath} = destination;
    stage = "save_" + destination.key;
    const saved = await callEffectTool("effect_save", {projectId, path: projectPath, overwrite: false});
    completedFiles.push({kind: "project", path: saved.savedPath});
    const savedState = await fxState(projectId);
    requireCondition(saved.dirty === false && savedState.dirty === false
        && resolve(saved.savedPath) === projectPath && resolve(savedState.editorPath) === projectPath,
        "INVALID_SAVE_RESPONSE", messages.invalidSave + projectPath);
    const definition = await exportReference(projectId, definitionPath, "definition");
    const pack = await exportReference(projectId, packPath, "pack");
    return {
        projectId, projectPath: saved.savedPath, definitionPath: definition.path, packPath: pack.path,
        definitionWarnings: definition.warnings, packWarnings: pack.warnings
    };
}

async function exportReference(projectId, path, kind) {
    stage = "export_" + kind;
    const exported = await callEffectTool("effect_export", {projectId, path, kind, overwrite: false});
    completedFiles.push({kind, path: exported.path});
    requireCondition(exported.kind === kind && resolve(exported.path) === path
        && Array.isArray(exported.warnings) && exported.warnings.every(warning => typeof warning === "string"),
        "INVALID_EXPORT_RESPONSE", messages.invalidSave + path);
    return exported;
}

async function requireNewFile(path) {
    try {
        await lstat(path);
    } catch (error) {
        if (error?.code === "ENOENT") {
            return;
        }

        throw error;
    }

    throw requestError("FILE_EXISTS", messages.fileExists + " " + path);
}

function replaceControllerDefinition(state, definition) {
    const controller = laserController(state);
    const source = state.snapshotSnbt;
    requireCondition(typeof source === "string", "INVALID_SNBT", messages.invalidSnbt);
    const root = parseSnbt(source);
    const objects = root.fields?.get("fx")?.fields?.get("fxData")?.fields?.get("fxObjects")?.items;
    requireCondition(Array.isArray(objects), "INVALID_SNBT", messages.invalidSnbt);
    const targets = objects.filter(object => {
        const data = object.fields?.get("data");
        return object.fields?.get("type")?.value === controller.type
            && data?.fields?.get("name")?.value === controller.name
            && data?.fields?.get("transform")?.fields?.get("id")?.value === controller.objectId;
    });
    const field = targets.length === 1 ? targets[0].fields.get("data").fields.get("definition") : null;
    requireCondition(field?.fields instanceof Map, "INVALID_SNBT", messages.invalidSnbt);
    const replacement = parseSnbt(definition);
    requireCondition(replacement.fields instanceof Map, "INVALID_SNBT", messages.invalidSnbt);
    return source.slice(0, field.start) + definition + source.slice(field.end);
}

// 仅记录原生 SNBT 值边界；替换控制器定义时保留工程中其他字段的原始文本与类型。
function parseSnbt(source) {
    const reader = {source, offset: 0};
    const value = readSnbtValue(reader);
    skipSnbtWhitespace(reader);
    requireCondition(reader.offset === source.length, "INVALID_SNBT", messages.invalidSnbt);
    return value;
}

function skipSnbtWhitespace(reader) {
    while (/\s/.test(reader.source[reader.offset] ?? "") && reader.offset < reader.source.length) {
        reader.offset++;
    }
}

function readSnbtValue(reader) {
    skipSnbtWhitespace(reader);
    const start = reader.offset;
    const next = reader.source[reader.offset];

    if (next === "{") {
        return readSnbtCompound(reader, start);
    }

    if (next === "[") {
        return readSnbtList(reader, start);
    }

    if (next === '"' || next === "'") {
        return {start, ...readSnbtString(reader)};
    }

    while (reader.offset < reader.source.length && !/[,\]}\s]/.test(reader.source[reader.offset])) {
        reader.offset++;
    }

    requireCondition(reader.offset > start, "INVALID_SNBT", messages.invalidSnbt);
    return {start, end: reader.offset, value: reader.source.slice(start, reader.offset)};
}

function readSnbtString(reader) {
    const quote = reader.source[reader.offset++];
    let value = "";
    while (reader.offset < reader.source.length) {
        const character = reader.source[reader.offset++];
        if (character === quote) {
            return {value, end: reader.offset};
        }

        if (character === "\\") {
            requireCondition(reader.offset < reader.source.length, "INVALID_SNBT", messages.invalidSnbt);
            value += reader.source[reader.offset++];
        } else {
            value += character;
        }
    }

    throw requestError("INVALID_SNBT", messages.invalidSnbt);
}

function readSnbtCompound(reader, start) {
    const fields = new Map();
    reader.offset++;
    skipSnbtWhitespace(reader);

    while (reader.source[reader.offset] !== "}") {
        const keyStart = reader.offset;
        let key;
        if (reader.source[reader.offset] === '"' || reader.source[reader.offset] === "'") {
            key = readSnbtString(reader).value;
        } else {
            while (reader.offset < reader.source.length && !/[:\s]/.test(reader.source[reader.offset])) {
                reader.offset++;
            }

            key = reader.source.slice(keyStart, reader.offset);
        }

        skipSnbtWhitespace(reader);
        requireCondition(key.length > 0 && !fields.has(key) && reader.source[reader.offset++] === ":",
            "INVALID_SNBT", messages.invalidSnbt);
        fields.set(key, readSnbtValue(reader));
        skipSnbtWhitespace(reader);

        if (reader.source[reader.offset] !== ",") {
            break;
        }

        reader.offset++;
        skipSnbtWhitespace(reader);
    }

    requireCondition(reader.source[reader.offset++] === "}", "INVALID_SNBT", messages.invalidSnbt);
    return {start, end: reader.offset, fields};
}

function readSnbtList(reader, start) {
    const items = [];
    reader.offset++;
    skipSnbtWhitespace(reader);

    if (/^[BIL];/.test(reader.source.slice(reader.offset, reader.offset + 2))) {
        reader.offset += 2;
        skipSnbtWhitespace(reader);
    }

    while (reader.source[reader.offset] !== "]") {
        items.push(readSnbtValue(reader));
        skipSnbtWhitespace(reader);

        if (reader.source[reader.offset] !== ",") {
            break;
        }

        reader.offset++;
        skipSnbtWhitespace(reader);
    }

    requireCondition(reader.source[reader.offset++] === "]", "INVALID_SNBT", messages.invalidSnbt);
    return {start, end: reader.offset, items};
}

async function inspectReferences() {
    await requireNewSnapshot();
    await client.connect(transport);
    const capabilities = await callEffectTool("effect_capabilities", {});
    validateEmitterCapabilities(capabilities);

    const starState = await createReference("star");
    const laserState = await createReference("laser");
    await mkdir(dirname(snapshotPath), {recursive: true});
    await writeFile(snapshotPath, JSON.stringify({capabilities, starState, laserState}, null, 2) + "\n",
        {encoding: "utf8", flag: "wx"});

    return {ok: true, ...progress, snapshotPath};
}

async function requireNewSnapshot() {
    try {
        await lstat(snapshotPath);
    } catch (error) {
        if (error?.code === "ENOENT") {
            return;
        }

        throw error;
    }

    throw requestError("FILE_EXISTS", messages.fileExists);
}

function validateEmitterCapabilities(capabilities) {
    requireCondition(Array.isArray(capabilities.emitterTypes), "INVALID_CAPABILITIES", messages.invalidCapabilities);
    const registered = new Set(capabilities.emitterTypes.map(type => type.type));

    for (const {emitters} of Object.values(referenceEffects)) {
        for (const emitter of emitters) {
            requireCondition(registered.has(emitter.type), "EMITTER_TYPE_MISSING",
                messages.missingEmitter + emitter.type);
        }
    }
}

async function createReference(key) {
    const definition = referenceEffects[key];
    const created = await callEffectTool("effect_create", {kind: "fx", name: definition.name});
    requireCondition(created.kind === "fx" && typeof created.projectId === "string" && created.projectId.length > 0,
        "INVALID_CREATE_RESPONSE", messages.invalidCreation + definition.name);
    const projectId = created.projectId;
    progress[key + "ProjectId"] = projectId;
    const paused = await callEffectTool("effect_preview", {projectId, action: "pause"});
    requireCondition(paused.playing === false, "PREVIEW_NOT_PAUSED", messages.invalidPause + projectId);
    const before = await fxState(projectId);
    const edited = await callEffectTool("effect_edit_batch", {
        projectId,
        expectedRevision: before.revision,
        operations: definition.emitters.map(emitter => ({op: "add_emitter", ...emitter})),
    });
    const after = await fxState(projectId);
    validateCreatedEmitters(definition, before, edited, after);
    return after;
}

async function fxState(projectId) {
    const state = await callEffectTool("effect_state", {projectId});
    requireCondition(state.projectId === projectId && state.kind === "fx"
        && typeof state.revision === "string" && state.revision.length > 0
        && Array.isArray(state.objects) && state.playing === false,
        "INVALID_FX_STATE", messages.invalidState + projectId);
    return state;
}

function validateCreatedEmitters(definition, before, edited, after) {
    const previousIds = new Set(before.objects.map(object => object.objectId));
    const added = after.objects.filter(object => !previousIds.has(object.objectId));
    const hasAllEmitters = definition.emitters.every(emitter =>
        added.filter(object => object.type === emitter.type && object.name === emitter.name).length === 1);
    requireCondition(edited.changed === true && edited.dryRun === false && edited.playing === false
        && edited.revision === after.revision && after.revision !== before.revision
        && added.length === definition.emitters.length && hasAllEmitters
        && after.objects.length === before.objects.length + added.length
        && before.objects.every(object => after.objects.some(candidate => candidate.objectId === object.objectId))
        && new Set(after.objects.map(object => object.objectId)).size === after.objects.length
        && after.undoCount === before.undoCount + 1 && after.redoCount === 0,
        "INVALID_EDIT_RESPONSE", messages.invalidEdit + definition.name);
}

async function callEffectTool(name, args) {
    const result = await client.callTool({name, arguments: args});
    const response = result.structuredContent;
    validateEffectResponse(response, name);
    requireCondition(Array.isArray(result.content) && result.content.length === 1
        && result.content[0].type === "text" && typeof result.content[0].text === "string"
        && isDeepStrictEqual(response, JSON.parse(result.content[0].text)),
        "INCONSISTENT_CONTENT", messages.inconsistentContent + name);
    requireCondition(result.isError === !response.ok, "INCONSISTENT_ERROR", messages.inconsistentError + name);

    if (!response.ok) {
        throw requestError(response.error.code, response.error.message);
    }

    return response.data;
}

function validateEffectResponse(response, name) {
    requireCondition(isObject(response) && Object.keys(response).sort().join(",") === "data,error,ok"
        && typeof response.ok === "boolean", "INVALID_ENVELOPE", messages.invalidEnvelope + name);
    const validPayload = response.ok
        ? isObject(response.data) && response.error === null
        : response.data === null && isObject(response.error)
        && Object.keys(response.error).sort().join(",") === "code,message"
        && typeof response.error.code === "string" && typeof response.error.message === "string";
    requireCondition(validPayload, "INVALID_PAYLOAD", messages.invalidPayload + name);
}

function isObject(value) {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isWithin(root, path) {
    const fromRoot = relative(root, path);
    return fromRoot === "" || fromRoot !== ".." && !fromRoot.startsWith(".." + sep) && !isAbsolute(fromRoot);
}

function requireCondition(condition, code, message) {
    if (!condition) {
        throw requestError(code, message);
    }
}

function requestError(code, message) {
    const error = new Error(message);
    error.code = code;
    return error;
}

function errorDetails(error) {
    return {
        code: typeof error?.code === "string" ? error.code : "REFERENCE_AUTHORING_FAILED",
        message: error instanceof Error ? error.message : String(error),
    };
}

function failureSummary(error) {
    return {
        ok: false, phase: process.argv[2] ?? null, stage, ...progress, snapshotPath,
        completedFiles, ...(failureDiagnostic ? {failureDiagnostic} : {}), error: errorDetails(error)
    };
}

