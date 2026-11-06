import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {StringDecoder} from 'node:string_decoder';
import {auditCase, fileHash, parseStages} from './first-cast-evidence.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const profileFile = path.join(root, 'tools/photon/performance/20261106-first-cast-matrix.json');
const read = file => fs.readFileSync(file, 'utf8');
const json = file => JSON.parse(read(file));
const slash = value => value.replaceAll('\\', '/');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const save = (file, value) => fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n', {flag: 'wx'});
const git = args => execFileSync('git', ['-C', root, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe']
}).trim();
const powershell = code => execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', code],
    {encoding: 'utf8', timeout: 30000, windowsHide: true}).trim();
const unpack = (result, allowNoScreen = false) => {
    const value = result.structuredContent ?? JSON.parse(result.content.filter(item => item.type === 'text').map(item => item.text).join('\n'));
    if (result.isError && !(allowNoScreen && value.error?.code === 'NO_SCREEN')) throw Error(JSON.stringify(result));
    return value;
};
const alive = pid => {
    try {
        process.kill(pid, 0);
        return true;
    } catch (error) {
        if (error.code === 'ESRCH') return false;
        throw error;
    }
};

function argumentsFor(argv) {
    const options = {
        batch: process.env.PHOTON_FIRST_CAST_BATCH || process.env.PHOTON_MATRIX_BATCH || '20261106-first-cast-standard-r1',
        slots: process.env.PHOTON_FIRST_CAST_SLOTS || process.env.PHOTON_MATRIX_SLOTS || '',
        quietMode: process.env.PHOTON_QUIET_MODE || 'unknown',
        environmentNote: process.env.PHOTON_ENVIRONMENT_NOTE || 'not_recorded'
    };
    const flags = new Set(['--run', '--resume', '--dry-run', '--discover', '--audit']);
    for (let index = 0; index < argv.length; index++) {
        const argument = argv[index];
        if (flags.has(argument)) options[argument.slice(2)] = true;
        else if (argument === '--batch' || argument === '--slots') {
            const value = argv[++index];
            if (!value || value.startsWith('--')) throw Error('Missing value for ' + argument);
            options[argument.slice(2)] = value;
        } else throw Error('Unknown argument: ' + argument);
    }
    if (!/^[a-zA-Z0-9_-]{1,64}$/.test(options.batch)) throw Error('Invalid batch name');
    if (!['enabled', 'disabled', 'unknown'].includes(options.quietMode)) throw Error('PHOTON_QUIET_MODE must be enabled, disabled or unknown');
    if (options.audit && (options.run || options.discover)) throw Error('--audit is read-only and cannot launch or connect');
    return options;
}

function validateProfile(profile) {
    if (profile.schema !== 'photon-first-cast-standard-v1' || profile.port !== 19877
        || profile.scenario !== 'lobecorp.photon-02-first-cast-performance'
        || profile.worldStableNs < 10000000000 || profile.primarySeconds !== 20
        || profile.castAtSeconds !== 2 || profile.warmupTicks !== 100
        || !profile.vmFlags.includes('-XX:FlightRecorderOptions=stackdepth=256')
        || profile.vmFlags.some(flag => flag.startsWith('-XX:StartFlightRecording'))
        || profile.properties['modBench.scenarios'] !== profile.scenario
        || profile.properties['modBench.jfr'] !== true
        || profile.properties['modBench.client.requireWindowFocus'] !== false
        || profile.properties['lobecorp.photon.bench.preserveWindow'] !== true) throw Error('Invalid standard profile');
    if (profile.runs.length !== 6 || new Set(profile.runs.map(row => row.id)).size !== 6
        || profile.runs.some(row => !/^[NS]_first_[1-3]$/.test(row.id) || row.shader !== row.id.startsWith('S'))) throw Error('Invalid six-case matrix');
    if (profile.expectedDimensions.length !== 2 || profile.expectedDimensions.some(value => !Number.isInteger(value) || value <= 0)) throw Error('Invalid dimensions');
    const endpoint = new URL(profile.launch.ideaUrl);
    if (endpoint.protocol !== 'http:' || !['127.0.0.1', 'localhost', '[::1]'].includes(endpoint.hostname)) throw Error('IDEA endpoint must be local');
}

