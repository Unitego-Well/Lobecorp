import fs from 'node:fs';
import {auditLog, completedScenario} from './log-audit.mjs';
import {verifyCompletedOwner} from './process-identity.mjs';
import {trackChild, bindVerifiedChild, waitChildExit, verifyCompletedChild} from './child-lifecycle.mjs';
const childRecords = new Map();
import {loadFrozenRuntime, splitCommandLine} from './frozen-runtime.mjs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {execFileSync, spawn} from 'node:child_process';
import {createHash} from 'node:crypto';

const port = 19878;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const slash = value => value.replaceAll('\\', '/');
const read = file => fs.readFileSync(file, 'utf8');
const json = file => JSON.parse(read(file));
const write = (file, value) => fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n', {flag: 'wx'});
const ps = code => execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', '[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); $OutputEncoding = [Console]::OutputEncoding; ' + code], {encoding: 'utf8', windowsHide: true, timeout: 30000}).trim();
const info = pid => JSON.parse(ps(`Get-CimInstance Win32_Process -Filter "ProcessId = ${pid}" | Select-Object ProcessId,CreationDate,CommandLine | ConvertTo-Json -Compress`) || 'null');
const alive = pid => {
    try {process.kill(pid, 0); return true;}
    catch (error) {if (error.code === 'ESRCH') return false; throw error;}
};
const allProcesses = () => [JSON.parse(ps("Get-CimInstance Win32_Process -Filter \"Name = 'java.exe' OR Name = 'javaw.exe'\" | Select-Object ProcessId,CreationDate,CommandLine | ConvertTo-Json -Compress") || '[]')].flat();
const matches = (value, row) => {
    const command = slash(value?.CommandLine || '');
    const gameDir = /--gameDir\s+(?:"([^"]+)"|(\S+))/.exec(command);
    return command.includes(slash(row.root)) && gameDir && (gameDir[1] || gameDir[2]) === slash(cwd(row));
};
function owner(row) {
    const found = allProcesses().filter(value => matches(value, row));
    if (found.length > 1) throw Error('Ambiguous owned game PID');
    return found[0];
}
function portOwner() {
    const lines = execFileSync('netstat.exe', ['-ano', '-p', 'tcp'], {encoding: 'utf8', windowsHide: true, timeout: 10000}).split(/\r?\n/);
    const found = new Set(lines.map(line => line.trim().split(/\s+/)).filter(c => c[0] === 'TCP' && c[1].endsWith(':' + port) && c[3] === 'LISTENING').map(c => Number(c[4])));
    if (found.size > 1) throw Error('Ambiguous port ownership');
    return [...found][0];
}
function assertIdentity(row, owned) {
    const current = info(owned.ProcessId);
    if (!matches(current, row) || current.CreationDate !== owned.CreationDate) throw Error('PID identity changed');
    return current;
}
async function session(row) {
    const require = createRequire(path.join(row.root, 'tools/lobecorp-normify/package.json'));
    const sdk = async name => import(pathToFileURL(require.resolve('@modelcontextprotocol/sdk/client/' + name + '.js')).href);
    const {Client} = await sdk('index');
    const {StreamableHTTPClientTransport} = await sdk('streamableHttp');
    const transport = new StreamableHTTPClientTransport(new URL('http://127.0.0.1:64342/stream'));
    const client = new Client({name: 'horde-standard-test', version: '1'});
    await client.connect(transport);
    return {client, close: async () => {try {await client.close();} finally {await transport.close();}},
        call: async (name, args) => {
            const result = await client.callTool({name, arguments: args}, undefined, {timeout: 60000});
            if (result.isError) throw Error(name + ': ' + JSON.stringify(result));
            return result.structuredContent ?? JSON.parse(result.content.filter(c => c.type === 'text').map(c => c.text).join('\n'));
        }};
}
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
const quote = value => '"' + slash(value) + '"';
const cwd = row => path.join(row.dir, 'game');
const results = row => path.join(row.dir, 'results');
const reportPath = row => path.join(results(row), 'artifacts/horde-encounter-performance.json');
const listFiles = dir => !fs.existsSync(dir) ? [] : fs.readdirSync(dir, {withFileTypes: true}).flatMap(e => e.isDirectory() ? listFiles(path.join(dir, e.name)) : [path.join(dir, e.name)]);
const environmentRoot = row => path.resolve(row.root, process.env.HORDE_ENVIRONMENT_SOURCE || 'build/modBench/runs/default/client');

