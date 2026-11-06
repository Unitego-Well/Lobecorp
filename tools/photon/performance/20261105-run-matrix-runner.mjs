import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
const base=path.join(root,'build/agent-backups/20261105-run-matrix');
const args=new Set(process.argv.slice(2));
if([...args].some(x=>!['--run','--resume','--dry-run','--discover','--fresh-batch'].includes(x))) throw Error('Unknown argument');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const matrix=json(path.join(root,'tools/photon/performance/20261105-run-matrix.json'));
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function audit(run,id) {
 const dir=path.join(base,id), result=path.join(dir,'results');
 if(!fs.existsSync(result)) return {id,state:'pending'};
 try {
  const summary=json(path.join(result,'summary.json'));
  const custom=path.join(result,'artifacts/custom');
  const names=fs.readdirSync(custom).filter(n=>n.endsWith('_result.json'));
  if(names.length!==1) throw Error('ambiguous custom results');
  const r=json(path.join(custom,names[0]));
  const checks={
   passed:summary.scenarios?.length>0 && summary.scenarios.every(s=>s.status==='PASSED' && s.workloadCorrect),
   identity:r.runId===id && r.scenario===run.scenario && r.batchId==='20261105-run-matrix',
   source:r.sourceCommit==='3c2725c48fb6d3c503b5e8eefb3a2abeac235acc' && r.sourceVariant==='working_tree_20261105',
   dimensions:r.dimensions==='2538x1472', shader:r.shader===run.shader,
   complete:r.complete===true && r.performance_matrix_eligible===true && r.pilot===false && !r.failure,
   frames:fs.existsSync(r.frames) && fs.readFileSync(r.frames,'utf8').trim().split(/\r?\n/).length===r.frame_count+1,
   screenshot:fs.existsSync(path.join(dir,'scene.png')) && fs.statSync(path.join(dir,'scene.png')).size>100
  };
  if(run.scene==='laser3') {
   checks.casts=r.server_completed_casts===3 && r.server_laser_transitions?.length===3 && r.server_laser_transitions.every(t=>t.ended_ns>t.active_ns);
   checks.components=r.owned_after?.components?.length===3 && r.owned_after.components.every(c=>new Set(c.activeObserved).size===15);
   checks.tails=r.tails_finished===true && r.owned_after.playing===0 && r.owned_after.tails===0;
  }
  return {id,state:Object.values(checks).every(Boolean)?'complete':'blocked',checks};
 } catch(e) {return {id,state:'blocked',error:e.message};}
}
function plan() {return matrix.runs.map(run=>{
 const id=run.id==='N_laser3_1'?'N_laser3_1r2':run.id==='N_blank45_1'?'N_blank45_1f':run.id;
 const evidence=audit(run,id);
 return {run,id,...evidence,nextAttempt:evidence.state==='blocked'?(run.id==='N_blank45_1'?'N_blank45_1r3':id+'r3'):id};
});}
const require=createRequire('E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/package.json');
const sdk=async p=>import(pathToFileURL(require.resolve('@modelcontextprotocol/sdk/'+p)).href);
async function ideaSession() {
 const {Client}=await sdk('client/index.js'),{SSEClientTransport}=await sdk('client/sse.js');
 const client=new Client({name:'photon-matrix-runner',version:'1'});
 const transport=new SSEClientTransport(new URL('http://127.0.0.1:64342/stream'));
 try {await client.connect(transport);return {client,transport,tools:(await client.listTools()).tools};}
 catch(e){await client.close();await transport.close();throw e;}
}
function portOwner() {
 const text=execFileSync('powershell.exe',['-NoProfile','-Command',"(Get-NetTCPConnection -LocalPort 19877 -State Listen -ErrorAction SilentlyContinue).OwningProcess"],{encoding:'utf8'});
 return text.trim()?Number(text.trim()):null;
}
async function bridge(pid,dir) {
 const {Client}=await sdk('client/index.js'),{StdioClientTransport}=await sdk('client/stdio.js');
 const transport=new StdioClientTransport({command:process.execPath,args:[path.join(root,'lib/minecraft-mcp/bridge/dist/cli.js'),'mcp','--no-discover'],cwd:root,env:{...process.env,MC_MCP_PORT:'19877'},stderr:'pipe'});
 const client=new Client({name:'photon-matrix-capture',version:'1'});
 try {
  await client.connect(transport);
  const tools=(await client.listTools()).tools;
  for(const name of ['ping','get_minecraft_status','screenshot_to_file']) if(!tools.some(t=>t.name===name)) throw Error('Missing bridge tool '+name);
  const deadline=Date.now()+120000;
  while(Date.now()<deadline) {
   if(portOwner()!==pid) throw Error('Instance changed');
   await client.callTool({name:'ping',arguments:{}},undefined,{timeout:10000});
   const status=await client.callTool({name:'get_minecraft_status',arguments:{}},undefined,{timeout:10000});
   // Fail closed until live status schema supplies explicit world/PID ownership.
   const data=status.structuredContent;
   if(data?.pid===pid && data?.port===19877 && data?.worldLoaded===true) {
    const shot=await client.callTool({name:'screenshot_to_file',arguments:{path:path.join(dir,'scene.png'),region:'game'}},undefined,{timeout:15000});
    if(shot.isError) throw Error('Screenshot failed');
    return;
   }
   await sleep(1000);
  }
  throw Error('World/PID evidence unavailable: status adapter needs schema review');
 } finally {await client.close();await transport.close();}
}
async function main() {
 const rows=plan();
 console.log(JSON.stringify({mode:args.has('--run')?'run':'dry-run',root,rows},null,2));
 if(args.has('--discover')) {
  const s=await ideaSession();
  try {console.log(JSON.stringify(s.tools.filter(t=>/run_configuration|stop/.test(t.name)),null,2));}
  finally {await s.client.close();await s.transport.close();}
 }
 if(!args.has('--run') || args.has('--dry-run')) return;
 // Launch remains locked until main-line review of live bridge status and PID cleanup.
 throw Error('Review gate: live bridge world/PID schema and owned-game shutdown not yet verified; no launch performed');
}
(args.has('--fresh-batch') ? import('./20261105-run-matrix-fresh.mjs').then(m=>m.fresh([...args])) : main()).catch(e=>{console.error(e.stack);process.exitCode=1;});
