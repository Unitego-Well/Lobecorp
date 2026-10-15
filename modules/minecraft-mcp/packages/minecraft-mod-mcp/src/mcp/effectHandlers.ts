import type {McpServer} from "@modelcontextprotocol/sdk/server/mcp.js";
import type {z} from "zod";
import type {ModClient} from "../api/modClient.js";
import {EFFECT_RESPONSE_SCHEMA, EFFECT_TOOLS} from "./effectTools.js";

type EffectResponse = z.infer<typeof EFFECT_RESPONSE_SCHEMA>;

const EFFECT_ERRORS = {
    invalidResponse: {code: "invalid_response", message: "The effect service returned an invalid response."},
    missingSeekTime: {code: "invalid_request", message: "Seeking an effect preview requires time in ticks."},
    requestFailed: {code: "transport_error", message: "The effect request failed."},
} as const;

export function registerEffectHandlers(server: McpServer, mod: ModClient): void {
    for (const tool of EFFECT_TOOLS) {
        server.registerTool(tool.name, {
            description: tool.description,
            inputSchema: tool.inputSchema,
            outputSchema: EFFECT_RESPONSE_SCHEMA.shape,
            annotations: {
                readOnlyHint: tool.readOnly,
                destructiveHint: !tool.readOnly,
                idempotentHint: tool.readOnly,
                openWorldHint: false,
            },
        }, async (params: Record<string, unknown>) => {
            if (tool.name === "effect_preview" && params.action === "seek" && params.time === undefined) {
                return formatResponse(failure(EFFECT_ERRORS.missingSeekTime));
            }

            try {
                const result = await mod.sendCommand(tool.name, params);
                return formatResponse(normalizeResponse(result));
            } catch (err: unknown) {
                const message = err instanceof Error ? err.message : EFFECT_ERRORS.requestFailed.message;
                return formatResponse(failure({code: EFFECT_ERRORS.requestFailed.code, message}));
            }
        });
    }
}

function normalizeResponse(result: unknown): EffectResponse {
    if (typeof result !== "object" || result === null || Array.isArray(result)) {
        return failure(EFFECT_ERRORS.invalidResponse);
    }

    const body = result as Record<string, unknown>;
    const parsed = EFFECT_RESPONSE_SCHEMA.safeParse({
        ok: body.ok,
        data: body.data ?? null,
        error: body.error ?? null,
    });
    if (!parsed.success || parsed.data.ok === (parsed.data.error !== null)) {
        return failure(EFFECT_ERRORS.invalidResponse);
    }

    return parsed.data;
}

function failure(error: { code: string; message: string }): EffectResponse {
    return {ok: false, data: null, error};
}

function formatResponse(response: EffectResponse) {
    return {
        content: [{type: "text" as const, text: JSON.stringify(response, null, 2)}],
        structuredContent: response,
        isError: !response.ok,
    };
}
