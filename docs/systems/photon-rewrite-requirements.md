# 特效系统重写需求与现有文件兼容契约

2026-10-19 范围更新：用户已授权并实施推荐移除方案，先备份后移除自建特效、专用渲染、模型和专属界面；原生 Photon、路径、命令、通用编辑器、资源、调试、实体动画和实体模型 LOD 保留。详见[实施记录](photon-removal-result.md)。本次只是移除旧核心，尚未实现新核心或完成旧文件可用性兼容；原件保留，缺失扩展类型在解码/覆盖前拒绝操作。下文功能表是后续重写需求与历史能力基线。

整理日期：2026-10-19。依据用户最新要求：先备份，重新设计并重写特效实现；先提交并推送特效以外的内容，再汇总需求；新实现必须兼容当前文件。

本文是本次重写的需求基线。现有代码、历史补丁和性能报告用于识别功能、数据契约与复现条件，新的内部架构可重新设计。旧工具的接入数量、测试数和局部收益不作为新实现完成的证据。

## 1. 已完成的隔离与备份

- 非特效提交：`a3240ad3fd9ab5cc9a729073d9e210f602b866e0`，72 个文件；已推送 `origin/dev`，远端哈希已核对。
- 提交包含实体动画、实体模型 LOD、统一战斗关系、女皇阶段、指挥家业务和通用规范。共享文件按内容拆分，新增 `LcVisualQualityUtil`
  解除实体距离档位对 Photon 工具的依赖。
- Photon、粒子/材质/着色器、特效资源、性能工具及光束与特效生命周期绑定的改动保留在本地。原生已存在的 Photon 接口不因提交拆分而移除。
- 重写前工作区快照：`refs/backups/before-effect-rewrite-20261019-165224-worktree`（
  `1c148db5078b0ddaa8c8e1e0885a66252fed9ba4`）。
- 原暂存快照：同前缀 `-index`（`dd601b5959a695f9f90a0c7f4cf12bd1eaa971f0`）；正式提交前另保存
  `refs/backups/before-non-effect-commit-20261019-index`（`2f0e72f984c8c0cefb4b9591d9cc0bbeefb7d414`）。
- 备份含当时已跟踪文件与 5 个未跟踪文件；五项 blob 已逐项核对。提交后排除路径的暂存内容核对通过，共享配置只合并已提交的实体侧更新。
- Git 快照不包含被忽略的运行输出、开发目录内容和未落盘编辑。重写前仍需将实际使用的开发资源目录、包内依赖与未保存工程补入资产清单和独立备份。

备份说明与提交拆分补丁在 [本地备份目录](../../backups/before-effect-rewrite-20261019-165224/README.md)。这些备份引用没有推送。

## 2. 必须解决的两个实际问题

1. **指挥家手动激光**
   ：第一次及间歇后触发出现明显停顿，多次施放后才正常。验收覆盖真实指挥家输入、预览、发送、服务端资格/施放、客户端同步、特效创建、蓄力/释放及首个可见结果。直接调用技能的样本单独标记，不能代替完整输入链。
2. **编辑器时间轴**：播放头定位、连续向前/向后拖动、大跨度跳转、关键帧/片段拖动和滚动分别测量；界面应持续响应，最终目标必达。保留零刻初始化、小数
   tick、播放/暂停、录制、编辑、撤销/重做和音频语义。

两项都以输入响应、最长阻塞、最终完成延迟和画面正确性验收；平均 FPS 改善不足以证明问题解决。首次加载、首次绘制、JIT、资源/管线准备、实例复制和预热成本分别列出。

## 3. 重写范围与必须保留的功能

