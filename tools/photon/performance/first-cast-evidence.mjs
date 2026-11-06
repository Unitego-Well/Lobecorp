import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';

export function fileHash(file) {
    const digest = createHash('sha256');
    const buffer = Buffer.alloc(65536);
    const fd = fs.openSync(file, 'r');
    try {
        let length;
        while ((length = fs.readSync(fd, buffer, 0, buffer.length, null)) > 0) digest.update(buffer.subarray(0, length));
    } finally {
        fs.closeSync(fd);
    }
    return digest.digest('hex');
}

const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const inside = (base, file) => {
    const relative = path.relative(base, file);
    if (relative.startsWith('..') || path.isAbsolute(relative)) throw Error('Artifact escapes results: ' + file);
    return file;
};

export function parseStages(log) {
    const events = [];
    for (const line of log.split(/\r?\n/)) {
        if (!line.includes('PHOTONSTAGE stage=')) continue;
        const fields = Object.fromEntries([...line.matchAll(/(\w+)=([^\s]+)/g)].map(match => [match[1], match[2]]));
        const ns = Number(fields.ns);
        if (!Number.isSafeInteger(ns) || ns <= 0) throw Error('Invalid stage clock: ' + fields.stage);
        events.push({...fields, ns});
    }
    return events;
}

function distribution(values) {
    if (!values.length) return {count: 0, mean: null, p50: null, p95: null, p99: null, max: null};
    const sorted = [...values].sort((a, b) => a - b);
    const percentile = p => sorted[Math.ceil(sorted.length * p) - 1];
    return {
        count: sorted.length,
        mean: sorted.reduce((sum, value) => sum + value, 0) / sorted.length,
        p50: percentile(0.5), p95: percentile(0.95), p99: percentile(0.99), max: sorted.at(-1)
    };
}

function properties(arguments_) {
    const result = {};
    for (const argument of arguments_ ?? []) {
        const match = /^-D([^=]+)=(.*)$/.exec(argument);
        if (match) result[match[1]] = match[2];
    }
    return result;
}

