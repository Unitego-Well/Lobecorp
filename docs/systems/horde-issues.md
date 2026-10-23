# Horde 问题汇总

更新日期：2026-10-23。范围仅为 `.workspace/lobecorp_horde`、`codex/horde` 当前源码，包含已有暂存和未暂存实现。本页是本工作唯一的问题清单；[总览](horde-index.md)、[玩法契约](horde-events.md)、[渲染契约](horde-rendering-implementation.md)和[基准说明](benchmod.md)不再重复维护待办。

本页只保留未解决缺陷、待验证项和历史遗留。代码确认表示静态路径成立，不代表已运行复现；性能候选不等于实测瓶颈。已完成的旧图集名称冲突修复、自写骨骼矩阵替换、顶点帧烘焙及区域检测接入不列为未完成开发。

## 优先级与处理边界

P1：优先处理玩法阻断与状态、资源生命周期。P2：处理可靠性、基准可信度、性能和验收。P3：处理维护债务。这里只记录问题与验收条件，不授权改代码、调整玩法、构建或运行。

## P1

### H01 出生可达预检与候选距离不匹配

- 现象：虚拟浪潮可见，但有合法目标时真实清道夫仍可能不生成。
- 证据：[HordeSpawnPlanner.candidate](../../src/main/java/org/unitego/lobecorp/world/horde/HordeSpawnPlanner.java)、[HordeRuntime.processRequests](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java)。
- 确认：候选后距按 16/12/8/4 分级，横向偏移却继续增加；步长 2 的广度搜索只扩展 32 节点。开阔平地搜索在到达候选所需距离前耗尽预算，不能把截断当作不可达。创造玩家被目标规则排除是独立事实，不能解释其他合法目标也不生成。
- 下一步：先记录候选拒绝原因与预算截断，再评估候选枚举和有界可达搜索；验证平地、障碍、不同目标尺寸及各距离档。
- 用户介入：代码排查无需另选方案；若放宽目标资格、出生距离或安全规则，须先确认玩法。

### H02 清除最后一个事件可能不发送客户端移除

- 确认：[HordeEvents.stop](../../src/main/java/org/unitego/lobecorp/world/horde/HordeEvents.java) 删除最后事件后，[HordeRuntime.tick](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) 可因事件和加载实体均为空直接返回，跳过 [HordeSync.tick](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java)。
- 影响：客户端没有超时移除，只限制时间外推，可能残留冻结浪潮和资源。
- 下一步：保证最后事件的移除、订阅和目标收尾完成；覆盖无人参战、全部死亡、已有淡出、多人订阅与最后事件清除。
- 用户介入：不需玩法选择，运行验证需另获授权。

### H03 目标记录缺少失效回收和规则生命周期

- 确认：[HordeTargetAssignment.seenAt](../../src/main/java/org/unitego/lobecorp/world/horde/HordeTargetAssignment.java) 只有写入；[HordeRuntime.prune](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) 仅按 domain 是否仍有事件清理，无目标 TTL。最后事件退出还受 H02 提前返回影响。
- 影响：死亡、离开或失效目标的配额、engaged、fightTicks、规则副本可持续保留；新同类事件可能复用旧状态，增加遍历与存档成本。
- 下一步：明确目标失效、共享事件结束及再进入的重置边界；核对重启后没有实际实体的成员占位，建立回收和归属一致性检查。
- 用户介入：目标再进入时是否保留配额、冷却等有歧义时需确认；不默认更改共享配额玩法。

### H04 全局 GPU 失败会让同帧全部事件持续停显

- 确认：[HordeLightField.ensureAtlas](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java) 和 [HordeGpuRenderer.ensureMotion/draw](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) 有设备容量上限；全局异常将 source 全部标为 failed 并清空共享资源，普通 header 不恢复。
- 影响：合法高负载或共享资源异常可能扩大为全部事件停显。光场槽位保留窗口和容量高水位也需纳入核算。
- 下一步：区分事件错误与共享错误，核对容量预检、分片及可恢复失败策略，覆盖单/多事件和设备上限。
- 用户介入：若方案涉及减量、降级或画面变化需确认；不以自动减少单位数作为默认修复。

## P2

### H05 基准负载与报告归属不够严格

- 确认：[HordeRenderScenario.initialize](../../src/bench/java/org/unitego/lobecorp/client/bench/HordeRenderScenario.java) 自行逐节点采高，绕过 [HordeRoute.prepare](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRoute.java) 的纵向粗采样插值，也绕过真实参战和网络；saveSpark 搜索聊天历史首个匹配链接，没有绑定本次采样。
- 影响：10000 实例提交计数不能证明全部正确可见；历史“粗高度轮”与生产实现对应关系仍需复核，可能误收旧 Spark 链接。
- 下一步：标识夹具版本和本次采样身份，区分快照渲染与完整事件负载，并校验可见图像、CPU/GPU 指标和报告来源。
- 用户介入：无额外业务选择；运行基准需另获授权。