const observers = new Map();
const observerEnabled = row => row.properties?.['lobecorp.horde.bench.windowObserver'] === true;
function startObserver(row, owned) {
    if (!observerEnabled(row) || observers.has(row.dir)) return;
    const script = path.join(row.root, 'tools/horde-standard-test/window-observer.py');
    const state = {child: null, error: null};
    observers.set(row.dir, state);
    const receipt = {at: new Date().toISOString(), gamePid: owned.ProcessId,
        script, sha256: createHash('sha256').update(fs.readFileSync(script)).digest('hex')};
    try {
        assertIdentity(row, owned);
        const child = spawn(process.env.HORDE_PYTHON || 'python', ['-u', script, '--pid', String(owned.ProcessId),
            '--output', path.join(row.dir, 'window-events.jsonl')], {windowsHide: true, stdio: 'ignore'});
        state.child = child;
        child.once('spawn', () => write(path.join(row.dir, 'window-observer-start.json'), {...receipt, observerPid: child.pid, status: 'spawned; readiness in window-events.jsonl'}));
        child.once('error', error => {state.error = error.message; fs.appendFileSync(path.join(row.dir, 'window-events.jsonl'), JSON.stringify({utc: new Date().toISOString(), event: 'gate-failed', error: error.message}) + '\n');});
    } catch (error) {
        state.error = error.message;
        write(path.join(row.dir, 'window-observer-start.json'), {...receipt, status: 'gate-failed', error: error.message});
    }
}
async function stopObserver(row) {
    const enabled = observerEnabled(row);
    const state = observers.get(row.dir);
    const child = state?.child;
    let forced = false, error = state?.error ?? null;
    const running = () => child && child.pid && child.exitCode === null && child.signalCode === null;
    if (running()) {
        await Promise.race([new Promise(resolve => child.once('exit', resolve)), sleep(2000)]);
        if (running()) {
            forced = true;
            try {child.kill();} catch (failure) {error = failure.message;}
            await Promise.race([new Promise(resolve => child.once('exit', resolve)), sleep(2000)]);
        }
    }
    const eventsPath = path.join(row.dir, 'window-events.jsonl');
    let events = [];
    try {
        if (fs.existsSync(eventsPath)) events = read(eventsPath).split(/\r?\n/).filter(Boolean).map(JSON.parse);
    } catch (failure) {error ??= failure.message;}
    error ??= events.find(event => event.event === 'gate-failed')?.error ?? null;
    if (enabled && !state) error ??= 'Observer was not started';
    const exited = !!child?.pid && !running();
    const complete = enabled && exited && !forced && !error && child.exitCode === 0
        && events.some(event => event.event === 'observer-ready')
        && events.some(event => event.event === 'process-exited')
        && events.some(event => event.event === 'observer-stopped');
    observers.delete(row.dir);
    return {enabled, pid: child?.pid ?? null, complete, exited, forced,
        eventsPath, eventsCount: events.length, error};
}

export async function preflight(row) {
    const frozen = row.runtimeSnapshot ? loadFrozenRuntime(row.runtimeSnapshot, row.root) : null;
    if (!frozen) {
        for (const relative of ['build/moddev/benchClientRunProgramArgs.txt', 'build/moddev/benchClientRunVmArgs.txt',
            'build/classes/java/bench/org/unitego/lobecorp/client/bench/HordeEncounterPerformanceScenario.class']) {
            if (!fs.existsSync(path.join(row.root, relative))) throw Error('Prepare/compile required: ' + relative);
        }
    }
    const inherited = read(frozen?.baseVmArgsFile ?? path.join(row.root, 'build/moddev/benchClientRunVmArgs.txt')) + '\n'
        + [process.env.JAVA_TOOL_OPTIONS, process.env.JDK_JAVA_OPTIONS, process.env._JAVA_OPTIONS].filter(Boolean).join(' ');
    if (inherited.includes('StartFlightRecording')) throw Error('Inherited JFR startup recording would create duplicate recording');
    if (portOwner()) throw Error('19878 occupied; existing instance left untouched');
    // Port availability is only isolation evidence, never GPU exclusivity evidence.
    if (allProcesses().some(value => /net\.neoforged\.devlaunch\.Main|net\.minecraft\.client\.main\.Main/.test(value.CommandLine || ''))) throw Error('Another game is running; coordinate exclusive performance sampling first');
    if (!frozen) {
        const guard = read(path.join(row.root, 'src/bench/java/org/unitego/lobecorp/mixin/bench/ClientAutomationNoScreenshotMixin.java'));
        const plugin = read(path.join(row.root, 'src/bench/java/org/unitego/lobecorp/bench/plugin/BenchMixinPlugin.java'));
        if (!guard.includes(row.scenario) || !plugin.includes(row.scenario)) throw Error('No-screenshot framework guard not wired for ' + row.scenario + '; main line must enable the existing mixin and its plugin selection before launch');
    }
    const env = environmentRoot(row);
    if (!fs.existsSync(path.join(env, 'options.txt'))) throw Error('Missing environment options: ' + env);
    if (row.shader) {
        const iris = read(path.join(env, 'config/iris.properties'));
        const pack = /^shaderPack=(.*)$/m.exec(iris)?.[1]?.trim();
        if (!pack || !fs.existsSync(path.join(env, 'shaderpacks', pack))) throw Error('Shader pack template missing');
    }
    if (frozen) return;
    const s = await session(row);
    try {
        const configs = await s.call('get_run_configurations', {projectPath: row.root});
        if (!configs.configurations?.some(c => c.name === 'Photon Bench Debug')) throw Error('Missing Photon Bench Debug template');
    } finally {await s.close();}
}