| 范围           | 需求                                                                                                                         |
|----------------|------------------------------------------------------------------------------------------------------------------------------|
| 特效运行       | 独立实例、对象/父子变换、创建/发射/暂停/继续/重启/停止/销毁；共享定义与运行状态分离，粒子、随机、材质和动画状态不能串实例    |
| 命名动画       | 名称与 UUID、独立播放句柄/时钟/形参、默认播放和触发、优先级与启动顺序、覆盖/相加/相乘、HOLD/RELEASE、循环及运行中改变时长    |
| 参数与绑定     | 现有标量/向量/整数/布尔/颜色签名、默认值与范围、UUID 绑定、固定/实时输入、缩放/偏移、出生属性与存活期属性的区别；保留精度    |
| 子特效与子动画 | 项目模板和资源来源、已有发射调度、形状/继承/跟随、数量/寿命边界；受控与独立子动画、后生成实例接入、回退/循环/替换句柄与清理  |
| 模型特效       | 现有模型发射器、部件层级、顶点/面角 UV/法线、材质槽与渲染参数、骨骼/权重、Morph、TRS/形状关键帧、长度宽度与局部伸长/锚点     |
| 模型编辑       | 几何、UV、模型资源与动画工作区；选择/Gizmo、基本建模操作、片段预览、独立工程、应用到来源发射器的身份校验、历史及 dirty       |
| 渲染           | 当前支持的材质/ShaderGraph、混合、透明排序、遮挡、双面/朝向、颜色/透明度、Bloom、部件/材质组与实例覆盖；编辑器和世界正确隔离 |
| 编辑器         | 游戏内嵌入入口、特效/模型标签、布局与快捷键、时间轴/曲线、检查器、系统剪贴板、资源目录、保存/另存/导出、错误与成功反馈       |
| 游戏接入       | 当前资源 ID、对象/动画 UUID、参数、技能同步阶段及空间锚点；复用已有权威技能/网络规则，不为视觉另建伤害或控制系统             |
| 自动化         | 保留实际使用的命令、MCP 工程归属与读写能力；操作目标须可追踪，关闭/切项目后旧引用和句柄失效                                  |

具体行为参照现有 [命名动画](photon-named-animations.md)、[发射器发射器](photon-emitter-spawner.md)、[模型特效](photon-model-effects.md)、[编辑器](photon-editor.md)
文档并通过当前文件核对。功能清单不是要求保留旧类、旧包或旧内部算法，也不授权增加通用建模软件等无关能力。

扩展入口 `/lobecorp photon_editor` 与原生 `/photon_editor`
的安装范围继续隔离；原生入口不被自动装入扩展菜单或编辑器状态。停止动画、停止发射、结束特效分别保留各自语义；单属性多绑定、逐播放参数和当前已保存的表达式/曲线不简化掉。默认优化保留现有画质与内容，减粒子、降分辨率或关闭
Bloom 等质量取舍须单独说明和确认。

## 4. 当前文件兼容是硬性要求

### 4.1 文件与数据

| 现有内容            | 新实现必须保留的契约                                                                                                                 |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `.fxproj`           | 当前 Photon FX 工程的 NBT 容器与完整工程内容可加载、编辑、保存、再次加载                                                             |
| `.fx` / `.fxpack`   | 当前压缩运行定义、资源包及包内依赖可加载和播放；导出后仍能通过现有资源入口使用                                                       |
| `.mfxproj`          | 当前模型工程的项目元数据、`data.asset` 严格 JSON 资产及渲染/动画数据可往返                                                           |
| `.mfx` / `.mfxpack` | 保留当前模型特效定义/发布包的用途和原生容器；不得变成仅能由新编辑器打开的私有工程                                                    |
| 模型 JSON / OBJ     | 现存 `counterattack.lcmodel.json` 与 `counterattack.obj` 纳入保护与兼容样本；沿各自已有资产读取/交换入口处理，不因后缀命名删除或忽略 |
| 内嵌快照            | 保留现有模型资产字节数组/已支持字符串快照、材质参数与依赖索引、模板/控制器/轨道的序列化引用；大模型不能被 NBT 字符串长度限制截断     |
| 资源与工程设置      | 贴图/材质/ShaderGraph/函数资源、相对和命名空间引用、导出包路径重写、工程显示信息与必要布局设置保持可用                               |

本轮源目录清单：15 个 `.fxproj`、15 个 `.fx`、9 个 `.fxpack`、1 个 `.mfxproj`、1 个 `.mfxpack`、1 个模型 JSON、1 个 OBJ，共 43
个文件；未单独统计包内条目和开发目录。清点不是加载或往返通过的证明。

- 新内部结构通过明确的读写适配兼容当前磁盘契约；默认保存继续使用现有格式，不要求用户重做、改名、重新导入或手工转换。
- 当前文件中的资源 ID、对象/动画/形参/片段 UUID、层级、轨道/模块类型、属性路径和既有名称保持意义；相同定义在不同运行实例中仍有独立身份。
- 已支持的数据版本和缺省语义保留。未知/损坏结构必须明确诊断并保护原始数据，不能静默跳过轨道、抹掉字段或误报兼容成功。
- 当前合法文件加载失败即视为兼容未通过。不能通过删掉内容、关闭功能或把带损失的转换记为成功来消除失败。
- JSON/OBJ 的表示能力按其已有契约分别验收；OBJ 不承担工程动画、Morph、完整材质参数等扩展内容的保存责任。
- 新格式或批量迁移不是本次默认交付；如确有必要，先提供等价性、备份、回退和逐文件结果。

### 4.2 现有内容的代表性验收

