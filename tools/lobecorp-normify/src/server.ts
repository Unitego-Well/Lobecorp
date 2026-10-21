import {Server} from '@modelcontextprotocol/sdk/server/index.js';
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio.js';
import {CallToolRequestSchema, ListToolsRequestSchema} from '@modelcontextprotocol/sdk/types.js';
import type {Tool} from '@modelcontextprotocol/sdk/types.js';
import {createCatalog} from './catalog.js';
import type {RuntimeConfig} from './config.js';

export async function serve(config: RuntimeConfig): Promise<void> {
    const catalog = createCatalog(config);
    const server = new Server(
        {name: 'lobecorp-normify', version: '0.1.0'},
        {capabilities: {tools: {}}},
    );
    server.setRequestHandler(ListToolsRequestSchema, async () => ({
        tools: [...catalog.definitions.values()].map(definition => ({
            name: definition.name,
            description: definition.description,
            inputSchema: (definition.parameters ?? {type: 'object', properties: {}}) as Tool['inputSchema'],
            annotations: {
                readOnlyHint: definition.readOnly,
                idempotentHint: definition.idempotent,
                destructiveHint: definition.destructive,
                openWorldHint: false,
            },
        })),
    }));
    server.setRequestHandler(CallToolRequestSchema, async request => {
        try {
            const value = await catalog.call(request.params.name, request.params.arguments ?? {});
            const result = value !== null && typeof value === 'object' && !Array.isArray(value)
                ? value as Record<string, unknown> : {result: value};
            return {
                content: [{type: 'text' as const, text: JSON.stringify(value, null, 2)}],
                structuredContent: result,
                isError: result.ok === false,
            };
        } catch (error) {
            return {
                content: [{type: 'text' as const, text: error instanceof Error ? error.message : String(error)}],
                isError: true,
            };
        }
    });
    await server.connect(new StdioServerTransport());
}
