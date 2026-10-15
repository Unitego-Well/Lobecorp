import {z} from "zod";

export interface EffectToolDef {
    name: string;
    description: string;
    inputSchema: z.ZodRawShape;
    readOnly: boolean;
}

const projectId = z.string().min(1).describe("Opaque project ID returned by effect_state or effect_create.");
const path = z.string().min(1).describe("Output path within the project's permitted effect workspace.");
const overwrite = z.boolean().default(false).describe("Allow replacing an existing output file.");
const tick = z.number().finite().nonnegative().describe("Timeline position in game ticks.");

export const EFFECT_RESPONSE_SCHEMA = z.object({
    ok: z.boolean(),
    data: z.record(z.unknown()).nullable(),
    error: z.object({
        code: z.string(),
        message: z.string(),
    }).nullable(),
});

export const EFFECT_TOOLS: EffectToolDef[] = [
    {
        name: "effect_capabilities",
        description: "Describe effect project kinds, editable operations, field types, units, ranges, enums, file formats, and permitted workspace. Read this before creating or editing an effect.",
        inputSchema: {},
        readOnly: true,
    },
    {
        name: "effect_state",
        description: "Read the current effect project, stable project/object IDs, revision, configuration, animation tracks, preview state, and history. Without projectId, list all open effect projects. Before editing a particle FX project, pause its preview, then read this state: native FX playback changes persisted parameters and therefore revisions. Model projects do not have this restriction.",
        inputSchema: {projectId: projectId.optional()},
        readOnly: true,
    },
    {
        name: "effect_create",
        description: "Create and open an editable particle FX or model effect project. Returns projectId and revision. Enter a Minecraft world before creating a project; the native editor requires world registries. Requires Minecraft MCP control mode.",
        inputSchema: {
            kind: z.enum(["fx", "model"]).describe("Particle FX project or model effect project."),
            name: z.string().min(1).optional().describe("Optional project display name."),
        },
        readOnly: false,
    },
    {
        name: "effect_edit_batch",
        description: "Validate structured effect operations against the specified project revision, then commit them as one undoable edit. dryRun validates without committing. Read operation schemas from effect_capabilities. For particle FX projects, call effect_preview with action=pause, then effect_state, then submit that revision; playing FX timelines change persisted parameters and invalidate earlier revisions. Model projects do not require pausing. The edit preserves the preview pause state. Requires control mode.",
        inputSchema: {
            projectId,
            expectedRevision: z.string().min(1).describe("Exact revision from the latest effect_state or mutation response."),
            operations: z.array(z.record(z.unknown())).min(1).describe("Ordered operation objects described by effect_capabilities; nested configuration values are preserved."),
            dryRun: z.boolean().default(false).describe("Validate the entire batch without changing the project."),
        },
        readOnly: false,
    },
    {
        name: "effect_preview",
        description: "Control the project's native preview clock. Seek requires a time in ticks. Use the existing screenshot tool to inspect the rendered result. Requires control mode.",
        inputSchema: {
            projectId,
            action: z.enum(["play", "pause", "stop", "seek"]),
            time: tick.optional(),
        },
        readOnly: false,
    },
    {
        name: "effect_save",
        description: "Save an editable effect project to its permitted workspace and return the actual output path. Existing files require overwrite=true. Requires control mode.",
        inputSchema: {projectId, path, overwrite},
        readOnly: false,
    },
    {
        name: "effect_export",
        description: "Export an effect definition or resource pack through the project's native exporter and return the actual output path. Existing files require overwrite=true. Requires control mode.",
        inputSchema: {
            projectId,
            path,
            kind: z.enum(["definition", "pack"]).describe("Runtime effect definition or bundled resource pack."),
            overwrite,
        },
        readOnly: false,
    },
    {
        name: "effect_history",
        description: "Undo or redo one native effect edit and return the resulting project revision and state. Requires control mode.",
        inputSchema: {
            projectId,
            action: z.enum(["undo", "redo"]),
        },
        readOnly: false,
    },
    {
        name: "effect_open",
        description: "Open an existing .fxproj or .mfxproj from the permitted effect workspace in a new editor tab. Preserve every existing project and return the new projectId and revision. Requires a world and control mode.",
        inputSchema: {path},
        readOnly: false,
    },
    {
        name: "effect_resources",
        description: "List native resource categories, provider entries, workspace files, and resource limits. Optionally restrict to a project, category, or workspace directory. Native resource paths returned here can be passed to effect_resource_read or used as a copy source.",
        inputSchema: {
            projectId: projectId.optional(),
            category: z.string().min(1).optional(),
            directory: z.string().min(1).optional(),
        },
        readOnly: true,
    },
    {
        name: "effect_resource_read",
        description: "Read a native resource or a permitted workspace resource file with its actual NBT/SNBT representation. Use a category and resource path from effect_resources. Does not change the project or files.",
        inputSchema: {
            projectId: projectId.optional(),
            category: z.string().min(1),
            path: z.string().min(1).describe("Native resource path or file path within the permitted effect workspace."),
        },
        readOnly: true,
    },
    {
        name: "effect_resource_write",
        description: "Validate and write a native resource to the permitted effect workspace. Supply exactly one of source (a native resource path) or snbt (the complete native resource value). Existing files require overwrite=true. File writes are outside FX undo history. Requires control mode.",
        inputSchema: {
            projectId: projectId.optional(),
            category: z.string().min(1),
            path,
            source: z.string().min(1).optional(),
            snbt: z.string().min(1).optional(),
            overwrite,
        },
        readOnly: false,
    },
    {
        name: "effect_texture_import",
        description: "Import a base64 PNG into assets/<namespace>/textures/...png and register the native texture without a global reload. Read size limits from effect_resources. Existing files require overwrite=true. File writes are outside FX undo history. Requires control mode.",
        inputSchema: {
            path,
            base64: z.string().min(1).describe("Raw PNG bytes encoded as base64, without a data URL prefix."),
            overwrite,
        },
        readOnly: false,
    },
    {
        name: "effect_view_state",
        description: "Read the specified project's preview camera, display settings, and game-embedded capture regions. Does not activate an editor tab or change the project. The existing screenshot tools use this state for scene/editor capture.",
        inputSchema: {projectId},
        readOnly: true,
    },
    {
        name: "effect_view",
        description: "Set the specified project's native preview camera and display options, then return its view state. Settings do not create FX undo steps or edit the asset. Read supported fields from effect_view_state. Requires control mode.",
        inputSchema: {
            projectId,
            center: z.tuple([z.number().finite(), z.number().finite(), z.number().finite()]).optional(),
            zoom: z.number().finite().positive().optional(),
            yaw: z.number().finite().optional().describe("Camera yaw in degrees."),
            pitch: z.number().finite().optional().describe("Camera pitch in degrees."),
            orthographic: z.boolean().optional(),
            orthoRange: z.number().finite().positive().optional(),
            bloomEnabled: z.boolean().optional(),
            effectsEnabled: z.boolean().optional(),
            shapeVisible: z.boolean().optional(),
            cullBoxVisible: z.boolean().optional(),
        },
        readOnly: false,
    },
];