function environment(profile, shader) {
    const cwd = path.resolve(root, profile.environmentSources[shader ? 'shader' : 'normal']);
    const relative = path.relative(root, cwd);
    if (relative.startsWith('..') || path.isAbsolute(relative)) throw Error('Environment template is outside workspace');
    const files = ['options.txt', 'config/iris.properties', 'config/spark/config.json'].map(name => path.join(cwd, name));
    for (const file of files) if (!fs.existsSync(file)) throw Error('Missing environment template: ' + file);
    if (json(files[2]).backgroundProfiler !== false) throw Error('Spark background profiler must be disabled');
    const iris = read(files[1]);
    const pack = /^shaderPack=(.*)$/m.exec(iris)?.[1]?.trim();
    const shaderFile = shader && pack ? path.resolve(cwd, 'shaderpacks', pack) : null;
    if (shader && (!shaderFile || !fs.existsSync(shaderFile))) throw Error('Selected shader pack is missing');
    if (shaderFile && (path.relative(root, shaderFile).startsWith('..') || fs.statSync(shaderFile).size > 8 * 1024 * 1024)) {
        throw Error('Shader input must be in workspace and fit the IDEA binary API limit');
    }
    return {cwd, files, iris, shaderFile};
}

function fingerprint(profile, rows, options) {
    const entries = new Map();
    const visit = relative => {
        const file = path.join(root, relative);
        if (!fs.existsSync(file)) return;
        const stat = fs.lstatSync(file);
        if (stat.isSymbolicLink()) throw Error('Unverified symbolic link in fingerprint: ' + relative);
        if (stat.isDirectory()) {
            for (const name of fs.readdirSync(file).sort()) visit(path.join(relative, name));
        } else if (stat.isFile()) entries.set(slash(relative), {
            path: slash(relative),
            bytes: stat.size,
            sha256: fileHash(file)
        });
    };
    for (const name of ['src/main', 'src/bench', 'src/generated', 'build/classes/java/main', 'build/classes/java/bench',
        'build/resources/main', 'build/resources/bench', 'lib/minecraft-mcp/bridge/dist', 'build.gradle.kts',
        'settings.gradle.kts', 'gradle.properties', 'gradle/libs.versions.toml', 'gradle.lockfile',
        profile.launch.programArgs, 'tools/photon/performance/20261106-first-cast-matrix.mjs',
        'tools/photon/performance/20261106-first-cast-matrix.json', 'tools/photon/performance/first-cast-evidence.mjs']) visit(name);
    for (const name of fs.readdirSync(path.join(root, 'lib')).filter(name => name.endsWith('.jar')).sort()) visit(path.join('lib', name));
    for (const shader of [false, true]) {
        const input = environment(profile, shader);
        for (const file of [...input.files, ...(input.shaderFile ? [input.shaderFile] : [])]) visit(path.relative(root, file));
    }
    const files = [...entries.values()].sort((left, right) => left.path.localeCompare(right.path, 'en'));
    const sha256 = createHash('sha256').update(JSON.stringify({
        schema: profile.schema, rows: rows.map(row => row.id),
        quietMode: options.quietMode, environmentNote: options.environmentNote, profile
    }));
    for (const file of files) sha256.update(file.path + '\0' + file.sha256 + '\n');
    return {sha256: sha256.digest('hex'), files: files.length, entries: files};
}

function caseRow(base, batch, row, attempt) {
    const slot = attempt === 1 ? row.id : row.id + '_attempt' + String(attempt).padStart(2, '0');
    const world = 'photon_first_' + createHash('sha256').update(batch).digest('hex').slice(0, 12) + '_' + slot;
    const dir = path.join(base, slot);
    return {
        ...row, attempt, slot, world, dir, cwd: path.join(dir, 'build/modBench/runs/default/client'),
        configuration: 'Photon Standard ' + batch + ' ' + slot
    };
}

function latestAttempt(base, batch, row) {
    let maximum = 0;
    if (fs.existsSync(base)) {
        for (const name of fs.readdirSync(base)) {
            if (name === row.id) maximum = Math.max(maximum, 1);
            const match = new RegExp('^' + row.id + '_attempt(\\d+)$').exec(name);
            if (match && Number.isSafeInteger(Number(match[1]))) maximum = Math.max(maximum, Number(match[1]));
        }
    }
    return caseRow(base, batch, row, maximum || 1);
}

