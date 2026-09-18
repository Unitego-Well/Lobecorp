### Q: Minecraft 26.1.2 中实体飞扑为什么会明显短于弹道计算距离？
**A:** 空中水平速度每 tick 保留 `0.91`，垂直速度在扣除 `0.08` 重力后保留 `0.98`，不能把两者统一按 `0.98` 计算。技能在地面设置速度时还要先调用 `setOnGround(false)`，否则起跳首帧会受到脚下方块摩擦影响。

- 对最大 12 格、预计 6–10 tick 的清道夫飞扑，使用 `1.8` 最大水平初速度和 `0.85` 最大垂直初速度。
- 按 `1.2 格/tick` 选择预计飞行时间，再分别用水平 `0.91` 和垂直 `0.98` 的等比运动因子反推初速度。
- 不要额外限制下降速度，否则实际轨迹会偏离蓄力阶段计算出的弹道。
- **关键词**: Minecraft 26.1.2, NeoForge, Sweeper, 飞扑, deltaMovement, 空气阻力, 地面摩擦
- **相关文件/Issue**: src/main/java/org/unitego/lobecorp/entity/entity_skill/sweeper/SweeperLeapSkill.java
- **版本**: Minecraft 26.1.2 / NeoForge 26.1.2.100 | **收录日期**: 2026-09-17
---
