# 网络协议与同步

核对日期：2026-10-05。协议登记由 [LcPayloads](../../src/main/java/org/unitego/lobecorp/registry/LcPayloads.java) 决定，当前
NETWORK_VERSION 为 11；本文不代表联机测试通过。

## 方向与入口

[ToPayload](../../src/main/java/org/unitego/lobecorp/network/ToPayload.java) 使用 context.enqueueWork
把处理转入正确线程。network.ts
的 [ToServerPayload](../../src/main/java/org/unitego/lobecorp/network/ts/ToServerPayload.java) 校验
ServerPlayer；network.tc 的 [ToClientPayload](../../src/main/java/org/unitego/lobecorp/network/tc/ToClientPayload.java)
面向客户端玩家；network.tsc
的 [ToServerAndClientPayload](../../src/main/java/org/unitego/lobecorp/network/tsc/ToServerAndClientPayload.java)
保留双向接口。本次不另建 Payload 分类。

| 方向  | 已登记 Payload                                                                                                                                                                                                                                                                                                | 职责                                           |
|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| C → S | [ConductorCommandPayload](../../src/main/java/org/unitego/lobecorp/network/ts/ConductorCommandPayload.java)                                                                                                                                                                                                   | 指挥家操作命令，服务端重新检查实体、资格和状态 |
| C → S | [ConductorViewPayload](../../src/main/java/org/unitego/lobecorp/network/ts/ConductorViewPayload.java)                                                                                                                                                                                                         | 观察/控制会话相关请求                          |
| S → C | [ConductorSnapshotPayload](../../src/main/java/org/unitego/lobecorp/network/tc/ConductorSnapshotPayload.java)                                                                                                                                                                                                 | 指挥家客户端快照                               |
| S → C | [EntitySkillSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/EntitySkillSyncPayload.java)                                                                                                                                                                                                     | 技能运行表现同步                               |
| S → C | [LcCustomAnimationSettingsSyncPayload](../../src/main/java/org/unitego/lobecorp/network/tc/LcCustomAnimationSettingsSyncPayload.java)                                                                                                                                                                         | 自定义动画设置同步                             |
| S → C | [HitboxCreatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/HitboxCreatePayload.java)、[HitboxUpdatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/HitboxUpdatePayload.java)、[HitboxRemovePayload](../../src/main/java/org/unitego/lobecorp/network/tc/HitboxRemovePayload.java) | 命中框客户端快照创建、更新与移除               |

当前登记为 2 项 C2S、6 项 S2C，不能把存在 tsc 接口解释为已登记双向业务包。

## 状态权威与复用

服务端决定控制、技能和伤害；客户端提交请求与表现缓存。协议层负责 Codec 与派发，不能信任客户端参数替代服务器资格验证。

新增同步前先检查附件自动同步、SynchedEntityData、实体事件、已有技能同步和快照是否已经表达该状态。普通注册与世界同步复用原版/NeoForge，不添加重复通道。持续动画通过状态边沿切换，瞬时粒子按已有表现链触发。

## 修改前与收尾检查

检查 LcPayloads 的版本和登记方向、具体
TYPE/STREAM_CODEC、双方处理器、发送点、缓存失效和世界卸载。变更结构或协议需按大型修改验证，并同步 [注册与数据](registration-data.md)
、对应业务页及 [修改记录](changes.md)。包迁移只改变 Java 类型位置，协议 ID 和编码保持不变。
