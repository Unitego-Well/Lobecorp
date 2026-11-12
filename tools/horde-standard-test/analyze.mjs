import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import {analyzeBench} from './bench-metrics.mjs';

const args = {};
for (let i = 2; i < process.argv.length; i += 2) {
    const key = process.argv[i];
    if (!['--batch', '--out', '--markdown'].includes(key) || !process.argv[i + 1] || args[key]) throw Error('Usage: --batch <name> [--out <new.json>] [--markdown <new.md>]');
    args[key] = process.argv[i + 1];
}
if (!/^[\w-]{1,64}$/.test(args['--batch'] ?? '')) throw Error('Invalid batch');
const root = path.resolve('build/horde-standard-test', args['--batch']);
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const stats = values => {
    if (!values.length) return {count: 0};
    const sorted = values.slice().sort((a, b) => a - b), sum = values.reduce((a, b) => a + b, 0);
    const percentile = p => sorted[Math.max(0, Math.ceil(p * sorted.length) - 1)];
    return {count: values.length, sampleCount: values.length, sum, min: sorted[0], max: sorted.at(-1), mean: sum / values.length, p50: percentile(.5), p95: percentile(.95), p99: percentile(.99)};
};
const variants = ['baseline', 'A', 'B', 'C'];
if (fs.readdirSync(root, {withFileTypes: true}).some(entry => entry.isDirectory() && /^D_(normal|shader)_\d+$/.test(entry.name))) variants.push('D');
const intents = fs.readdirSync(root, {withFileTypes: true}).filter(e => e.isDirectory()).flatMap(e => {const p = path.join(root, e.name, 'launch-intent.json'); return fs.existsSync(p) ? [read(p)] : [];});
const schema2 = intents.some(i => i.variantSchemaVersion === 2);
const iteration = intents.some(i => i.experimentFamily === 'a-iteration-1');
const adaptive = intents.some(i => i.experimentFamily === 'a-adaptive-1' || i.variant === 'a_adaptive');
if (adaptive && intents.some(i => i.experimentFamily !== 'a-adaptive-1')) throw Error('Mixed experiment families in adaptive batch');
if (adaptive && intents.some(i => !['a', 'a_adaptive'].includes(i.variant) || i.variantSchemaVersion !== 2)) throw Error('Unknown A adaptive mode/schema');
if (adaptive) for (const entry of fs.readdirSync(root, {withFileTypes: true})) {
    if (!entry.isDirectory() || !fs.existsSync(path.join(root, entry.name, 'launch-intent.json'))) continue;
    if (!/^(a|a_adaptive)_(normal|shader)_\d+$/.test(entry.name)) throw Error('Unknown A adaptive slot');
    if (entry.name.replace(/_(normal|shader)_\d+$/, '') !== read(path.join(root, entry.name, 'launch-intent.json')).variant) throw Error('Adaptive slot/variant mismatch');
}
if (iteration && intents.some(i => i.experimentFamily !== 'a-iteration-1')) throw Error('Mixed experiment families in one batch');
if (iteration && intents.some(i => !['a', 'a_selected', 'a_depth', 'a_next'].includes(i.variant))) throw Error('Unknown A iteration mode');
if (schema2 && intents.some(i => i.variantSchemaVersion !== 2)) throw Error('Mixed variant schemas in one batch');
if (schema2) variants.splice(0, variants.length, ...["default", "a", "b", "no_hiz", "no_facing_cache", "no_motion_opt", "no_avoidance_opt", "no_iris_opt", "no_detail_lod", "no_hidden_faces"]);
if (iteration) variants.splice(0, variants.length, 'a', 'a_selected', 'a_depth', 'a_next');
if (adaptive) variants.splice(0, variants.length, 'a', 'a_adaptive');
const expected = variants.flatMap(v => ['normal', 'shader'].map(m => `${v}_${m}`));
const analysisNotice = 'Analyzer layer-counts-v1: offline analysis only; analyzer changes do not imply recapture; sourceFingerprint remains the original launch fingerprint.';
const result = {experimentFamily: adaptive ? 'a-adaptive-1' : iteration ? 'a-iteration-1' : null, analysisNotice, schema: 2, variantSchemaVersion: schema2 ? 2 : 1, primaryFrameStatistics: 'BenchMod client.frame.interval; GPU render HEAD windows are supplemental', batch: args['--batch'], root, policy: {interval: 'actual render HEAD interval; both endpoints inside window; no stall removal', fps: 'arithmetic mean of 1000/interval_ms; aggregate FPS=count*1000/sum(interval_ms)', percentile: 'nearest rank', startup: 'GPU has no pre-context coverage', stages: 'CPU parents include children; GPU cohorts differ; never sum stages into frame time', samples: 'scenario tick samples are environment/instance metadata, not frame timing'}, slots: {}, caseStatus: {}, rejected: [], missing: []};
for (const slot of expected) {
    const candidates = fs.readdirSync(root).filter(n => new RegExp(`^${slot}_\\d+$`).test(n)).sort((a, b) => Number(b.split('_').at(-1)) - Number(a.split('_').at(-1)));
    let chosen;
    for (const name of candidates) {
        const dir = path.join(root, name);
        try {
            if (fs.existsSync(path.join(dir, 'invalidation-source-changed.json'))) throw Error('Source fingerprint invalidated: ' + read(path.join(dir, 'invalidation-source-changed.json')).message);
            const receipts = fs.readdirSync(dir).filter(n => n === 'completion.json' || /^repair-receipt-.*\.json$/.test(n));
            const valid = receipts.filter(n => { const r = read(path.join(dir, n)); return r.runId === `${args['--batch']}_${name}` && r.checks && Object.keys(r.checks).length >= 9 && Object.values(r.checks).every(v => v === true); });
            if (!valid.length) throw Error('No valid completion/repair receipt');
            const reportPath = path.join(dir, 'results/artifacts/horde-encounter-performance.json');
            const gpuPath = path.join(dir, 'results/artifacts/gpu/horde-encounter-gpu.json');
            const stagePath = path.join(dir, 'results/artifacts/stages/horde-encounter-stages.jsonl');
            const r = read(reportPath), g = read(gpuPath), f = g.frames;
            if (schema2 && r.variantSchemaVersion !== 2) throw Error('Variant schema mismatch');
            if (!r.workloadCorrect || !r.collectionComplete || !r.encounterRemoved || !(r.captureStartNs < r.measureStartNs && r.measureStartNs < r.measureEndNs && r.measureEndNs <= r.captureEndNs) || !r.frameTimestampContract?.includes('interval_ns')) throw Error('Incomplete report/clock contract');
            if (g.summary.state !== 'STOPPED' || !f.interval_ns || f.interval_ns.length !== f.head_offset_ns.length) throw Error('Incomplete GPU frame arrays');
            const windows = {capture: [r.captureStartNs, r.captureEndNs], warmup: [r.captureStartNs, r.measureStartNs], measure: [r.measureStartNs, r.measureEndNs], teardown: [r.measureEndNs, r.captureEndNs]};
            const phases = {};
            const crossBoundary = [];
            for (let i = 0; i < f.interval_ns.length; i++) {
                const ns = f.interval_ns[i], end = g.summary.started_ns + f.head_offset_ns[i], start = end - ns;
                if (!(ns > 0) || end < r.captureStartNs || end > r.captureEndNs) continue;
                const endPhase = end < r.measureStartNs ? 'warmup' : end < r.measureEndNs ? 'measure' : 'teardown';
                const boundaries = Object.entries({captureStart: r.captureStartNs, measureStart: r.measureStartNs, measureEnd: r.measureEndNs}).filter(([, t]) => start < t && end >= t).map(([k]) => k);
                if (boundaries.length) crossBoundary.push({frameIndex: i, renderFrameId: f.render_frame_id?.[i], startNs: start, endNs: end, durationMs: ns / 1e6, endPhase, crossBoundary: true, crossStart: boundaries.includes('captureStart'), boundaries, longThresholdsMs: [16.667, 33.333, 50, 100].filter(t => ns / 1e6 > t)});
            }
            for (const [phase, [start, end]] of Object.entries(windows)) {
                const durations = [], indices = [];
                for (let i = 0; i < f.interval_ns.length; i++) {
                    const ns = f.interval_ns[i], head = g.summary.started_ns + f.head_offset_ns[i];
                    if (Number.isFinite(ns) && ns > 0 && head - ns >= start && head <= end) {durations.push(ns / 1e6); indices.push(i);}
                }
                const sum = durations.reduce((a, b) => a + b, 0);
                const longFrames = {};
                for (const threshold of [16.667, 33.333, 50, 100]) {
                    let count = 0, total = 0, excess = 0, runs = 0, longest = 0, current = 0, previous = -2;
                    durations.forEach((ms, j) => {
                        if (ms > threshold) {if (indices[j] !== previous + 1 || !current) {runs++; current = 0;} current += ms; longest = Math.max(longest, current); count++; total += ms; excess += ms - threshold; previous = indices[j];}
                        else current = 0;
                    });
                    longFrames[threshold] = {count, frameDurationMs: total, excessMs: excess, frameRatio: durations.length ? count / durations.length : null, durationRatio: sum ? total / sum : null, consecutiveSegments: runs, longestSegmentMs: longest};
                }
                const samples = (r.samples ?? []).filter(s => s.nanoTime >= start && s.nanoTime <= end);
                phases[phase] = {windowNs: [start, end], coveredIntervalMs: sum, frameTimeMs: stats(durations), intervalFps: stats(durations.map(ms => 1000 / ms)), aggregateFps: sum ? durations.length * 1000 / sum : null, longFrames, environment: {scope: 'tick samples only', samples: samples.length, focusedRatio: samples.length ? samples.filter(s => s.focused).length / samples.length : null, dimensions: [...new Set(samples.map(s => `${s.width}x${s.height}`))], submittedInstances: stats(samples.map(s => s.submittedInstances).filter(Number.isFinite))}, stages: {}};
            }
            const stageHeader = {};
            if (fs.existsSync(stagePath)) {
                const bins = {}, countBins = {};
                for await (const line of readline.createInterface({input: fs.createReadStream(stagePath), crlfDelay: Infinity})) {
                    if (!line.trim()) continue;
                    const row = JSON.parse(line);
                    if (!row.stages) {Object.assign(stageHeader, row); continue;}
                    for (const [phase, [start, end]] of Object.entries(windows)) if (row.nano_time >= start && row.nano_time <= end) {
                        for (const field of ['draws', 'passes', 'upload_bytes', 'copies', 'copy_bytes', 'expanded_vertices', 'instances', 'layer_input', 'layer_visible', 'layer_readback_bytes', 'depth_target_pixels', 'depth_updated_pixels', 'lod_selected_tests']) if (Number.isFinite(row.counts?.[field])) (countBins[`${phase}/${field}`] ??= []).push(row.counts[field]);
                        for (const [stage, data] of Object.entries(row.stages)) for (const field of ['cpu_frame_sum_ns', 'gpu_frame_sum_ns']) {
                            const value = data[field];
                            const valid = data.calls > 0 && row.render_frame_valid === true && (field === 'cpu_frame_sum_ns' || (row.gpu_cohort === 'STAGE_SUM' && !row.gpu_incomplete && data.gpu_coverage?.complete === true && data.gpu_coverage?.state === 'COMPLETE' && data.gpu_coverage?.expected_calls > 0));
                            if (valid && Number.isFinite(value)) (bins[`${phase}/${stage}/${field}`] ??= []).push(value / 1e6);
                        }
                    }
                }
                for (const [key, values] of Object.entries(countBins)) {const [phase, field] = key.split('/'); (phases[phase].counts ??= {})[field] = stats(values);}
                for (const [key, values] of Object.entries(bins)) {const [phase, stage, field] = key.split('/'); (phases[phase].stages[stage] ??= {})[field.replace('_ns', '_ms')] = stats(values);}
            }
            const nativeWindowEvidence = [];
            for (const logPath of [path.join(dir, 'game/logs/latest.log'), path.join(dir, 'results/client.log')]) if (fs.existsSync(logPath)) {
                let lineNumber = 0;
                for await (const line of readline.createInterface({input: fs.createReadStream(logPath), crlfDelay: Infinity})) {lineNumber++; if (/event=(creation-hints|first-visible)|nativeIsZoomed|foregroundBefore|placementRead/.test(line)) nativeWindowEvidence.push({path: logPath, lineNumber, raw: line});}
            }
            chosen = {effectiveFlags: r.effectiveFlags ?? null, variantSchemaVersion: schema2 ? 2 : 1, qualityCaveats: slot.startsWith('no_detail_lod_') ? ['All cards: reduced detail, excluded from equal-quality ranking'] : [], applicability: ['no_facing_cache_normal', 'no_iris_opt_normal'].includes(slot) ? 'inactive/not_applicable' : slot.startsWith('no_detail_lod_') ? 'quality-changed' : 'configured; execution requires stage evidence', sourceFingerprint: fs.existsSync(path.join(dir, 'launch-intent.json')) ? read(path.join(dir, 'launch-intent.json')).sourceFingerprint : null, attempt: name, bench: analyzeBench(dir, r.scenarioId), crossBoundary, nativeWindowEvidence: {status: nativeWindowEvidence.length ? 'observed_raw_not_automatically_passed' : 'not_recorded', records: nativeWindowEvidence}, evidence: {receipts: valid.map(n => path.join(dir, n)), report: reportPath, gpu: gpuPath, stages: fs.existsSync(stagePath) ? stagePath : null}, gpuSummary: g.summary, stageHeader, phases};
            break;
        } catch (e) {result.rejected.push({attempt: name, reason: e.message});}
    }
    if (chosen) {
        result.slots[slot] = chosen;
        result.caseStatus[slot] = {status: 'PASS', attempt: chosen.attempt};
    } else {
        result.missing.push(slot);
        const latest = candidates[0];
        const failureFile = latest && path.join(root, latest, 'failure.json');
        const invalidationFile = latest && path.join(root, latest, 'invalidation-source-changed.json');
        const reason = invalidationFile && fs.existsSync(invalidationFile) ? read(invalidationFile).message
            : failureFile && fs.existsSync(failureFile) ? read(failureFile).message : result.rejected.find(r => r.attempt === latest)?.reason;
        result.caseStatus[slot] = {status: latest ? 'FAIL' : 'UNKNOWN', reason: reason ?? 'not collected / unknown', attempt: latest ?? null};
    }
}
result.matrixComplete = !result.missing.length;
const headLines = ['# 辅助 render HEAD 逐帧分析', '', `批次：${result.batch}；矩阵完整：${result.matrixComplete}。`, '', '逐帧采用实际 render HEAD 间隔，两端均在阶段窗内；保留卡帧。启动前无 GPU context，不覆盖 JVM 启动。FPS 平均为逐帧倒数的算术平均，aggregate FPS 为帧数/累计间隔。P50/P95/P99 使用 nearest rank。CPU/GPU stage 单列，禁止相加替代整帧。', '', '|配置/阶段|帧数|FPS 最小/最大/平均|Aggregate FPS|帧时最小/最大/平均 ms|P50/P95/P99 ms|', '|---|---:|---|---:|---|---|'];
const fmt = n => Number.isFinite(n) ? n.toFixed(3) : 'N/A';
const lines = ['# Horde 性能详细报告（BenchMod 数据优先）', '', analysisNotice, '', `批次：${result.batch}；矩阵完整：${result.matrixComplete}。`, '',
    '帧时最大、最小、平均、分位数与长帧次数直接来自 BenchMod summary；仅从已有原始样本补算 FPS、长帧累计/超额时长和连续段。没有新增游戏采集器。Bench 阶段样本没有逐帧时间戳，不与下面的 render HEAD 时间窗混算。', '',
    '|配置/Bench阶段|帧数/丢弃|FPS 最小/最大/算术平均|Aggregate FPS|帧时最小/最大/平均 ms|P50/P95/P99 ms|',
    '|---|---:|---|---:|---|---|'];
