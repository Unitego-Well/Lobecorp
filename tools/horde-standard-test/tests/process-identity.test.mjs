import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyCompletedOwner} from '../process-identity.mjs';

const owned = {ProcessId: 4123, CreationDate: '2026-11-11T10:00:00Z', CommandLine: 'private-classpath'};
const same = {ProcessId: owned.ProcessId, CreationDate: owned.CreationDate, CommandLine: 'matched-row'};
function fixture(rows = [same], live = [true], completion = true) {
    const calls = [];
    let infoIndex = 0, liveIndex = 0;
    const deps = {
        owned,
        readInfo: async pid => {
            assert.equal(pid, owned.ProcessId);
            calls.push('info');
            const value = rows[infoIndex++];
            if (value instanceof Error) throw value;
            return value;
        },
        isAlive: async pid => {
            assert.equal(pid, owned.ProcessId);
            calls.push('alive');
            const value = live[liveIndex++];
            if (value instanceof Error) throw value;
            return value;
        },
        matchesRow: async row => {calls.push('match'); return row.CommandLine === same.CommandLine;},
        isCompleted: async () => {calls.push('completed'); return completion;},
        sleep: async ms => {assert.equal(ms, 50); calls.push('sleep');}
    };
    return {deps, calls};
}
async function rejected(f, code) {
    await assert.rejects(() => verifyCompletedOwner(f.deps), error => {
        assert.equal(error.code, code);
        assert.equal(error.diagnostics.pid, owned.ProcessId);
        assert.ok(!JSON.stringify(error).includes('private-classpath'));
        assert.ok(!JSON.stringify(error).includes('secret-classpath'));
        assert.equal(error.cause, undefined);
        return true;
    });
}

test('same-live requires complete matching identity and current liveness', async () => {
    const f = fixture();
    assert.deepEqual(await verifyCompletedOwner(f.deps), {state: 'same-live'});
    assert.deepEqual(f.calls, ['info', 'match', 'alive']);
});
test('different creation date hard fails and preserves explicit dates', async () => {
    const f = fixture([{...same, CreationDate: '2026-11-11T10:01:00Z'}]);
    await assert.rejects(() => verifyCompletedOwner(f.deps), error => {
        assert.equal(error.code, 'CREATION_DATE_MISMATCH');
        assert.equal(error.diagnostics.expectedCreationDate, owned.CreationDate);
        assert.equal(error.diagnostics.observations[0].creationDate, '2026-11-11T10:01:00Z');
        return true;
    });
    assert.deepEqual(f.calls, ['info']);
});
test('different commandLine hard fails without serializing command', async () => {
    const f = fixture([{...same, CommandLine: 'secret-classpath'}]);
    await rejected(f, 'COMMAND_LINE_MISMATCH');
    assert.deepEqual(f.calls, ['info', 'match']);
});
test('fully absent then two fresh info+alive confirmations accepts completed export', async () => {
    const f = fixture([null, null, null], [false, false, false]);
    assert.deepEqual(await verifyCompletedOwner(f.deps), {state: 'completed-exited'});
    assert.deepEqual(f.calls, ['info', 'alive', 'sleep', 'info', 'alive', 'sleep', 'info', 'alive', 'completed']);
});
test('CIM absent but process alive rejects without completion exception', async () => {
    const f = fixture([null, null, null, null], [true, true, true, true]);
    await rejected(f, 'ABSENT_BUT_ALIVE');
    assert.equal(f.calls.filter(value => value === 'info').length, 4);
    assert.equal(f.calls.filter(value => value === 'sleep').length, 3);
    assert.ok(!f.calls.includes('completed'));
});
for (const confirmation of [1, 2]) {
    test(`PID reuse at fresh confirmation ${confirmation} hard fails`, async () => {
        const rows = Array(confirmation).fill(null).concat({...same, CreationDate: 'reused'});
        const f = fixture(rows, Array(confirmation).fill(false));
        await rejected(f, 'CREATION_DATE_MISMATCH');
        assert.equal(f.calls.filter(x => x === 'info').length, confirmation + 1);
        assert.ok(!f.calls.includes('completed'));
    });
}
test('same creation date with changed command after absence rejects', async () => {
    await rejected(fixture([null, {...same, CommandLine: 'secret-classpath'}], [false]), 'COMMAND_LINE_MISMATCH');
});
test('same owner reappearing after null returns same-live, never completed-exited', async () => {
    for (const initiallyAlive of [false, true]) {
        const f = fixture([null, same], [initiallyAlive, true], false);
        assert.deepEqual(await verifyCompletedOwner(f.deps), {state: 'same-live'});
        assert.ok(!f.calls.includes('completed'));
    }
});
test('second confirmation absent but alive rejects', async () => {
    await rejected(fixture([null, null, null, null], [false, false, true, true]), 'ABSENT_BUT_ALIVE');
});
for (const missing of ['CreationDate', 'CommandLine']) {
    test(`partial metadata ${missing} is not same-live or completed-exited`, async () => {
        await rejected(fixture([{...same, [missing]: null}], [false]), 'IDENTITY_METADATA_MISSING');
    });
}
test('undefined info is invalid, only explicit null represents absent', async () => {
    await rejected(fixture([undefined]), 'INVALID_IDENTITY_SNAPSHOT');
});
test('wrong PID in nonnull snapshot rejects', async () => {
    await rejected(fixture([{...same, ProcessId: 99}]), 'PID_MISMATCH');
});
test('nonnull row with false liveness is not accepted as exited', async () => {
    await rejected(fixture([same, same, same, same], [false, false, false, false]), 'PRESENT_BUT_NOT_ALIVE');
});
for (const field of ['benchPassed', 'workloadCorrect', 'collectionComplete', 'encounterRemoved']) {
    test(`missing ${field} export evidence refuses completed exit`, async () => {
        const evidence = {benchPassed: true, workloadCorrect: true, collectionComplete: true, encounterRemoved: true};
        evidence[field] = false;
        const f = fixture([null, null, null], [false, false, false]);
        f.deps.isCompleted = async () => Object.values(evidence).every(value => value === true);
        await rejected(f, 'COMPLETION_NOT_CONFIRMED');
        assert.equal(f.calls.filter(value => value === 'info').length, 3);
    });
}
test('truthy completion object cannot stand in for strict true', async () => {
    await rejected(fixture([null, null, null], [false, false, false], {passed: true}), 'COMPLETION_NOT_CONFIRMED');
});
for (const at of [0, 1, 2]) {
    test(`CIM throw at observation ${at} refuses and sanitizes error`, async () => {
        const f = fixture(Array(at).fill(null).concat(new Error('secret-classpath')), Array(at).fill(false));
        await rejected(f, 'IDENTITY_READ_FAILED');
    });
}
test('liveness reader throw refuses', async () => {
    await rejected(fixture([null], [new Error('secret-classpath')]), 'LIVENESS_READ_FAILED');
});
test('completion reader throw refuses after fresh checks', async () => {
    const f = fixture([null, null, null], [false, false, false]);
    f.deps.isCompleted = async () => {throw new Error('secret-classpath');};
    await rejected(f, 'COMPLETION_READ_FAILED');
});
test('wait failure refuses without additional stale observations', async () => {
    const f = fixture([null], [false]);
    f.deps.sleep = async () => {throw new Error('secret-classpath');};
    await rejected(f, 'EXIT_CONFIRMATION_WAIT_FAILED');
});
test('matches reader throw refuses without leaking exception', async () => {
    const f = fixture();
    f.deps.matchesRow = async () => {throw new Error('secret-classpath');};
    await rejected(f, 'COMMAND_MATCH_CHECK_FAILED');
});