function nextAttempt(base, batch, row, source) {
    const latest = latestAttempt(base, batch, row);
    if (source && fs.existsSync(path.join(latest.dir, 'completion.json'))) {
        try {
            const completion = json(path.join(latest.dir, 'completion.json'));
            const audit = auditCase(latest, source.profile, source);
            if (completion.collection_valid === true && completion.cleanup_confirmed === true
                && completion.source_fingerprint === source.fingerprint.sha256 && audit.collection_valid) return {
                ...latest,
                skip: true
            };
        } catch {
            // 损坏的完成回执不算成功，保留原目录并使用新 attempt。
        }
    }
    const launched = ['launch-intent.json', 'runner-failure.json', 'completion.json', 'results/summary.json', 'build/modBench/runs/default/client/logs/latest.log',
        'build/modBench/runs/default/client/saves'].some(name => fs.existsSync(path.join(latest.dir, name)));
    return launched ? caseRow(base, batch, row, latest.attempt + 1) : latest;
}

function processInfo(pid) {
    return JSON.parse(powershell('Get-CimInstance Win32_Process -Filter "ProcessId = ' + pid
        + '" | Select-Object ProcessId,CreationDate,CommandLine | ConvertTo-Json -Compress') || 'null');
}

function matchesProcess(info, row) {
    return info && typeof info.CommandLine === 'string' && slash(info.CommandLine).includes(slash(root))
        && new RegExp('-DmodBench\\.client\\.worldId=' + row.world + '(?:\\s|"|$)').test(info.CommandLine);
}

function ownedProcess(row) {
    const candidates = JSON.parse(powershell("Get-CimInstance Win32_Process -Filter \"Name = 'java.exe' OR Name = 'javaw.exe'\""
        + ' | Select-Object ProcessId,CreationDate,CommandLine | ConvertTo-Json -Compress') || '[]');
    const matches = [candidates].flat().filter(info => matchesProcess(info, row));
    if (matches.length > 1) throw Error('Ambiguous game process ownership');
    return matches[0] ?? null;
}

function portOwner(port) {
    const text = execFileSync('netstat.exe', ['-ano', '-p', 'tcp'], {
        encoding: 'utf8',
        timeout: 10000,
        windowsHide: true
    });
    const owners = new Set(text.split(/\r?\n/).map(line => line.trim().split(/\s+/))
        .filter(columns => columns[0] === 'TCP' && columns[1].endsWith(':' + port) && columns[3] === 'LISTENING')
        .map(columns => Number(columns[4])));
    if (owners.size > 1) throw Error('Ambiguous MCP port ownership');
    return [...owners][0] ?? null;
}

function xmlEscape(value) {
    return String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}

function launchXml(profile, row, source) {
    const properties = {
        ...profile.properties,
        'modBench.client.worldId': row.world, 'modBench.resultDirectory': slash(path.join(row.dir, 'results')),
        'lobecorp.photon.bench.shader': row.shader, 'lobecorp.photon.run.batchId': source.batch,
        'lobecorp.photon.run.sourceCommit': source.head, 'lobecorp.photon.run.sourceVariant': source.variant,
        'log4j2.configurationFile': pathToFileURL(path.join(root, 'build/moddev/benchClientLog4j2.xml')).href,
        'fml.modFolders': ['build/classes/java/main', 'build/resources/main', 'build/classes/java/bench', 'build/resources/bench']
            .map(name => 'lobecorp%%' + slash(path.join(root, name))).join(';')
    };
    const quote = argument => /\s/.test(argument) ? '"' + argument.replaceAll('"', '\\"') + '"' : argument;
    const vm = [...profile.vmFlags, ...Object.entries(properties).map(([key, value]) => '-D' + key + '=' + value)].map(quote).join(' ');
    return '<component name="ProjectRunConfigurationManager">\n'
        + '  <configuration default="false" name="' + xmlEscape(row.configuration) + '" type="Application" factoryName="Application">\n'
        + '    <option name="MAIN_CLASS_NAME" value="' + xmlEscape(profile.launch.mainClass) + '" />\n'
        + '    <module name="' + xmlEscape(profile.launch.module) + '" />\n'
        + '    <option name="PROGRAM_PARAMETERS" value="' + xmlEscape('@' + slash(path.join(root, profile.launch.programArgs))) + '" />\n'
        + '    <option name="VM_PARAMETERS" value="' + xmlEscape(vm) + '" />\n'
        + '    <option name="WORKING_DIRECTORY" value="' + xmlEscape(slash(row.cwd)) + '" />\n'
        + '    <method v="2"><option name="Make" enabled="false" /></method>\n'
        + '  </configuration>\n</component>\n';
}

