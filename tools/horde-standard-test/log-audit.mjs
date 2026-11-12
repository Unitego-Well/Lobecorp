// Pure audit: raw log records are preserved; only the exact shutdown signature is eligible.
export function completedScenario(summary, report, scenario) {
    const selected = summary?.scenarios?.filter(s => s.id === scenario);
    return summary?.schema === '1.0.0' && selected?.length === 1 && selected[0].status === 'PASSED'
        && selected[0].failure == null && report?.workloadCorrect === true
        && report?.collectionComplete === true && report?.encounterRemoved === true;
}

export function auditLog(text, {summary, report, scenario, final = false} = {}) {
    const records = [], blockers = [], pending = [], shutdownLogExceptions = [];
    const lines = text.split(/\r?\n/);
    const header = /^\[[^\]\r\n]+\] \[([^\]\r\n]+)\/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\] \[([^\]\r\n]+)\/\]: (.*)$/;
    let current;
    lines.forEach((line, index) => {
        const match = header.exec(line);
        if (match) {
            current = {line: index + 1, title: line, thread: match[1], level: match[2], logger: match[3], message: match[4], body: []};
            records.push(current);
        } else if (/\/(?:ERROR|FATAL)\]/.test(line)) {
            blockers.push({line: index + 1, title: line, reason: 'Unrecognized ERROR/FATAL record'});
        } else if (current) current.body.push(line);
    });
    let stoppingLine = null;
    for (const record of records) {
        if (record.level === 'INFO' && record.logger === 'net.minecraft.client.Minecraft' && record.message === 'Stopping!') stoppingLine = record.line;
        if (!['ERROR', 'FATAL'].includes(record.level)) continue;
        const body = record.body.filter(line => line.trim());
        const signature = record.level === 'ERROR' && stoppingLine !== null && record.line > stoppingLine
            && /^Netty Local IO #\d+$/.test(record.thread) && record.logger === 'net.minecraft.network.Connection'
            && record.message === 'Exception caught in connection';
        const exactRoot = body[0] === 'java.nio.channels.ClosedChannelException';
        // Only ordinary stack frames are permitted after the exact root. Causes/suppressed/other payload fail closed.
        const ordinaryStack = body.slice(1).every(line => /^\s+at\s+\S.*$/.test(line));
        const entry = {line: record.line, title: record.title, reason: 'Exact post-Stopping local Netty ClosedChannelException with completed Bench/report evidence'};
        if (signature && exactRoot && ordinaryStack) {
            if (completedScenario(summary, report, scenario)) shutdownLogExceptions.push(entry);
            else (final ? blockers : pending).push({...entry, reason: 'Shutdown signature pending completed PASSED Bench and report evidence'});
        } else if (signature && !body.length && !final && record === records.at(-1)) {
            pending.push({...entry, reason: 'Live trailing record incomplete; not accepted'});
        } else blockers.push({...entry, reason: 'ERROR/FATAL outside narrowly permitted completed shutdown closure'});
    }
    return {passed: blockers.length === 0 && pending.length === 0, blockers, pending, shutdownLogExceptions, stoppingLine};
}