export async function launch(row) {
    const frozen = row.runtimeSnapshot ? loadFrozenRuntime(row.runtimeSnapshot, row.root) : null;
    const game = cwd(row);
    fs.mkdirSync(path.join(game, 'config/spark'), {recursive: true});
    const env = environmentRoot(row);
    fs.copyFileSync(path.join(env, 'options.txt'), path.join(game, 'options.txt'));
    const clientConfig = path.join(env, 'config/lobecorp-client.toml');
    if (fs.existsSync(clientConfig)) {
        fs.copyFileSync(clientConfig, path.join(game, 'config/lobecorp-client.toml'));
    }
    const fmlSource = path.join(env, 'config/fml.toml');
    let fml = fs.existsSync(fmlSource) ? read(fmlSource) : '';
    const mode = row.properties['lobecorp.developmentWindowTestMode'];
    if (mode === 'native') {
        fml = fml.replace(/^earlyWindowControl\s*=.*$/m, 'earlyWindowControl = false');
        if (!/^earlyWindowControl\s*=/m.test(fml)) fml += '\nearlyWindowControl = false\n';
    }
    if (fml) fs.writeFileSync(path.join(game, 'config/fml.toml'), fml, {flag: 'wx'});

    const sparkFile = path.join(env, 'config/spark/config.json');
    const spark = fs.existsSync(sparkFile) ? json(sparkFile) : {};
    spark.backgroundProfiler = false;
    write(path.join(game, 'config/spark/config.json'), spark);
    const irisFile = path.join(env, 'config/iris.properties');
    let iris = fs.existsSync(irisFile) ? read(irisFile) : 'enableShaders=false\nshaderPack=\n';
    iris = iris.replace(/^enableShaders=.*$/m, 'enableShaders=' + row.shader);
    if (!/^enableShaders=/m.test(iris)) iris += '\nenableShaders=' + row.shader + '\n';
    fs.writeFileSync(path.join(game, 'config/iris.properties'), iris, {flag: 'wx'});
    if (row.shader) {
        const pack = /^shaderPack=(.*)$/m.exec(iris)[1].trim();
        fs.mkdirSync(path.join(game, 'shaderpacks'), {recursive: true});
        fs.cpSync(path.join(env, 'shaderpacks', pack), path.join(game, 'shaderpacks', pack), {recursive: true});
    }
    write(path.join(row.dir, 'environment.json'), {source: env, shader: row.shader, port,
        files: listFiles(game).map(file => ({path: path.relative(game, file), sha256: createHash('sha256').update(fs.readFileSync(file)).digest('hex')})),
        gpuExclusivity: 'game-process inventory only; external GPU consumers unverified'});
    const properties = {...row.properties, 'mcp.port': port, 'modBench.phaseTimeoutTicks': 12000,
        'modBench.client.autoWorld': true, 'modBench.client.worldPreset': 'flat',
        'modBench.client.worldId': row.worldId, 'modBench.resultDirectory': slash(results(row))};
    const folders = frozen?.modFolders ?? ['build/classes/java/main', 'build/resources/main', 'build/classes/java/bench', 'build/resources/bench'].map(p => 'lobecorp%%' + slash(path.join(row.root, p))).join(';');
    // Keep all VM options opaque to IDEA's -D extraction/reordering.
    // The generated file contains no nested @file and no inherited conflicting keys.
    const vmFile = path.join(row.dir, 'launch-vm.args');
    const overridden = new Set([...Object.keys(properties), 'fml.modFolders']);
    const inheritedLines = read(frozen?.baseVmArgsFile ?? path.join(row.root, 'build/moddev/benchClientRunVmArgs.txt')).split(/\r?\n/);
    const retained = inheritedLines.filter(line => {
        const value = line.trim();
        if (value.startsWith('@')) throw Error('Nested VM argument file is not allowed');
        if (/^-XX:(StartFlightRecording|FlightRecorderOptions)=/.test(value)) return false;
        const property = /^-D([^=\s]+)(?:=|$)/.exec(value);
        return !property || !overridden.has(property[1]);
    });
    for (const arg of frozen?.launchJvmArgs ?? []) {
        if (/^-XX:(StartFlightRecording|FlightRecorderOptions)=/.test(arg)) continue;
        const property = /^-D([^=\s]+)(?:=|$)/.exec(arg);
        if (property && overridden.has(property[1])) continue;
        retained.push(/\s/.test(arg) ? quote(slash(arg)) : slash(arg));
    }
    const javaOptions = [...row.vmFlags.map(slash), '-Dfml.modFolders=' + folders,
        ...Object.entries(properties).map(([key, value]) => '-D' + key + '=' + slash(String(value)))];
    if (javaOptions.filter(arg => arg.startsWith('-XX:StartFlightRecording=')).length !== 1) throw Error('Exactly one startup JFR required');
    // Only this run's child environment receives the final JVM properties and JFR.
    const childJavaOptions = javaOptions.map(arg => /\s/.test(arg) ? quote(arg) : arg).join(' ');
    const childEnvs = {MC_MCP_PORT: '19878', _JAVA_OPTIONS: childJavaOptions};
    fs.writeFileSync(vmFile, retained.join('\n') + '\n', {flag: 'wx'});
    write(path.join(row.dir, 'vm-arguments.json'), {vmFile, sha256: createHash('sha256').update(read(vmFile)).digest('hex'),
        properties, childEnvs, startupRecordings: 1});
    if (frozen) {
        const args = ['@' + vmFile, '-cp', frozen.classpath.join(';'), 'net.neoforged.devlaunch.Main',
            '@' + frozen.programArgsFile, '--mixin', 'META-INF/lobecorp.bench.mixins.json', '--gameDir', game];
        if (portOwner()) throw Error('Port occupied before frozen launch');
        write(path.join(row.dir, 'launch-request.json'), {backend: 'node-spawn-frozen', runtimeSnapshot: row.runtimeSnapshot, javaExe: frozen.javaExe, args, at: new Date().toISOString()});
        const log = fs.openSync(path.join(row.dir, 'java-launch.log'), 'wx');
        let child;
        try {child = spawn(frozen.javaExecutable, args, {cwd: game, windowsHide: true, stdio: ['ignore', log, log], env: {...process.env, ...childEnvs}});}
        finally {fs.closeSync(log);}
        const childRecord = trackChild(child, {runId: row.runId, runtimeFingerprint: frozen.runtimeFingerprint,
            onError: error => write(path.join(row.dir, 'spawn-error.json'), error),
            onExit: exit => write(path.join(row.dir, 'spawn-exit.json'), {...exit, at: new Date().toISOString()})});
        childRecord.javaExecutable = frozen.javaExecutable;
        childRecords.set(row.runId, childRecord);
        write(path.join(row.dir, 'spawn-receipt.json'), {pid: child.pid ?? null, javaExe: frozen.javaExe, args, cwd: game, runtimeSnapshot: row.runtimeSnapshot});
        const limit = Date.now() + 240000;
        while (Date.now() < limit) {
            if (childRecord.error) throw Error('Frozen spawn error: ' + childRecord.error.code);
            const owned = owner(row);
            if (owned) {
                if (owned.ProcessId !== child.pid) throw Error('Frozen spawn PID differs from CIM ownership');
                const observed = splitCommandLine(owned.CommandLine), ci = observed.indexOf('-cp');
                if (ci < 0 || JSON.stringify(observed[ci + 1].split(';').map(slash)) !== JSON.stringify(frozen.classpath.map(slash))) throw Error('Frozen observed classpath mismatch');
                if (!observed.includes('@' + vmFile) && !observed.includes('@' + slash(vmFile))) throw Error('Frozen VM file not observed');
                startObserver(row, owned);
                if (portOwner() === owned.ProcessId) {
                    bindVerifiedChild(childRecord, {owned, runId: row.runId, runtimeFingerprint: frozen.runtimeFingerprint, classpathVerified: true, portVerified: true});
                    write(path.join(row.dir, 'runtime-isolation.json'), {snapshotRoot: frozen.snapshotRoot, runtimeFingerprint: frozen.runtimeFingerprint, classpathVerified: true, mutableProjectEntries: []});
                    write(path.join(row.dir, 'vmfile-observation.json'), {references: 1, duplicated: false, backend: 'node-spawn-frozen', classpathVerified: true, runtimeSnapshot: row.runtimeSnapshot});
                    return owned;
                }
            }
            if (childRecord.exit) throw Error('Frozen JVM exited before MCP ownership ready');
            await sleep(500);
        }
        throw Error('Frozen owned PID/19878 startup timeout');
    }
    const vm = '@' + quote(vmFile);
    const name = 'Horde Standard ' + row.runId;
    const xml = '<component name="ProjectRunConfigurationManager"><configuration default="false" name="' + escape(name)
        + '" type="Application" factoryName="Application"><option name="MAIN_CLASS_NAME" value="net.neoforged.devlaunch.Main"/>'
        + '<module name="Lobecorp.photonBenchClient"/><option name="VM_PARAMETERS" value="' + escape(vm)
        + '"/><option name="PROGRAM_PARAMETERS" value="' + escape('@' + quote(path.join(row.root, 'build/moddev/benchClientRunProgramArgs.txt')) + ' --mixin META-INF/lobecorp.bench.mixins.json --gameDir ' + quote(game))
        + '"/><option name="WORKING_DIRECTORY" value="' + escape(slash(game)) + '"/><envs><env name="MC_MCP_PORT" value="19878"/><env name="_JAVA_OPTIONS" value="' + escape(childJavaOptions) + '"/></envs><method v="2"><option name="Make" enabled="false"/></method></configuration></component>\n';
    const configFile = path.join(row.root, '.idea/runConfigurations/Horde_Standard_' + row.runId + '.xml');
    fs.writeFileSync(configFile, xml, {flag: 'wx'});
    write(path.join(row.dir, 'configuration-created.json'), {configFile, sha256: createHash('sha256').update(xml).digest('hex'), runId: row.runId});
    const s = await session(row);
    try {
        const deadline = Date.now() + 30000;
        let config;
        while (Date.now() < deadline) {
            config = (await s.call('get_run_configurations', {projectPath: row.root})).configurations?.find(c => c.name === name);
            if (config) break;
            await sleep(500);
        }
        if (!config) throw Error('IDE did not discover attempt configuration');
        if (portOwner()) throw Error('Port occupied before launch');
        write(path.join(row.dir, 'launch-request.json'), {configuration: name, configFile, at: new Date().toISOString()});
        write(path.join(row.dir, 'configuration-launch-submitted.json'), {runId: row.runId});
        const launched = await s.call('execute_run_configuration', {projectPath: row.root, configurationName: name, waitForExit: false,
            ...(config.supportsDynamicLaunchOverrides ? {workingDirectory: game, envs: childEnvs} : {})});
        write(path.join(row.dir, 'idea-launch.json'), launched);
        if (launched.exitCode != null && launched.exitCode !== 0) throw Error('IDE launch failed');
        const limit = Date.now() + 240000;
        while (Date.now() < limit) {
            const owned = owner(row);
            if (owned) {
                const command = slash(owned.CommandLine);
                const occurrences = command.split(slash(vmFile)).length - 1;
                if (occurrences < 1 || command.includes('benchClientRunVmArgs.txt') || command.includes('-XX:StartFlightRecording='))
                    throw Error('IDE injected original VM file or command-line JFR; refusing this attempt');
                const commandProperties = [...command.matchAll(/(?:^|\s)-D([^=\s]+)=([^\s]*)/g)];
                if (commandProperties.some(match => overridden.has(match[1])))
                    throw Error('IDE injected conflicting command-line override property');
                startObserver(row, owned);
                if (portOwner() === owned.ProcessId) {
                    write(path.join(row.dir, 'vmfile-observation.json'), {references: occurrences,
                        duplicated: occurrences > 1, policy: 'sanitized base flags may repeat; overrides and JFR are child environment only'});
                    return owned;
                }
            }
            await sleep(500);
        }
        throw Error('Owned PID/19878 startup timeout');
    } finally {await s.close();}
}