function readback(file, expected) {
    if (!fs.existsSync(file) || read(file).replaceAll('\r\n', '\n') !== expected.replaceAll('\r\n', '\n')) throw Error('IDE readback mismatch: ' + file);
}

async function createText(call, file, text) {
    const relative = slash(path.relative(root, file));
    if (relative.startsWith('..') || path.isAbsolute(relative)) throw Error('IDE write escapes workspace');
    text = text.replaceAll('\r\n', '\n');
    if (!text.endsWith('\n')) text += '\n';
    fs.mkdirSync(path.dirname(file), {recursive: true});
    if (fs.existsSync(file)) {
        const snapshot = await call('ide_read_file_snapshot', {projectPath: root, filePath: relative, source: 'document'});
        if (snapshot.unsaved || snapshot.documentDiffersFromDisk || typeof snapshot.text !== 'string'
            || snapshot.text.replaceAll('\r\n', '\n') !== text) throw Error('Existing IDE Document differs: ' + relative);
        readback(file, text);
        return;
    }
    const lines = text.split('\n');
    lines.pop();
    const patch = '*** Begin Patch\n*** Add File: ' + relative + '\n'
        + lines.map(line => '+' + line).join('\n') + '\n*** End Patch';
    const arguments_ = {projectPath: root, expectedContentHash: {[relative]: 'absent'}, patch};
    const dry = await call('ide_apply_patch', {...arguments_, dryRun: true});
    if (dry.status !== 'dry_run') throw Error('IDE dry-run failed: ' + relative + ': ' + JSON.stringify(dry));
    const written = await call('ide_apply_patch', arguments_);
    if (written.status !== 'completed') throw Error('IDE write failed: ' + relative + ': ' + JSON.stringify(written));
    readback(file, text);
}

async function prepareRow(call, profile, row, source) {
    const input = environment(profile, row.shader);
    fs.mkdirSync(row.dir, {recursive: true});
    await createText(call, path.join(row.cwd, 'options.txt'), read(input.files[0]));
    await createText(call, path.join(row.cwd, 'config/spark/config.json'), read(input.files[2]));
    let iris = input.iris.replace(/^enableShaders=.*$/m, 'enableShaders=' + row.shader)
        .replace(/^shaderPack=.*$/m, 'shaderPack=' + (row.shader ? 'standard-pack.zip' : ''));
    if (!/^enableShaders=/m.test(iris) || !/^shaderPack=/m.test(iris)) throw Error('Iris template is incomplete');
    if (row.shader) {
        const output = path.join(row.cwd, 'shaderpacks/standard-pack.zip');
        fs.mkdirSync(path.dirname(output), {recursive: true});
        fs.mkdirSync(path.join(row.dir, 'binary-backups'), {recursive: true});
        if (fs.existsSync(output)) {
            if (fileHash(output) !== fileHash(input.shaderFile)) throw Error('Existing shader copy differs');
        } else {
            const arguments_ = {
                projectPath: root, backupDirectory: slash(path.relative(root, path.join(row.dir, 'binary-backups'))),
                files: [{
                    filePath: slash(path.relative(root, output)), expectedDiskHash: 'absent',
                    contentBase64: fs.readFileSync(input.shaderFile).toString('base64')
                }]
            };
            if ((await call('ide_write_binary_files', {
                ...arguments_,
                dryRun: true
            })).status !== 'dry_run') throw Error('Shader dry-run failed');
            if ((await call('ide_write_binary_files', arguments_)).status !== 'completed') throw Error('Shader write failed');
            if (fileHash(output) !== fileHash(input.shaderFile)) throw Error('Shader byte verification failed');
        }
    }
    await createText(call, path.join(row.cwd, 'config/iris.properties'), iris);
    await createText(call, path.join(root, '.idea/runConfigurations/Photon_Standard_' + source.batch + '_' + row.slot + '.xml'),
        launchXml(profile, row, source));
}

async function closePair(client, transport) {
    try {
        await client.close();
    } finally {
        await transport.close();
    }
}

