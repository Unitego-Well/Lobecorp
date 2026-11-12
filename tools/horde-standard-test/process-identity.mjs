// 仅供collect完成阶段识别正常退出；不得用于cleanup或终止进程的身份放宽。
// matchesRow绑定当前测试行；isCompleted须重新读取完整Bench PASSED及报告三项严格true。
// 所有依赖可同步或异步。失败诊断不保存命令行、classpath或依赖异常原文。
export async function verifyCompletedOwner({owned, readInfo, isAlive, matchesRow, isCompleted, sleep}) {
    const pid = owned?.ProcessId;
    const expectedCreationDate = owned?.CreationDate;
    const observations = [];
    const fail = code => {
        const error = new Error(`${code}; pid=${Number.isInteger(pid) ? pid : 'invalid'}`);
        error.code = code;
        // 由collect持久化此结构；本模块不写文件，不暴露owned或完整CIM行。
        error.diagnostics = {pid: Number.isInteger(pid) ? pid : null,
            expectedCreationDate: typeof expectedCreationDate === 'string' ? expectedCreationDate : null,
            observations: observations.map(value => ({...value}))};
        throw error;
    };
    if (!Number.isSafeInteger(pid) || pid <= 0 || typeof expectedCreationDate !== 'string' || !expectedCreationDate) {
        fail('INVALID_OWNED_IDENTITY');
    }
    for (const dependency of [readInfo, isAlive, matchesRow, isCompleted, sleep]) {
        if (typeof dependency !== 'function') {
            fail('INVALID_IDENTITY_DEPENDENCY');
        }
    }
    const call = async (dependency, args, code) => {
        try {
            return await dependency(...args);
        } catch {
            // 不附带cause：CIM/子进程错误可能包含整条命令行。
            fail(code);
        }
    };
    const observe = async () => {
        const current = await call(readInfo, [pid], 'IDENTITY_READ_FAILED');
        const observation = {creationDate: null, match: 'unverified', alive: null};
        observations.push(observation);
        if (current !== null) {
            if (!current || typeof current !== 'object' || Array.isArray(current)) {
                fail('INVALID_IDENTITY_SNAPSHOT');
            }
            observation.creationDate = typeof current.CreationDate === 'string' ? current.CreationDate : null;
            if (current.CreationDate != null && current.CreationDate !== expectedCreationDate) {
                fail('CREATION_DATE_MISMATCH');
            }
            if (current.ProcessId != null && current.ProcessId !== pid) {
                fail('PID_MISMATCH');
            }
            if (current.CommandLine != null) {
                const match = await call(matchesRow, [current], 'COMMAND_MATCH_CHECK_FAILED');
                observation.match = match === true ? 'matched' : 'mismatched';
                if (match !== true) {
                    fail('COMMAND_LINE_MISMATCH');
                }
            } else {
                observation.match = 'missing';
            }
            // 部分CIM行不等于进程不存在，更不能伪称身份相同。
            if (observation.creationDate === null || observation.match === 'missing') {
                fail('IDENTITY_METADATA_MISSING');
            }
        } else {
            observation.match = 'absent';
        }
        const alive = await call(isAlive, [pid], 'LIVENESS_READ_FAILED');
        if (alive !== true && alive !== false) {
            fail('INVALID_LIVENESS_SNAPSHOT');
        }
        observation.alive = alive;
        return {absent: current === null, alive};
    };
    const initial = await observe();
    if (!initial.absent && initial.alive) {
        return {state: 'same-live'};
    }
    // 完整匹配后退出，或CIM短暂缺失，都只能进入有界新鲜重查。
    // 初始快照不计入退出确认；必须连续两次完全缺失且不存活。
    let absentConfirmations = 0;
    let latest = initial;
    for (let attempt = 0; attempt < 3; attempt++) {
        await call(sleep, [50], 'EXIT_CONFIRMATION_WAIT_FAILED');
        latest = await observe();
        if (!latest.absent && latest.alive) {
            return {state: 'same-live'};
        }
        if (latest.absent && !latest.alive) {
            absentConfirmations++;
        } else {
            absentConfirmations = 0;
        }
        if (absentConfirmations === 2) {
            const completed = await call(isCompleted, [], 'COMPLETION_READ_FAILED');
            if (completed !== true) {
                fail('COMPLETION_NOT_CONFIRMED');
            }
            return {state: 'completed-exited'};
        }
    }
    if (latest.absent && latest.alive) {
        fail('ABSENT_BUT_ALIVE');
    }
    if (!latest.absent && !latest.alive) {
        fail('PRESENT_BUT_NOT_ALIVE');
    }
    fail('EXIT_NOT_CONFIRMED');
}
