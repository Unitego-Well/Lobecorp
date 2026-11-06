# Horde 当前文档入口

更新：2026-11-06。所属工作区 codex/horde；只记录本工作区状态。

- [最新状态及实际验证边界](../../.agent/reports/20261106-current-state.md)
- [HUD当前契约](player-hud-shields.md)
- [唯一问题清单](horde-issues.md)
- [系统修改记录](changes.md)

非指挥家HUD模块化、左上区域解耦、NEW值条与护盾配置、独立纹理/nine_slice已实现。最近源码及测试源码编译、生产包隔离通过；12张纹理像素及14份元数据/生产包核对完成。未执行本轮测试、游戏、视觉或性能验收。

GPU后续验收及跨卸载持久恢复目标已取消。保留混合WIP，未提交。旧过程材料和历史数据按用户要求清理，不再保留旧会话进度入口。唯一保留备份为工作区build/agent-backups/20261106-newhud-texture-split。