async function screenshotAfterStop(sdk, profile, row, owned, events) {
    const {Client} = await sdk('index');
    const {StdioClientTransport} = await sdk('stdio');
    const transport = new StdioClientTransport({
        command: process.execPath,
        args: [path.join(root, 'lib/minecraft-mcp/bridge/dist/cli.js'), 'mcp', '--no-discover'], cwd: root,
        env: {...process.env, MC_MCP_PORT: String(profile.port)}, stderr: 'pipe'
    });
    const client = new Client({name: 'photon-standard-post-measure', version: '1'});
    try {
        await client.connect(transport);
        const current = processInfo(owned.ProcessId);
        if (portOwner(profile.port) !== owned.ProcessId || !matchesProcess(current, row)
            || current.CreationDate !== owned.CreationDate) throw Error('MCP ownership changed');
        const tools = (await client.listTools()).tools;
        for (const name of ['ping', 'get_minecraft_status', 'gui_control', 'screenshot_to_file']) {
            if (!tools.some(tool => tool.name === name)) throw Error('Missing bridge tool: ' + name);
        }
        const stop = events.filter(event => event.stage === 'spark_stop_request');
        const main = events.filter(event => event.stage === 'main_capture_request');
        if (stop.length !== 1 || main.length !== 1 || stop[0].ns <= main[0].ns) throw Error('Primary stop evidence is incomplete');
        const ping = await client.callTool({name: 'ping', arguments: {}}, undefined, {timeout: 10000});
        if (ping.isError) throw Error('MCP handshake failed: ' + JSON.stringify(ping));
        const status = unpack(await client.callTool({
            name: 'get_minecraft_status',
            arguments: {}
        }, undefined, {timeout: 10000}));
        if (!status.connected || status.port !== profile.port || !status.processAlive) throw Error('Bridge instance unavailable');
        const gui = unpack(await client.callTool({
            name: 'gui_control',
            arguments: {action: 'list'}
        }, undefined, {timeout: 10000}), true);
        if (gui.error && gui.error.code !== 'NO_SCREEN') throw Error('GUI query failed: ' + JSON.stringify(gui));
        const visible = gui.error?.code === 'NO_SCREEN' || gui.screen === null || gui.screenClass === null || gui.screenId === null;
        if (!visible) throw Error('World GUI visibility not confirmed');
        const requestedAt = new Date().toISOString();
        const shot = unpack(await client.callTool({
            name: 'screenshot_to_file',
            arguments: {path: path.join(row.dir, 'scene-after-measure.png'), region: 'game'}
        }, undefined, {timeout: 15000}));
        save(path.join(row.dir, 'screenshot-after-measure.json'), {
            pid: owned.ProcessId, world: row.world, port: profile.port,
            observed_stop_ns: stop[0].ns, observed_main_ns: main[0].ns,
            causal_order: 'read_spark_stop_request_then_request_screenshot', requested_at_utc: requestedAt,
            gui_world_visible: visible, gui, status, shot
        });
    } finally {
        await closePair(client, transport);
    }
}

async function cleanup(row, owned, profile, launchRequested) {
    owned ??= ownedProcess(row);
    if (!owned) return {
        confirmed: !launchRequested, pid: null,
        reason: launchRequested ? 'Launch requested but PID ownership is unknown; no process killed' : 'No game launch was requested'
    };
    const pid = owned.ProcessId;
    if (alive(pid)) {
        const current = processInfo(pid);
        if (!matchesProcess(current, row) || current.CreationDate !== owned.CreationDate) throw Error('Cleanup identity changed; process left untouched');
        powershell('(Get-Process -Id ' + pid + ').CloseMainWindow() | Out-Null');
        const deadline = Date.now() + profile.timeouts.shutdownMs;
        while (alive(pid) && Date.now() < deadline) await sleep(profile.timeouts.pollMs);
        if (alive(pid)) {
            const same = processInfo(pid);
            if (!matchesProcess(same, row) || same.CreationDate !== owned.CreationDate) throw Error('Cleanup PID reused; process left untouched');
            powershell('Stop-Process -Id ' + pid);
            const forcedDeadline = Date.now() + profile.timeouts.shutdownMs;
            while (alive(pid) && Date.now() < forcedDeadline) await sleep(profile.timeouts.pollMs);
        }
    }
    return {confirmed: !alive(pid), pid, creation_date: owned.CreationDate};
}

