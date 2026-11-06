import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const root='E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon';
const batch=process.env.PHOTON_MATRIX_BATCH||'20261105-run-matrix-script-r2';
if(!/^[a-zA-Z0-9_-]+$/.test(batch))throw Error('Invalid batch name');
const base=path.join(root,'build/agent-backups',batch);
const req=createRequire('E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/package.json');
const sdk=async p=>import(pathToFileURL(req.resolve('@modelcontextprotocol/sdk/client/'+p+'.js')).href);
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const read=p=>fs.readFileSync(p,'utf8');
const unpack=r=>r.structuredContent??JSON.parse(r.content.find(c=>c.type==='text').text);
const ps=code=>execFileSync('powershell.exe',['-NoProfile','-Command',code],{encoding:'utf8',timeout:30000}).trim();
const alive=pid=>{try{process.kill(pid,0);return true;}catch(e){if(e.code==='ESRCH')return false;throw e;}};
const owner=()=> {
 const output=execFileSync('netstat.exe',['-ano','-p','tcp'],{encoding:'utf8',timeout:10000});
 const pids=new Set(output.split(/\r?\n/).map(line=>line.trim().split(/\s+/)).filter(c=>c[0]==='TCP'&&/:19877$/.test(c[1])&&c[3]==='LISTENING').map(c=>Number(c[4])));
 if(pids.size>1)throw Error('Ambiguous 19877 ownership');
 return [...pids][0]||null;
};
function processInfo(pid) {return JSON.parse(ps('Get-CimInstance Win32_Process -Filter "ProcessId = '+pid+'" | Select-Object ProcessId,CreationDate,CommandLine | ConvertTo-Json -Compress')||'null');}
function identity(pid,world) {
 const info=processInfo(pid);
 if(!info?.CommandLine?.includes(world)||!info.CommandLine.replaceAll('\\','/').includes(root)) throw Error('PID identity mismatch');
 return info;
}
const selected=(process.env.PHOTON_MATRIX_SLOTS||'').split(',').filter(Boolean);
const matrix=JSON.parse(read(path.join(root,'tools/photon/performance/20261105-run-matrix.json'))).runs.filter(r=>!selected.length||selected.includes(r.id));
if(!matrix.length||selected.some(id=>!matrix.some(r=>r.id===id)))throw Error('Invalid selected slots');
function sourceFingerprint() {
 const entries=[],buffer=Buffer.alloc(64*1024);
 function visit(relative) {
  const absolute=path.join(root,relative);
  if(!fs.existsSync(absolute))return;
  const stat=fs.lstatSync(absolute);
  if(stat.isSymbolicLink())throw Error('源指纹不接受未核实的符号链接：'+relative);
  if(stat.isDirectory()) {for(const name of fs.readdirSync(absolute).sort())visit(path.join(relative,name));return;}
  if(!stat.isFile())return;
  const hash=createHash('sha256'),fd=fs.openSync(absolute,'r');
  try {let bytes;while((bytes=fs.readSync(fd,buffer,0,buffer.length,null))>0)hash.update(buffer.subarray(0,bytes));}
  finally {fs.closeSync(fd);}
  entries.push({path:relative.split(path.sep).join('/'),bytes:stat.size,sha256:hash.digest('hex')});
 }
 for(const relative of ['src/main','src/bench','src/generated','build/classes/java/main','build/classes/java/bench',
  'build/resources/main','build/resources/bench','lib/minecraft-mcp/bridge/dist','build.gradle.kts',
  'settings.gradle.kts','gradle.properties','gradle/libs.versions.toml','gradle.lockfile',
  'build/moddev/benchClientRunProgramArgs.txt','tools/photon/performance/20261105-run-matrix-runner.mjs',
  'tools/photon/performance/20261105-run-matrix-fresh.mjs','tools/photon/performance/20261105-run-matrix.json'])visit(relative);
 if(fs.existsSync(path.join(root,'lib')))for(const name of fs.readdirSync(path.join(root,'lib')).filter(n=>n.endsWith('.jar')).sort())visit(path.join('lib',name));
 for(const row of matrix) {
  const game=path.join('build/agent-backups/20261105-run-matrix',row.id,'build/modBench/runs/default/client');
  for(const relative of ['options.txt','config/iris.properties','config/spark/config.json'])visit(path.join(game,relative));
  if(row.shader)visit(path.join(game,'shaderpacks/ComplementaryReimagined_r5.9.3.zip'));
  visit('.idea/runConfigurations/Photon_Run_Matrix_20261105_'+row.id+'.xml');
 }
 entries.sort((a,b)=>a.path<b.path?-1:a.path>b.path?1:0);
 const hash=createHash('sha256');
 hash.update(JSON.stringify({schema:1,slots:matrix.map(r=>r.id),jfr:process.env.PHOTON_MATRIX_JFR==='true'}));
 for(const entry of entries)hash.update(entry.path+'\0'+entry.sha256+'\n');
 return {schema:1,sha256:hash.digest('hex'),files:entries.length,bytes:entries.reduce((n,e)=>n+e.bytes,0),entries};
}

