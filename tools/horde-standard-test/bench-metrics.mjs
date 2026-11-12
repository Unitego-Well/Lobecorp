import fs from 'node:fs';
import path from 'node:path';

export const LONG_FRAMES = [
    {ns: 16666667, key: 'over_16_67ms_frames'},
    {ns: 33333334, key: 'over_33_33ms_frames'},
    {ns: 50000000, key: 'over_50ms_frames'},
    {ns: 100000000, key: 'over_100ms_frames'}
];

// 汇总值来自 BenchMod；这里只为已有原始样本补充尚未导出的时长和连续段。
export function analyzeBench(dir, scenario) {
    const summaryPath = path.join(dir, 'results/summary.json');
    const samplesPath = path.join(dir, 'results/artifacts/samples', scenario + '.jsonl');
    const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf8'));
    const matches = summary.scenarios.filter(s => s.id === scenario);
    if (matches.length !== 1 || matches[0].status !== 'PASSED') throw Error('Bench scenario not uniquely passed');
    const records = fs.readFileSync(samplesPath, 'utf8').split(/\r?\n/).filter(Boolean).map(JSON.parse)
        .filter(s => s.metric === 'client.frame.interval');
    if (!matches[0].metrics.some(m => m.name === 'client.frame.interval') || !records.length) throw Error('Bench frame metrics/raw samples missing');
    const phases = {};
    for (const metric of matches[0].metrics.filter(m => m.name === 'client.frame.interval')) {
        const chunks = records.filter(s => s.phase === metric.phase);
        if (!chunks.length || metric.unit !== 'ns' || chunks.some(s => s.unit !== 'ns')) throw Error('Bench sample units missing or different');
        const values = chunks.flatMap(s => s.values);
        const dropped = chunks.reduce((sum, s) => sum + s.dropped, 0);
        if (values.length !== metric.count || dropped !== metric.dropped || values.some(n => !Number.isSafeInteger(n) || n <= 0)) throw Error('Bench count/dropped/value mismatch');
        const totalNs = values.reduce((sum, n) => sum + n, 0);
        if (values.length && (values.reduce((a, n) => Math.min(a, n), Infinity) !== metric.min || values.reduce((a, n) => Math.max(a, n), -Infinity) !== metric.max
            || Math.abs(totalNs / values.length - metric.mean) > 1)) throw Error('Bench raw and API summary disagree');
        const longFrames = {};
        for (const threshold of LONG_FRAMES) {
            let count = 0, durationNs = 0, excessNs = 0, segments = 0, longestNs = 0, currentNs = 0;
            for (const n of values) {
                if (n > threshold.ns) {
                    if (!currentNs) segments++;
                    currentNs += n;
                    longestNs = Math.max(longestNs, currentNs);
                    count++; durationNs += n; excessNs += n - threshold.ns;
                } else currentNs = 0;
            }
            if (count !== metric.extra[threshold.key]) throw Error('Bench long-frame count disagrees: ' + metric.phase + '/' + threshold.key);
            longFrames[threshold.ns] = {thresholdNs: threshold.ns, count: metric.extra[threshold.key],
                frameDurationMs: durationNs / 1e6, excessMs: excessNs / 1e6,
                frameRatio: values.length ? count / values.length : null,
                durationRatio: totalNs ? durationNs / totalNs : null,
                consecutiveSegments: segments, longestSegmentMs: longestNs / 1e6};
        }
        const frameTimeMs = {};
        for (const k of ['min', 'max', 'mean', 'median', 'p90', 'p95', 'p99', 'stdDev']) frameTimeMs[k] = metric[k] / 1e6;
        phases[metric.phase] = {count: metric.count, dropped: metric.dropped,
            frameTimeMs, intervalFps: {min: 1e9 / metric.max, max: 1e9 / metric.min,
                mean: values.length ? values.reduce((sum, n) => sum + 1e9 / n, 0) / values.length : null},
            aggregateFps: metric.count ? 1e9 / metric.mean : null,
            coveredIntervalMs: totalNs / 1e6, longFrames, apiSummary: metric};
    }
    return {source: 'BenchMod 0.1.5-beta exported client.frame.interval summary and ordered raw samples',
        evidence: {summary: summaryPath, samples: samplesPath}, phases,
        scope: 'Bench phases are authoritative; raw samples have no timestamps and cannot be cut by custom nanosecond windows',
        countSource: 'Bench summary.extra; verified against raw values',
        durationSource: 'derived from existing Bench raw values; no additional game collector',
        thresholds: 'strict > 16666667 / 33333334 / 50000000 / 100000000 ns; nested, do not add'};
}