function stageReader(file) {
    let offset = 0;
    let pending = '';
    const decoder = new StringDecoder('utf8');
    const buffer = Buffer.alloc(65536);
    const events = [];
    return () => {
        if (!fs.existsSync(file)) return events;
        const size = fs.statSync(file).size;
        if (size < offset) throw Error('Log truncated during capture; keep this attempt as failed');
        const fd = fs.openSync(file, 'r');
        try {
            while (offset < size) {
                const length = fs.readSync(fd, buffer, 0, Math.min(buffer.length, size - offset), offset);
                if (length === 0) break;
                offset += length;
                const lines = (pending + decoder.write(buffer.subarray(0, length))).split(/\r?\n/);
                pending = lines.pop();
                for (const line of lines) {
                    if (line.includes('PHOTONSTAGE stage=')) events.push(...parseStages(line));
                }
            }
        } finally {
            fs.closeSync(fd);
        }
        return events;
    };
}

async function executeCase(call, sdk, profile, row, source) {
    let owned = null;
    let failure = null;
    let audit = null;
    let closed;
    let cleanupError = null;
    let launchRequested = false;
    save(path.join(row.dir, 'launch-intent.json'), {
        batch: source.batch, id: row.id, slot: row.slot,
        head: source.head, fingerprint: source.fingerprint.sha256, world: row.world, at_utc: new Date().toISOString()
    });
    try {
        if (portOwner(profile.port)) throw Error('MCP port is occupied; no existing game will be touched');
        launchRequested = true;
        const launch = await call('execute_run_configuration', {
            projectPath: root,
            configurationName: row.configuration,
            waitForExit: false
        });
        save(path.join(row.dir, 'launch.json'), launch);
        if (launch.exitCode != null && launch.exitCode !== 0) throw Error('IDEA launch failed with exit code ' + launch.exitCode);
        const launchDeadline = Date.now() + profile.timeouts.launchMs;
        while (Date.now() < launchDeadline) {
            owned = ownedProcess(row);
            if (owned && portOwner(profile.port) === owned.ProcessId) break;
            await sleep(profile.timeouts.pollMs);
        }
        if (!owned || portOwner(profile.port) !== owned.ProcessId) throw Error('Owned game/MCP startup timeout');
        save(path.join(row.dir, 'process.json'), owned);
        const deadline = Date.now() + profile.timeouts.caseMs;
        const logFile = path.join(row.cwd, 'logs/latest.log');
        const readStages = stageReader(logFile);
        let events;
        while (Date.now() < deadline) {
            if (!alive(owned.ProcessId)) throw Error('Game exited before primary capture finished');
            events = readStages();
            if (events.some(event => event.stage === 'spark_stop_request')) break;
            await sleep(profile.timeouts.pollMs);
        }
        if (!events?.some(event => event.stage === 'spark_stop_request')) throw Error('Primary capture stop timeout');
        await screenshotAfterStop(sdk, profile, row, owned, events);
        while (Date.now() < deadline && !fs.existsSync(path.join(row.dir, 'results/summary.json'))) {
            if (!alive(owned.ProcessId)) throw Error('Game exited before final artifact archive');
            await sleep(profile.timeouts.pollMs);
        }
        audit = auditCase(row, profile, source);
        save(path.join(row.dir, 'case-audit.json'), audit);
        if (!audit.collection_valid) throw Error('Standard evidence gate failed: ' + JSON.stringify(audit.checks) + ' ' + (audit.error || ''));
    } catch (error) {
        failure = error;
    } finally {
        try {
            closed = await cleanup(row, owned, profile, launchRequested);
            save(path.join(row.dir, 'cleanup.json'), closed);
            if (!closed.confirmed) throw Error('Owned game cleanup is incomplete');
        } catch (error) {
            cleanupError = error.stack;
            failure ??= error;
        }
    }
    if (failure) {
        save(path.join(row.dir, 'runner-failure.json'), {
            error: failure.stack,
            cleanup: closed ?? null,
            cleanup_error: cleanupError,
            at_utc: new Date().toISOString()
        });
        throw failure;
    }
    return audit;
}

