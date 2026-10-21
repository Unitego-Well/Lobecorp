import type {McpServer} from "@modelcontextprotocol/sdk/server/mcp.js";
import type {ModClient} from "../api/modClient.js";
import {EFFECT_RESPONSE_SCHEMA} from "./effectTools.js";
import {CONTROL_TOOLS} from "./controlTools.js";

export function registerControlHandlers(server: McpServer, mod: ModClient): void {
    for (const tool of CONTROL_TOOLS) {
        server.registerTool(tool.name, {
            description: tool.description,
            inputSchema: tool.inputSchema,
            outputSchema: EFFECT_RESPONSE_SCHEMA.shape,
            annotations: {
                readOnlyHint: false,
                destructiveHint: true,
                idempotentHint: false,
                openWorldHint: false,
            },
        }, async (params: Record<string, unknown>) => {
            try {
                const raw = await mod.sendCommand(tool.name, params);
                const parsed = EFFECT_RESPONSE_SCHEMA.safeParse(
                    typeof raw === "object" && raw !== null && !Array.isArray(raw)
                        ? {
                            ok: (raw as Record<string, unknown>).ok,
                            data: (raw as Record<string, unknown>).data ?? null,
                            error: (raw as Record<string, unknown>).error ?? null
                        }
                        : null,
                );
                if (!parsed.success || parsed.data.ok === (parsed.data.error !== null)) {
                    return failure("INVALID_RESPONSE", "Game control returned an invalid structured response.");
                }
                const result = parsed.data;
                return {
                    content: [{type: "text" as const, text: JSON.stringify(result)}],
                    structuredContent: result,
                    isError: !result.ok,
                };
            } catch (error: unknown) {
                return failure("TRANSPORT_ERROR", error instanceof Error ? error.message : "Game control request failed.");
            }
        });
    }
}

function failure(code: string, message: string) {
    const result = {ok: false, data: null, error: {code, message}};
    return {
        content: [{type: "text" as const, text: JSON.stringify(result)}],
        structuredContent: result,
        isError: true,
    };
}
