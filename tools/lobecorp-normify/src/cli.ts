#!/usr/bin/env node
import {argumentsFrom, configFrom} from './config.js';
import {createCatalog} from './catalog.js';
import {serve} from './server.js';

const aliases: Record<string, string> = {
    init: 'normify_project_init',
    validate: 'normify_validate',
    build: 'normify_build',
    render: 'normify_render',
    sync: 'normify_sync',
    brief: 'normify_brief',
    help: 'normify_help',
};

async function main(): Promise<void> {
    const parsed = argumentsFrom(process.argv.slice(2));
    const config = configFrom(parsed.values);
    const [command = 'serve', ...extra] = parsed.positionals;
    if (extra.length) throw new Error('仅允许一个子命令；工具参数通过 --args JSON 传入');
    if (command === 'serve') {
        await serve(config);
        return;
    }
    const catalog = createCatalog(config);
    if (command === 'tools') {
        console.log(JSON.stringify([...catalog.definitions.values()].map(tool => ({
            name: tool.name,
            description: tool.description,
            inputSchema: tool.parameters,
            readOnly: tool.readOnly,
        })), null, 2));
        return;
    }
    const args: unknown = JSON.parse(parsed.values.args ?? '{}');
    if (!args || typeof args !== 'object' || Array.isArray(args)) {
        throw new Error('--args 必须为 JSON 对象');
    }
    const input = args as Record<string, unknown>;
    if (parsed.values.project) input.project = parsed.values.project;
    const value = await catalog.call(aliases[command] ?? command, input);
    console.log(JSON.stringify(value, null, 2));
    if (value && typeof value === 'object' && 'ok' in value && value.ok === false) process.exitCode = 1;
}

main().catch(error => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
});