export async function standard(argv = process.argv.slice(2)) {
    const options = argumentsFor(argv);
    const base = path.join(root, 'build/agent-backups', options.batch);
    const saved = fs.existsSync(path.join(base, 'source.json')) ? json(path.join(base, 'source.json')) : null;
    if (saved && (saved.schema !== 'photon-first-cast-standard-v1' || !saved.profile || !Array.isArray(saved.selectedIds))) {
        throw Error('Batch belongs to another runner or has incomplete source metadata; use a new standard batch');
    }
    const profile = options.audit && saved ? saved.profile : json(profileFile);
    validateProfile(profile);
    const ids = options.slots ? options.slots.split(',') : options.audit && saved ? saved.selectedIds : profile.runs.map(row => row.id);
    if (new Set(ids).size !== ids.length || ids.some(id => !profile.runs.some(row => row.id === id))) throw Error('Invalid selected slots');
    const selected = profile.runs.filter(row => ids.includes(row.id));
    if (!selected.length) throw Error('No selected cases');
    if (options.audit) {
        if (!saved || saved.batch !== options.batch) throw Error('No matching source record');
        const audits = selected.map(row => {
            const latest = latestAttempt(base, options.batch, row);
            const audit = auditCase(latest, profile, saved);
            const completionFile = path.join(latest.dir, 'completion.json');
            try {
                const completion = fs.existsSync(completionFile) ? json(completionFile) : null;
                audit.cleanup_confirmed = completion?.cleanup_confirmed === true
                    && completion.source_fingerprint === saved.fingerprint.sha256;
            } catch (error) {
                audit.cleanup_confirmed = false;
                audit.completion_error = error.message;
            }
            return audit;
        });
        const complete = audits.every(audit => audit.collection_valid && audit.cleanup_confirmed);
        console.log(JSON.stringify({
            mode: 'read-only-audit',
            batch: options.batch,
            selected_complete: complete,
            matrix_complete: complete && selected.length === profile.runs.length,
            performance_verdict: 'NOT_EVALUATED',
            audits
        }, null, 2));
        if (!complete) process.exitCode = 1;
        return;
    }
    const rows = selected.map(row => nextAttempt(base, options.batch, row, saved));
    for (const shader of [false, true]) environment(profile, shader);
    if (!fs.existsSync(path.join(root, profile.launch.programArgs))
        || !fs.existsSync(path.join(root, 'build/classes/java/main'))
        || !fs.existsSync(path.join(root, 'build/classes/java/bench'))) throw Error('Compile artifacts/program arguments are missing');
    const preview = {
        batch: options.batch,
        mode: options.run && !options['dry-run'] ? 'run' : 'dry-run',
        scenario: profile.scenario,
        primary_seconds: profile.primarySeconds,
        stable_before_fixture_ns: profile.worldStableNs,
        screenshot_policy: 'only_after_spark_stop_request',
        driver_pid: process.pid,
        selected_cases: rows.length,
        full_matrix_cases: profile.runs.length,
        rows: rows.map(row => ({id: row.id, slot: row.slot, world: row.world, directory: row.dir, skip: !!row.skip}))
    };
    for (const row of rows) {
        const xml = launchXml(profile, row, {batch: options.batch, head: 'dry-run', variant: 'dry-run'});
        if (!xml.includes('requireWindowFocus=false') || !xml.includes(profile.scenario)
            || xml.includes('-XX:StartFlightRecording')) throw Error('Rendered launch profile violates standard');
    }
    console.log(JSON.stringify(preview, null, 2));
    if ((!options.run || options['dry-run']) && !options.discover) return;
    if (options.run && fs.existsSync(base) && !options.resume && !options.discover) throw Error('Existing batch requires --resume; history will not be overwritten');
    if (options.resume && !saved && !options.discover) throw Error('--resume requires the original source.json');
    const require = createRequire(path.resolve(root, '../../tools/lobecorp-normify/package.json'));
    const sdk = async name => import(pathToFileURL(require.resolve('@modelcontextprotocol/sdk/client/' + name + '.js')).href);
    const {Client} = await sdk('index');
    const {StreamableHTTPClientTransport} = await sdk('streamableHttp');
    const transport = new StreamableHTTPClientTransport(new URL(profile.launch.ideaUrl));
    const client = new Client({name: 'photon-first-cast-standard', version: '1'});
    try {
        await client.connect(transport, {timeout: 15000});
        const listed = (await client.listTools()).tools;
        if (options.discover) {
            console.log(JSON.stringify(listed.filter(tool => ['ide_apply_patch', 'ide_write_binary_files',
                'ide_read_file_snapshot', 'get_run_configurations', 'execute_run_configuration'].includes(tool.name)), null, 2));
            return;
        }
        for (const name of ['ide_apply_patch', 'ide_read_file_snapshot', 'ide_write_binary_files', 'get_run_configurations', 'execute_run_configuration']) {
            if (!listed.some(tool => tool.name === name)) throw Error('Missing required IDEA capability: ' + name);
        }
        const call = async (name, arguments_) => {
            if (!listed.some(tool => tool.name === name)) throw Error('Missing actual IDEA tool: ' + name);
            return unpack(await client.callTool({name, arguments: arguments_}, undefined, {timeout: 120000}));
        };
        const head = git(['rev-parse', 'HEAD']);
        const current = fingerprint(profile, selected, options);
        const source = {
            schema: profile.schema, batch: options.batch, head, variant: 'workspace_' + options.batch,
            dirty: git(['status', '--porcelain']), fingerprint: current, profile, selectedIds: ids,
            runner_runtime: {node: process.version, platform: process.platform, arch: process.arch, pid: process.pid},
            quietMode: options.quietMode, environmentNote: options.environmentNote,
            observer: 'IDEA Run; incremental stage-log polling only during primary; MCP created after spark_stop_request; no Debug log-capture probe',
            performance_verdict: 'NOT_EVALUATED'
        };
        if (saved && (saved.head !== head || saved.fingerprint?.sha256 !== current.sha256 || saved.batch !== source.batch || saved.variant !== source.variant
            || saved.runner_runtime?.node !== process.version || JSON.stringify(saved.selectedIds) !== JSON.stringify(ids))) throw Error('Source/profile/selection changed; use a new batch');
        fs.mkdirSync(base, {recursive: true});
        if (!saved) save(path.join(base, 'source.json'), source);
        const assertSource = () => {
            if (git(['rev-parse', 'HEAD']) !== head || fingerprint(profile, selected, options).sha256 !== current.sha256) {
                throw Error('Source or compile inputs changed; existing cases remain historical, use a new batch');
            }
        };
        const audits = [];
        for (const row of rows) {
            if (row.skip) {
                audits.push(auditCase(row, profile, source));
                continue;
            }
            assertSource();
            if (portOwner(profile.port)) throw Error('Port 19877 occupied before launch; existing process left untouched');
            await prepareRow(call, profile, row, source);
            let configurations;
            const discoveryDeadline = Date.now() + 20000;
            do {
                configurations = JSON.stringify(await call('get_run_configurations', {projectPath: root}));
                if (configurations.includes(row.configuration)) break;
                await sleep(profile.timeouts.pollMs);
            } while (Date.now() < discoveryDeadline);
            if (!configurations.includes(row.configuration)) throw Error('IDEA did not discover generated configuration');
            console.log(JSON.stringify({event: 'case_start', id: row.id, slot: row.slot}));
            const audit = await executeCase(call, sdk, profile, row, source);
            assertSource();
            save(path.join(row.dir, 'completion.json'), {
                collection_valid: true, cleanup_confirmed: true,
                source_fingerprint: current.sha256, at_utc: new Date().toISOString()
            });
            audits.push(audit);
            console.log(JSON.stringify({
                event: 'case_complete', id: row.id, max_interval_ms: audit.primary.max,
                performance_verdict: 'NOT_EVALUATED'
            }));
        }
        const report = {
            schema: profile.schema,
            batch: options.batch,
            head,
            source_fingerprint: current.sha256,
            selected_complete: audits.every(audit => audit.collection_valid),
            matrix_complete: rows.length === profile.runs.length
                && audits.every(audit => audit.collection_valid),
            performance_verdict: 'NOT_EVALUATED',
            audits
        };
        const reportFile = path.join(base, 'matrix-report-' + Date.now() + '.json');
        save(reportFile, report);
        console.log(JSON.stringify({
            report: reportFile,
            selected_complete: report.selected_complete,
            matrix_complete: report.matrix_complete
        }));
    } finally {
        await closePair(client, transport);
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    standard().catch(error => {
        console.error(error.stack);
        process.exitCode = 1;
    });
}