function attemptLogAudit(row, final = false) {
    const load = file => {try {return json(file);} catch {return null;}};
    const logFile = path.join(cwd(row), 'logs/latest.log');
    if (!fs.existsSync(logFile)) return {passed: false, blockers: [{reason: 'Isolated log missing'}], pending: [], shutdownLogExceptions: []};
    return auditLog(read(logFile), {summary: load(path.join(results(row), 'summary.json')), report: load(reportPath(row)), scenario: row.scenario, final});
}

export async function collect(row, owned) {
    const deadline = Date.now() + 600000;
    const summaryFile = path.join(results(row), 'summary.json');
    while (Date.now() < deadline) {
        const originalChild = row.runtimeSnapshot ? childRecords.get(row.runId) : null;
        if (row.runtimeSnapshot && !originalChild) throw Error('Frozen child lifecycle record missing');
        const processAlive = row.runtimeSnapshot ? !originalChild.exit && !originalChild.error : alive(owned.ProcessId);
        const liveLog = path.join(cwd(row), 'logs/latest.log');
        if (fs.existsSync(liveLog)) {
            const text = read(liveLog);
            const liveAudit = attemptLogAudit(row);
            if (liveAudit.blockers.length) throw Error('Log audit failed: ' + JSON.stringify(liveAudit));
            const loaded = [...text.matchAll(/phase=scenario\s+(?:id=)?([a-z0-9_.:-]+)/gi)].map(match => match[1]);
            if (loaded.some(id => id !== row.scenario)) throw Error('Runtime scenario mismatch: ' + loaded.join(','));
        }
        if (fs.existsSync(summaryFile)) {
            const summary = json(summaryFile);
            // Bench schema 1.0.0 checked against before-normal/results/summary.json.
            const selected = summary.scenarios?.filter(s => s.id === row.scenario);
            if (summary.schema !== '1.0.0' || selected?.length !== 1 || selected[0].status !== 'PASSED' || selected[0].failure != null)
                throw Error('Bench selected scenario failed/missing: ' + JSON.stringify(selected));
            const scenarioArgs = summary.environment?.java?.jvmArgs?.filter(arg => arg.startsWith('-DmodBench.scenarios='));
            if (scenarioArgs?.at(-1) !== '-DmodBench.scenarios=' + row.scenario) throw Error('Effective scenario mismatch');
            const variantArgs = summary.environment?.java?.jvmArgs?.filter(arg => arg.startsWith('-Dlobecorp.horde.variant='));
            if (variantArgs?.at(-1) !== '-Dlobecorp.horde.variant=' + row.variant) throw Error('Launched variant mismatch');
            const report = json(reportPath(row));
            if (!completedScenario(summary, report, row.scenario)) throw Error('Completed report evidence missing');
            let completedOwnerCheck;
            try {
                const isCompleted = () => completedScenario(json(summaryFile), json(reportPath(row)), row.scenario);
                completedOwnerCheck = row.runtimeSnapshot
                    ? await verifyCompletedChild(originalChild, {owned, runId: row.runId, runtimeFingerprint: originalChild.runtimeFingerprint, isCompleted})
                    : await verifyCompletedOwner({owned, readInfo: info, isAlive: alive,
                        matchesRow: value => matches(value, row), sleep, isCompleted});
                write(path.join(row.dir, 'completed-owner-check.json'), completedOwnerCheck);
            } catch (error) {
                write(path.join(row.dir, 'completed-owner-check-failure.json'), {code: error.code ?? 'unknown', diagnostics: error.diagnostics ?? null});
                throw error;
            }
            const stageLines = read(path.join(results(row), 'artifacts/stages/horde-encounter-stages.jsonl')).trim().split(/\r?\n/).map(JSON.parse);
            const stageHeader = stageLines.shift();
            const measureStages = stageLines.filter(stage => stage.phase === 'MEASURE');
            if (!measureStages.some(stage => stage.counts?.instances > 0) || measureStages.some(stage => stage.counts?.failures > 0)
                || !(stageHeader.gpu_completed > 0) || stageHeader.gpu_errors !== 0) throw Error('Stage workload/GPU gate failed');
            const gpu = json(path.join(results(row), 'artifacts/gpu/horde-encounter-gpu.json'));
            const logFile = path.join(cwd(row), 'logs/latest.log');
            const logAudit = attemptLogAudit(row, true);
            if (!logAudit.passed) throw Error('Log audit failed: ' + JSON.stringify(logAudit));
            const files = listFiles(results(row));
            if (files.some(file => file.endsWith('.jfr'))) throw Error('Unexpected Bench JFR; modBench.jfr must be false');
            const effectiveJfr = summary.environment?.java?.jvmArgs?.filter(arg => arg.startsWith('-DmodBench.jfr='))?.at(-1);
            const starts = summary.environment?.java?.jvmArgs?.filter(arg => arg.startsWith('-XX:StartFlightRecording='));
            if (effectiveJfr !== '-DmodBench.jfr=false' || starts?.length !== 1) throw Error('JFR recording configuration mismatch');
            const spark = path.join(results(row), 'artifacts/spark/horde-encounter.sparkprofile');
            if (!fs.existsSync(spark) || fs.statSync(spark).size === 0 || report.sparkLocalValidated !== true) throw Error('Missing/unvalidated local Spark profile');
            if (files.some(file => /\.(png|jpg|jpeg)$/i.test(file)) || listFiles(path.join(cwd(row), 'screenshots')).length) throw Error('Unexpected screenshot artifact');
            return {runId: row.runId, scenario: row.scenario, sourceFingerprint: row.sourceFingerprint,
                report, summary, completedOwnerCheck, logAudit, shutdownLogExceptions: logAudit.shutdownLogExceptions, stageHeader, measureStages, gpu, complete: true, shader: row.shader, screenshotCount: 0,
                jfr: {path: 'run.jfr', complete: false, scope: 'JVM startup->normal exit', validation: 'deferred until cleanup normal exit'},
                captureScopes: row.captureScopes,
                spark: {path: path.relative(row.dir, spark), complete: true}};
        }
        if (!processAlive) throw Error('Game exited before summary export');
        await sleep(500);
    }
    throw Error('Scenario/artifact timeout');
}

