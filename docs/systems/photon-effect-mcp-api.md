# Photon 特效 MCP API

核对日期：2026-10-23，范围仅为 photon 工作区当前源码。入口由 [LcPhotonMcpCommands](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonMcpCommands.java) 注册，[PhotonEffectAutomation](../../src/main/java/org/unitego/lobecorp/client/photon/editor/automation/PhotonEffectAutomation.java) 调度。桥接只消费 [lib 制品](minecraft-mcp-artifacts.md)，没有本地 MCP 源码子项目。本页补齐原先缺失的接口索引，不声称恢复了旧版 API 文档或完成了运行验收。

## 使用顺序

1. 先读取 `effect_capabilities`，取得允许路径、类型、字段、操作与预算；再读取 `effect_state`，使用当前扩展窗口返回的 projectId。原生 `/photon_editor` 工程不作为扩展工程操作。
2. 编辑普通 FX 前通过 `effect_preview` 暂停，再读 state 的 revision；提交携带当前 expectedRevision，不复用旧快照。
3. 修改后读取结果并检查 revision、工程与运行身份；保存、导出、资源写入各自受文件保护约束。
4. 只关闭本次创建／打开且已确认归属的工程与实例；不关闭用户既有工程。源码工具登记与游戏会话可用性分开判断。

## 公开工具

当前源码登记 20 个扩展操作；这是注册表统计，不是本轮 stdio 发现或游戏连接结果。

| 分组 | 操作 | 当前职责 |
|---|---|---|
| 只读能力／工程 | effect_capabilities、effect_state | 能力、工程状态和原始快照 |
| 只读资源／视角 | effect_resources、effect_resource_read、effect_view_state | 枚举／读取许可资源、读取工程视角 |
| 只读命名／审查 | effect_animation_state、effect_resource_audit | 控制器、制作定义、运行句柄；显式来源依赖审查 |
| 工程生命周期 | effect_create、effect_open、effect_close | 新建、准备打开及关闭扩展工程 |
| 工程编辑／预览 | effect_edit_batch、effect_preview、effect_history | 批量事务、播放／暂停／停止／定位、撤销／重做 |
| 保存／发布 | effect_save、effect_export | 保存工程、导出定义或包；不绕过保护 |
| 资源／视角修改 | effect_resource_write、effect_texture_import、effect_view | 许可根中的资源写入、纹理导入及工程相机 |
| 命名编辑／控制 | effect_animation_edit、effect_animation_control | 制作事务与瞬时播放控制分别处理 |

## Photon MCP 部分能力接入

能力响应包含 `operations`、`animationOperations`、`emitterTypes`、`timelineTypes`、`timelineTemplates`、`allowedRoots` 及请求／批次／定位预算。类型的 writable 状态与工程保护由实际内容决定。

当前源码给出 `namedAuthoringAvailable=true`、`namedRuntimeAvailable=true`、`modelAuthoringAvailable=true`、`modelProjectsAvailable=true`，同时明确 `modelFileAcceptancePending=true`、`modelFileSupport=validated_embedded_v1`、`customRuntimeAvailable=false`。这些字段说明接线及门禁，不能解释为全部旧资源兼容或 G1 通过。

## 批量操作与参数

普通批次的 schema 由能力接口返回：对象增删、已有参数 JSON Pointer、时间轴、模型完整资产／局部补丁／数值、模型播放、OBJ、原生 renderer SNBT 及原始工程数据。按 projectKind 与 objectId 要求操作，不向不存在的字段写入猜测路径。列表整体替换时由调用方保留需要的未知条目；完整原树替换必须精确往返。

当前请求上限 2 MiB、排队等待 8 秒、普通粒子定位上限 1200 tick，具体预算仍以能力响应为准。后台文件准备只有一个工作线程、最多八个排队请求；提交阶段再次检查工程、revision、运行身份与路径。准备成功不等于提交成功。

## 命名制作与运行 API（2026-10-20）

本节按当前源码重新核对，日期用于承接旧路由，不代表本轮执行过历史调用。

- [PhotonEffectNamedUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectNamedUtil.java) 读取控制器原树并执行制作事务；`effect_animation_edit` 使用 projectId、controllerId、expectedRevision、operations，可用 dryRun 读取候选。
- 当前制作操作包括 add、duplicate、rename、remove、move、replace_definition、patch_definition、patch_project_preview、edit_list、set_default、set_rules、set_project_preview、clear_project_preview。具体 required／optional 字段来自能力响应，不凭旧示例拼造 UUID。
- rename 保留 UUID 并更新库内名称引用；有引用的定义不能直接删除。edit_list 按当前草稿下标操作 tracks／parameters／bindings／markers；复制轨道重建子窗口局部 UUID。
- [PhotonEffectNamedControlUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectNamedControlUtil.java) 处理 `effect_animation_control`。请求共同身份为 projectId、controllerId、expectedRuntimeId、target、action。
- target=named 支持 play、pause、resume、stop、seek、parameters、duration、trigger、signal；后续句柄操作携带实际 state 返回的 handleId。inputs 为参数名称到有限数值数组的映射。
- target=project_test 与 target=authoring 只接受各自 play／seek／stop 路径，不混用普通命名句柄；authoring play 还检查 expectedRevision 并指定 animationId。
- 命名 seek 的能力说明为 `authored_cursor_without_particle_resimulation`，不能把它当作普通粒子从零重新模拟。运行控制不写制作定义或工程历史。
- runtimeId 变化、工程关闭、控制器不匹配、句柄过期或 revision 冲突必须重新读状态；不能通过删掉身份检查重试覆盖。
- 工程测试定义不进入发布 FX。编写态与预览态隔离属于最新 WIP，F11／F12 原生复现尚待验证，见 [未完成记录](photon-mesh-beam-g1-20261023.md)。

## 模型状态去重与视角保持

模型工程由 [PhotonModelProject](../../src/main/java/org/unitego/lobecorp/client/photon/model/project/PhotonModelProject.java) 保留制作数据，原生 FX 承担预览。MCP 的数据编辑、会话历史、场景重建与视角恢复沿当前 [PhotonEffectProjectUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectProjectUtil.java) 和 [PhotonEffectViewUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectViewUtil.java) 接口执行。旧文档中的 snapshotIncluded 或旧模型类名须先对照当前响应，不作为固定协议承诺。

## 生命周期、文件与依赖

[PhotonEffectFileUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectFileUtil.java) 使用 DevelopmentResourceUtil 提供的开发资源根、LDLib2 assets 和 FXPacks 路径；不写死其他工作区路径。文件工作先准备候选／临时文件，客户端提交时复查身份、完整容器和文件锁，结束释放临时文件。

[PhotonEffectResourceAuditUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectResourceAuditUtil.java) 只读取 sources 显式声明的许可路径和 logicalPath；同逻辑路径采用首来源，默认不从开发目录补依赖。响应包含 closed、versions、edges、cycles，并固定 `runtimeAccepted=false`、`developmentFallback=false`。闭包通过不等于包独立运行通过。

## 实际检查与未完成

本轮仅通过 IDEA 阅读注册、调度、制作／控制、文件和审查入口，核对本地链接及编辑器诊断；没有调用游戏能力、编辑工程、运行 MCP 回归或重新连接 bridge。历史运行证据见 [集中记录](photon-static-fixes-20261021.md) 和 [G1 台账](photon-resource-acceptance-20261021.md)。当前能力与恢复顺序以 [Photon 工作区入口](photon-workspace.md) 为准。
