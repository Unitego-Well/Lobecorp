import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
const studyModes = ["default", "a", "b", "no_hiz", "no_facing_cache", "no_motion_opt", "no_avoidance_opt", "no_iris_opt", "no_detail_lod", "no_hidden_faces"];
const iterationModes = ['a_selected', 'a_depth', 'a_next'];
const knownModes = [...studyModes, ...iterationModes, 'a_adaptive'];
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const runtimeSnapshot = process.env.HORDE_RUNTIME_SNAPSHOT ? path.resolve(process.env.HORDE_RUNTIME_SNAPSHOT) : null;
if (runtimeSnapshot && !runtimeSnapshot.startsWith(path.join(root, 'build/horde-standard-test/runtime-snapshots') + path.sep)) throw Error('Runtime snapshot must be inside the dedicated workspace snapshot directory');
const frozenRuntime = runtimeSnapshot ? (await import('./frozen-runtime.mjs')).loadFrozenRuntime(runtimeSnapshot, root) : null;
const scenario = 'lobecorp.horde-encounter-performance';
const windowMode = process.env.HORDE_WINDOW_MODE || 'native';
if (!['native', 'workarea'].includes(windowMode)) throw Error('Invalid HORDE_WINDOW_MODE');
const options = {batch: 'horde-standard', slots: 'default_normal'};
for (let i = 2; i < process.argv.length; i++) {
    const key = process.argv[i];
    if (['--dry-run', '--run', '--audit', '--repair-receipt', '--all', '--run-matrix'].includes(key)) options[key.slice(2)] = true;
    else if (['--batch', '--slots', '--modes', '--adapter'].includes(key)) {
        const value = process.argv[++i];
        if (!value || value.startsWith('--')) throw Error('Missing value: ' + key);
        options[key.slice(2)] = value;
    } else throw Error('Unknown argument: ' + key);
}
if (!/^[a-zA-Z0-9_-]{1,64}$/.test(options.batch)) throw Error('Invalid batch');
if (options.run && (options.audit || options['dry-run'] || options['repair-receipt'])) throw Error('Conflicting modes');
if (options['run-matrix']) options.run = true;
if (options.all || options['run-matrix'] || options.modes) options.slots = (options.modes ? options.modes.split(',') : studyModes).flatMap(m => ['normal', 'shader'].map(v => m + '_' + v)).join(',');
const slots = options.slots.split(',');
if (new Set(slots).size !== slots.length || slots.some(s => !knownModes.some(m => s === m + '_normal' || s === m + '_shader'))) throw Error('Invalid slots');
const adaptive = slots.some(slot => /^a_adaptive_(normal|shader)$/.test(slot));
const experimentFamily = adaptive ? 'a-adaptive-1' : iterationModes.some(m => slots.includes(m + '_normal') || slots.includes(m + '_shader')) ? 'a-iteration-1' : null;
if (adaptive && slots.some(slot => !/^(a|a_adaptive)_(normal|shader)$/.test(slot))) throw Error('A adaptive batch only accepts a/a_adaptive');
if (experimentFamily === 'a-iteration-1' && slots.some(slot => !['a', ...iterationModes].some(mode => slot === mode + '_normal' || slot === mode + '_shader'))) throw Error('A iteration batch only accepts a/a_selected/a_depth/a_next');
const base = path.join(root, 'build/horde-standard-test', options.batch);
const read = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const save = (file, data) => fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n', {flag: 'wx'});
function fingerprint() {
    const hash = createHash('sha256');
    const visit = (relative, sourceRoot = root, prefix = '') => {
        const file = path.join(sourceRoot, relative);
        if (!fs.existsSync(file)) return;
        const stat = fs.lstatSync(file);
        if (stat.isSymbolicLink()) throw Error('Unverified symlink: ' + relative);
        if (stat.isDirectory()) for (const name of fs.readdirSync(file).sort()) visit(path.join(relative, name), sourceRoot, prefix);
        else hash.update(prefix + relative.replaceAll('\\', '/') + '\0').update(fs.readFileSync(file));
    };
    if (runtimeSnapshot) {
        visit('', runtimeSnapshot, 'frozen-runtime/');
        visit('tools/horde-standard-test');
    } else {
        for (const item of ['src/main', 'src/bench', 'src/generated', 'build/classes/java/main',
            'build/classes/java/bench', 'build/resources/main', 'build/resources/bench',
            'build.gradle.kts', 'gradle.properties', 'tools/horde-standard-test']) visit(item);
    }
    if (options.adapter) hash.update(fs.readFileSync(path.resolve(options.adapter)));
    return hash.digest('hex');
}
function assertFrozenSource(row, expected, phase) {
    const observed = fingerprint();
    if (observed !== expected) {
        save(path.join(row.dir, 'invalidation-source-changed.json'), {runId: row.runId, message: 'Source changed during run: ' + row.slot, phase, expectedFingerprint: expected, observedFingerprint: observed});
        throw Error('Source changed during run: ' + row.slot);
    }
}
function latest(slot) {
    let attempt = 1;
    while (fs.existsSync(path.join(base, slot + '_' + attempt))) attempt++;
    return attempt;
}
function audit(row, source) {
    const result = read(path.join(row.dir, 'result.json'));
    const cleanup = read(path.join(row.dir, 'cleanup.json'));
    const measureSamples = result.report.samples?.filter(sample => sample.phase === 'MEASURE') ?? [];
    const durationSeconds = (result.report.measureEndNs - result.report.measureStartNs) / 1e9;
    const iteration = row.experimentFamily === 'a-iteration-1';
    const selected = ['a_selected', 'a_next'].includes(row.variant);
    const depth = ['a_depth', 'a_next'].includes(row.variant);
    const activeStages = result.measureStages?.filter(frame => frame.counts?.instances > 0) ?? [];
    const flags = result.report.effectiveFlags ?? {};
    const iterationWork = !iteration || (flags.selectedScatter === selected && flags.depthScissor === depth
        && flags.motionKernel === true && flags.avoidanceKernel === true && flags.irisKernel === true
        && flags.layered === false && flags.detailLod === true && flags.sceneOcclusion === true
        && activeStages.length > 0 && activeStages.every(frame => {
            const c = frame.counts;
            return (selected ? c.lod_selected_tests === 128 : c.lod_selected_tests > 0)
                && c.depth_updated_pixels > 0 && c.depth_updated_pixels <= c.depth_target_pixels
                && (depth || c.depth_updated_pixels === c.depth_target_pixels);
        }) && (!depth || activeStages.some(frame => frame.counts.depth_updated_pixels < frame.counts.depth_target_pixels)));
    const checks = {
        iterationWork,
        runtimeIsolation: !row.runtimeSnapshot || (() => {
            const isolated = read(path.join(row.dir, 'runtime-isolation.json'));
            const completed = read(path.join(row.dir, 'completed-owner-check.json'));
            const spawned = read(path.join(row.dir, 'spawn-receipt.json'));
            const owned = read(path.join(row.dir, 'ownership.json'));
            return completed.state === 'completed-owned-child' && completed.runId === row.runId
                && completed.pid === spawned.pid && completed.pid === owned.ProcessId && completed.creationDate === owned.CreationDate
                && completed.runtimeFingerprint === row.runtimeFingerprint && completed.verified === true
                && completed.exitCode === 0 && completed.signal === null
                && isolated.snapshotRoot === row.runtimeSnapshot && isolated.runtimeFingerprint === row.runtimeFingerprint
                && isolated.classpathVerified === true && isolated.mutableProjectEntries?.length === 0;
        })(),
        variantSchema: row.variantSchemaVersion !== 2 || result.report.variantSchemaVersion === 2,
        identity: result.runId === row.runId && result.scenario === scenario && result.sourceFingerprint === source,
        formal: result.report?.scenarioId === scenario && result.report.workloadCorrect === true
            && typeof result.report.eventId === 'string' && result.report.eventId !== 'unavailable',
        workload: result.report.nonemptyMeasureSamples > 0 && Number.isSafeInteger(result.report.measureTicks) && result.report.measureTicks > 0
            && result.report.measureTicks >= measureSamples.length
            && measureSamples.length > 0
            && measureSamples.every((sample, index) => index === 0 || sample.frame > measureSamples[index - 1].frame)
            && result.report.nonemptyMeasureSamples === measureSamples.filter(sample => sample.submittedInstances > 0).length
            && result.report.measureEndNs > result.report.measureStartNs
            && durationSeconds >= 19.8
            && durationSeconds <= 20.2
            && result.report.samples?.some(sample => sample.phase === 'MEASURE' && sample.submittedInstances > 0)
            && result.report.positiveStageFrames > 0 && result.measureStages?.some(frame => frame.counts?.instances > 0)
            && result.measureStages.every(frame => frame.counts?.failures === 0)
            && Math.abs(result.report.actualMeasureStartEventTick - result.report.measureStartEventTick) <= 2,
        debugOverlay: row.properties['lobecorp.horde.bench.controlledDebugOverlay'] !== true
            || (result.report.testConditionsVersion >= 2 && result.report.setupGate.debugOverlayAfter === false
                && measureSamples.every(sample => sample.debugOverlay === false)
                && (row.properties['lobecorp.horde.bench.requireDebugOverlayGuardApplied'] !== true
                    || result.report.setupGate.debugOverlayGuardApplied === true)),
        windowObserver: row.properties['lobecorp.horde.bench.windowObserver'] !== true
            || (cleanup.observer?.enabled === true && cleanup.observer.complete === true
                && cleanup.observer.exited === true && cleanup.observer.forced === false
                && cleanup.observer.eventsCount >= 3 && !cleanup.observer.error),
        setupGate: result.report.setupGate?.passed === true
            && result.report.setupGate.effectiveScenario === scenario
            && result.report.setupGate.explicitVariant?.toLowerCase() === row.variant
            && result.report.setupGate.effectiveVariant?.toLowerCase() === row.variant
            && result.report.setupGate.runningJfrRecordingCount === 1
            && (row.properties['lobecorp.horde.bench.requireWindowPreservationApplied'] !== true
                || result.report.setupGate.benchWindowPreservationApplied === true),
        fullCapture: result.report.collectionComplete === true && result.report.gpuMaxSeconds === 600 && result.report.captureStartNs > 0
            && result.report.captureEndNs > result.report.captureStartNs,
        complete: result.complete === true && !result.failure && result.shader === row.shader && result.screenshotCount === 0,
        cleanup: cleanup.runId === row.runId && cleanup.confirmed === true && cleanup.encounterRemoved === true && cleanup.processExited === true
    };
    for (const kind of ['jfr', 'spark']) {
        const artifact = kind === 'jfr' ? cleanup.jfr : result[kind];
        const file = artifact?.path && path.resolve(row.dir, artifact.path);
        const relative = file && path.relative(row.dir, file);
        checks[kind] = !!file && !relative.startsWith('..') && !path.isAbsolute(relative)
            && fs.existsSync(file) && fs.statSync(file).size > 0 && artifact.complete === true
;
        if (kind === 'jfr') checks.jfr = checks.jfr && cleanup.forced === false
            && artifact.scope === 'JVM startup->normal exit' && path.resolve(file) === path.join(row.dir, 'run.jfr');
    }
    if (!Object.values(checks).every(Boolean)) throw Error('Evidence gate failed: ' + JSON.stringify(checks));
    return checks;
}
async function main() {
    const source = fingerprint();
    const rows = slots.map(slot => {
        const attempt = options.audit || options['repair-receipt'] ? latest(slot) - 1 : latest(slot);
        const runId = options.batch + '_' + slot + '_' + attempt;
        return {slot, runId, variantSchemaVersion: 2, experimentFamily, runtimeSnapshot, runtimeFingerprint: frozenRuntime?.runtimeFingerprint ?? null,
            fingerprintPolicy: runtimeSnapshot ? 'immutable runtime snapshot plus current harness; live workspace excluded' : 'live workspace and build outputs', variant: slot.replace(/_(normal|shader)$/, ''), shader: slot.endsWith('_shader'), dir: path.join(base, slot + '_' + attempt),
            worldId: runId, root, scenario, sourceFingerprint: source,
            screenshots: false, preserveProductionCount: true,
            properties: {'lobecorp.horde.bench.requireVariantSchemaVersion': 2, 'lobecorp.horde.variant': slot.replace(/_(normal|shader)$/, ''), 'modBench.scenarios': scenario, 'modBench.jfr': false,
                'lobecorp.performance.capture.maxSeconds': 600,
                'modBench.client.requireWindowFocus': false,
                'lobecorp.horde.bench.controlledDebugOverlay': true,
                'lobecorp.horde.bench.requireDebugOverlayGuardApplied': true,
                'lobecorp.horde.bench.requireWindowPreservationApplied': true,
                'lobecorp.horde.bench.windowObserver': true,
                'lobecorp.developmentWindowTestPolicy': true,
                'lobecorp.developmentWindowTrace': true,
                'lobecorp.developmentWindowTestMode': windowMode,
                'lobecorp.photon.bench.preserveWindow': true},
            captureScopes: {jfr: 'JVM startup->normal exit', spark: 'earliest scenario context available->teardown', gpu: 'earliest GPU context available->teardown; unavailable before context creation'},
            vmFlags: ['-XX:FlightRecorderOptions=stackdepth=256',
                '-XX:StartFlightRecording=filename=' + path.join(base, slot + '_' + attempt, 'run.jfr') + ',settings=profile,dumponexit=true']};
    });
    if (options.audit || options['repair-receipt']) {
        for (const planned of rows) {
            const row = read(path.join(planned.dir, 'launch-intent.json'));
            if (row.runId !== planned.runId || row.scenario !== scenario) throw Error('Stored launch identity mismatch');
            const executorFingerprint = source;
            if (options['repair-receipt']) {
                const adapter = await import(new URL('./idea-adapter.mjs', import.meta.url).href);
                if (!fs.existsSync(path.join(row.dir, 'cleanup.json'))) {
                    try {save(path.join(row.dir, 'cleanup.json'), await adapter.cleanup(row, read(path.join(row.dir, 'ownership.json'))));}
                    catch (error) {
                        save(path.join(row.dir, 'cleanup-failure-' + Date.now() + '.json'), {message: error.stack, recovery: true});
                        throw error;
                    }
                }
                const checks = audit(row, row.sourceFingerprint);
                save(path.join(row.dir, 'repair-receipt-' + Date.now() + '.json'), {runId: row.runId, checks,
                    originalLaunchFingerprint: row.sourceFingerprint, repairExecutorFingerprint: executorFingerprint,
                    clockEvidence: {callbackCount: row.scenario && read(path.join(row.dir, 'result.json')).report.measureTicks,
                        scope: 'client callbacks, server event ticks and monotonic nanoseconds are distinct clocks; raw values unchanged',
                        durationToleranceSeconds: 0.2},
                    reusedExistingCapture: true, noGameLaunch: true});
                console.log(JSON.stringify({runId: row.runId, checks, originalLaunchFingerprint: row.sourceFingerprint, repairExecutorFingerprint: executorFingerprint}));
            } else console.log(JSON.stringify({runId: row.runId, checks: audit(row, row.sourceFingerprint), originalLaunchFingerprint: row.sourceFingerprint, auditExecutorFingerprint: executorFingerprint}));
        }
        return;
    }
    console.log(JSON.stringify(options.run ? {mode: 'run', batch: options.batch, sourceFingerprint: source, slots: rows.map(row => row.slot)} : {mode: 'dry-run', rows}, null, 2));
    if (!options.run) return;
    let primaryFailure;
    try {
    const adapter = await import(options.adapter ? pathToFileURL(path.resolve(options.adapter)).href : new URL('./idea-adapter.mjs', import.meta.url).href);
    for (const name of ['preflight', 'launch', 'collect', 'cleanup']) if (typeof adapter[name] !== 'function') throw Error('Missing adapter.' + name);
    let matrixFailed = false;
    for (const row of rows) {
        console.log(`[${rows.indexOf(row) + 1}/${rows.length}] START ${row.slot}`);
        if (fingerprint() !== source) throw Error('Source changed before case');
        fs.mkdirSync(row.dir, {recursive: true});
        save(path.join(row.dir, 'launch-intent.json'), row);
        let owned;
        let failure;
        let cleanupFailure;
        let cleanupReceipt;
        try {
            await adapter.preflight(row);
            owned = await adapter.launch(row);
            save(path.join(row.dir, 'ownership.json'), owned);
            const result = await adapter.collect(row, owned);
            save(path.join(row.dir, 'result.json'), result);
        } catch (error) {
            failure = error;
            save(path.join(row.dir, 'failure.json'), {message: error.stack});
        } finally {
            // The adapter must recover ownership from runId/worldId even if launch threw.
            try {
                cleanupReceipt = await adapter.cleanup(row, owned);
                save(path.join(row.dir, 'cleanup.json'), cleanupReceipt);
            } catch (error) {
                cleanupFailure = error;
                save(path.join(row.dir, 'cleanup-failure.json'), {message: error.stack, originalFailure: failure?.stack ?? null});
            }
        }
        assertFrozenSource(row, source, 'before-completion');
        if (!failure && !cleanupFailure) {
            try {
                const checks = audit(row, source);
                save(path.join(row.dir, 'completion.json'), {runId: row.runId, checks, sourceFingerprint: source});
            } catch (error) {
                failure = error;
                save(path.join(row.dir, 'failure.json'), {message: error.stack});
            }
        }
        assertFrozenSource(row, source, 'after-receipt');
        const missingCleanup = ['processExited', 'encounterRemoved', 'confirmed'].filter(key => cleanupReceipt?.[key] !== true);
        if (cleanupFailure || missingCleanup.length) {
            const detail = {runId: row.runId, fields: Object.fromEntries(['processExited', 'encounterRemoved', 'confirmed'].map(key => [key, cleanupReceipt?.[key] ?? 'unknown'])), cleanupException: cleanupFailure?.stack ?? null, originalFailure: failure?.stack ?? null};
            save(path.join(row.dir, 'cleanup-stop.json'), detail);
            const cleanupStop = Error('Cleanup unconfirmed: ' + JSON.stringify(detail), {cause: failure ?? cleanupFailure});
            if (failure) {console.error(cleanupStop.stack); throw failure;}
            throw cleanupStop;
        }
        if (failure) {
            matrixFailed = true;
            console.log(`[${rows.indexOf(row) + 1}/${rows.length}] FAIL ${row.slot}: ${failure.message.replaceAll('\n', ' ')}`);
            if (row.slot === 'default_normal' || (experimentFamily && row.slot === 'a_normal') || !options['run-matrix']) throw failure;
        } else console.log(`[${rows.indexOf(row) + 1}/${rows.length}] PASS ${row.slot}`);
    }
    if (matrixFailed) process.exitCode = 1;
    } catch (error) {
        primaryFailure = error;
        throw error;
    } finally {
        const timestamp = Date.now();
        fs.mkdirSync(base, {recursive: true});
        if (primaryFailure) save(path.join(base, 'matrix-stop-' + timestamp + '.json'), {message: primaryFailure.stack, sourceFingerprint: source, partial: true});
        try {
            execFileSync(process.execPath, [path.join(root, 'tools/horde-standard-test/analyze.mjs'), '--batch', options.batch, '--out', path.join(base, 'analysis-' + timestamp + '.json'), '--markdown', path.join(base, 'analysis-' + timestamp + '.md')], {stdio: 'inherit', cwd: root});
        } catch (reportError) {
            console.error('Partial report generation failed:', reportError.stack);
            save(path.join(base, 'analysis-failure-' + timestamp + '.json'), {message: reportError.stack, originalFailure: primaryFailure?.stack ?? null});
            process.exitCode = 1;
        }
    }
}
main().catch(error => {console.error(error.stack); process.exitCode = 1;});
