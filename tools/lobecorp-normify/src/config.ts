import {realpathSync, statSync} from 'node:fs';
import {isAbsolute, resolve} from 'node:path';
import {parseArgs} from 'node:util';

export interface RuntimeConfig {
    workspaceRoot: string;
    dataRoot: string;
    requireBilingual: boolean;
}

export function argumentsFrom(argv: string[]) {
    return parseArgs({
        args: argv,
        allowPositionals: true,
        options: {
            'workspace-root': {type: 'string'},
            'data-root': {type: 'string'},
            'no-bilingual': {type: 'boolean', default: false},
            project: {type: 'string'},
            args: {type: 'string'},
        },
    });
}

export function configFrom(values: ReturnType<typeof argumentsFrom>['values']): RuntimeConfig {
    const requested = values['workspace-root'];
    if (!requested || !isAbsolute(requested)) {
        throw new Error('--workspace-root 必须是明确的绝对目录；不采用当前目录作为隐式目标');
    }
    const workspaceRoot = realpathSync.native(requested);
    if (!statSync(workspaceRoot).isDirectory()) {
        throw new Error('workspaceRoot 不是目录');
    }
    return {
        workspaceRoot,
        dataRoot: resolve(workspaceRoot, values['data-root'] ?? '.'),
        requireBilingual: !values['no-bilingual'],
    };
}
