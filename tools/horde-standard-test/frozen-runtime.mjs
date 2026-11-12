import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';
const slash = s => s.replaceAll('\\', '/');
const digest = p => createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const inside = (root, p) => {const r=path.relative(root,p);return r==='' || (!r.startsWith('..')&&!path.isAbsolute(r));};
const save = (p,v) => fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
// CommandLineToArgv-style quote/backslash handling, without shell evaluation.
export function splitCommandLine(s) {
    const result=[];let i=0;
    while(i<s.length){while(/\s/.test(s[i]??'')&&i<s.length)i++;if(i>=s.length)break;let value='',quoted=false;
        while(i<s.length&&(quoted||! /\s/.test(s[i]))){let n=0;while(s[i]==='\\'){n++;i++;}if(s[i]==='"'){value+='\\'.repeat(Math.floor(n/2));if(n%2)value+='"';else quoted=!quoted;i++;}else{value+='\\'.repeat(n);if(i<s.length)value+=s[i++];}}
        if(quoted)throw Error('Unclosed launch quote');result.push(value);
    }return result;
}
export function createFrozenRuntime({projectRoot,ownershipFile,id,verifiedPathMappings=[]}) {
    const root=path.resolve(projectRoot);
    if(!/^[A-Za-z0-9_-]{1,80}$/.test(id))throw Error('Invalid snapshot id');
    const directory=path.join(root,'build/horde-standard-test/runtime-snapshots',id);
    if(fs.existsSync(directory))throw Error('Snapshot already exists');
    fs.mkdirSync(directory,{recursive:true});
    const manifest={schema:1,status:'creating',projectRoot:root,snapshotPath:directory,ownershipFile:path.resolve(ownershipFile),files:[],externalFiles:[],rewriteMapping:[]};
    try {
        const receiptPath=path.join(path.dirname(path.resolve(ownershipFile)),'completion.json');
        const receipt=JSON.parse(fs.readFileSync(receiptPath,'utf8'));
        if(!receipt.checks || Object.keys(receipt.checks).length<9 || !Object.values(receipt.checks).every(v=>v===true))throw Error('Ownership source has no passed completion audit');
        const owned=JSON.parse(fs.readFileSync(ownershipFile,'utf8')); const args=splitCommandLine(owned.CommandLine);const cpIndex=args.indexOf('-cp'),mainIndex=args.indexOf('net.neoforged.devlaunch.Main');
        if(cpIndex<1||mainIndex<cpIndex||!args[mainIndex+1]?.startsWith('@'))throw Error('Unsupported owned launch recipe');
        manifest.javaExe=path.resolve(args[0]);
        manifest.launchJvmArgs=args.slice(1,cpIndex).filter(arg=>!arg.startsWith('@')); 
        const rawClasspath=args[cpIndex+1].split(';');
        const mappingFile=typeof verifiedPathMappings==='string'?path.resolve(verifiedPathMappings):null;
        const mappings=mappingFile?JSON.parse(fs.readFileSync(mappingFile,'utf8')):verifiedPathMappings;
        if(!Array.isArray(mappings))throw Error('Verified mappings must be an array or JSON path');
        const seenMappings=new Set();
        for(const mapping of mappings){
            if(typeof mapping.from!=='string'||typeof mapping.to!=='string'||!rawClasspath.includes(mapping.from)||seenMappings.has(mapping.from))throw Error('Unknown/duplicate exact classpath mapping');
            seenMappings.add(mapping.from);
            const e=mapping.evidence;
            if(!e||!Number.isSafeInteger(e.line)||e.line<1||digest(e.path)!==e.logSHA256||digest(mapping.to)!==e.jarSHA256)throw Error('Unverified mapping evidence/hash');
            const line=fs.readFileSync(e.path,'utf8').split(/\r?\n/)[e.line-1];
            if(!line||!slash(line).includes(slash(mapping.to)))throw Error('Verified mapping target missing from evidence line');
        }
        manifest.verifiedPathMappings={file:mappingFile,fileSHA256:mappingFile?digest(mappingFile):null,entries:mappings};
        const cp=rawClasspath.map(p=>path.resolve(mappings.find(mapping=>mapping.from===p)?.to??p));
        const base=path.join(root,'build/moddev/benchClientRunVmArgs.txt'),program=path.resolve(args[mainIndex+1].slice(1));
        const relative = p => slash(path.relative(root,p));
        const forbidden = p => {
            const r=relative(p);
            return !inside(root,p) || r==='' || /(^|\/)(?:\.git|saves)(\/|$)/i.test(r)
                || /^(?:run|build\/horde-standard-test)(\/|$)/i.test(r)
                || /(^|\/)runtime-snapshots(\/|$)/i.test(r);
        };
        const outputDirectory = p => /^build\/(?:classes\/java|resources)\/[^/]+$/.test(relative(p));
        const generatedFile = p => /^build\/moddev\/.+\.(?:txt|args|xml|properties|json|classpath|cfg|list)$/i.test(relative(p));
        const projectJar = p => /^(?:lib|build)\/.+\.jar$/i.test(relative(p));
        const cpEntries=new Set(cp.filter(p=>inside(root,p)));
        for(const p of cpEntries) if(forbidden(p)||!(outputDirectory(p)||projectJar(p)))throw Error('Disallowed project classpath entry: '+p);
        if(forbidden(program)||!generatedFile(program))throw Error('Program args outside generated launch files');
        const optionalMissing = new Set([...cpEntries].filter(p=>outputDirectory(p)&&!fs.existsSync(p)));
        manifest.emptyProjectEntries=[...optionalMissing];
        const entries=new Set(cpEntries);entries.add(base);entries.add(program);
        manifest.discoveredReferences=[];manifest.rejectedReferences=[];manifest.textRewrites=[];
        // Only generated launch/config text can discover references. Resource JSON is rewritten, never traversed for references.
        let changed=true;
        while(changed){changed=false;for(const entry of [...entries]){
            if(!fs.existsSync(entry)){if(optionalMissing.has(entry))continue;throw Error('Missing project launch entry: '+entry);}
            if(!generatedFile(entry)||!fs.lstatSync(entry).isFile())continue;
            const text=slash(fs.readFileSync(entry,'utf8').replaceAll('\\\\','\\'));
            const re=new RegExp(slash(root).replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'[^;"\\r\\n\\s<>]*','gi');
            for(const hit of text.matchAll(re)){
                const p=path.resolve(hit[0]);
                if(entries.has(p))continue;
                if(forbidden(p)||!(generatedFile(p)||projectJar(p))){
                    manifest.rejectedReferences.push({source:entry,reference:p,reason:'Not an allowed generated launch file/project jar; not traversed or copied (dynamic game/results paths are overridden)'});continue;
                }
                if(!fs.existsSync(p)||!fs.lstatSync(p).isFile())throw Error('Missing/non-file required generated reference: '+p);
                entries.add(p);manifest.discoveredReferences.push({source:entry,path:p});changed=true;
            }
        }}
        const inventory=()=>{
            const all=new Map();const visit=p=>{if(!fs.existsSync(p)&&optionalMissing.has(p)){all.set(p,null);return;}if(forbidden(p))throw Error('Forbidden inventory path: '+p);const st=fs.lstatSync(p);if(st.isSymbolicLink())throw Error('Symlink not supported: '+p);if(st.isDirectory()){all.set(p,null);for(const name of fs.readdirSync(p).sort())visit(path.join(p,name));}else if(st.isFile())all.set(p,digest(p));else throw Error('Not a regular file: '+p);};
            for(const p of entries)visit(p);return [...all].sort(([a],[b])=>a.localeCompare(b));
        };
        const before=inventory();
        const target=p=>path.join(directory,path.relative(root,p));
        for(const [source,hash] of before){const dest=target(source);if(hash===null)fs.mkdirSync(dest,{recursive:true});else{fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(source,dest);if(digest(dest)!==hash)throw Error('Source changed while copying: '+source);}}
        if(JSON.stringify(before)!==JSON.stringify(inventory()))throw Error('Source bytes/inventory changed during snapshot');
        // Rewrite copied text only, never originals. Include doubled Windows separators used in Java argfiles.
        manifest.rewriteMapping=[{from:root,to:directory}];
        for(const [source,hash] of before){if(hash===null||! /\.(txt|args|xml|properties|json)$/i.test(source))continue;
            const dest=target(source);let text=fs.readFileSync(dest,'utf8');
            const prefixes = [...new Set([root.replaceAll('\\','\\\\'),root,slash(root)])].sort((a,b)=>b.length-a.length);
            const pattern = new RegExp(prefixes.map(p=>p.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')).join('|'),'gi');
            const original=text;let replacements=0;
            text = text.replace(pattern,()=>{replacements++;return slash(directory);});
            if(replacements)manifest.textRewrites.push({source,destination:dest,replacements,sourceSHA256:createHash('sha256').update(original).digest('hex'),rewrittenSHA256:createHash('sha256').update(text).digest('hex')});
            fs.writeFileSync(dest,text);
        }
        manifest.classpath=cp.map(p=>inside(root,p)?target(p):p);
        manifest.baseVmArgsFile=target(base);manifest.programArgsFile=target(program);
        manifest.modFolders=['build/classes/java/main','build/resources/main','build/classes/java/bench','build/resources/bench'].map(p=>'lobecorp%%'+slash(target(path.join(root,p)))).join(';');
        manifest.recipe={mainClass:args[mainIndex],originalCommandLine:owned.CommandLine,ownershipSHA256:digest(ownershipFile)};
        manifest.files=before.filter(([,h])=>h!==null).map(([p,h])=>({path:target(p),source:p,sourceSHA256:h,sha256:digest(target(p))}));
        manifest.directories=before.filter(([,h])=>h===null).map(([p])=>target(p));
        for(const p of [...new Set([manifest.javaExe,...cp.filter(p=>!inside(root,p))])]){
            if(!fs.statSync(p).isFile())throw Error('External classpath must be an immutable file: '+p);
            if(p!==manifest.javaExe&&! /[\\/]caches[\\/].*[\\/][a-f0-9]{32,64}[\\/].*\.jar$/i.test(p))throw Error('External jar not content-addressed Gradle artifact: '+p);
            manifest.externalFiles.push({path:p,sha256:digest(p)});
        }
        if(JSON.stringify(before)!==JSON.stringify(inventory()))throw Error('Source changed before snapshot commit');
        manifest.status='complete';manifest.snapshotRoot=directory;manifest.javaExecutable=manifest.javaExe;manifest.runtimeFingerprint=createHash('sha256').update(JSON.stringify(manifest)).digest('hex');save(path.join(directory,'manifest.json'),manifest);return loadFrozenRuntime(directory,root);
    }catch(error){manifest.status='failed';manifest.failure=error.stack;save(path.join(directory,'failed-manifest.json'),manifest);throw error;}
}
export function loadFrozenRuntime(snapshotPath,projectRoot) {
    const directory=path.resolve(snapshotPath),root=path.resolve(projectRoot);
    if(!inside(path.join(root,'build/horde-standard-test/runtime-snapshots'),directory))throw Error('Snapshot outside fixed root');
    const m=JSON.parse(fs.readFileSync(path.join(directory,'manifest.json'),'utf8'));
    if(m.status!=='complete'||path.resolve(m.projectRoot)!==root||path.resolve(m.snapshotPath)!==directory)throw Error('Incomplete/wrong-root snapshot');
    const fingerprintPayload={...m};delete fingerprintPayload.runtimeFingerprint;
    if(createHash('sha256').update(JSON.stringify(fingerprintPayload)).digest('hex')!==m.runtimeFingerprint)throw Error('Manifest fingerprint mismatch');
    for(const f of [...m.files,...m.externalFiles])if(digest(f.path)!==f.sha256)throw Error('Frozen artifact hash mismatch: '+f.path);
    for(const p of m.classpath)if(inside(root,p)&&!inside(directory,p))throw Error('Mutable project classpath: '+p);
    for(const p of [m.baseVmArgsFile,m.programArgsFile,...(m.directories??[])])if(!inside(directory,p)||!fs.existsSync(p))throw Error('Missing/outside frozen entry: '+p);
    const rewriteText = text => {
        const prefixes=[...new Set([root.replaceAll('\\','\\\\'),root,slash(root)])].sort((a,b)=>b.length-a.length);
        const pattern=new RegExp(prefixes.map(p=>p.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')).join('|'),'gi');
        return text.replace(pattern,()=>slash(directory));
    };
    return {...m,snapshotRoot:directory,javaExecutable:m.javaExecutable??m.javaExe,rewriteText};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
    const a=Object.fromEntries(process.argv.slice(2).reduce((r,v,i,all)=>i%2?r:[...r,[v,all[i+1]]],[]));
    const runtime = createFrozenRuntime({projectRoot:a['--project-root']??process.cwd(),ownershipFile:a['--ownership'],id:a['--id'],verifiedPathMappings:a['--verified-path-mappings']??[]});
    console.log(JSON.stringify({snapshotRoot: runtime.snapshotRoot, runtimeFingerprint: runtime.runtimeFingerprint, classpathEntries: runtime.classpath.length, files: runtime.files.length}));
}