if (schema2) {
    lines.splice(4, 0, '质量边界：no_detail_lod 为全卡片降细节参考，不参与等画质胜负；no_facing_cache_normal 与 no_iris_opt_normal 在普通模式均为 inactive/not_applicable，不能把波动解释为功能收益。effectiveFlags（含 reduceHiddenFaces、shaderActive）表示有效配置，实际是否执行以 stage 数据为准。负提升照实列出；缺失/失败不补零。', '');
    lines.push('', '## 相对新 A 的增益（负值表示更慢）', '', '|配置/阶段|Aggregate FPS 变化|说明|', '|---|---:|---|');
    for (const [slot, entry] of Object.entries(result.slots)) {
        const reference = result.slots['a_' + (slot.endsWith('_shader') ? 'shader' : 'normal')];
        for (const [phase, value] of Object.entries(entry.bench.phases)) {
            const base = reference?.bench.phases[phase]?.aggregateFps;
            const delta = entry.applicability !== 'inactive/not_applicable' && entry.applicability !== 'quality-changed' && base > 0 && Number.isFinite(value.aggregateFps) ? 100 * (value.aggregateFps / base - 1) : null;
            lines.push(`|${slot}/${phase}|${fmt(delta)}%|${entry.qualityCaveats.join('; ') || entry.applicability}|`);
        }
    }
}
for (const [slot, entry] of Object.entries(result.slots)) for (const [phase, p] of Object.entries(entry.bench.phases)) {
    const t = p.frameTimeMs, fps = p.intervalFps;
    lines.push(`|${slot}/${phase}|${p.count}/${p.dropped}|${[fps.min, fps.max, fps.mean].map(fmt).join('/')}|${fmt(p.aggregateFps)}|${[t.min, t.max, t.mean].map(fmt).join('/')}|${[t.median, t.p95, t.p99].map(fmt).join('/')}|`);
}
for (const [slot, entry] of Object.entries(result.slots)) {
    lines.push('', `## ${slot} Bench 长帧`, '', `[Bench summary](${entry.bench.evidence.summary.replaceAll('\\', '/')})；[Bench 原始样本](${entry.bench.evidence.samples.replaceAll('\\', '/')})`, '',
        '阈值严格采用 Bench 的 16666667 / 33333334 / 50000000 / 100000000 ns；各档嵌套不可相加。累计帧时长含完整长帧，超额时长只累计超过阈值的部分。', '',
        '|Bench阶段/严格阈值 ns|次数（Bench）|累计帧时 ms|超额 ms|帧比例|时长比例|连续段数|最长连续段 ms|', '|---|---:|---:|---:|---:|---:|---:|---:|');
    for (const [phase, p] of Object.entries(entry.bench.phases)) for (const [threshold, l] of Object.entries(p.longFrames)) {
        lines.push(`|${phase}/>${threshold}|${l.count}|${fmt(l.frameDurationMs)}|${fmt(l.excessMs)}|${fmt(l.frameRatio)}|${fmt(l.durationRatio)}|${l.consecutiveSegments}|${fmt(l.longestSegmentMs)}|`);
    }
}
lines.push('', ...headLines);

