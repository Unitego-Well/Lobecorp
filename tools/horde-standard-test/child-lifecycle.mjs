// Original ChildProcess lifecycle only: no PID lookup, polling or process termination here.
function fail(code) {const error = new Error(code); error.code = code; throw error;}
export function trackChild(child, {runId, runtimeFingerprint, onExit = () => {}, onError = () => {}}) {
    const record = {child, runId, runtimeFingerprint, binding: null, exit: null, error: null, waiters: new Set()};
    const notify = () => {for (const waiter of [...record.waiters]) waiter();};
    child.once('error', error => {record.error = {code: error.code ?? 'CHILD_ERROR'}; onError(record.error); notify();});
    child.once('exit', (code, signal) => {
        record.exit = {runId, pid: child.pid ?? null, code, signal, runtimeFingerprint,
            initialCreationDate: record.binding?.creationDate ?? null, verified: record.binding !== null};
        onExit({...record.exit}); notify();
    });
    return record;
}
export function bindVerifiedChild(record, {owned, runId, runtimeFingerprint, classpathVerified, portVerified}) {
    if (!record || record.exit || record.error || record.binding) fail('CHILD_BINDING_UNAVAILABLE');
    if (record.runId !== runId || record.runtimeFingerprint !== runtimeFingerprint || owned?.ProcessId !== record.child.pid
        || !Number.isSafeInteger(owned?.ProcessId) || typeof owned?.CreationDate !== 'string' || !owned.CreationDate
        || classpathVerified !== true || portVerified !== true) fail('CHILD_BINDING_MISMATCH');
    record.binding = {runId, pid: owned.ProcessId, creationDate: owned.CreationDate, runtimeFingerprint, verified: true};
}
export function waitChildExit(record, timeoutMs = 60000) {
    if (!record) return Promise.reject(Object.assign(new Error('CHILD_RECORD_MISSING'), {code:'CHILD_RECORD_MISSING'}));
    return new Promise((resolve, reject) => {
        let timer;
        const finish = (error, value) => {clearTimeout(timer); record.waiters.delete(check); error ? reject(error) : resolve(value);};
        const check = () => {
            if (record.error) finish(Object.assign(new Error('CHILD_ERROR'), {code:'CHILD_ERROR'}));
            else if (record.exit) finish(null, record.exit);
        };
        record.waiters.add(check);
        timer = setTimeout(() => finish(Object.assign(new Error('CHILD_EXIT_TIMEOUT'), {code:'CHILD_EXIT_TIMEOUT'})), timeoutMs);
        check();
    });
}
export async function verifyCompletedChild(record, {owned, runId, runtimeFingerprint, isCompleted, timeoutMs = 60000}) {
    const b = record?.binding;
    if (!b) fail('CHILD_NOT_VERIFIED');
    if (b.runId !== runId || record.runId !== runId || b.pid !== owned?.ProcessId || b.creationDate !== owned?.CreationDate
        || b.runtimeFingerprint !== runtimeFingerprint || record.runtimeFingerprint !== runtimeFingerprint) fail('CHILD_COMPLETION_IDENTITY_MISMATCH');
    if (typeof isCompleted !== 'function' || await isCompleted() !== true) fail('CHILD_EXPORTS_INCOMPLETE');
    const exit = await waitChildExit(record, timeoutMs);
    if (exit.runId !== b.runId || exit.pid !== b.pid || exit.runtimeFingerprint !== b.runtimeFingerprint
        || exit.initialCreationDate !== b.creationDate || exit.verified !== true) fail('CHILD_EXIT_BINDING_MISMATCH');
    if (exit.code !== 0 || exit.signal !== null) fail('CHILD_ABNORMAL_EXIT');
    if (await isCompleted() !== true) fail('CHILD_EXPORTS_INCOMPLETE');
    return {state:'completed-owned-child', runId, pid:b.pid, exitCode:exit.code, signal:exit.signal,
        creationDate:b.creationDate, runtimeFingerprint, verified:true};
}
