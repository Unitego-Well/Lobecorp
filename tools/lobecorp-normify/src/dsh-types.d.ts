/** 上游仅在类型层使用 Context；运行时不加载 DSH/Cordis。 */
declare module '@deepseek-ai/cordis' {
    export interface Context {
    }
}