- `laser_spell`：蓄力/释放组合，保留 `charge_start/charge_end/laser_start/laser_end` 及其 UUID、引用、形参与共享锚点。
- `light_ball`：启动、结束及可重复爆发；结束与启动在同一实例上操作，爆发不错误结束持续层。
- `laser`：长度/宽度/方向及其模型/材质/子对象，保持当前工程控制入口。
- `star_beam_spell`、球体与光柱子定义、`queen_star_beam`：阶段、独立实例、依赖与现有画面。
- 三份法阵、蓄力资源、`lasel` 及两份参考工程：不同模板、命名动画、子发射及资源来源。
- `counterattack`：模型工程、资产 JSON、OBJ 与模型包分别核对支持范围；不把交换格式的缺省内容当作完整工程。

## 5. 性能检测工具必须回答的问题

按“会话 → 特效定义 → 运行实例 → 对象/子实例 → 模型/部件/材质或动画/轨道 → 操作阶段 → 线程/时刻”定位。相同名称、重复
UUID、重置代次和子播放上下文不能被合并为一个运行对象。

- **时间**：完整帧间隔、各阶段 CPU 墙钟、inclusive/self、调用次数/最大值；线程 CPU 支持时单列，排队/锁/汇合/上传/应用等待分别计量。
- **特效/对象**：解析、复制、创建、发射、tick、逐帧动画、提取、延迟几何、提交/绘制、重置/重放与销毁。
- **模型**：来源解析、拓扑/采样、骨骼/Morph/姿态、法线/部件/材质处理、顶点/三角形、上传与批次。
- **动画**：每个命名播放及时间、轨道/曲线/表达式、绑定/所有权/混合/应用、子动画和事件；目标对象与执行播放器可双向追踪。
- **内存**：分配、存活/峰值、缓存命中/驱逐/预算、借出缓冲、资源/纹理/FBO/管线准备及清理；堆估算、原生内存和显存不混读。
- **GPU**：真实可测的 batch/pass/Bloom 时间与来源；合并/共享/无法归属项明确列出，不强行平均成每模型“精确 GPU 成本”。
- **交互与异步**：请求编号/版本、目标时间、排队、开始/取消/丢弃、完成、应用与首个可见结果，最终请求完成率。
- **工具本身**：采集/身份映射/聚合/排序/面板/导出开销、预算、丢样、覆盖、截断和不可用字段。

提供可用的帧图、逐对象树/排行、模型与动画视图、范围筛选、长帧定位、原始记录和可复核导出。关闭采集时保持低开销；细粒度按选中对象/子树和短窗口展开，有界处理容量，不再次因全场身份膨胀而失去关键卡顿证据。

CPU/GPU、共享/私有、累计/单次/逐帧、线程 CPU/墙钟/等待的口径必须清楚；P95/P99 从实际有效原始数据计算，父子 inclusive 不相加。

## 6. 性能架构方向

优先切断输入和呈现被长同步任务阻塞的路径。时间轴采用有界最新请求、可取消/分预算的计算与版本化结果交接；需要历史模拟时保留因果顺序和完整随机状态，最终落点仍精确计算。

- 分帧重放期间须明确“目标时间/已完成时间/计算状态”。绘制不能无意读取半更新的模拟状态；保留上一张完整预览或采用隔离工作状态，完成后发布正确结果。
- 不保留全部鼠标历史目标的无界队列；内容编辑、撤销、录制、切动画/项目、资源重载和关闭都会使旧结果失效。
- 多线程用于经过审计的纯 CPU 模型/动画/数据准备；输入快照、任务粒度、工作池和内存均有界。活世界/实体、UI 树和 GPU 资源的所有权明确。
- GUI 独立线程渲染列为正式研究与原型需求：必须验证独立状态/上下文/资源、输入命中版本、纹理完成交接、主窗口合成/present
  和清理。只有后台统计/离屏绘制时，不能宣称主线程卡住期间 GUI 已独立响应。
- 多线程、分帧、缓存和准备分别计量；总 CPU 增加、工作线程等待、快照复制及输入延迟也要报告。仅提高 CPU 利用率不算成功。
- 不预设必须沿用现有缓存/池/混入结构；先用真实激光和拖动证明新执行路径有效，再扩展复杂工具界面。

当前项目的性能问题和精确瓶颈仍需新基线核对；上述是实现方向，不是已完成的新架构。

## 7. 验收与交付顺序