export async function cleanup(row, owned) {
    let receipt;
    try {
        receipt = await cleanupGame(row, owned);
    } finally {
        const observer = await stopObserver(row);
        if (receipt) receipt.observer = observer;
        else write(path.join(row.dir, 'window-observer-cleanup.json'), observer);
    }
    retireConfiguration(row, receipt);
    return receipt;
}

function retireConfiguration(row, receipt) {
    const createdFile = path.join(row.dir, 'configuration-created.json');
    if (!fs.existsSync(createdFile)) return;
    const created = json(createdFile);
    const expected = path.join(row.root, '.idea/runConfigurations/Horde_Standard_' + row.runId + '.xml');
    if (created.runId !== row.runId || path.resolve(created.configFile) !== path.resolve(expected)) throw Error('Configuration ownership mismatch');
    if (!fs.existsSync(expected)) return;
    const bytes = fs.readFileSync(expected);
    if (createHash('sha256').update(bytes).digest('hex') !== created.sha256) throw Error('Configuration changed; preserved');
    const submitted = fs.existsSync(path.join(row.dir, 'configuration-launch-submitted.json'));
    if (owner(row)) return;
    if (submitted && !receipt?.processExited) return;
    const archive = path.join(row.dir, 'archived-run-configuration.xml');
    fs.writeFileSync(archive, bytes, {flag: 'wx'});
    fs.unlinkSync(expected);
    write(path.join(row.dir, 'configuration-retired.json'), {runId: row.runId, sha256: created.sha256, archive, submitted});
}

