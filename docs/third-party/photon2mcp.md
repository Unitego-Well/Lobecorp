# Photon MCP 部分能力移植

来源为 [spawner1145/photon2mcp](https://github.com/spawner1145/photon2mcp)，参考 `26.2` 分支提交 `7b57fb4c3956526396d88b5d8eec13c2141c21d8`。原作者已授权纳入本项目；仓库的 Apache-2.0 正文原样保留于 [photon2mcp-LICENSE.txt](photon2mcp-LICENSE.txt)，不改变 Lobecorp 根许可证。

本项目仅参考并改写 `EditorBridge.java` 的工程打开／预览视角、`ResourceTools.java` 的资源发现／读写以及 `ScreenshotService.java` 的原生 GPU 截图／区域裁剪。适配目标为 Minecraft 26.1.2、Photon 26.1.2.3、LDLib2 26.1.2.42，使用本地已解析 API；原独立服务器、菜单、Groovy 执行器和重复编辑功能未导入。

修改包括：接入既有 Minecraft MCP 与多工程标签；复用受限路径、控制模式及准备／提交事务；资源类型采用原生 Tag，纹理导入沿用当前资源路径；截图复用现有工具并处理当前 Minecraft 异步 GPU 回调。具体接口与本轮实际检查见 [Photon 特效 MCP](../systems/photon-effect-mcp.md) 和 [修改记录](../systems/changes.md)。

对应改写文件：

- `src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectProjectUtil.java`
- `src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectViewUtil.java`
- `src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectResourceUtil.java`
- `modules/minecraft-mcp/packages/common/src/main/java/xyz/langyo/minecraft/mcp/common/ScreenshotHelper.java`

后续维护以这些本地适配后的文件为准；上游版本与案例运行结果不构成本地运行验收证据。
