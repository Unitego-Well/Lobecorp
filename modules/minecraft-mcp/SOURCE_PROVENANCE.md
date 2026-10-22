# Source provenance

2026-10-22：从 Lobecorp 的 modules/minecraft-mcp 提取 Git 历史，独立根目录为 E:/xm/mc/mod/Unitego-Well/minecraft-mcp。没有配置指向 Lobecorp 的 push remote，也没有发布至 GitHub。

- 原始 Lobecorp dev：f7579f6ffedd1ea3d8eaa4b867bdae4ccdb6a7c1。
- dev 子树提取：45076865d0435246a92729b7f423290de1a8df4e。
- 特效工作区已提交子树：57814b054a8fbaa9743567dac2ad60890c42147e；通过 fast-forward 保留 effectTools 与资源往返验收脚本。
- 两个工作区的 MC_MCP_PORT 错误文案统一保留为 in range 1..65535，固定端口验证逻辑未改变。

上游 langyo/minecraft-mod-mcp 基础提交为 50e059dccb09a9e23b91833ffdbf42efd97fa6e6，作者与原始 MIT／Apache-2.0／CC0 许可证保留。当前模组声明沿用 MIT。

ScreenshotHelper 的部分能力参考 spawner1145/photon2mcp 的 26.2 分支提交 7b57fb4c3956526396d88b5d8eec13c2141c21d8，适配 Minecraft 26.1.2 的异步 GPU 截图与区域裁剪。Apache-2.0 正文保留在 LICENSE-APACHE；原 Lobecorp 第三方记录仍在原仓库 docs/third-party/photon2mcp.md。本仓库不包含 Photon 业务实现、Groovy 执行器或独立 Photon 服务。

本次修复保留开发客户端默认授权，取消持续释放，内部输入不再回退到系统设备；消费方的端口、存档、运行输出和用户配置不属于此源码仓库。