### H06 全线路同步扫描与多事件发送缺少公平性

- 确认：[HordeSync.tick](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java) 每 tick 扫描已发送节点 revision，按距离排序事件；字节预算未限制遍历工作，近事件可优先耗尽预算。
- 影响：长线路、多玩家或多事件时 CPU、首次完整就绪延迟和 header 饥饿风险增加；客户端时间最多外推 40 tick。
- 下一步：核对待发送队列、公平调度和控制消息预算，保持完整线路就绪契约；测首次就绪、header 间隔、字节及服务端成本。
- 用户介入：若改为局部线路显示或改变协议兼容策略需确认。

### H07 服务端工作预算没有覆盖实体候选与线性查询

- 确认：[HordeRuntime.scan/assignment/updateRegions/prune](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) 仍有全目标、成员和区域遍历；[HordeRoute.nearest](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRoute.java) 为线性查询，粗扫描节点额度不限制节点内实体数。
- 影响：目标数、线路长度与事件数相乘，配置额度不能保证实际 tick 成本受控。
- 下一步：先测扫描、最近点、assignment、区域构建与 prune 的独立成本，再评估索引和增量更新。
- 用户介入：不需玩法选择；调整规则值或调度语义须另确认。

### H08 软预算等待计入无法接敌超时

- 确认：[HordeRuntime.processRequests](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) 在软预算拦截前设置 startedAt。
- 影响：未执行候选检查的请求也可能超时并冷却，不符合预算排队不计入无法接敌时间的约定。
- 下一步：区分排队等待与实际尝试计时，核对软预算、公平性、取消和预约释放。
- 用户介入：不需玩法选择。

### H09 客户端区域、批次和光场准备仍有持续重复工作

- 确认：[HordeClient.extract/updateGaps](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) 逐帧提取、逐事件扫描附近实体与走廊；[HordeLightField.prepare](../../src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java) 每帧建集合和排序；[HordeGpuRenderer.EventData.updateRegions](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) 区域变化时遍历块×区域并重建装箱数组和纹理。
- 下一步：按变化原因测分配、上传和 CPU 时间；评估区段/批次缓存、空间索引、区域合并与增量上传，保持失效条件正确。
- 用户介入：仅优化等价机制无需玩法选择；区域合并、刷新频率和视觉变化需确认。

### H10 motion pass 重复求解位置与让行

- 确认：[horde_motion.fsh](../../src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh) 用 8 像素保存一个实例，前 7 分量分别进入计算，普通路径约调用 9 次 location，朝向回退还会增加求值。
- 下一步：测 motion pass GPU 时间，再评估向量通道复用或其他数据布局；不得把帧间隔直接当作 GPU 耗时。
- 用户介入：无需玩法选择；不默认引入原生 OpenGL 旁路。

### H11 首次模型烘焙阻塞绘制准备

- 确认：[HordeGpuRenderer.prepare/geometry](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) 同步调用 [HordeMeshCache.prepare](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java)，首次读取、解析、完整帧烘焙与上传在绘制准备路径发生。
- 下一步：记录首次显示和重载后准备耗时，评估预准备及分阶段上传；核对共享模型和线程归属。
- 用户介入：若改变首次显示时机需确认。

### H12 header 解析失败缺少事务与立即恢复

- 确认：[HordeClient.apply/header](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) reset 时先安装事件并移除旧 GPU 数据，再完整校验 header；异常只记录，可能留下半初始化状态。
- 下一步：临时解析后提交有效快照；定义失败、重请求、连续 route 与限流语义。
- 用户介入：无玩法选择；不自动增加旧格式兼容。

### H13 维度卸载缺少 Horde 本地清理

- 确认：[ClientRuntimeEvents.onClientLevelUnload](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java) 没有 HordeClient.clear；[HordeClient](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) 的事件绘制不保存/检查所属维度，依赖后续服务端 remove。
- 下一步：核对换维度、快速往返、重连、世界卸载与延迟包，确保旧世界事件及资源不进入新世界。
- 用户介入：无玩法选择，联机验证另获授权。

### H14 真实玩法、持久化和联机链路未完成当前版本验收

- 证据：[HordeRuntime](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java)、[HordeSavedData](../../src/main/java/org/unitego/lobecorp/world/horde/HordeSavedData.java)、[HordeRulesTest](../../src/test/java/org/unitego/lobecorp/world/horde/HordeRulesTest.java)、[历史基准](benchmod.md)。
- 待验证：真实生成与朝向、配额1→2→3、死亡增援、转派、预算、出界与超时、取消/清除、区块卸载、关服恢复、完整成员归属和多玩家订阅。现有规则/区域单测与客户端快照不能覆盖这些链路。
- 下一步：先处理相关 P1，再按场景确认测试期望和运行授权。
- 用户介入：运行授权；仅遇到规则歧义才另问玩法。