| 顺序              | 必须得到的结果                                                                            |
|-------------------|-------------------------------------------------------------------------------------------|
| R0 保护与兼容样本 | 当前源资产、开发目录和包内依赖的清单/哈希/备份；最小加载器可读取真实工程并保留数据        |
| R1 两条实际交互链 | 新运行/预览路径能播放真实组合激光，手动触发与连续拖动保持响应；记录首次、最差帧与最终结果 |
| R2 功能和文件往返 | 上述模型/动画/子特效/编辑功能保持，加载→编辑→另存→重开→导出→运行有逐样本结果              |
| R3 细粒度诊断     | 每对象/模型/动画归属与原始证据可用，容量和工具开销符合预算，真实负载面板可操作            |
| R4 对照与清理     | 冷启动/预热/间歇/多实例，向前/向后/大跨度拖动，编辑/撤销/录制与关闭/卸载/重载完成验证     |

- 单个完整 `laser_spell` 延续 100 FPS / 10 ms 帧预算目标；世界和编辑器、首次和预热分别报告。原计划建议的有效区间 P99 ≤ 10
  ms、拖动 P95 ≤ 33.33 ms/P99 ≤ 50 ms 和工具概览预算保留为待实测指标，不把建议数值写成已达成绩。
- 同资源/种子/相机/窗口/画质/LOD 对照，记录原始帧、最大帧、长帧次数、输入到可见时间、最终完成时间、分配/GC 和峰值内存；不平均多个会话的
  P99，不裁掉首次或过渡长帧。
- 固定输入与受控模拟用于可复现检查；原生合成鼠标、直接调方法和物理人工交互明确区分。全程不以菜单夹具替代真实负载。
- 文件往返核对对象/层级/UUID/参数/轨道/材质/依赖/模型/动画，结合画面、粒子/随机、事件/结束、复制隔离、撤销/dirty
  检查；完整原件先保留，首次写入另存到样本副本。
- 交付需求文档、新实现、文件兼容表、可使用的诊断工具、原始报告与优化对照、已知限制和回退说明。单元测试、编译、静态诊断与实际交互/视觉验收分别报告。

旧详细测量规格见 [原性能计划](photon-detailed-performance-plan.md)，历史状态见 [旧工具记录](photon-performance-tool.md)
。其实现进度不抵扣本次重写验收。

## 8. 当前源资产清单

以下仅为此次通过 IDEA 枚举的源资源；重写前补充实际开发目录与包内条目，原资源暂未修改。

- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/lasel.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/laser.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/lasel.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/laser.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/light_ball.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/laser_spell.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/magic_circle.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/magic_circle2.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/magic_circle3.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/light_ball.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/laser_spell.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/queen_star_beam.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/star_beam_spell.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/magic_circle.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/magic_circle2.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/magic_circle3.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/accumulate_particle.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/queen_star_beam.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/star_beam_spell.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/star_beam_spell_ball.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/star_beam_spell_beam.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/accumulate_light_ball.fx`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/accumulate_particle.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/star_beam_spell_ball.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/star_beam_spell_beam.fxproj`
- `src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/accumulate_light_ball.fxproj`
- `src/main/resources/assets/lobecorp/fx/reference_star_trail.fx`
- `src/main/resources/assets/lobecorp/fx/reference_star_trail.fxproj`
- `src/main/resources/assets/lobecorp/fx/reference_variable_laser.fx`
- `src/main/resources/assets/lobecorp/fx/reference_variable_laser.fxproj`
- `src/main/resources/assets/lobecorp/models/photon/the_queen_of_hatred/counterattack.obj`
- `src/main/resources/assets/lobecorp/models/photon/the_queen_of_hatred/counterattack.mfxproj`
- `src/main/resources/assets/lobecorp/models/photon/the_queen_of_hatred/counterattack.lcmodel.json`
- `src/main/resources/fxpacks/laser.fxpack`
- `src/main/resources/fxpacks/light_ball.fxpack`
- `src/main/resources/fxpacks/laser_spell.fxpack`
- `src/main/resources/fxpacks/queen_star_beam.fxpack`
- `src/main/resources/fxpacks/star_beam_spell.fxpack`
- `src/main/resources/fxpacks/reference_star_trail.fxpack`
- `src/main/resources/fxpacks/star_beam_spell_ball.fxpack`
- `src/main/resources/fxpacks/star_beam_spell_beam.fxpack`
- `src/main/resources/fxpacks/reference_variable_laser.fxpack`
- `src/main/resources/fxpacks/mfx-acceptance-20261012-12686476-7d09-4953-9b4a-b152f3812b47.mfxpack`

## 9. 本轮状态

非特效提交/推送和重写前 Git 备份已完成。兼容清单与上述需求已整理；特效内部实现尚未开始重写，43
个文件尚未在新实现上执行兼容验收。本文及相关特效文档更新按当前提交边界保留本地，不混入已推送的非特效提交。
