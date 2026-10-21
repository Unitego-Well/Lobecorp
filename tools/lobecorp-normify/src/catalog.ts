import {registerTools} from '../vendor/normify/src/tools.js';
import type {RuntimeConfig} from './config.js';
import {WorkspaceBoundary} from './sandbox.js';

export interface RegisteredTool {
    name: string;
    description: string;
    parameters?: Record<string, unknown>;
    readOnly: boolean;
    idempotent: boolean;
    destructive: boolean;

    execute(args: Record<string, unknown>): Promise<unknown>;
}

export function createCatalog(config: RuntimeConfig) {
    const boundary = new WorkspaceBoundary(config);
    const definitions = new Map<string, RegisteredTool>();
    registerTools({
        tools: {
            register: (definition: RegisteredTool) => {
                definitions.set(definition.name, definition);
            }
        }
    }, {rootDir: config.dataRoot, requireBilingual: config.requireBilingual});
    let pending: Promise<unknown> = Promise.resolve();
    const call = (name: string, args: Record<string, unknown>): Promise<unknown> => {
        const operation = pending.then(async () => {
            const definition = definitions.get(name);
            if (!definition) throw new Error('未知工具：' + name);
            return definition.execute(boundary.prepare(name, args));
        });
        pending = operation.catch(() => undefined);
        return operation;
    };
    return {definitions, call};
}
