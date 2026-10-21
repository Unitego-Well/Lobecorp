import {z} from "zod";

const mode = z.enum(["direct", "simulate"]).default("simulate");
const position = z.object({x: z.number().finite(), y: z.number().finite(), z: z.number().finite()});
const blockPosition = z.object({x: z.number().int(), y: z.number().int(), z: z.number().int()});
const hand = z.enum(["main", "off"]).default("main");
const inputKeys = z.object({
    forward: z.boolean().optional(),
    backward: z.boolean().optional(),
    left: z.boolean().optional(),
    right: z.boolean().optional(),
    jump: z.boolean().optional(),
    sneak: z.boolean().optional(),
    sprint: z.boolean().optional(),
    attack: z.boolean().optional(),
    use: z.boolean().optional(),
});

export const CONTROL_TOOL_NAMES = ["gui_control", "player_control", "entity_control", "block_control"] as const;

export const CONTROL_TOOLS = [
    {
        name: "gui_control",
        description: "Inspect GUI widgets with screenId/widgetId. Direct mode invokes visible enabled buttons, focuses widgets or sets EditBox text. Simulate mode sends click/drag/scroll/type/key/close through the current screen. Mutations require the latest screenId and MCP control mode. Coordinates explicitly use GUI units or framebuffer pixels; no OS input is used. accepted means the GUI handled the event, not that a workflow completed.",
        inputSchema: {
            action: z.enum(["list", "click", "drag", "scroll", "type", "key", "close", "set_text", "focus"]),
            mode,
            screenId: z.string().min(1).optional(),
            widgetId: z.string().min(1).optional(),
            coordinateSpace: z.enum(["gui", "framebuffer"]).default("gui"),
            x: z.number().finite().nonnegative().optional(),
            y: z.number().finite().nonnegative().optional(),
            endX: z.number().finite().nonnegative().optional(),
            endY: z.number().finite().nonnegative().optional(),
            button: z.number().int().min(0).max(2).default(0),
            scrollX: z.number().finite().min(-100).max(100).default(0),
            scrollY: z.number().finite().min(-100).max(100).optional(),
            text: z.string().max(4096).optional(),
            key: z.number().int().min(0).max(348).optional(),
            modifiers: z.number().int().min(0).max(15).default(0),
        },
    },
    {
        name: "player_control",
        description: "Read local player/inventory. Direct integrated-server actions: teleport, set_state(health/food/saturation), gamemode, set_slot(item/count), select_slot. Simulate actions: input(keys held for 1..200 client ticks), release, look(yaw/pitch), select_slot, attack current aimed entity, use held item. Input is released on expiry, GUI opening, player change or control exit. Multiplayer simulation uses normal game networking; direct mutations require the integrated server. Mutations require MCP control mode.",
        inputSchema: {
            action: z.enum(["get", "inventory", "teleport", "set_state", "gamemode", "set_slot", "select_slot", "input", "release", "look", "attack", "use"]),
            mode,
            position: position.optional(),
            state: z.object({
                health: z.number().finite().nonnegative().optional(),
                food: z.number().int().min(0).max(20).optional(),
                saturation: z.number().finite().min(0).max(20).optional(),
            }).optional(),
            gamemode: z.enum(["survival", "creative", "adventure", "spectator"]).optional(),
            slot: z.number().int().nonnegative().optional(),
            item: z.string().min(1).optional(),
            count: z.number().int().nonnegative().optional(),
            keys: inputKeys.optional(),
            ticks: z.number().int().min(1).max(200).optional(),
            yaw: z.number().finite().min(-180).max(180).optional(),
            pitch: z.number().finite().min(-90).max(90).optional(),
            hand,
        },
    },
    {
        name: "entity_control",
        description: "List/get client-loaded entities by UUID, radius<=128 and optional type; list is capped at 256. Direct integrated-server actions: spawn, teleport, set_state(name/health/noAi/invulnerable/glowing), navigate(position/speed), stop navigation, remove. Direct mutation of players is rejected. Simulate attack/interact requires the named UUID to be the current crosshair target, in reach and visible; normal server rules apply. Mutations require MCP control mode.",
        inputSchema: {
            action: z.enum(["list", "get", "spawn", "teleport", "set_state", "navigate", "stop", "remove", "attack", "interact"]),
            mode,
            uuid: z.string().uuid().optional(),
            center: position.optional(),
            radius: z.number().finite().min(0).max(128).default(32),
            limit: z.number().int().min(1).max(256).default(64),
            type: z.string().min(1).optional(),
            position: position.optional(),
            speed: z.number().finite().min(0.01).max(4).optional(),
            state: z.object({
                name: z.string().max(256).optional(),
                health: z.number().finite().nonnegative().optional(),
                noAi: z.boolean().optional(),
                invulnerable: z.boolean().optional(),
                glowing: z.boolean().optional(),
            }).optional(),
            hand,
        },
    },
    {
        name: "block_control",
        description: "Read a loaded client block and state properties. Direct integrated-server set/fill/break uses normal world updates; fill is inclusive and capped at 4096 loaded blocks with full bounds preflight. Supply registry block ID and optional string properties; block-entity NBT is not modified. Simulate start_break/continue_break/stop_break/use/place uses the held item and current crosshair BlockHitResult within player reach. accepted is not proof of a completed break or placement. Mutations require MCP control mode.",
        inputSchema: {
            action: z.enum(["get", "set", "fill", "break", "start_break", "continue_break", "stop_break", "use", "place"]),
            mode,
            position: blockPosition.optional(),
            to: blockPosition.optional(),
            block: z.string().min(1).optional(),
            properties: z.record(z.string()).optional(),
            drops: z.boolean().default(false),
            hand,
        },
    },
] satisfies Array<{ name: string; description: string; inputSchema: z.ZodRawShape }>;
