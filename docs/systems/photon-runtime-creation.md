# Photon 定义编译与受管实例创建

本模块在定义准备、实例状态与释放之间建立统一边界，覆盖根特效、FX来源和PROJECT子树。默认模式为 NATIVE；A/B/C 是可显式启动的实验后端。2026-10-31 的12局对照没有证明任一后端同时改善最坏长帧，详见[基准结果](benchmod.md#2026-10-31-实例创建架构三方案对照)。

## 模式与职责

启动属性 `lobecorp.photon.creation.mode` 支持 `native`、`compiled`、`pooled`、`indexed`、`hybrid`，大小写不敏感，启动时读取；资源重载不热切换策略。默认仍为 NATIVE，HYBRID 仅显式选择，尚未进行运行或性能验收。

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

未知节点类型仍不复用，保持fresh及原native字段回退；动态Map操作仍可物化以保持插入次序和Map语义，不强行维持数组。复位失败由现有池丢弃旧实例并fresh创建，发射数量、时序、自然尾迹与native kernel不变。成功的数组复位与物化回退应分别观测，Map类型本身不是数组路径生效证据。

HYBRID不改女皇生成时的beamDefinitions/prewarm drain、材质准备或调度预算，不能据租约/索引机制宣称秒级生成卡顿已解决。

`statistics()` 追加五个累计指标：`hybrid_lease_acquires` 为成功获取的受管租约；`hybrid_fresh_indexed_creates` 为构造完成时数组未物化的新实例（含无租约fresh）；`hybrid_indexed_resets` 为完整复位结束且数组未物化；`hybrid_reuse_type_rejections` 为创建/复位检查发现的非白名单类型；`hybrid_layout_fallbacks` 为构造或复位检查发现的布局缺失/物化。最后两项是检查事件计数，不是唯一实例数，也不覆盖借出后每次动态编辑；后续仍需现场核对Map状态。原统计键保留。

### 后续五模式比较

当前阶段仅实现与定向IDEA静态检查，不构建、Data、JUnit、微基准或游戏。恢复测试授权后新增独立Scenario实现、ID与配置，在同一新场景比较NATIVE/COMPILED/POOLED/INDEXED/HYBRID，不改旧Scenario或旧矩阵。完整规划在本地 `.agent/plans/20261031-photon-hybrid-comparison.md`。

每模式至少保留三次独立JVM/世界同负载窗口；记录主窗前后创建统计、数组是否物化、同一runtime/Map身份、完整复位成本、逐局分位/最坏长帧/阈值频次、分配与GC。数据缺失记unknown，不能用旧测试或仅计数证明融合验收。

旧 `PhotonCreationNativeCheck` 的五处枚举循环固定为原四模式集合，避免新增枚举值自动扩张旧场景检查范围。HYBRID专属契约尚未运行；2026-10-31本次三个Java文件经IDEA静态分析均完成、无截断、0错误，Factory保留3项原位置提示（未使用managed、setSceneInternal(null)、minecraft空判断）。未构建或执行任何测试。

## 接口与所有权

- `PhotonRuntimeFactory.prepare(FX)` 发布不可原地改写的新定义。FXHelper缓存资源的原地制作变更必须重新prepare或随clearCache失效；替换FXData也会重新准备。
- `acquire(FX, world)` / `acquire(Definition, world, Mode)` 返回独占Lease；`instantiate(Definition, Mode)` 明确创建fresh后端。
- Lease.runtime()仅在当前租约内借用。close使旧租约失效；借出的原生FXRuntime/FXObject引用不可跨close保存继续操作，Java原生对象接口本身不会变成代理句柄。
- 世界/资源代次失效会清理受管池与定义缓存；过期Definition不能继续实例化。池键包含定义、世界及代次，操作由客户端线程串行执行。
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

[本地研究与完整参照表](../../.agent/reports/20261031-runtime-redesign-final.md)；证据根 `build/agent-backups/20261031-runtime-redesign/`。四方案完整原字节在freeze-v1各694文件，freeze-v2各8文件覆盖形成实际受测版本，manifest明确恢复次序；不覆盖准备/失败版本。