export function auditCase(row, profile, source) {
    const checks = {};
    try {
        const resultDir = path.join(row.dir, 'results');
        const summary = readJson(path.join(resultDir, 'summary.json'));
        const custom = path.join(resultDir, 'artifacts/custom');
        const workload = readJson(path.join(custom, profile.label + '-workload.json'));
        const data = readJson(path.join(custom, profile.label + '-frames.json'));
        const prewarmData = readJson(path.join(custom, profile.label + '-prewarm-frames.json'));
        const spark = readJson(path.join(custom, profile.label + '-spark-teardown.json'));
        const details = readJson(path.join(custom, profile.label + '-deserialize-details.json'));
        const observer = readJson(path.join(row.dir, 'screenshot-after-measure.json'));
        const intent = readJson(path.join(row.dir, 'launch-intent.json'));
        const process = readJson(path.join(row.dir, 'process.json'));
        const log = fs.readFileSync(path.join(row.cwd, 'logs/latest.log'), 'utf8');
        const events = parseStages(log);
        const one = name => {
            const found = events.filter(event => event.stage === name);
            if (found.length !== 1) throw Error('Expected one ' + name + ', found ' + found.length);
            return found[0];
        };
        const settled = one('world_settled');
        const fixture = one('fixture_queue');
        const captureRequest = one('main_capture_request');
        const stop = one('spark_stop_request');
        const capture = workload.capture;
        const raw = data.frames;
        const count = capture?.frame_count;
        const selected = summary.scenarios?.filter(scenario => scenario.id === profile.scenario) ?? [];
        checks.selected_scenario = selected.length === 1 && selected[0].status === 'PASSED' && selected[0].workloadCorrect === true;
        checks.unselected_scenarios = summary.scenarios?.filter(scenario => scenario.id !== profile.scenario).every(scenario => scenario.status === 'SKIPPED') === true;
        checks.identity = intent.batch === source.batch && intent.id === row.id && intent.slot === row.slot
            && intent.head === source.head && intent.fingerprint === source.fingerprint.sha256
            && intent.world === row.world && process.ProcessId === observer.pid;
        const vm = properties(summary.environment?.java?.jvmArgs);
        const expected = {
            ...profile.properties,
            'modBench.client.worldId': row.world,
            'modBench.resultDirectory': path.join(row.dir, 'results').replaceAll('\\', '/'),
            'lobecorp.photon.bench.shader': row.shader,
            'lobecorp.photon.run.batchId': source.batch,
            'lobecorp.photon.run.sourceCommit': source.head,
            'lobecorp.photon.run.sourceVariant': source.variant
        };
        checks.effective_jvm = Object.entries(expected).every(([key, value]) => String(vm[key]).replaceAll('\\', '/') === String(value).replaceAll('\\', '/'));
        checks.no_extra_jfr_start_argument = !summary.environment?.java?.jvmArgs?.some(argument => argument.startsWith('-XX:StartFlightRecording'));
        checks.world = workload.world_name === row.world + '-flat'
            && log.includes('event=world_open world=' + row.world)
            && workload.world_settle_required_ns >= profile.worldStableNs
            && workload.world_settle_elapsed_ns >= profile.worldStableNs
            && workload.world_settled_ns - workload.world_available_ns >= profile.worldStableNs
            && settled.ns === workload.world_settled_ns
            && events.some(event => event.stage === 'world_available' && event.ns === workload.world_available_ns)
            && !events.some(event => event.stage === 'world_settle_reset' && event.ns > workload.world_available_ns && event.ns < settled.ns);
        checks.delayed_fixture = fixture.ns === workload.fixture_setup_triggered_ns
            && fixture.ns >= settled.ns && fixture.ns >= spark.started_ack_ns
            && fixture.ns < captureRequest.ns && spark.started_ack_ns >= settled.ns;
        const warmup = selected[0]?.phases?.find(phase => phase.phase === 'WARMUP');
        checks.warmup = warmup?.outcome === 'completed' && warmup.durationTicks === profile.warmupTicks;
        checks.primary_window = workload.primary_capture_seconds === profile.primarySeconds
            && capture?.state === 'DURATION' && workload.capture_mode === 'natural_wall_duration' && capture.requested_seconds === profile.primarySeconds
            && capture.elapsed_seconds >= profile.primarySeconds && !capture.error
            && capture.started_ns >= captureRequest.ns && stop.ns >= capture.started_ns + profile.primarySeconds * 1e9
            && workload.main_capture_started_ns === capture.started_ns;
        checks.no_primary_visual_validation = workload.evidence_mode === 'PERFORMANCE'
            && workload.comparison_group === 'laser' && workload.image_validation === 'NOT_USED'
            && workload.bench_measure_includes_visual_validation === false;
        checks.recording_depth_argument = summary.environment?.java?.jvmArgs?.includes('-XX:FlightRecorderOptions=stackdepth=256') === true;
        checks.first_cast_only = workload.cold_first_emit === true && capture.configuration_changed_by_capture === false;
        const prewarm = workload.setup_prewarm_capture;
        checks.fixture_preparation_frames = prewarm?.state === 'STOPPED' && !prewarm.error && prewarm.frame_count > 1
            && prewarmData.summary?.frame_count === prewarm.frame_count
            && Object.values(prewarmData.frames ?? {}).every(column => Array.isArray(column) && column.length === prewarm.frame_count)
            && prewarmData.frames?.interval_ns?.slice(1).every((value, index) => Number.isSafeInteger(value) && value >= 0
                && value === prewarmData.frames.head_offset_ns[index + 1] - prewarmData.frames.head_offset_ns[index]);
        const phases = capture?.phases ?? [];
        checks.scheduled_cast = phases.length === 1 && phases[0].scheduled_seconds === profile.castAtSeconds
            && phases[0].triggered_seconds >= profile.castAtSeconds && phases[0].triggered_seconds < profile.primarySeconds
            && phases[0].error == null && workload.measured_casts === 1 && workload.verification_cast_started_ns === 0;
        const marks = workload.beam_marks?.filter(mark => mark.verification === false) ?? [];
        const casts = marks.filter(mark => mark.event === 'server_cast_completed');
        const clientPhases = marks.filter(mark => mark.event === 'client_phase');
        checks.real_target = workload.beam_targets?.length === 1
            && typeof workload.beam_targets[0].target_uuid === 'string'
            && workload.beam_targets[0].max_health === profile.targetMaxHealth
            && workload.real_workload_verified === true;
        checks.cast_lifecycle = casts.length === 1 && casts[0].status === 'STARTED'
            && casts[0].observed_ns > capture.started_ns && casts[0].observed_ns < stop.ns
            && [0, 1, 2].every(phase => clientPhases.filter(mark => mark.phase === phase).length === 1)
            && marks.filter(mark => mark.event === 'client_removed').length === 1
            && marks.every((mark, index) => index === 0 || mark.observed_ns >= marks[index - 1].observed_ns);
        const intervals = raw?.interval_ns;
        checks.raw_frames = Number.isInteger(count) && count > 1 && Array.isArray(intervals)
            && Object.values(raw).every(column => Array.isArray(column) && column.length === count)
            && data.summary?.frame_count === count && workload.main_capture_frames === count
            && capture.frame_interval_ms?.count === count - 1
            && intervals.slice(1).every((value, index) => Number.isSafeInteger(value) && value >= 0
                && value === raw.head_offset_ns[index + 1] - raw.head_offset_ns[index]);
        const [width, height] = profile.expectedDimensions;
        checks.dimensions = ['framebuffer_width', 'window_width'].every(key => raw[key]?.every(value => value === width))
            && ['framebuffer_height', 'window_height'].every(key => raw[key]?.every(value => value === height));
        checks.visible_window = Number.isInteger(data.flag_bits?.iconified)
            && raw.flags?.every(value => (value & data.flag_bits.iconified) === 0);
        checks.camera = workload.camera_gate_verified === true && workload.pose_gate_verified === true
            && workload.fixed_camera_observed === true && workload.fixed_window_observed === true;
        checks.no_input_capture = workload.setup_focus_requests === 0
            && workload.focus_audit?.focus_restore_requests === 0 && workload.mouse_grabbed_observed === false
            && workload.bench_input_isolation_contract_active === true;
        checks.shader = workload.shaderpack_enabled === row.shader && workload.expected_shaderpack_enabled === row.shader;
        checks.sodium = summary.environment?.loadedMods?.some(mod => mod.id === 'sodium') === true && typeof workload.sodium === 'string';
        checks.screenshot_after_primary = observer.world === row.world && observer.port === profile.port
            && observer.observed_stop_ns === stop.ns && observer.observed_main_ns === captureRequest.ns
            && observer.causal_order === 'read_spark_stop_request_then_request_screenshot'
            && observer.shot?.sourceWidth === width && observer.shot?.sourceHeight === height
            && observer.shot?.size > 0 && observer.gui_world_visible === true
            && fs.statSync(path.join(row.dir, 'scene-after-measure.png')).size > 0;
        checks.spark = spark.state === 'COMPLETE' && spark.archived === true && spark.closed === true
            && spark.cleanup_completion_confirmed === true && spark.owns_sampler === false
            && spark.cleanup_unconfirmed === false && !spark.failure && !spark.message_overflow
            && workload.spark_client?.profile_sha256 === spark.profile_sha256;
        checks.detailed_capture = details.complete === true && details.truncated === false && details.dropped === 0
            && details.pendingBoundaries === 0 && details.startedBoundaries === details.completedBoundaries;
        const recording = details.bench_jfr;
        checks.bench_jfr = recording?.recording_name === 'modbench:' + profile.scenario
            && recording.game_pid === process.ProcessId && recording.recording_id > 0
            && recording.recording_state === 'RUNNING' && recording.settings_verified === true
            && ['jdk.ClassLoad', 'jdk.ClassDefine', 'jdk.FileRead', 'jdk.FileWrite', 'jdk.JavaMonitorEnter'].every(event =>
                recording.actual_settings?.[event + '#enabled'] === 'true'
                && recording.actual_settings?.[event + '#stackTrace'] === 'true'
                && recording.actual_settings?.[event + '#threshold'] === (event === 'jdk.ClassLoad' || event === 'jdk.ClassDefine' ? '0 ns' : '1000000 ns'));
        const artifacts = summary.artifacts ?? [];
        checks.artifact_integrity = artifacts.length > 0 && artifacts.every(artifact => {
            const file = inside(resultDir, path.resolve(resultDir, artifact.path));
            return fs.existsSync(file) && (!Number.isFinite(artifact.bytes) || fs.statSync(file).size === artifact.bytes)
                && (!artifact.sha256 || fileHash(file) === artifact.sha256);
        });
        const profiles = artifacts.filter(artifact => artifact.path.endsWith('.sparkprofile'));
        checks.spark_file = profiles.length === 1 && spark.profile_bytes > 0
            && fs.statSync(inside(resultDir, path.resolve(resultDir, profiles[0].path))).size === spark.profile_bytes
            && fileHash(path.resolve(resultDir, profiles[0].path)) === spark.profile_sha256;
        const jfrs = artifacts.filter(artifact => artifact.path.endsWith('.jfr'));
        checks.jfr_file = jfrs.length === 1;
        let jfrHash = null;
        if (checks.jfr_file) {
            const file = inside(resultDir, path.resolve(resultDir, jfrs[0].path));
            const header = Buffer.alloc(4);
            const fd = fs.openSync(file, 'r');
            try {
                fs.readSync(fd, header, 0, 4, 0);
            } finally {
                fs.closeSync(fd);
            }
            checks.jfr_file = fs.statSync(file).size > 4 && header.toString('hex') === '464c5200';
            jfrHash = fileHash(file);
        }
        const frameMs = intervals.slice(1).map(value => value / 1e6);
        return {
            schema: profile.schema,
            id: row.id,
            slot: row.slot,
            collection_valid: Object.values(checks).every(Boolean),
            checks,
            performance_verdict: 'NOT_EVALUATED',
            primary: {
                ...distribution(frameMs), unit: 'ms', scope: capture.frame_interval_scope,
                over_16_67ms: frameMs.filter(value => value > 16.67).length,
                over_33_33ms: frameMs.filter(value => value > 33.33).length,
                over_50ms: frameMs.filter(value => value > 50).length,
                over_100ms: frameMs.filter(value => value > 100).length
            },
            startup_and_world_excluded: {
                process_created: process.CreationDate,
                bench_started_at: summary.run?.startedAt,
                setup_observed_ns: workload.setup_observed_ns,
                world_available_ns: workload.world_available_ns,
                world_settled_ns: workload.world_settled_ns,
                scope: 'Startup and world entry before delayed fixture; preserved in process/Bench/log artifacts'
            },
            fixture_preparation_window: {
                included_in_optimization_scope: true,
                fixture_ns: fixture.ns,
                main_ns: captureRequest.ns,
                summary: prewarm?.frame_interval_ms ?? null,
                capture_started_ns: prewarm?.started_ns ?? null,
                setup_triggered_ns: prewarm?.setup_triggered_ns ?? null,
                pose_gate_ready_ns: prewarm?.pose_gate_ready_ns ?? null,
                scope: 'Separate setup-prewarm window, never added to primary; may start after fixture queue and is not complete creation CPU'
            },
            gpu: {
                available: capture.gpu_available,
                scope: capture.gpu_scope,
                sampled_frames: raw.render_gpu_ns.filter(value => value >= 0).length,
                total_frames: count,
                distribution: capture.render_gpu_ms ?? null,
                skipped_pending: capture.gpu_skipped_pending,
                unavailable: capture.gpu_unavailable ?? null
            },
            allocation: {
                scope: capture.allocation_scope, distribution: capture.render_thread_allocated_bytes ?? null,
                unavailable: capture.allocation_unavailable ?? null
            },
            environment: {
                quiet_mode: source.quietMode, note: source.environmentNote,
                focused: workload.focus_audit, first: capture.environment_first, last: capture.environment_last,
                throttling: Object.fromEntries([...new Set(raw.throttle_reason)].map(reason => [reason, raw.throttle_reason.filter(value => value === reason).length]))
            },
            precision: {
                attribution_complete: details.attributionComplete,
                missing: details.missing,
                input_isolation_runtime: workload.bench_input_isolation_runtime_verification,
                lease_gpu_drain_acceptance: null,
                cast_lifecycle_scope: 'Reported skill phases and removal, not complete lease/GPU-drain acceptance',
                target_hit_damage_values: null,
                target_evidence: 'Scenario fixture contract plus UUID/health/lifecycle; no hit-count or damage artifact',
                jfr_stack_depth_requested: 256,
                jfr_stack_depth_actual: null,
                jfr_uniqueness_scope: 'Scenario verifies one RUNNING recording of the expected name; other names are not enumerated here',
                architecture_cache_metrics: null,
                architecture_cache_metrics_reason: 'No dedicated prepared_project_* artifact in this scenario; not inferred from registration',
                interval_render_alignment: 'CPU/GPU body i-1 corresponds to interval i; CPU and GPU are not added',
                gpu_coverage_is_partial: raw.render_gpu_ns.filter(value => value >= 0).length < count,
                comparison_scope: 'Same runner/profile/environment only; historical Debug captures are not strict A/B'
            },
            jfr_sha256: jfrHash
        };
    } catch (error) {
        return {
            schema: profile.schema, id: row.id, slot: row.slot, collection_valid: false, checks, error: error.message,
            performance_verdict: 'NOT_EVALUATED'
        };
    }
}