for (const [slot, entry] of Object.entries(result.slots)) for (const [phase, p] of Object.entries(entry.phases)) {
    const t = p.frameTimeMs, fps = p.intervalFps;
    lines.push(`|${slot}/${phase}|${t.count}|${[fps.min, fps.max, fps.mean].map(fmt).join('/')}|${fmt(p.aggregateFps)}|${[t.min, t.max, t.mean].map(fmt).join('/')}|${[t.p50, t.p95, t.p99].map(fmt).join('/')}|`);
}
for (const [slot, entry] of Object.entries(result.slots)) {
    lines.push('', `## ${slot} — ${entry.attempt}`, '', ...Object.entries(entry.evidence).flatMap(([k, v]) => (Array.isArray(v) ? v : [v]).filter(Boolean).map(p => `[${k}](${p.replaceAll('\\', '/')})`)), '', '|阶段/严格阈值 ms|次数|帧时总和 ms|超额 ms|帧比例|时长比例|连续段数|最长段 ms|', '|---|---:|---:|---:|---:|---:|---:|---:|');
    for (const [phase, p] of Object.entries(entry.phases)) for (const [threshold, l] of Object.entries(p.longFrames)) lines.push(`|${phase}/>${threshold}|${l.count}|${fmt(l.frameDurationMs)}|${fmt(l.excessMs)}|${fmt(l.frameRatio)}|${fmt(l.durationRatio)}|${l.consecutiveSegments}|${fmt(l.longestSegmentMs)}|`);
    lines.push('', '跨边界间隔（归 endPhase，独立于主统计；含 capture cross-start）：', '```json', JSON.stringify(entry.crossBoundary, null, 2), '```', '', '原生窗口日志证据：', '```json', JSON.stringify(entry.nativeWindowEvidence, null, 2), '```');
    lines.push('', '环境（tick 样本，非逐帧）与独立阶段摘要：', '', '```json', JSON.stringify(Object.fromEntries(Object.entries(entry.phases).map(([k, p]) => [k, {environment: p.environment, stages: p.stages, counts: p.counts}])), null, 2), '```');
}
lines.push('', '## 每格状态（失败/未知不补零）', '', '|配置|状态|原因|', '|---|---|---|', ...Object.entries(result.caseStatus).map(([slot, c]) => `|${slot}|${c.status}|${(c.reason ?? '').replaceAll('\n', ' ').replaceAll('|', '/') }|`));
lines.push('', `缺失：${result.missing.join(', ') || '无'}`, '', '拒绝记录：', '```json', JSON.stringify(result.rejected, null, 2), '```');
const outputs = [['--out', JSON.stringify(result, null, 2)], ['--markdown', lines.join('\n')]];
for (const [key] of outputs) if (args[key] && fs.existsSync(args[key])) throw Error(`Refusing overwrite: ${args[key]}`);
if (args['--out'] && args['--markdown'] && path.resolve(args['--out']) === path.resolve(args['--markdown'])) throw Error('Output paths must differ');
for (const [key, content] of outputs) if (args[key]) fs.writeFileSync(args[key], content + '\n', {flag: 'wx'});
console.log(args['--out'] || args['--markdown'] ? JSON.stringify({analysisNotice, batch: result.batch, selected: Object.keys(result.slots), missing: result.missing, rejected: result.rejected}) : JSON.stringify(result, null, 2));
