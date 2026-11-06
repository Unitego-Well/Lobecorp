import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const require=createRequire('E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/package.json');
const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/sdk/client/index.js')).href);
const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/sdk/client/stdio.js')).href);
const transport=new StdioClientTransport({command:'C:/Program Files/nodejs/node.exe',args:['E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon/lib/minecraft-mcp/bridge/dist/cli.js','mcp','--no-discover'],env:{...process.env,MC_MCP_PORT:'19877'},stderr:'pipe'});
const client=new Client({name:'photon-performance',version:'1.0'});
try {
 await client.connect(transport);
 const list=await client.listTools();
 if (!list.tools.some(t=>t.name==='screenshot_to_file')) throw Error('snapshot_tool_missing');
 console.log(JSON.stringify(await client.callTool({name:'ping',arguments:{}})));
 console.log(JSON.stringify(await client.callTool({name:'get_minecraft_status',arguments:{}})));
 if(process.argv[2]) console.log(JSON.stringify(await client.callTool({name:'screenshot_to_file',arguments:{path:process.argv[2],region:'game'}})));
} finally {await client.close();}