test('matching row then exit requires two additional fresh absent+dead confirmations', async () => {
    const f = fixture([same, null, null], [false, false, false]);
    assert.deepEqual(await verifyCompletedOwner(f.deps), {state: 'completed-exited'});
    assert.deepEqual(f.calls, ['info', 'match', 'alive', 'sleep', 'info', 'alive', 'sleep', 'info', 'alive', 'completed']);
});
test('matching row then exit without complete export still refuses', async () => {
    await rejected(fixture([same, null, null], [false, false, false], false), 'COMPLETION_NOT_CONFIRMED');
});
test('initial null+alive may settle to two fresh null+dead confirmations', async () => {
    const f = fixture([null, null, null], [true, false, false]);
    assert.deepEqual(await verifyCompletedOwner(f.deps), {state: 'completed-exited'});
});
test('initial null+alive then different ctime is immediate hard failure', async () => {
    const f = fixture([null, {...same, CreationDate: 'reused'}], [true]);
    await rejected(f, 'CREATION_DATE_MISMATCH');
    assert.deepEqual(f.calls, ['info', 'alive', 'sleep', 'info']);
});
test('matching row dead then matching alive recovery is same-live', async () => {
    const f = fixture([same, same], [false, true], false);
    assert.deepEqual(await verifyCompletedOwner(f.deps), {state: 'same-live'});
    assert.ok(!f.calls.includes('completed'));
});
test('intervening null+alive resets consecutive exit confirmation count', async () => {
    const f = fixture([null, null, null, null], [false, false, true, false]);
    await rejected(f, 'EXIT_NOT_CONFIRMED');
    assert.ok(!f.calls.includes('completed'));
});
test('partial metadata during retry still fails immediately', async () => {
    await rejected(fixture([null, {...same, CommandLine: null}], [true]), 'IDENTITY_METADATA_MISSING');
});