async function cleanupGame(row, owned) {
    if (row.runtimeSnapshot) return cleanupFrozenGame(row, owned);
    owned ??= owner(row);
    const launched = fs.existsSync(path.join(row.dir, 'launch-request.json'));
    let forced = false;
    const verifyIfAlive = () => {
        if (!owned || !alive(owned.ProcessId)) return false;
        const current = info(owned.ProcessId);
        if (!current) return false;
        if (!matches(current, row) || current.CreationDate !== owned.CreationDate) {
            // A dying Windows process may still answer kill(pid,0) after its command line disappears.
            const state = ps(`$taskProcess = Get-Process -Id ${owned.ProcessId} -ErrorAction SilentlyContinue; if ($null -eq $taskProcess -or $taskProcess.HasExited) { 'exited' } else { 'running' }`);
            if (state === 'exited') return false;
            const checked = info(owned.ProcessId);
            if (!checked) return false;
            if (!matches(checked, row) || checked.CreationDate !== owned.CreationDate) throw Error('Cleanup identity changed; process untouched');
        }
        return true;
    };
    if (verifyIfAlive()) {
        try {ps(`(Get-Process -Id ${owned.ProcessId} -ErrorAction Stop).CloseMainWindow() | Out-Null`);}
        catch (error) {if (alive(owned.ProcessId)) throw error;}
        const deadline = Date.now() + 15000;
        while (alive(owned.ProcessId) && Date.now() < deadline) await sleep(500);
        if (verifyIfAlive()) {
            forced = true;
            try {ps(`Stop-Process -Id ${owned.ProcessId} -ErrorAction Stop`);}
            catch (error) {if (alive(owned.ProcessId)) throw error;}
            const limit = Date.now() + 15000;
            while (alive(owned.ProcessId) && Date.now() < limit) await sleep(500);
        }
    }
    return finalizeCleanup(row, owned, {exited: !!owned && !alive(owned.ProcessId), normalChildExit: true, forced, launched, originalChild: null});
}

