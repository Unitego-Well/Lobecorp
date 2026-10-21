import {existsSync, lstatSync, readdirSync, readFileSync} from 'node:fs';
import {basename, isAbsolute, join, relative, resolve, sep} from 'node:path';
import {parse} from 'yaml';
import {isValidId} from '../vendor/normify/src/engine/ids.js';
import type {RuntimeConfig} from './config.js';

export class WorkspaceBoundary {
    constructor(private readonly config: RuntimeConfig) {
        this.within(config.workspaceRoot);
        this.within(config.dataRoot);
    }

    within(candidate: string): string {
        const absolute = resolve(candidate);
        const rel = relative(this.config.workspaceRoot, absolute);
        if (rel === '..' || rel.startsWith('..' + sep) || isAbsolute(rel)) {
            throw new Error('拒绝访问 workspaceRoot 外路径：' + candidate);
        }
        let current = this.config.workspaceRoot;
        for (const segment of rel.split(sep).filter(Boolean)) {
            current = join(current, segment);
            if (existsSync(current) && lstatSync(current).isSymbolicLink()) {
                throw new Error('拒绝符号链接或目录联接：' + current);
            }
        }
        return absolute;
    }

    private sourcePaths(value: unknown): void {
        if (!value || typeof value !== 'object') return;
        if (Array.isArray(value)) {
            value.forEach(item => this.sourcePaths(item));
            return;
        }
        for (const [key, item] of Object.entries(value)) {
            if (key === 'source' && Array.isArray(item)) {
                for (const source of item) {
                    if (source && typeof source === 'object' && 'path' in source) {
                        const path = source.path;
                        if (typeof path !== 'string' || isAbsolute(path) ||
                            path.split(/[\\/]/).includes('..') || path.includes(':')) {
                            throw new Error('source.path 必须是工作区内相对路径');
                        }
                        this.within(resolve(this.config.workspaceRoot, path));
                    }
                }
            } else {
                this.sourcePaths(item);
            }
        }
    }

    private inspectData(directory: string): void {
        this.within(directory);
        if (!existsSync(directory)) return;
        for (const entry of readdirSync(directory, {withFileTypes: true})) {
            const path = this.within(join(directory, entry.name));
            if (entry.isSymbolicLink()) throw new Error('架构数据不能包含符号链接：' + path);
            if (entry.isDirectory()) {
                this.inspectData(path);
            } else if (entry.name.endsWith('.md')) {
                const text = readFileSync(path, 'utf8');
                const frontmatter = /^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/.exec(text);
                if (frontmatter) this.sourcePaths(parse(frontmatter[1]));
            }
        }
    }

    prepare(name: string, input: Record<string, unknown>): Record<string, unknown> {
        const args = structuredClone(input);
        if (typeof args.repoRoot === 'string' &&
            this.within(resolve(args.repoRoot)) !== this.config.workspaceRoot) {
            throw new Error('repoRoot 必须等于本进程的 workspaceRoot');
        }
        if ('repoRoot' in args || ['normify_sync', 'normify_fingerprint', 'normify_module_refresh'].includes(name)) {
            args.repoRoot = this.config.workspaceRoot;
        }
        if (name === 'normify_tree_list') {
            if (args.root !== undefined &&
                (typeof args.root !== 'string' || resolve(args.root) !== this.config.dataRoot)) {
                throw new Error('tree_list 仅允许当前 dataRoot');
            }
            args.root = this.config.dataRoot;
            if (existsSync(this.config.dataRoot)) {
                for (const entry of readdirSync(this.config.dataRoot, {withFileTypes: true})) {
                    if (entry.name.startsWith('normify-')) {
                        this.inspectData(join(this.config.dataRoot, entry.name));
                    }
                }
            }
        }
        if (args.project !== undefined &&
            (typeof args.project !== 'string' || !/^[a-z0-9][a-z0-9-]*$/.test(args.project))) {
            throw new Error('project 只接受小写 slug；绝对路径请使用 dir');
        }
        let projectDirectory: string | undefined;
        if (args.dir !== undefined) {
            if (typeof args.dir !== 'string') throw new Error('dir 必须是字符串');
            projectDirectory = this.within(resolve(this.config.dataRoot, args.dir));
            const rel = relative(this.config.dataRoot, projectDirectory);
            if (rel === '..' || rel.startsWith('..' + sep) || isAbsolute(rel) ||
                !/^normify-[a-z0-9][a-z0-9-]*$/.test(basename(projectDirectory))) {
                throw new Error('dir 必须是 dataRoot 内的 normify-<slug> 目录');
            }
            args.dir = projectDirectory;
        } else if (typeof args.project === 'string') {
            projectDirectory = this.within(join(this.config.dataRoot, 'normify-' + args.project));
        }
        if (projectDirectory) this.inspectData(projectDirectory);
        if (name === 'normify_render' && args.out !== undefined) {
            if (typeof args.out !== 'string' ||
                !/^[a-zA-Z0-9][a-zA-Z0-9._-]*\.html$/.test(args.out)) {
                throw new Error('render.out 只接受当前架构目录内的 HTML 文件名');
            }
        }
        if (typeof args.diff === 'string' && (!/^[a-zA-Z0-9_./~^@{}:+-]*$/.test(args.diff) || args.diff.startsWith('-'))) {
            throw new Error('diff 只允许 Git 引用或引用范围');
        }
        for (const key of ['id', 'new_id', 'new_parent']) {
            if (args[key] !== undefined && args[key] !== null &&
                (typeof args[key] !== 'string' || !isValidId(args[key]))) {
                throw new Error(key + ' 必须是合法模块或变更标识');
            }
        }
        this.sourcePaths(args);
        return args;
    }
}
