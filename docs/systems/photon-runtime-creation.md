# Photon 定义编译与受管实例创建

## 准备缓存入口收敛（2026-11-03）

SharedPrepared.sampler恢复冻结配置后使用SourceUtil.prepare，get返回同一sampler.prepared，不再重复load/Topology.prepare绕过已有来源缓存。单定义多实例及独立编译计划共享纯sampler，Workspace/pose/时间/RNG/材质运行态仍隔离；瞬态可变FX消费当前内容与FILE动态排除不变。R2 Global新增独立计划共享检查通过，Native总体通过；新材质蓝图Global与Native功能已通过，部分截图/身份留档缺口仍保留，性能目标继续开放，统一见[工作入口](photon-workspace.md#2026-11-02-通用性能重构候选与统一验证)。

## 静态程序与真实消费契约（2026-11-02）

本分支候选使用 PhotonTemplateProgram 描述 ROOT、FX、PROJECT 静态来源，保存定义、路径和来源当前性；程序不持有运行租约或场景强引用。ChildSpawner 的准备槽保存程序，真实发射时才取得 Lease 并接线当前动态状态。受管来源按修订复用；未登记的可变 FX 在实际消费时重新读取当前内容，不能将 prepare 到首次消费之间的编辑当作不变。NATIVE 与未受管 PROJECT 保留真实原生复制回退，不新增池或需求调度器。

PROJECT 的当前性从现有 scope 重新解析目标、子树和对象身份；同 UUID 换实例、增删子树会使旧程序失效，仅 TRS 改变不使静态结构重新编译。相同字段 schema 可以复用构造计划，但实例字段必须来自当前 live 来源。NumberFunction 与 TransformRef 的类型复制保持独占；未知类型继续显式原生回退。

模型 SharedPrepared 在定义来源内同步惰性复用 Topology 和纯 CPU Sampler，PreparedModel 与实际 ModelOutput 消费同一来源。现有预热任务触发静态 CPU 准备及基础材质入口，不预创建运行树或求值动态变量。工作区、姿态、输入、Frame 缓存和材质 Runtime 仍由实例拥有；FILE 动态来源不冻结到该共享缓存。该契约是当前分支实现，运行与性能状态统一见[工作入口](photon-workspace.md#2026-11-02-通用性能重构候选与统一验证)。


## 设备戳通知与通用ID索引基线（2026-11-02）

已确认根因是设备戳消费入口漏通知：`PhotonOwnedEffectAssets` 在准备来源时比较并保存 `DEVICE_STAMPS`，但已READY来源没有再次准备时，`PhotonOwnedEffectResources` 未比较当前设备身份，导致光影等设备条件变化后旧来源未重新提交。修复在发布时采样 `deviceIdentity()`，保存为已发布来源基线；后续客户端入口仅在基线存在且变化时置`deviceDirty`并只重排已发布来源，关闭与reload清空基线。该修复不缓存姿态、不改变创建/恢复顺序，也不替换Scheduler的失败重试。

功能确认局完成3次光影激光，376条readiness全READY、回退/错误0、verified/cleanup=true；源码dirty阶段仅证明设备通知路径可用，不是性能收益或完整复测。`PhotonModelPoseSampler` 同时将part/morph ID校验改为构造期不可变集合，保留既有骨骼/clip索引、ThreadLocal scratch与每次输出所有权。性能门槛仍开放，后续安静A/B须以包含本修复的已提交基线比较。

## 事件准备与静态资源生命周期（2026-11-01）

PhotonOwnedPreparationScheduler只管理来源/修订/代次、任务状态和各自重试期限；成功、取消仅删除相应任务，不吞掉其他来源的dueAt。PhotonOwnedEffectResources的客户端入口消费设备/资源通知并派发CPU任务，worker持有独占快照；恢复FX、材质和设备资源在客户端提交。AUTHORING、PUBLISHED_PREVIEW与EXPORTED_FX隔离，新修订失败保留旧成果；cancelSnapshot不误删旧来源DEVICE恢复任务。reload改变dispatch epoch并重排仍有效的待准备修订；旧epoch回调直接返回。静态准备不持有世界，世界切换不等同全局关闭；只有客户端最终退出关闭账本并取消future。关闭后queueSnapshot在任何pending写入前拒绝。

F7真实global设备戳变化、三来源恢复、reload晚到及最终关闭检查通过，后两者通过THREAD暂停worker与客户端epoch拒绝返回的实际Debug证据验证。此处不保证CPU提交无长帧，也不把回调故障注入称为驱动故障恢复；见[验收范围](photon-workspace.md#2026-11-01-完整逻辑与端到端导出验收)。

## 2026-10-31 排空与私有定义退役补正

`PhotonLeasePool.release`对不可复用类型也保持DRAINING所有权；自然结束并跨真实tick/完成帧QUARANTINE后才discard/dispose，不进入idle。RuntimePool不在reusable判定时提前移除owned，销毁finally通知Scope清理。Factory在父级停放或显式force销毁时退役其PROJECT定义与对应空闲池；活跃/排空中的子实例仍按原屏障完成，不能用destroy(false)立即抹掉Scope或可见尾迹。

PhotonChildSpawner在update（paused早退之前）和getParticleAmount前prune已finished实例，使停止后的父实例能够自然结束。索引Map非结构性物化时既有迭代器切换到当前游标，remove资格和次序保持；租约代次对entry.equals也生效。维护时不得把未知类型不复用等同于提前放弃生命周期管理。LeasePool15/IndexedMap15/ScopedInvalidation1项限定JUnit已通过，完整宿主/游戏生命周期仍未验收；用户禁止后续游戏测试。

本模块在定义准备、实例状态与释放之间建立统一边界，覆盖根特效、FX来源和PROJECT子树。默认模式为 NATIVE；A/B/C 是可显式启动的实验后端。2026-10-31 的12局对照没有证明任一后端同时改善最坏长帧，详见[基准结果](benchmod.md#2026-10-31-实例创建架构三方案对照)。

## 模式与职责

启动属性 `lobecorp.photon.creation.mode` 支持 `native`、`compiled`、`pooled`、`indexed`、`hybrid`，大小写不敏感，启动时读取；资源重载不热切换策略。默认仍为 NATIVE，HYBRID 仅显式选择。26.1.2.9迁移的普通/光影独立回归已实际验证私有缓存、池复用/复位和来源隔离；两局HYBRID主窗编译0、池命中/复位238、失败/布局回退0。不等于完整性能或视觉验收，首发长帧仍在，见[迁移](photon-dependency-upgrade.md#2026-10-31-同平台迁移)。

| 模式 | 实际实现 |
|---|---|
| NATIVE | 原生创建/复制路径，保留本批共同的观测与租约外层 |
| COMPILED（A） | 私有冻结定义、已编译字段指令、无目标命名/时间轴程序；每次创建独占状态 |
| POOLED（B） | A的初始化程序加显式租约；仅复用已覆盖完整复位的确切类型 |
| INDEXED（C） | A的fresh实例加UUID索引/数组Map；保留原生LinkedHashMap插入次序和模拟/绘制kernel |
| HYBRID | A的编译定义/初始化、B的独占租约/完整复位、有界复用与C的UUID数组布局共同作用于同一runtime；根、FX及PROJECT接入同一路径 |

C没有重写集中模拟、SoA或GPU粒子执行器。公开 `createRuntime(false)` 保留原生行为；没有显式租约的deep调用创建fresh状态，不进入池。未注册的可变作者数据按本次内容准备，不跨调用缓存。

## HYBRID 组合与索引生命周期

底子选择 COMPILED：B、C本来共用A的定义与初始化层，融合继续复用该层。六局A/B/C的P99中位26.384/28.303/26.914ms只是历史比较依据；三者最坏长帧均未稳定优于原生，不能据此宣称融合有收益。

受管获取以定义、世界、代次、模式隔离池键，POOLED与HYBRID不互借。HYBRID miss使用编译程序创建独占节点和索引Map；hit对原runtime完整复位，再按root与定义顺序装回同一Map。未持有显式租约的HYBRID deep调用和 `instantiate(..., HYBRID)` 只创建fresh索引实例。

原 `PhotonIndexedMap.putAll()` 与 `clear()` 会物化为LinkedHashMap。因此旧INDEXED虽安装该Map类型，现有构造接线并未保持数组存储；历史C的分配结果不能单独归因为数组查找。本次保留旧API和四模式行为，只让HYBRID逐项装载，并在排空、跨真实tick/完成帧屏障后的独占租约复位中调用 `resetForLease()`。该入口清空引用、占位与计数，撤销物化并使旧迭代器失效；旧租约的引用、视图及条目不可跨代使用。

未知节点类型仍不复用，保持fresh及原native字段回退。已知 UUID 槽新增独立插入顺序数组，允许任意顺序加入、删除和重插，不因原生子先父后接线而物化；替换值不改变插入序。未知键及原动态 compute/merge/putAll 仍可物化以保留 Map 语义。resetForLease 同时撤销旧视图、条目与迭代器的代次使用。复位失败由现有池丢弃旧实例并fresh创建，发射数量、时序、自然尾迹与native kernel不变。成功的数组复位与物化回退应分别观测，Map类型本身不是数组路径生效证据。

上述 HYBRID 融合最初没有改女皇预热。2026-10-31 迁移进一步将旧 Star 根/依赖声明到资源阶段准备，并默认关闭附近女皇触发的运行时预热；自有冻结 FX 也补齐显式资源登记，避免每次 acquire 重编译导致池键变化。该修复须以迁移场景的主窗编译增量、真实池 hit/reset 和帧数据验收，机制修复本身不证明秒级卡顿全部消失。

`statistics()` 追加五个累计指标：`hybrid_lease_acquires` 为成功获取的受管租约；`hybrid_fresh_indexed_creates` 为构造完成时数组未物化的新实例（含无租约fresh）；`hybrid_indexed_resets` 为完整复位结束且数组未物化；`hybrid_reuse_type_rejections` 为创建/复位检查发现的非白名单类型；`hybrid_layout_fallbacks` 为构造或复位检查发现的布局缺失/物化。最后两项是检查事件计数，不是唯一实例数，也不覆盖借出后每次动态编辑；后续仍需现场核对Map状态。原统计键保留。

### 首次接入时的五模式比较计划（历史）

当前阶段仅实现与定向IDEA静态检查，不构建、Data、JUnit、微基准或游戏。恢复测试授权后新增独立Scenario实现、ID与配置，在同一新场景比较NATIVE/COMPILED/POOLED/INDEXED/HYBRID，不改旧Scenario或旧矩阵。完整规划在本地 `.agent/plans/20261031-photon-hybrid-comparison.md`。

每模式至少保留三次独立JVM/世界同负载窗口；记录主窗前后创建统计、数组是否物化、同一runtime/Map身份、完整复位成本、逐局分位/最坏长帧/阈值频次、分配与GC。数据缺失记unknown，不能用旧测试或仅计数证明融合验收。

旧 `PhotonCreationNativeCheck` 的五处枚举循环固定为原四模式集合，避免新增枚举值自动扩张旧场景检查范围。HYBRID专属契约尚未运行；2026-10-31本次三个Java文件经IDEA静态分析均完成、无截断、0错误，Factory保留3项原位置提示（未使用managed、setSceneInternal(null)、minecraft空判断）。未构建或执行任何测试。

## 接口与所有权

- `PhotonRuntimeFactory.prepare(FX)` 发布不可原地改写的新定义。FXHelper缓存资源的原地制作变更必须重新prepare或随clearCache失效；替换FXData也会重新准备。
- `acquire(FX, world)` / `acquire(Definition, world, Mode)` 返回独占Lease；`instantiate(Definition, Mode)` 明确创建fresh后端。
- Lease.runtime()仅在当前租约内借用。close使旧租约失效；借出的原生FXRuntime/FXObject引用不可跨close保存继续操作，Java原生对象接口本身不会变成代理句柄。
- 世界失效 `invalidateWorld()` 退休租约与池、清世界引用，但保留不可变资源程序；资源失效 `invalidateAll()` 才清静态定义并提高定义代次。过期租约不能继续使用。池键包含定义、世界及租约代次，操作由客户端线程串行执行。
- 自有冻结 FX 使用 `registerPreparedSource(FX, PreparedSourceKey(source, revision))`，释放作者/资源快照时对应 unregister；`preparedSourceCurrent` 仅检查，不在发射中隐式登记或编译。未登记可变作者对象仍按内容 fresh 准备，不跨调用缓存。统计区分 `prepared_source_cache_hits` 与 `prepared_source_compilations`。
- PROJECT程序按ID、类型、目标、是否含子对象、控制器修订缓存；transfer取本次live作者配置/TRS。过滤命名定义不改源库，当前源/source未知数据独占恢复。

只共享私有冻结Tag、不可变拓扑和未绑定程序。配置可变组、renderer状态、随机数、变换、运行槽、回调、求值器、粒子队列与已绑定动画目标属于实例。FILE来源不预绑旧共享准备；已编译模型的配置指纹变化使旧准备失效。

## 复用生命周期

`ACTIVE → STOPPING → DRAINING → QUARANTINE → PARKING → IDLE → RESETTING → ACTIVE`。先排空，再跨过真实tick和已完成渲染帧；不伪造时钟。miss立即fresh创建，不删发射、不降低数量或时长。未知复位类型退出池，非force尾迹保留自然结束语义；idle保留上限取该键观测到的活跃峰值。

force会清理待加入粒子；复位时丢弃旧Transform激活标记后重接父子关系。FXRuntime发射/销毁/宿主代次、时间轴播放器、root回调以及节点私有时钟、光照、renderer、执行器/作用域均按明确入口恢复。私有字段通过固定Mixin Accessor连接，不克隆任意运行私有对象。

## 编译、反射与回退

PhotonStatePlanUtil只编译已声明的LDLib持久化schema，准备时读取注解并缓存MethodHandle，保留别名、setter、final子组、回调、additional和custom序列化语义。受控根基类可投影到CompiledModelConfig；不复制子类未声明的扩展。primitive holder使用原生ofType完成包装类型转换。

不支持的字段/类型走明确的原生回退，暴露fallbackFields/fallbackReasons；原生回退仍可能扫描字段。不能据“编译”宣称整个热路径零反射。大资产通过PhotonFrozenTag私有拥有，公开导出返回副本；PhotonNamedLibrary的Tag getter也返回副本。

## 文件与验证入口

核心在 `client/photon/runtime/creation`、`util/photon/creation`、`mixin/photon/creation/client`。根入口为PhotonGeoEffect；FX/PROJECT模板入口为PhotonSpawnTemplateUtil；所有权转移由Template和PhotonSpawnInstance负责。Native kernel、资源格式及公共编码保护沿用现有契约。

本批38项纯JUnit通过；宿主检查在主窗口归档后运行，11资源/80对象、四模式fresh对照、复用/旧租约、source隔离、状态及命名等每局74项通过。测试不等于完整视觉、Iris、净保留或长期生命周期验收。P22/P23保持开放。

本地研究与完整参照表（本地未发布定位：`../../.agent/reports/20261031-runtime-redesign-final.md`，不作为公共证据入口）；证据根 `build/agent-backups/20261031-runtime-redesign/`。四方案完整原字节在freeze-v1各694文件，freeze-v2各8文件覆盖形成实际受测版本，manifest明确恢复次序；不覆盖准备/失败版本。

