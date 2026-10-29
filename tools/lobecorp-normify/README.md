# Lobecorp Normify

独立标准 MCP stdio 服务、手动 CLI 和 Codex Skill。保留 Normify 的 31 个工具和结构引擎，面向 Lobecorp 使用，也可显式指定其他单个仓库。没有 DSH/Cordis 运行时依赖，不连接 Minecraft，不修改全局 MCP 配置。

## 安装与运行

当前工作区的 MCP 配置复用原始项目已构建的 CLI，显式 workspace-root 指向本工作区，数据和输出保持隔离；不依赖本工作区尚不存在的 dist。下列依赖安装和构建仅在明确授权维护本包时执行。

需要 Node.js 22 或更新版本。仅在本包目录执行：

```powershell
npm ci
npm run build
npm run verify:protocol
node 'E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/dist/src/cli.js' serve --workspace-root 'E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_horde'
```

最后一条启动标准输入输出 MCP 服务；stdout 只传输协议。客户端连接配置的 command 使用 node，args 使用 CLI 的绝对路径、serve、--workspace-root 和明确仓库路径。此文档提供配置示例，不自动注册服务：

```json
{
  "command": "node",
  "args": [
    "E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/dist/src/cli.js",
    "serve",
    "--workspace-root",
    "E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_horde"
  ]
}
```

默认架构数据位于 workspaceRoot/normify-<slug>，可通过 --data-root 指定 workspaceRoot 内的子目录。--no-bilingual 可关闭上游双语要求。

## 手动 CLI

以下命令会创建或更新架构数据，需要明确要求生成架构后再执行。本包交付时不会自动为 Lobecorp 初始化数据：

```powershell
node 'E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/dist/src/cli.js' init --workspace-root 'E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_horde' --project lobecorp
node 'E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/dist/src/cli.js' validate --workspace-root 'E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_horde' --project lobecorp --args '{"repoRoot":"E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_horde"}'
node 'E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/dist/src/cli.js' build --workspace-root 'E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_horde' --project lobecorp
node 'E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/dist/src/cli.js' render --workspace-root 'E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_horde' --project lobecorp
```

init 只建立结构数据目录与规则；模块仍须通过工具写入。build 指结构数据编译，生成 tree.json、api-index.json、outline.md、receipt.json，不是 Java/Gradle 构建。render 读取 tree.json 生成独立 normify.html。sync 仅生成漂移报告，不改源码。

tools 列出全部工具；help 支持 --args '{"topic":"tool:normify_module_upsert"}'。其他上游工具名可直接作为子命令，并通过 --args 传 JSON。brief、sync、build 等别名对应同名 normify_* 工具。

## 隔离边界

每个进程必须显式配置 workspaceRoot，不默认采用 cwd。源码证据只读取该根内文件；repoRoot 不能切换到其他仓库。架构数据写入限定在 dataRoot 内 normify-<slug> 目录；project 仅接受 slug，dir 可指定范围内目录；render.out 只接受 HTML 文件名。拒绝数据目录与 source 路径中的符号链接或目录联接，不沿它们扫描其他工作区。工具调用在进程内串行执行；不要让多个进程写同一结构数据目录。

这些约束用于防止误指向其他 worktree，并非针对其他本地进程并发修改路径的 OS 安全沙箱。Git 工作树的共享 Git 元数据仍由 Git 读取。

## 来源和差异

来源版本、原文件哈希与修改范围见 SOURCE_PROVENANCE.json；保留上游 MIT LICENSE。vendor/normify/src 是未改动的上游源码，不自动格式化。src 为本项目适配层；不携带上游 Schemastery vendor 或 DSH 配置。工具注册通过类型层 shim 与运行时适配解耦；未加入 DSH 的连续写入提醒钩子。

配套技能位于 ../../.agents/skills/lobecorp-normify/SKILL.md。工具名继续使用 normify_*，项目、服务、CLI 和 Skill 使用 lobecorp-normify。

## 验证边界

verify:protocol 只执行 MCP 初始化、31 工具枚举、只读帮助与越界请求拒绝检查，不建立架构数据。实际执行结果由任务交付说明记录；未运行的引擎回归、完整架构生成、HTML 视觉验收不视为通过。