export async function fresh(argv) {
 const sourcePath=path.join(base,'source.json');
 const saved=fs.existsSync(sourcePath)?JSON.parse(read(sourcePath)):null;
 if(argv.includes('--resume')&&(!saved?.head||!saved.fingerprint?.sha256||saved.variant!=='dirty_'+batch))throw Error('Missing/invalid same-batch source.json');
 const rows=matrix.map(r=>{
  const original={...r,world:'photon_run_'+batch+'_'+r.id,dir:path.join(base,r.id),name:'Photon '+batch+' '+r.id,slot:r.id};
  let row=original;
  for(let attempt=1;;attempt++){
   if(saved) {try{verify(row,saved.head,saved.variant);return {...row,skip:true};}catch{}}
   if(!fs.existsSync(path.join(row.dir,'launch.json'))&&!fs.existsSync(path.join(row.dir,'launch-intent.json'))&&!fs.existsSync(path.join(row.dir,'build/modBench/runs/default/client/logs/latest.log')))return row;
   const suffix='_attempt'+String(attempt+1).padStart(2,'0');
   row={...original,slot:r.id+suffix,dir:path.join(base,r.id+suffix),world:original.world+suffix,name:original.name+suffix};
  }
 });
 console.log(JSON.stringify({batch,mode:argv.includes('--run')?'run':'dry-run',rows:rows.map(r=>({id:r.id,world:r.world,directory:r.dir,existing:fs.existsSync(r.dir),skip:!!r.skip}))},null,2));
 if(!argv.includes('--run')&&!argv.includes('--discover')||argv.includes('--dry-run')&&!argv.includes('--discover')) {
  for(const r of rows.filter(r=>!r.skip)) {
   const template=read(path.join(root,'.idea/runConfigurations/Photon_Run_Matrix_20261105_'+r.id+'.xml'));
   const rendered=template.replace(/20261105-run-matrix\/[^/\s"]+/g,batch+'/'+r.slot).replace(/-DmodBench.client.worldId=\S+/,'-DmodBench.client.worldId='+r.world);
   const result=rendered.match(/-DmodBench.resultDirectory=([^\s"]+)/)?.[1]?.replace('$PROJECT_DIR$',root);
   const cwd=rendered.match(/name="WORKING_DIRECTORY" value="([^"]+)"/)?.[1]?.replace('$PROJECT_DIR$',root);
   if(path.resolve(result||'')!==path.resolve(r.dir,'results')||path.resolve(cwd||'')!==path.resolve(r.dir,'build/modBench/runs/default/client')||!rendered.includes('expectedDimensions=2538x1472')) throw Error('Dry-run config mismatch '+r.id);
  }
  console.log(rows.length+' rendered configurations: paths/dimensions checked');
  return;
 }
 if(!argv.includes('--discover')&&fs.existsSync(base)&&!argv.includes('--resume')) throw Error('Existing batch requires --resume for setup-only recovery');

 const {Client}=await sdk('index'),{StreamableHTTPClientTransport}=await sdk('streamableHttp');
 const transport=new StreamableHTTPClientTransport(new URL('http://127.0.0.1:64342/stream'));
 const client=new Client({name:'photon-fresh-matrix',version:'1'});
 const call=async(name,arguments_)=>{
  const r=await client.callTool({name,arguments:arguments_},undefined,{timeout:120000});
  if(r.isError) throw Error(name+': '+JSON.stringify(r));
  return r;
 };
 try {
  await client.connect(transport,{timeout:15000});
  const listed=(await client.listTools()).tools;
  if(argv.includes('--discover')) {console.log(JSON.stringify(listed.filter(t=>/run_configuration|create_new|backup_files|git_background/.test(t.name)),null,2));return;}
  for(const n of ['create_new_file','get_run_configurations','execute_run_configuration','git_background']) if(!listed.some(t=>t.name===n)) throw Error('Missing actual IDEA tool '+n);
  const git=async arguments_=>call('git_background',{projectPath:root,arguments:arguments_,maxOutputChars:20000});
  const headResult=await git(['rev-parse','HEAD']);
  const head=JSON.stringify(headResult).match(/[a-f0-9]{40}/)?.[0];
  if(!head) throw Error('HEAD unavailable');
  const dirty=unpack(await git(['status','--porcelain'])).output;
  const variant='dirty_'+batch;
  const sourceFile=path.join(base,'source.json');
  const fingerprint=sourceFingerprint();
  const sourceRecord=JSON.stringify({head,variant,dirty,fingerprint},null,2);
  if(saved&&(saved.head!==head||saved.variant!==variant||saved.fingerprint?.sha256!==fingerprint.sha256))throw Error('源文件或编译输出已变化，必须使用新批次');
  const assertSource=()=>{if(sourceFingerprint().sha256!==fingerprint.sha256)throw Error('采集期间源文件或编译输出发生变化，本局证据不完整');};
  async function create(relative,text) {
   text=text.replaceAll('\r\n','\n').replaceAll('\r','\n');
   if(!text.length)throw Error('Empty input '+relative);
   const target=path.join(root,relative);
   if(fs.existsSync(target)&&read(target).replaceAll('\r\n','\n')===text)return;
   if(fs.existsSync(target)&&fs.statSync(target).size!==0)throw Error('Creation conflict '+relative);
   if(!fs.existsSync(target))await call('create_new_file',{projectPath:root,pathInProject:relative,overwrite:false,text});
   if(fs.existsSync(target)&&fs.statSync(target).size===0) {
    const snapshot=unpack(await call('ide_read_file_snapshot',{projectPath:root,filePath:relative,source:'document'}));
    if(snapshot.text?.replaceAll('\r\n','\n')===text&&!snapshot.unsaved) { if(read(target).replaceAll('\r\n','\n')!==text) throw Error('Pending disk write '+relative); return; }
    if(snapshot.text!==''||snapshot.unsaved)throw Error('Empty Document conflict '+relative);
    const patch='*** Begin Patch\n*** Update File: '+relative.replaceAll('\\','/')+'\n@@\n'+text.split('\n').map(line=>'+'+line).join('\n')+'\n*** End Patch';
    const params={projectPath:root,patch,expectedContentHash:{[relative]:snapshot.documentContentHash}};
    const dry=unpack(await call('ide_apply_patch',{...params,dryRun:true}));
    if(dry.status!=='dry_run')throw Error('Empty insert dry-run failed '+relative);
    await call('ide_apply_patch',{...params,dryRun:false});
   }
   if(!fs.existsSync(path.join(root,relative))||read(path.join(root,relative)).replaceAll('\r\n','\n')!==text.replaceAll('\r\n','\n')) throw Error('Readback failed '+relative);
  }
  for(const r of rows.filter(r=>!r.skip)) {
   const old=path.join(root,'build/agent-backups/20261105-run-matrix',r.id,'build/modBench/runs/default/client');
   const cwd=path.join(r.dir,'build/modBench/runs/default/client');
   r.cwd=cwd;
   let irisText='';
   for(const rel of ['options.txt','config/iris.properties','config/spark/config.json']) {
    let text=read(path.join(old,rel));
    if(rel.endsWith('iris.properties')) text=text.replace(/enableShaders=.*/,'enableShaders='+r.shader).replace(/shaderPack=.*/,'shaderPack=build/agent-backups/20261105-run-matrix/'+r.id+'/build/modBench/runs/default/client/shaderpacks/ComplementaryReimagined_r5.9.3.zip');
    if(rel.endsWith('config.json')&&JSON.parse(text).backgroundProfiler!==false) throw Error('Spark background not disabled');
    if(rel==='config/iris.properties') irisText=text; else await create(path.relative(root,path.join(cwd,rel)),text);
   }
   if(r.shader && !fs.existsSync(path.join(cwd,'shaderpacks','build/agent-backups/20261105-run-matrix',r.id,'build/modBench/runs/default/client/shaderpacks/ComplementaryReimagined_r5.9.3.zip'))) {
    // Binary copy remains IDEA-owned, using the discovered backup/copy tool.
    const tool=listed.find(t=>t.name==='ide_backup_files');
    if(!tool) throw Error('IDEA shader binary copy unavailable');
    const source=path.relative(root,path.join(old,'shaderpacks/ComplementaryReimagined_r5.9.3.zip')).replaceAll('\\','/');
    const {createHash}=await import('node:crypto');
    const hash=createHash('sha256').update(fs.readFileSync(path.join(root,source))).digest('hex');
    const params={projectPath:root,sourcePaths:[source],expectedHashes:{[source]:hash},backupDirectory:path.relative(root,path.join(cwd,'shaderpacks'))};
    const preview=unpack(await call('ide_backup_files',{...params,dryRun:true}));
    if(preview.status!=='dry_run')throw Error('Shader copy dry-run failed');
    const copied=unpack(await call('ide_backup_files',{...params,dryRun:false}));
    if(!copied.files?.every(f=>f.backupPath&&fs.existsSync(f.backupPath)))throw Error('Shader copy failed');
    irisText=irisText.replace(/shaderPack=.*/,'shaderPack='+path.relative(path.join(cwd,'shaderpacks'),copied.files[0].backupPath).replaceAll('\\','/'));
   }
   await create(path.relative(root,path.join(cwd,'config/iris.properties')),irisText);
   let xml=read(path.join(root,'.idea/runConfigurations/Photon_Run_Matrix_20261105_'+r.id+'.xml'));
   xml=xml.replace(/name="Photon Run Matrix 20261105 [^"]+"/,'name="'+r.name+'"')
    .replace(/20261105-run-matrix\/[^/\s"]+/g,batch+'/'+r.slot)
    .replace(/-DmodBench.client.worldId=\S+/,'-DmodBench.client.worldId='+r.world)
    .replace(/-Dlobecorp.photon.run.batchId=\S+/,'-Dlobecorp.photon.run.batchId='+batch)
    .replace(/-Dlobecorp.photon.run.sourceCommit=\S+/,'-Dlobecorp.photon.run.sourceCommit='+head)
    .replace(/-Dlobecorp.photon.run.sourceVariant=\S+/,'-Dlobecorp.photon.run.sourceVariant='+variant);
   if(process.env.PHOTON_MATRIX_JFR==='true') xml=xml.replace('name="VM_PARAMETERS" value="','name="VM_PARAMETERS" value="-XX:StartFlightRecording=name=photon_diagnostic,settings=profile,dumponexit=true,filename='+path.join(r.dir,'runtime.jfr').split(path.sep).join('/')+' ');
   if(!xml.includes('-Dlobecorp.photon.run.expectedDimensions=2538x1472')||!xml.includes('-Dlobecorp.photon.bench.ownedStatistics=true')||!xml.includes('-DmodBench.jfr=false'))throw Error('Invalid rendered configuration');
   await create('.idea/runConfigurations/Photon_'+batch+'_'+r.slot+'.xml',xml);
  }
  let configs='';
  for(let attempt=0;attempt<20;attempt++) { configs=JSON.stringify(await call('get_run_configurations',{projectPath:root})); if(rows.every(r=>configs.includes(r.name)))break; await sleep(500); }
  for(const r of rows) if(!configs.includes(r.name)) throw Error('IDE has not discovered '+r.name);
  if(!fs.existsSync(sourceFile))fs.writeFileSync(sourceFile,sourceRecord,{flag:'wx'});
  for(const r of rows) {
   if(r.skip){console.log(r.id+' skipped: complete same-batch evidence');continue;}
   if(owner()) throw Error('Previous game still owns 19877');
   assertSource();
   console.log(r.id+' starting');
   let pid=null,owned=null;
   fs.writeFileSync(path.join(r.dir,'launch-intent.json'),JSON.stringify({world:r.world,cwd:r.cwd,at:new Date().toISOString()}),{flag:'wx'});
   try {
   const launched=await call('execute_run_configuration',{projectPath:root,configurationName:r.name,waitForExit:false,envs:{JAVA_TOOL_OPTIONS:'',JDK_JAVA_OPTIONS:'',_JAVA_OPTIONS:''}});
   fs.writeFileSync(path.join(r.dir,'launch.json'),JSON.stringify(launched,null,2),{flag:'wx'});
    const limit=Date.now()+180000;
    while(Date.now()<limit&&!pid){pid=owner();if(!pid)await sleep(1000);}
    if(!pid) throw Error('Game startup timeout');
    owned=identity(pid,r.world);
    await capture(r,pid,owned);
    const deadline=Date.now()+600000;
    while(Date.now()<deadline&&alive(pid)) await sleep(1000);
    if(alive(pid)) throw Error('Game exit timeout');
    assertSource();
    verify(r,head,variant);
    console.log(r.id+' passed');
   } catch(error) {
    console.error(r.id+' failed: '+error.stack);
    fs.writeFileSync(path.join(r.dir,'runner-failure.json'),JSON.stringify({error:error.stack,at:new Date().toISOString()},null,2));
    throw error;
   } finally {
    if(!owned){
     const candidates=JSON.parse(ps("Get-CimInstance Win32_Process -Filter \"Name = 'java.exe' OR Name = 'javaw.exe'\" | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress")||'[]');
     const matches=[candidates].flat().filter(x=>x?.CommandLine?.includes(r.world)&&x.CommandLine.replaceAll('\\','/').includes(root));
     if(matches.length>1)throw Error('Ambiguous startup cleanup identity');
     if(matches.length===1){pid=matches[0].ProcessId;owned=identity(pid,r.world);}
    }
    if(pid&&owned&&alive(pid)) {
     const current=identity(pid,r.world);
     if(current.CreationDate===owned.CreationDate) {
      ps('(Get-Process -Id '+pid+').CloseMainWindow() | Out-Null');
      const closing=Date.now()+15000;
      while(Date.now()<closing&&alive(pid))await sleep(500);
      if(alive(pid)&&identity(pid,r.world).CreationDate===owned.CreationDate)ps('Stop-Process -Id '+pid);
     }
    }
   }
  }
 } finally {await client.close();await transport.close();}
}
async function capture(r,pid,owned) {
 const {Client}=await sdk('index'),{StdioClientTransport}=await sdk('stdio');
 const t=new StdioClientTransport({command:process.execPath,args:[path.join(root,'lib/minecraft-mcp/bridge/dist/cli.js'),'mcp','--no-discover'],cwd:root,env:{...process.env,MC_MCP_PORT:'19877'},stderr:'pipe'});
 const c=new Client({name:'photon-shot',version:'1'});
 try {
  await c.connect(t);
  const tools=(await c.listTools()).tools;
  for(const name of ['ping','get_minecraft_status','screenshot_to_file','gui_control'])if(!tools.some(t=>t.name===name))throw Error('Missing '+name);
  const limit=Date.now()+150000;
  let phaseSeen=null;
  while(Date.now()<limit) {
   if(!alive(pid))throw Error('Game exited before screenshot');
   try {
    await c.callTool({name:'ping',arguments:{}},undefined,{timeout:5000});
    const s=unpack(await c.callTool({name:'get_minecraft_status',arguments:{}},undefined,{timeout:5000}));
    const log=fs.existsSync(path.join(r.cwd,'logs/latest.log'))?read(path.join(r.cwd,'logs/latest.log')):'';
    if(s.connected&&s.port===19877&&s.processAlive&&log.includes('event=world_open world='+r.world)&&log.split(/\r?\n/).some(line=>line.includes('Render thread')&&line.includes('MODBENCH event=run_start'))) {
     phaseSeen??=Date.now();
     if(Date.now()-phaseSeen>=7000) {
      identity(pid,r.world);
      const gui=unpack(await c.callTool({name:'gui_control',arguments:{action:'list'}},undefined,{timeout:5000}));
      fs.writeFileSync(path.join(r.dir,'gui-state.json'),JSON.stringify(gui,null,2));
      if(gui.error?.code!=='NO_SCREEN'&&gui.screen!==null&&gui.screenClass!==null&&gui.screenId!==null) throw Error('GUI world visibility not confirmed: '+JSON.stringify(gui));
      const shot=unpack(await c.callTool({name:'screenshot_to_file',arguments:{path:path.join(r.dir,'scene.png'),region:'game'}},undefined,{timeout:15000}));
      if(shot.sourceWidth!==2538||shot.sourceHeight!==1472||!shot.size) throw Error('Screenshot dimensions invalid');
      fs.writeFileSync(path.join(r.dir,'identity-ack.json'),JSON.stringify({pid,owned,world:r.world,shot,at:new Date().toISOString()},null,2),{flag:'wx'});
      return;
     }
    }
   } catch(e) {if(!alive(pid))throw e;}
   await sleep(500);
  }
  throw Error('World/run-start screenshot timeout');
 } finally {await c.close();await t.close();}
}
function verify(r,head,variant) {
 const result=path.join(r.dir,'results');
 const s=JSON.parse(read(path.join(result,'summary.json')));
 if(!s.scenarios?.length||!s.scenarios.every(x=>x.status==='PASSED'&&x.workloadCorrect))throw Error('Scenario failed');
 const folder=path.join(result,'artifacts/custom');
 const files=fs.readdirSync(folder).filter(n=>n.endsWith('_result.json'));
 if(files.length!==1)throw Error('Ambiguous result');
 const x=JSON.parse(read(path.join(folder,files[0])));
 if(x.batchId!==batch||x.runId!==r.id||x.sourceCommit!==head||x.sourceVariant!==variant||x.dimensions!=='2538x1472'||x.shader!==r.shader||!x.complete||!x.performance_matrix_eligible||x.failure)throw Error('Result identity/quality failure');
 if(read(x.frames).trim().split(/\r?\n/).length!==x.frame_count+1)throw Error('Incomplete raw frames');
 if(r.scene==='laser3'&&!(x.server_completed_casts===3&&x.server_laser_transitions.length===3&&x.server_laser_transitions.every(t=>t.ended_ns>t.active_ns)&&x.tails_finished&&x.owned_after.playing===0&&x.owned_after.tails===0&&x.owned_after.components.length===3&&x.owned_after.components.every(c=>new Set(c.activeObserved).size===15)))throw Error('Laser evidence failure');
 const ack=JSON.parse(read(path.join(r.dir,'identity-ack.json')));
 if(ack.world!==r.world||ack.shot?.sourceWidth!==2538||ack.shot?.sourceHeight!==1472||!ack.shot?.size||!fs.statSync(path.join(r.dir,'scene.png')).size)throw Error('Invalid screenshot identity');
}