### H15 让行、粗高度、动画与光影边界未完成专项验收

- 证据：[当前渲染契约](horde-rendering-implementation.md)、[HordeClient](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java)、[HordeMeshCache](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java)。
- 待验证：复杂重叠区域、移动目标、每目标一区域、高低楼层、旁观/未参战本地玩家、宽筛选普通生物缺口，以及 corridorMargin 与 regionMargin 不同时的战斗/视觉边界。保留宽筛选是不新增目标 UUID 同步的既有选择，不擅自改目标规则。
- 待验证：粗高度下台阶/坑洞与方块更新、长线路8192节点边界、更多模型、20 FPS离散帧观感、BoneSnapshot临时挂载的单线程/重载安全、完整光影包和透明排序。原版光照不等于完整 GBuffer/阴影支持。
- 下一步：先定义可接受精度和场景矩阵；背面剔除、压缩帧编码、帧插值和新增 LOD 只作为需评估方案，不视为已选需求。
- 用户介入：画质、精度、目标筛选、插值或 LOD 变化须确认；实机验收需授权。

### H16 性能承诺缺少分阶段、多轮证据

- 证据：[历史采样与夹具说明](benchmod.md)。10000 实例历史轮均含 Spark 扰动，粗高度标记轮 P95=20.54ms、P99=21.46ms，不能证明稳定60FPS或纯GPU耗时。
- 下一步：绑定代码/夹具版本和环境，分别记录 server tick、extract、prepare、motion pass、主体 draw、CPU分配/上传及显存；做重复轮次并覆盖单/多事件、长线路、多目标、显式上限与无限配置。
- 用户介入：运行与采样授权；不以降低严格环境门禁制造通过。

### H17 历史 Debug 异常及配置换行恢复证据不足

- 来源为已合并的旧图片图集复验过程：两次 Debug 提前结束原因未确认；普通 Run 的成功不证明 Debug 稳定，也不能推断当前游戏或进程状态。
- 当时配置值恢复后留有 CRLF→LF 差异，旧记录称仅换行；引用的 `.backups/horde-retest-20261022/before-runtime-config.json` 在2026-10-23存在性检查中未找到。
- 下一步：若仍需追溯，先从本轮文档备份和现有配置建立证据，不凭缺失快照还原文件；与当前主路径缺陷分开处理。
- 用户介入：追溯范围、真实调试运行或配置恢复需明确授权。

### H18 集成测试与配置缓存仍有既有失败边界

- 证据：[工作区构建历史](workspace-layout.md)、[修改记录](changes.md)。2026-10-23 完整 build 在 test 阶段失败，另有 reserveBuildIdentity 配置缓存问题；不能用编译/assemble完成代替测试通过。
- 下一步：后续获授权后核对本工作区实际失败用例与输出，区分 Photon 测试资源/基线和 Horde 回归，按所属工作处理，不在文档整理中扩展修复。
- 用户介入：若扩大到其他系统或运行构建/测试须授权。

## P3

### H19 调度配置、冗余状态与 API 维护债务

- 确认：[HordeRules.scanTicks](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRules.java) 仅配置/序列化传递，实际 [HordeRuntime](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) 固定20tick调度；scanTicks调整不生效。
- 待整理：HordeEvent.revision、seenAt、snapshot API、scope_first/scope_last、slice total/length、外层clock的消费者和职责；seenAt生命周期问题优先按H03处理。不能仅因当前无调用方就删除公开API。
- 待整理：[HordeEventConfig](../../src/main/java/org/unitego/lobecorp/config/server/HordeEventConfig.java) 时长/出生距离注释与实现不符；[HordeMeshCache](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java) 使用 GeckoLib 内部 apply/cleanup API，需维护版本约束。
- 下一步：先决定配置是否兑现、公开API是否保留，再局部清理声明、传输与注释；每目标规则副本、每tick存档脏标记和全表序列化成本随H03/H07一并评估。
- 用户介入：删除公开API、改变配置语义或升级依赖需确认。

## 维护与证据

问题修复并完成相应验证后，从本页移除该问题，只在[修改记录](changes.md)保留实际结果；不维护另一份长期待办副本。没有运行证据的项目不得因文档合并而标为完成。

2026-10-23 本轮只做文档合并与静态核对，未修复业务代码，未编译、构建、Data、执行测试或启动游戏。旧过程文档全文、SHA256清单和原始Git差异备份位于 `C:/Users/wang_/AppData/Local/Temp/horde-docs-consolidation-20261023-kwqkdh3p`；备份包含用户已有未提交文档。