async function cleanupFrozenGame(row, owned) {
    const originalChild = childRecords.get(row.runId);
    let forced = false;
    if (originalChild && !originalChild.exit && !originalChild.error) {
        try {await waitChildExit(originalChild, 60000);} catch {
            if (!originalChild.exit && !originalChild.error) {
                forced = true;
                originalChild.child.kill();
                try {await waitChildExit(originalChild, 15000);} catch {}
            }
        }
    }
    const exited = originalChild?.exit != null;
    const normalChildExit = originalChild?.binding != null && !originalChild.error
        && originalChild.exit?.code === 0 && originalChild.exit?.signal === null;
    return finalizeCleanup(row, owned, {exited, normalChildExit, forced,
        launched: fs.existsSync(path.join(row.dir, 'launch-request.json')), originalChild});
}

function finalizeCleanup(row, owned, {exited, normalChildExit, forced, launched, originalChild}) {
    const report = fs.existsSync(reportPath(row)) ? json(reportPath(row)) : null;
    // Do not turn process exit or asynchronous teardown scheduling into an encounter-removal receipt.
    const removed = report?.cleanup?.encounterRemoved === true || report?.encounterRemoved === true;
    const fullExport = !row.runtimeSnapshot || (fs.existsSync(path.join(results(row), 'summary.json'))
        && completedScenario(json(path.join(results(row), 'summary.json')), report, row.scenario));
    const jfrFile = path.join(row.dir, 'run.jfr');
    let validJfr = false;
    if (exited && !forced && normalChildExit && fs.existsSync(jfrFile)) {
        const fd = fs.openSync(jfrFile, 'r');
        const header = Buffer.alloc(4);
        try {validJfr = fs.fstatSync(fd).size > 68 && fs.readSync(fd, header, 0, 4, 0) === 4 && header.equals(Buffer.from([0x46, 0x4c, 0x52, 0]));}
        finally {fs.closeSync(fd);}
    }
    let activeRecordingCount = null;
    let recordingValidationError = null;
    if (validJfr) {
        try {
            const executable = row.runtimeSnapshot ? [null, originalChild.javaExecutable] : /^\"([^\"]+)\"|^(\S+)/.exec(owned.CommandLine);
            const jfrTool = path.join(path.dirname(executable[1] || executable[2]), 'jfr.exe');
            const output = execFileSync(jfrTool, ['print', '--json', '--events', 'jdk.ActiveRecording', jfrFile],
                {encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 16 * 1024 * 1024});
            const events = JSON.parse(output).recording.events;
            const ids = new Set(events.filter(event => event.type === 'jdk.ActiveRecording').map(event => event.values.id));
            activeRecordingCount = ids.size;
            validJfr = ids.size === 1 && !ids.has(undefined);
        } catch (error) {validJfr = false; recordingValidationError = error.message;}
    }
    const logAudit = exited && !forced && normalChildExit ? attemptLogAudit(row, true) : {passed: false, blockers: [{reason: 'Normal process exit not established; final log not accepted'}], pending: [], shutdownLogExceptions: []};
    return {runId: row.runId, logAudit, shutdownLogExceptions: logAudit.shutdownLogExceptions, jfr: {path: 'run.jfr', complete: validJfr, scope: 'JVM startup->normal exit', activeRecordingCount, recordingValidationError,
        validation: 'normal exit, JFR magic and exactly one jdk.ActiveRecording id; temporal coverage requires analysis'}, confirmed: launched ? exited && normalChildExit && !forced && fullExport && removed && logAudit.passed : !owned,
        fullExport, originalChildExit: row.runtimeSnapshot ? originalChild?.exit ?? null : null, processExited: exited, encounterRemoved: removed, forced, owned: owned ?? null,
        reason: !fullExport ? 'Completed Bench/report evidence missing' : !logAudit.passed ? 'Final log audit not accepted: ' + JSON.stringify(logAudit.blockers) : removed ? 'scenario receipt observed; final log audited' : 'scenario has not confirmed server encounter removal'};
}
