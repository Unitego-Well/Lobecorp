# Lobecorp

Lobotomy Corporation（Lobecorp）是基于 Minecraft NeoForge 的模组。

Face the Fear, Build the Future.

## 基本信息

| 项目 | 当前值 |
|---|---|
| Mod ID | `lobecorp` |
| 版本 | `0.0.3` |
| Minecraft | `26.1.2` |
| NeoForge | `26.1.2.100` |
| Java | `25` |
| 作者 | `Unitego` |
| 许可 | All Rights Reserved |

开发和维护入口见[项目总索引](docs/README.md)。

## 本机开发配置

使用 Minecraft MCP 时，需在当前工作区准备本机配置 `.codex/config.toml`，将 Minecraft MCP bridge 的启动路径、工作目录及 `MC_MCP_PORT` 指向本工作区。该文件不随 Git checkout 自动提供，不要直接复制其他工作区的配置；游戏实例、端口与运行目录必须相互对应。接入方式见[Minecraft MCP 外部制品消费](docs/tooling/minecraft-mcp/minecraft-mcp-artifacts.md)。

Gradle 在项目同步阶段就需要解析游戏端口，读取优先级为：环境变量 `MC_MCP_PORT` → Gradle 属性 `MC_MCP_PORT` → `.codex/config.toml` 中的 `MC_MCP_PORT = "端口号"`。三个来源均未配置时，同步与构建会失败。原始 dev 使用本机配置中的独立端口；女皇工作区的 `19879` 不作为公共默认值。各工作区可通过 Gradle 属性配置各自端口，连接 MCP 时仍需对应的本机配置，端口应与游戏保持一致。
