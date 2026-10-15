# minecraft-mod-mcp

[![npm](https://img.shields.io/npm/v/minecraft-mod-mcp)](https://www.npmjs.com/package/minecraft-mod-mcp)
[![node](https://img.shields.io/node/v/minecraft-mod-mcp)](https://nodejs.org)
![Deno](https://img.shields.io/badge/deno-%E2%9C%93-222?logo=deno)
![Bun](https://img.shields.io/badge/bun-%E2%9C%93-333?logo=bun)
[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](#license)

MCP (Model Context Protocol) server bridge for Minecraft — connect AI tools to Minecraft via standard MCP.

Works with **OpenCode**, **Claude Code**, **Cursor**, and any MCP-compatible AI tool.

## Features

- **35+ MCP tools**: screenshot, click, type, scroll, drag, hotkey, execute commands, query player/world state
- **Multi-version**: Minecraft 1.8.9 through 26.2, Forge / Fabric / NeoForge
- **Zero-config port discovery**: mod and server auto-negotiate ports (9876–9000)
- **Cross-runtime**: Node.js 20+, Deno, Bun
- **MCP stdio transport**: the bridge speaks the Model Context Protocol over stdio and proxies every call to the in-game mod's HTTP API

## Quick Start

```bash
# Install globally
npm install -g minecraft-mod-mcp

# Or use directly — starts the MCP stdio server
npx minecraft-mod-mcp

# Start the MCP server, skip background port discovery
npx minecraft-mod-mcp mcp --no-discover
```

For the full CLI reference (launching servers, managing versions, accounts, building SDKs), see **[CLI Usage Guide](https://github.com/langyo/minecraft-mod-mcp/blob/master/docs/guides/en/CLI.md)** — available in 8 languages.

## MCP Configuration

This package **is** the MCP server. Add it to your AI tool's config as a **stdio** server launched via `npx` — do not point a URL at it, and do not point an MCP client at the mod's HTTP port (the mod does not speak MCP):

```json
{
  "mcpServers": {
    "minecraft-mod-mcp": {
      "type": "local",
      "command": ["npx", "-y", "minecraft-mod-mcp"]
    }
  }
}
```

(Claude Desktop / older clients use `"command": "npx", "args": ["-y", "minecraft-mod-mcp"]` instead.)

The bridge auto-scans ports 9876–9000 to find the running mod, so you never hard-code a port. See **[AI Tool Integration Guide](https://github.com/langyo/minecraft-mod-mcp/blob/master/docs/guides/en/AI-TOOLS.md)** for per-tool config and headless/Linux notes.

## Tools

### Sensing (always available)

| Tool | Description |
|------|-------------|
| `ping` | Ping the mod |
| `screenshot` | Capture screenshot with coordinate grid |
| `get_player_info` | Player position, health, mode, etc. |
| `get_world_info` | World seed, time, weather, etc. |
| `debug_fields` | F3 debug overlay data |
| `get_screen_buttons` | List GUI buttons |
| `enumerate_widgets` | Full widget tree |

### Input (requires control mode)

| Tool | Description |
|------|-------------|
| `click` | Click at (x, y) |
| `right_click` | Use / place |
| `mouse_drag` | Drag from A to B |
| `scroll` | Scroll wheel |
| `press_key` | Press keyboard key |
| `type_text` | Type text |
| `paste_text` | Paste via clipboard |
| `hotkey` | Key combination |
| `set_view_angle` | Set yaw/pitch |
| `look_delta` | Rotate view |
| `execute_command` | Minecraft slash command |

### Control

| Tool | Description |
|------|-------------|
| `enter_control_mode` | Release mouse, enable input |
| `exit_control_mode` | Return to normal |
| `release_mouse` | Release cursor |
| `set_gamemode` | Change game mode |
| `launch_minecraft` | Start a MC client (downloads version + injects mod) |
| `kill_minecraft` | Stop MC |
| `get_minecraft_status` | Connection status |

## Lobecorp 特效创作接口

本项目副本保留原有 46 项工具，并通过独立注册入口增加以下 8 项工具。先读取 `effect_capabilities` 的操作、字段、单位与约束，再根据 `effect_state` 返回的工程 ID、对象 UUID 和 revision 提交修改。

编辑粒子 FX 工程时，采用 `effect_preview(action=pause)` → `effect_state` → `effect_edit_batch` 的顺序。原生 FX 时间轴在播放中会直接改写持久化参数，因此 revision 会随预览推进而变化；先暂停再读取状态，避免提交已经过期的 revision。编辑会保留原先的暂停状态，需要继续观看时再显式播放。模型工程不受这一限制。

- `effect_capabilities`：查询工程类型、编辑操作、参数 schema 与工作区。
- `effect_state`：读取当前或指定工程及预览、历史状态。
- `effect_create`：创建粒子或模型工程。
- `effect_edit_batch`：按 revision 校验并一次提交可撤销修改，支持 dryRun。
- `effect_preview`：播放、暂停、停止或按 tick 定位。
- `effect_save`：保存可编辑工程并返回实际路径。
- `effect_export`：导出定义或资源包并返回实际路径。
- `effect_history`：撤销或重做一次编辑。

前两项只读；其余由 Mod 端检查控制模式。批量操作保留完整嵌套 JSON，具体操作定义以当前客户端返回的能力为准。保存和导出默认拒绝覆盖，显式 `overwrite=true` 才能替换已有文件。

新增工具同时返回 text JSON 和 `structuredContent`，统一为 `{ok,data,error}`；业务失败设置 MCP `isError=true`。这些工具依赖项目内的 Mod 扩展与 Lobecorp 后端，不属于上游 npm 发布版本。源码构建完成后需要重连 stdio bridge，已运行进程不会热加载工具。

项目接入与已知限制见 [Minecraft MCP 系统文档](../../../../docs/systems/minecraft-mcp.md)。

## How It Works

```
AI Tool ──MCP/stdio──► minecraft-mod-mcp bridge ──HTTP──► Minecraft Mod (Java, in-game)
                        scans ports 9876-9000              serves /api/* on first free port
```

1. The Minecraft mod starts an HTTP server on the first available port (9876→9000)
2. The bridge discovers the mod by scanning the same range and reading `/api/status`
3. Each MCP tool call is translated to an HTTP request — no WebSocket, no hardcoded ports

## Requirements

- Minecraft with the [minecraft-mcp mod](https://github.com/langyo/minecraft-mod-mcp) installed — or just let the `launch_minecraft` / `serve` tools start one for you
- Node.js 20+ (or Deno, or Bun)
- Java (auto-downloaded per version when launching via the bridge)

## License

Licensed under either of:

- Apache License, Version 2.0
- MIT License

at your option.
