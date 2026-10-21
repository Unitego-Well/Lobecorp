import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';

const packageRoot = fileURLToPath(new URL('../', import.meta.url));
const entry = fileURLToPath(new URL('../dist/src/cli.js', import.meta.url));
const client = new Client({name: 'normify-protocol-check', version: '0.1.0'});
const transport = new StdioClientTransport({
    command: process.execPath,
    args: [entry, 'serve', '--workspace-root', packageRoot],
    stderr: 'pipe',
});
try {
    await client.connect(transport);
    const result = await client.listTools();
    assert.equal(result.tools.length, 31);
    assert.ok(result.tools.every(tool => tool.name.startsWith('normify_')));
    const help = await client.callTool({name: 'normify_help', arguments: {topic: 'tool:normify_module_batch'}});
    assert.notEqual(help.isError, true);
    const escape = await client.callTool({
        name: 'normify_tree_list',
        arguments: {root: fileURLToPath(new URL('../../', import.meta.url))},
    });
    assert.equal(escape.isError, true);
    console.log('PASS: initialize, tools/list (31), read-only help, workspace escape rejected');
} finally {
    await client.close();
}
