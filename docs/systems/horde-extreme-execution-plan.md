# Horde 极端情况优化 · GPT 执行文档

2026-10-26 第二轮更新（HORDE-RETEST-02）：按“先测试、再修改、再测试”完成关闭光影组复测与失焦防护、外观改善。基线光影 13.889/17.863ms、世界GPU 13.305ms，普通 3.402/5.690ms、世界GPU 2.026ms。修改：BenchWindow 每tick防失焦并计数、MEASURE 失焦硬门禁与环境字段；中景 5盒136、远景 2盒48，近景520与4帧动画不变，新增按档实例计数。复测光影三轮 14.365/15.059/15.026ms、P99 18.064/18.624/17.971ms、世界GPU 13.834/14.398/14.534ms，目标仍满足；普通两轮有效帧 6.168/6.184ms（165Hz地板）、世界GPU 2.026/1.897ms，另两轮因焦点被抢被门禁/BenchMod环境拒绝。截图与限制见[BenchMod 2026-10-26 第二轮节](benchmod.md#2026-10-26-第二轮失焦防护外观改善与普通光影复测)。仍未完成 E4、区域净空定量、普通稳定第三轮与LOD切换录像。

2026-10-26 更新（HORDE-RETEST-01）：已先修复 H27 基准空画面门禁（地面锚点 + 同机位浪潮开关 A/B 像素门禁 + 相机几何断言 + visible_pixels），并在同一 512 宽直线/10000 实例夹具完成普通/光影修改前后复测与新一轮优化。最终配置光影三轮帧均 14.444/14.456/14.992 ms、P99 16.348/16.273/16.919 ms、世界 GPU 14.089/14.123/14.648 ms，达到帧均≤16.7、P99≤20、世界 GPU≤15；普通同夹具帧均 3.365 ms/P99 6.777 ms。实际改动为三档模型 520/112/24 顶点与单位历史行环页稳定映射，并修复单位页列片计数缺陷。仍未完成 E4、游戏内区域净空定量、首帧≤100 ms 与中远 LOD 美术接受；本轮已用 Debug+MCP 取得真实事件可见、夜间发光、近中远三档、650ms 动画位姿变化和镜头移动后持续可见的证据（区域净空定量、E4 与真实事件 256 尸体仍缺）；详见[BenchMod 2026-10-26 节](benchmod.md#2026-10-26-基准有效性修复与修改前后对照)与[horde-issues 2026-10-26 节](horde-issues.md#2026-10-26-基准有效性复测与优化后问题)。本段不把代码交付或三轮 PASSED 当作全部任务 DONE。

最新运行状态（2026-10-25）：用户已另行明确授权Sodium + BenchMod + Minecraft MCP + IDEA MCP + Debug测试，覆盖下方交付时的禁止运行条款。本轮普通/光影各一次、三个场景均实际跑完，最终summary均PASSED，但浪潮截图近乎天空，光影P99与世界GPU目标未过，整体验收不通过。Debug/MCP新建隔离世界的生产事件可见，4帧/三档及64尸体批绘有实机证据；三次一致性、严格A/B、256尸体与完整矩阵未执行，不标任何任务DONE。数据、证据缺口和当前运行结论见[BenchMod本轮报告](benchmod.md#2026-10-25极端优化实机测试与历史对照)，后续问题只维护horde-issues.md；第9节保留c21899d代码交付时快照，不回填历史数据。

当前执行规则（2026-10-25，用户选择A）：按代码实现依赖继续所有已确认修改，前置实机门禁“未执行”不再阻止后续代码实施；尚未确认的关键取舍继续询问。验收阈值保持，未验收不得标DONE。游戏、BenchMod Client/Server和Debug仍禁止，只有另行明确授权后才运行。本段覆盖下文与此冲突的执行/门禁条款，不构成测试授权。

执行记录（2026-10-25）：EX-01…EX-17代码已接入，统一IDEA错误诊断与无游戏编译/Data已完成；全部运行、GPU硬件、画面、A/B和首帧门禁仍NOT_RUN，不标DONE。用户已确认现有Horde WIP归属A；逐项代码、回滚、实际检查和限制见第9节，总进度见horde-index.md，未解决项只维护horde-issues.md。

版本：2026-10-25 v2。**本文件是执行命令，不是建议书。** 方案背景、预算与取舍见[极端情况优化方案](horde-extreme-optimization-plan.md)；问题状态见[唯一问题清单](horde-issues.md)。执行者必须按任务编号完整落地，**不允许自行缩减范围、降级目标、把验证推给用户，或用"最小改动"替换规格**。

> 执行前完整阅读本页与[极端情况优化方案](horde-extreme-optimization-plan.md)第 8 节“已确认决策”。任何与本文冲突的“优化直觉”都以本文与问题清单为准。

---

## 0. 强制指令（先读；把下面整段作为你的开场提示词）

```text
你是本项目的执行工程师，不是顾问，也不是评审。你的任务是把《Horde 极端情况优化 · GPT 执行文档》里 EX-01…EX-12 全部实现、跑基准、达到门禁、同步文档。

硬性要求：
1) 大规模修改是预期行为。允许跨文件、跨包、跨渲染/着色器/基准/文档改造；一次改 5–10 个文件是正常工作量，不要因为“改动大”而缩水。
2) 禁止保守替代。禁止只改一行、只加开关不实现、只写 TODO、只做实验不做落地、用“最小可行方案”替换规格；规格要求什么就实现什么。
3) 禁止把决定权推回用户。只有本文 §7 的停止条件允许停下；其余情况自己判断、自己实现、自己验证。
4) 禁止降低目标。不得减少可见数量、不得静默 LOD、不得关发光/降动画/降精度、不得放松基准门禁；60 FPS 由 W1–W4 + W0 + W9 组合达成。
5) 必须自己运行 BenchMod Client、自己读产物、自己算门禁；不得以“无法验证”为由跳过。工具受限时先用可用手段（IDE MCP、日志、artifact）完成，仍不行才按 §7 停。
6) 每个任务按 §6 模板分别记录代码和门禁状态；按用户2026-10-25确认的A顺序，已确认代码按实现依赖继续，实机门禁未执行不阻止后续代码实施；不得将其写成验收通过。
7) 遇到难点至少尝试 2 种实现方案并记录；不得直接放弃或改需求。
8) 在 §0.1 授权的范围内，把改动做到底、做到能通过门禁，是你的职责，不是可选项。
```

**反保守条款（违反任一条即视为该任务未完成）**：

- 只改了常量/开关而没有实现规格描述的结构改动 → 未完成。
- 把“双缓冲/减面/距离档”等大项拆成“先观察/先保守”而不落地 → 未完成。
- 以“风险高/影响面大”为理由减少改动范围 → 未完成；风险用开关、回滚、A/B 管理，不用“不做”规避。
- 把验证推给用户、或以“我无法运行游戏”结束 → 未完成（先按 §2 的工具与产物自行验证）。
- 为过门禁而减少可见数量、降 LOD/发光/动画/精度或放松 BenchMod 门禁 → 直接判失败并回滚。

---

## 0.1 执行者合同

**目标**：在 E1（10,000 实例 + Iris 光影 + 热机）下达到 **60 FPS**（帧均 ≤16.7 ms、P99 ≤20 ms、GPU ≤15 ms），**绘制数量等于配置值（默认 10,000）**，且不出现外观破洞/错位/停显。**这是硬指标，不是方向。**

**已授权（在范围内必须做到，不是可选）**：

1. 目标 60 FPS（2026-10-25 用户确认），必须实施 W0 静态减面与 W9 距离质量档；两者必须显式配置、写文档、做 A/B。
2. Iris 工作集可用 128–256 MiB；`MAX_WORDS` 4M→16M 与双缓冲是规定动作。
3. 极端情况必须实现自动缩小工作集/回退实例化路径，并记录原因；不得减少可见数量。
4. H15 维持 3 格净空，本任务不改玩法。
5. **必须修复“左右移动进入分割区域”**：正常随机横摆/漂移不得主动进入分割区域；用横向约束把单位挡在区域外，绝不隐藏。用户于2026-10-25选择B：目标瞬移、新区域或扩张包住单位时，优先保持每tick位移≤0.75格，允许暂时在区域内多tick平滑退出（见 EX-13）。
6. **动画烘焙改为在原始动画中平均取 4 帧**（覆盖此前 20 FPS 采样要求，见 EX-14）：默认 4 帧、保留配置回退，循环稳定、无 T-pose/炸模。
7. **尸体渲染默认静态、无动画**；实体死亡动画归实体自身逻辑，不由 Horde 接管。必须在 `EntityCorpse` 提供可覆写方法：`renderDistance()`（默认 180，复杂/动画 32）、`hasCorpseAnimation()`（默认 false）、`isComplexModel()`（默认 false）；只有覆写为有动画的类型才播放尸体专用动画；必须冻结姿态、消除每帧分配与实体副作用、批渲染，并做同步优化（见 EX-15/16）。
8. **允许降低精度/顶点（LOD），但绘制数量必须等于配置值**（如 `MAX_VISIBLE`），不得静默减少数量；后续性能测试统一使用 **宽度 512、直线、x≈+160 到 x≈-160** 的事件夹具（见 EX-17）。

**禁止**：

- 不得减少可见实例数量、不得静默 LOD、不得关闭发光、不得降低 20 FPS 动画采样、不得降低位置精度。
- 不得放松 BenchMod 环境门禁（窗口聚焦/最大化/VSync 关/不限帧）。
- 不得使用 26.1.2 不支持的 MRT/浮点颜色附件/计算着色器；不得把逐单位变换搬回 CPU。
- 不得整份重排/格式化未修改文件；不得覆盖用户未提交改动、暂存区或未跟踪资源。
- 不得把“PASSED”“截图可见”“单轮最快值”当作性能达标。
- 不得把未实现的结构改动写成“已完成/后续优化”。

**环境约束（本机）**：文件编辑只允许通过 IDEA MCP（`ide_create_file` / `ide_replace_text_in_file` / `ide_edit_member` 等）；禁用 `apply_patch`/shell 写文件。Git 提交可能因沙箱拒绝 `.git/index.lock` 而失败，失败时如实上报“未提交”，不得绕过。

---

## 0.2 完成定义（DoD，缺一即未完成）

1. EX-01…EX-17 全部状态为 DONE，且每项有 §6 模板的 A/B 数据。
2. E1 达到 60 FPS（或触发 §7 停止条件并附完整证据与已排除方案）。
3. 回归矩阵（§5）全部通过；外观/哈希/夜间发光/动画/重载检查有记录。
4. 文档同步：`horde-issues.md` 状态行、本页任务状态、`horde-extreme-optimization-plan.md` 数据更新。
5. 交付源码改动清单 + 每个改动的开关/回滚方式 + 显存与首帧数据。

---

## 0.3 违规判定

出现以下任一情况，执行结果判为失败，必须回滚并按 §6 汇报原因：减少可见数量或画质换帧率；改门禁/改夹具让 PASS；只写 TODO/开关不实现；跳过 W0 或 W9 直接宣布 60 FPS；把未验证写成已验证。

---

## 1. 机器可读任务索引

```yaml
tasks:
  - id: EX-01
    name: 测量固化（整帧阶段求和 + 环境记录）
    deps: []
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeRenderMetrics.java,
            src/bench/java/org/unitego/lobecorp/client/bench/HordeRenderScenario.java]
    gate: 同轮三次帧间隔 ±5%
  - id: EX-02
    name: W0 静态减面（隐藏面剔除）
    deps: [EX-01]
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java,
            src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java]
    gate: 顶点数 -15~35%，逐帧截图无破洞
  - id: EX-03
    name: W1a MAX_WORDS 4M→16M
    deps: [EX-01]
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java]
    gate: passes ≤20、copies ≤5、expand 总时长 -15%
  - id: EX-04
    name: W1b 双缓冲（纹理+顶点缓冲）
    deps: [EX-03]
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java]
    gate: 整帧 GPU -10~25%
  - id: EX-05
    name: W2 每实例预计算（sin/cos/frameStart）
    deps: [EX-04]
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java,
            src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh,
            src/main/resources/assets/lobecorp/shaders/core/horde_iris_vertices.fsh,
            src/main/resources/assets/lobecorp/shaders/include/horde_motion.glsl]
    gate: expand GPU -10~20%，哈希一致
  - id: EX-06
    name: W3 copy 与材质重叠
    deps: [EX-04]
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java]
    gate: copy 占比 <3%
  - id: EX-07
    name: W4 材质 pass 跨块合并
    deps: [EX-04]
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java]
    gate: passes -30%
  - id: EX-08
    name: W9 距离质量档（60 FPS 关键）
    deps: [EX-02, EX-04]
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java,
            src/main/java/org/unitego/lobecorp/client/horde/HordeIrisAnimation.java,
            src/main/java/org/unitego/lobecorp/client/horde/HordeVertexLayout.java,
            src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java,
            src/main/java/org/unitego/lobecorp/config/client/HordeVisualConfig.java]
    gate: E1 60 FPS，切换无跳变
  - id: EX-09
    name: W8 极端鲁棒（降级/自适应/恢复）
    deps: [EX-03, EX-08]
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeIrisShadows.java,
            src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java]
    gate: E4 全矩阵
  - id: EX-10
    name: W6 光照区段打包与缓存
    deps: []
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeLightField.java]
    gate: light CPU P95 ≤0.5 ms
  - id: EX-11
    name: W7 首帧与预编译
    deps: []
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java,
            src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java]
    gate: 首可见帧 ≤100 ms
  - id: EX-12
    name: W5 motion 精简（可选）
    deps: [EX-07]
    files: [src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh,
            src/main/resources/assets/lobecorp/shaders/include/horde_motion.glsl]
    gate: motion GPU -30%
  - id: EX-13
    name: W10 分割区域不可进入（左右移动不进入；绝不隐藏）
    deps: [EX-01]
    files: [src/main/resources/assets/lobecorp/shaders/include/horde_spatial.glsl,
            src/main/resources/assets/lobecorp/shaders/include/horde_motion.glsl,
            src/main/resources/assets/lobecorp/shaders/core/horde_motion.fsh,
            src/main/resources/assets/lobecorp/shaders/core/horde_iris_vertices.fsh]
    gate: 正常横摆不进入区域；突发包围允许多tick平滑退出且不得隐藏；单 tick 位移 ≤0.75 格
  - id: EX-14
    name: W11 动画在原动画中等距取 4 帧（可配置回退）
    deps: [EX-02]
    files: [src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java,
            src/main/java/org/unitego/lobecorp/config/client/HordeVisualConfig.java,
            src/main/resources/assets/lobecorp/shaders/core/horde_instanced.vsh,
            src/main/resources/assets/lobecorp/shaders/core/horde_iris_frames.fsh]
    gate: 帧数=配置值（默认4）；内存 -70% 以上；循环无闪烁
  - id: EX-16
    name: W12a 新增尸体渲染基准场景
    deps: [EX-01]
    files: [src/bench/java/org/unitego/lobecorp/client/bench/CorpseRenderScenario.java,
            src/bench/java/org/unitego/lobecorp/registry/bench/LobecorpBenchProvider.java,
            src/bench/java/org/unitego/lobecorp/config/bench/LobecorpBenchConfig.java]
    gate: 场景 PASSED，64/256 尸体计数稳定，产物可重复
  - id: EX-15
    name: W12b 尸体渲染优化（默认静态+实体类距离/动画方法+批渲染+同步）
    deps: [EX-16]
    files: [src/main/java/org/unitego/lobecorp/client/entity/renderer/EntityCorpseRenderer.java,
            src/main/java/org/unitego/lobecorp/world/entity/EntityCorpse.java,
            src/main/java/org/unitego/lobecorp/client/entity/renderer/CorpseBatchRenderer.java,
            src/main/java/org/unitego/lobecorp/config/client/HordeVisualConfig.java]
    gate: 64 尸体 CPU -40%、分配 -50%；256 尸体不劣于 2×64；默认静态/180 格；复杂/动画 32 格；重组临时动画一致
  - id: EX-17
    name: W13 512 宽直线性能夹具（后续测试标准）
    deps: [EX-01]
    files: [src/bench/java/org/unitego/lobecorp/client/bench/HordeLineRenderScenario.java,
            src/bench/java/org/unitego/lobecorp/registry/bench/LobecorpBenchProvider.java,
            src/bench/java/org/unitego/lobecorp/config/bench/LobecorpBenchConfig.java]
    gate: 场景 PASSED；数量全程=配置值；同轮 3 次 ±5%
```

---

## 2. 前置检查（每次开始任务前执行并记录）

1. 读：本文、`horde-extreme-optimization-plan.md`、`horde-issues.md` 顶部“2026-10-25 联合诊断”、`horde-rendering-implementation.md`。
2. 记录 `git rev-parse HEAD`、`git status --porcelain`（暂存数、未暂存数、未跟踪数）、源码最后修改时间；保存到本轮报告。
3. 确认工作区静止（10 分钟内无源码改动）；若用户仍在编辑，**停止并上报**，不要在其未完成的树上跑基准。
4. 确认 BenchMod 可用：IDEA run configuration `BenchMod Client`；产物目录 `build/modBench/raw-results/default/client/`；失败时产物在 `artifacts/screenshots/failure-*.png` 与 `artifacts/thread-dumps/`。
5. 记录 GPU 空载状态：`nvidia-smi --query-gpu=clocks.sm,power.draw,temperature.gpu,utilization.gpu --format=csv,noheader`。
6. 备份本轮将修改的文件到 `build/agent-backups/<date>-<task>/`（构建目录，不污染源码）。

---

## 3. 任务规格

> 每个任务都必须完成规格中的**结构改动**；只加日志、只做实验、只加开关不实现，不算完成。任务之间的依赖与门禁必须按 §1 执行，不得跳序。

### EX-01 测量固化

**改动**

1. `HordeRenderMetrics`：新增“整帧阶段求和”指标（当前只有轮换单次 GPU 样本）。做法：为 `Stage` 增加累计字段；`beginFrame` 清零；`end(stage)` 累加；`finish` 时输出 `stage.<name>.gpu_frame_sum_ns` 与 `cpu_frame_sum_ns`；GPU 样本仍保留轮换取样的 `gpu_sample_ns`，两者并列不混用。
2. `HordeRenderScenario.saveStages`：把上述累计值写入 `horde-stages.jsonl` 头部/每帧字段；记录轮次环境（GPU 时钟/温度/功率、窗口尺寸、`throttle_reason`、HEAD+dirty）。
3. BenchMod report 行新增：`passes`、`draws`、`copies`、`copy_bytes`、`expand_vertices`、`instances`（已有）与 `stage.*.gpu_frame_sum`。
4. **2026-10-25用户确认A**：新增独立`Horde draw elapsed`采样，WORLD/STAGE_SUM/HORDE_DRAW_TOTAL三组非嵌套；按共同渲染帧标识分组，缺失或多次draw不能靠行号取模假定一致，失配明确标无效。总量独立记录，不混入Stage调用次数；未知、忙碌、取消、错误保持null。仅在同负载窗口统计比较阶段和与draw总量；draw总量不包含extract资源预热，不冒称整个Horde系统GPU总时间。

**验证**：同一场景连续3次，帧间隔均值差≤5%。按用户确认A，将阶段求和与同负载的独立Horde draw总量作跨帧统计对照，15%作为覆盖审计阈值；超出必须调查并记录未归因开销或分组失配，不直接判为统计错误，不补零或改数造通过。世界GPU独立记录，原帧率/P99/GPU性能目标保持；禁止逐帧精确对账或混算不同负载窗口。实机验收仍须另行授权。

**禁止**：不要改变现有轮换采样语义；不要用累计值替换 BenchMod 的 `client.frame.interval`。

---

### EX-02 W0 静态减面

**步骤**

1. 在 `HordeMeshCache` 内增加“可见 quad 过滤”：对每个 `CuboidGeoBone` 的所有 cube，判断相邻 cube 的共享面；仅剔除被两个 cube 完全覆盖、且在骨骼动画最大摆幅内也不会露出的面。实现前先用调试统计输出每模型 `quads_before/quads_after`。
2. 若无法可靠判断动画摆幅，退化方案：只剔除同一 cube 内退化面（面积≈0）与重复面；不得盲目剔除。
3. `vertexCount` 与索引数量随减面变化；`HordeIrisAnimation` 帧缓存按新顶点数生成；`HordeVertexLayout` 自动适配。

**验收**：每个模型顶点数下降 15–35%（记录实际值）；逐帧截图 A/B（含最大摆臂、转身、近景/远景、夜间发光）无破洞/缺面；动画哈希或逐帧像素差在阈值内。

**回滚**：按 visual 开关；保留原几何生成路径。

---

### EX-03 W1a `MAX_WORDS` 实验

**改动**：`HordeGpuRenderer.IrisVertices.MAX_WORDS` 由 `4 * 1024 * 1024` 改为 `16 * 1024 * 1024`（L539）。

**验证**：同轮 A/B（改前/改后各 3 轮），记录 `passes`（62→?）、`copies`（18.7→?）、`expand` 单次与整帧、`整帧 GPU`、显存峰值（`nvidia-smi memory.used` 与纹理/缓冲尺寸计算）。
**门禁**：passes ≤20 且 copies ≤5；expand 整帧下降 ≥15%；无错位/漏画；显存峰值 ≤300 MiB 增量。
**回滚**：改回常量。

---

### EX-04 W1b 双缓冲

**设计**

1. `IrisVertices` 内部由单套 `(texture, view, vertices)` 改为 `[2]`；`draw(items, …, slot)` 使用 slot；`prepare` 按 `MAX_WORDS` 分配两套。
2. 块循环（`HordeGpuRenderer.drawGroup` L229）按块号 `i % 2` 选 slot；每块自己的 `copyTextureToBuffer` fence 只保护自身 slot。
3. 保留 `HordeVertexLayout` 的块边界；`parameters` UBO 布局不变。

**验证**：块 N 的材质绘制与块 N+1 的 expand/copy 交错提交后，`整帧 GPU` 下降 10–25%；截图/动画与改造前逐帧对比；显存约 256 MiB。
**门禁**：E1 ≥35 FPS（阶段 1 门禁）。
**回滚**：开关切回单缓冲。

---

### EX-05 W2 每实例预计算

**设计**

1. 方案 A（首选）：`MOTION_VALUES` 5→8，motion pass 额外写 `sin(angle)`、`cos(angle)`、`frameStart`，第 8 字可放可见性；`horde_iris_vertices.fsh` 直接读，不再每片元 `sin/cos/floor/fract` 与 `row/column` 推导。
2. 方案 B（若 A 影响面大）：新增 per-instance 小纹理 `HordeIrisAux`，由 motion pass 一次写入。

**验证**：展开纹理 readback 哈希（改造前后同帧序号逐字节一致）；截图 diff=0；expand GPU −10~20%。
**禁止**：不得改变 20 FPS 动画语义与 float32 位置。

---

### EX-06 W3 copy 与材质重叠

在 EX-04 基础上：块 N 的 `base/glow` pass 与块 N+1 的 `expand/copy` 顺序交错；确保 `queueFencedTask` 的等待点按块而非帧聚合；若 GL 强制同步导致无收益，记录并回退到 EX-04 的串行方案。

**验证**：copy 整帧占比 <3%；整帧 GPU −2~5 ms；无错位/幽灵。

---

### EX-07 W4 材质 pass 合并

把每块的 `base`、`glow` 各合并为 1 个跨块 pass（同一 pass 内循环 `setVertexBuffer`、`drawIndexed(baseVertex,…)`、按材质分组绑定纹理）；`DynamicTransforms`、lightmap、overlay 提到循环外。

**验证**：passes 下降 ≥30%；draws 不变；截图一致；多事件/多材质场景正确。

---

### EX-08 W9 距离质量档（60 FPS 关键）

**设计**

1. 每个 `HordeVisualDefinition` 生成 2–3 个档位（全精度 / 中 / 低），顶点数比例建议 100% / 60% / 35%；档位由 `HordeMeshCache` 生成、`HordeIrisAnimation` 逐档缓存。
2. 选择依据：**绘制块中心到相机距离**（块级，非逐单位），阈值建议 ≤48 格全精度、48–96 中、>96 低；阈值写入 `HordeVisualConfig`（新增 `lodEnabled`、`lodNear`、`lodFar`，默认开启、可在配置关闭）。
3. `HordeVertexLayout` 按“本帧实际档位”的顶点数计算块边界；`MAX_WORDS`/双缓冲按档位重算。
4. 档位切换仅发生在块边界；同一块内不混档。

**验收**

- E1 60 FPS（帧均 ≤16.7 ms、P99 ≤20 ms、GPU ≤15 ms），绘制数量 == 配置值（默认 10,000）。
- 切换阈值附近截图 A/B 无跳变/破洞；夜间发光、动画、切线正常。
- 关闭 LOD（配置）时回到全精度，帧率回到 40 FPS 档；两种配置都要记录。

**风险**：远景细节下降需用户可见说明；若“可接受外观”下 60 FPS 仍不可达，**停止并上报**，不得继续降档。

---

### EX-09 W8 极端鲁棒

1. `HordeIrisShadows.entityVertexWords()` 不支持布局时：不抛异常给 `prepare` 导致永久 `failed`，改为返回“不可用”，由 `HordeGpuRenderer` 回退实例化路径并退避重试；日志限流。
2. `IrisVertices.prepare` 失败（显存/容量）时：按 16M→8M→4M 逐级缩小并记录降级；仍失败才标记事件 failed（带原因）。
3. 资源重载/切包后：重建纹理/缓冲、清空 `IRIS_ANIMATIONS` 代际、下一帧恢复。
4. 统计输出降级原因（`Counter.DEGRADED` 或日志字段）。

**验收**：E4 矩阵：切包、关光影、资源重载、改窗口、多事件、显存压力模拟（可临时改小上限）均恢复且无残留。

---

### EX-10 W6 光照区段打包

`HordeLightField`：`Section` 改为 `long` 打包（x/y/z 位域）；`requiredSections` 按 Block 缓存候选集合，`batches.distinct()`、槽位遍历只在集合变化时执行；保留逐 tick 加载失效与 5 tick 亮度更新。

**验收**：light CPU 均值 ≤0.3 ms、P95 ≤0.5 ms；每帧分配下降；同场景帧间隔不退化。

---

### EX-11 W7 首帧

1. 用 `GpuDevice.precompilePipeline` 预热 `horde_motion`、`horde_iris_vertices`、`horde_region_update`、`horde_terrain_update`、`horde_spatial_index`、`horde_iris_frames`、`horde_gpu`。
2. 模型烘焙拆为“解析/骨骼/帧/上传”，给每帧预算（例如 ≤3 ms），在 header 到达后提前开始；未就绪不绘制半成品。

**验收**：首可见帧 ≤100 ms；STABILIZE 最大帧 ≤150 ms；重载不重复全额阻塞。

---

### EX-12 W5 motion 精简（可选）

在 EX-07 之后，把 `horde_motion.fsh` 的 6 次 `movingLocation` 中重复项合并（CPU 预混合区域或位移缓存），目标 −30% motion GPU。若收益 <0.3 ms，记录后放弃。

---

### EX-13 W10 分割非穿透（修复“进入区域即消失”）

**已确认根因（必读，不得另找借口跳过）**

- `horde_motion.fsh:30`：`value = light.x < 0.0 || !regionClear(point) ? -1.0 : …`；`regionClear` 在 `horde_spatial.glsl:44-71` 用原始区域 AABB + `radius` 判定。
- `horde_iris_vertices.fsh:45-48`：word 3 可见性要求 `scalar(HordeMotion, cached + 4) >= 0.0`，否则 `bits = 0u`（整单位 alpha=0）。
- 结果：单位被软流场推进、目标瞬移、新区域出现或过渡期没被及时推出时，只要最终点落在原始区域内就**整帧消失**，离开后突然出现。这正是用户看到的“进入分割区域后消失”。

**改动（必须全部实现）**

1. `horde_spatial.glsl`：新增 `bool regionBlocked(vec3 point)` 与 `vec3 regionClamp(vec3 desired)`（或等价接口）：遍历同一空间索引候选，对每个原始区域（`raw` 7 int，active>0）计算点是否落在 AABB + `radius` 内，并返回把点挡在区域外的 XZ 位置（沿最近边，含 0.05 格余量）；Y 只用于判定是否同层。
2. `horde_motion.glsl` 的 `movingLocation()`：**在施加 sway/drift 时就约束**，不是事后补救——对当前 `station` 求出允许的横向区间，把 `sway` 与 `displacement` 的合成结果 clamp 在区域外；若目标瞬移、区域新增或扩张导致已被包住，则按用户确认的B方案持续多tick平滑拉出；每tick总位移≤0.75格，期间允许暂时处于区域内，绝不隐藏或瞬间推出。每次采样的几何求解最多迭代2次，不代表必须在1或2个tick内完成退出。
3. `horde_motion.fsh`：component 4 **不再**因 `!regionClear(point)` 写 `-1`；只有“光照未加载”（`light.x < 0`）才允许 `-1`。
4. `horde_iris_vertices.fsh` word 3：删除区域可见性门控；可见性只保留 route/segment/光照条件；单位在区域内也必须 alpha=1.0（不得淡出到 0，也不得用软 alpha 规避“进入”问题）。
5. 统计：新增 `PENETRATION_REJECTED`、`PENETRATION_MAX`（每帧最大穿透深度），写进 stages JSONL，便于回归断言。

**验收（必须逐帧记录，不接受“看着还行”）**

- 构造用例：目标瞬移进单位群、单位横穿目标、两目标夹击、区域从零尺寸长出、随机横摆到边界。
- 逐帧采样断言：正常横摆/漂移不产生新增区域穿透；突发包围允许多tick纠正中间态，逐帧记录 `PENETRATION_MAX` 与退出耗时。区域稳定且出口可达时，最终收敛到 `PENETRATION_MAX == 0`；不再要求1tick内归零。
- alpha 全程 1.0，无消失/闪现；单位数量与配置一致；单 tick 位移 ≤0.75 格；无左右抖动/抖动累积。
- 正常让行、平滑过渡、高度隔离、随机横摆身份稳定性回归通过；截图/录像无闪烁。
- 若约束造成边界抖动，必须改为平滑限幅（限制加速度）后复测，不得退回“隐藏”，也不得靠降低横摆幅度掩盖。

**回滚**：保留原函数实现于独立提交，出问题整段回退；不得以开关默认关闭来规避验收。

---

### EX-14 W11 动画烘焙改为 4 帧

**改动**

1. `HordeMeshCache`：删除 `SAMPLE_FPS = 20` 的时间×采样逻辑；新增客户端配置 `HordeVisualConfig.animationFrames`（范围 4–20，默认 4，带翻译），`int frames = HordeVisualConfig.animationFrames.get()`；采样时间 `clip.durationSeconds() * frame / frames`，即**在原动画上等距平均取 4 帧**；`frames` 必须 ≥1 且写进 `Model`。
2. `HordeIrisAnimation` / `FrameLayout` 已按 `model.frames()` 计算，无需特判；确认帧缓存字数按配置帧数计算（4 帧时为原来的 1/5）。
3. shader 检查（不要求改时间基）：`horde_instanced.vsh` 与 `horde_iris_vertices.fsh` 用 `AnimationInfo.y`（帧数）与 `OriginTime.w/(20.0*AnimationInfo.z)`（20 = tick/秒）选择帧；确认 `horde_iris_frames.fsh` 的帧索引同理，无 20 帧假设。
4. 统计：`iris_frame_words` 与动画纹理字节下降必须记录；报告写入前后数值。
5. 文档：同步更新 `horde-issues.md`/`horde-rendering-implementation.md` 中“20 FPS 采样”的描述为“默认 4 帧等距烘焙（2026-10-25 需求）”，并在 H21 记录该需求覆盖。

**验收**

- `frames` 等于配置值（默认 4）可在统计/日志核对；动画纹理与帧缓存内存下降 ≥70%。
- 循环边界（frame 3→0）无闪烁、无 T-pose、无炸模；连续 30 s 稳定。
- 四个相位截图（0/0.25/0.5/0.75）与旧 20 帧版本对比：姿态合理、无穿插/拉伸；动画速率不变（仍每 `duration` 秒循环一次）。
- 20 TPS 与 30/60/144 FPS 下帧选择单调、无跳帧错误；资源重载后重建正确。

**风险**：4 帧步进是用户明确要求，不得用 20 帧“更平滑”为由拒绝；若出现循环抖动，修采样取整，不得改回 20。

---

### EX-16 W12a 新增尸体渲染基准场景（先测再改）

**改动**

1. 新增 `src/bench/java/org/unitego/lobecorp/client/bench/CorpseRenderScenario.java`：客户端隔离世界、固定相机、生成 N=64（可配 256）个真实 `EntityCorpse`（owner 为真实/本地创建的 Sweeper），不启用 AI，与 `SweeperRenderScenario` 相同的截图/GPU 采集/门禁模式；MEASURE 600 ticks。
2. `LobecorpBenchProvider` 注册 `lobecorp.corpse-render`；`LobecorpBenchConfig` 增加 `corpseRenderCount`（默认 64）。
3. 场景校验：尸体数量全程稳定、无异常、产物（screenshot/gpu/jfr）登记。

**验收**：`BenchMod Client` 中该场景 PASSED；64 与 256 两种数量各跑 3 轮，CPU/GPU/分配可重复（±10%）；无服务器依赖（H25 不阻塞）。

---

### EX-15 W12b 尸体渲染优化

**距离范围已确认B（2026-10-25）**：保留尸体类型现有5区块（80格）基础跟踪，不扩大服务器下发；下述180格仅为客户端已收到且仍存在的常规STATIC尸体渲染上限，复杂/动画尸体仍为32格。最终可见距离继续受原版跟踪缩放、玩家/服务器视距及区块跟踪限制；本地客户端基准不验收服务器180格可见。

**已确认问题（代码证据）**

- `EntityCorpseRenderer.extractRenderState`（L33-57）每帧对每个尸体调用 `entityRenderDispatcher.getRenderer(owner).createRenderState(...)`，**每帧新分配 RenderState 并重建 owner 渲染状态**；`submit`（L60-89）每个尸体再走完整 owner renderer（GeckoLib 骨骼/动画）。
- L53-56 每帧直接改 `livingEntity.hurtTime/deathTime`（对客户端实体的副作用），并与渲染状态职责混在一起。
- 没有距离策略/批处理；尸体数量随战斗累积（事件尸体保留 200 tick），在事件+粒子场景叠加。

**改动（全部必须实现）**

1. **尸体默认静态 + 实体类方法**：在 `EntityCorpse` 增加可覆写方法：`renderDistance()`（默认 180，复杂/动画尸体 32）、`hasCorpseAnimation()`（默认 `false`）、`isComplexModel()`（默认 `false`）；默认尸体不播放任何动画、使用静态烘焙姿态。子类或注册项可覆写这些方法以启用“尸体专用死亡动画”或 32 格距离。渲染器与批渲染只读这些方法，不维护外部猜测表。
2. **冻结姿态缓存**（STATIC）：首次提取用固定 `partialTicks` 与 `IS_CORPSE=true` 生成一次 owner 渲染状态/网格并缓存；后续帧只更新位置/朝向/光照；不得每帧 `createRenderState/getRenderer`。
3. **动画边界（2026-10-25用户确认A）**：**实体死亡动画属于实体自身逻辑，Horde不接管**；生成的尸体默认无动画、静态模型。本轮不新增尸体自身重组动画、动作资源或对应客户端同步状态；保留施术清道夫的CLEAR1/2/3、现有服务端重组进度与复活流程，以及既有反向躺倒变换。只有已明确提供尸体专用动画且 `hasCorpseAnimation() == true` 的类型才进入对应动画路径；不得把接口存在写成新增动画完成，也不得把实体死亡动画接入尸体渲染管线。
4. **消除每帧分配与副作用**：复用 `RenderState`；移除对 `livingEntity.hurtTime/deathTime` 的每帧写入（改在 render state 上置位）。
5. **批渲染**：新增 `CorpseBatchRenderer`，把可见 STATIC 尸体按共享静态网格 + 每尸体变换合并为少量 draw；Iris 光影下若批量管线无法映射，保留冻结姿态的每尸体渲染并标记路径；不得永久停显。
6. **距离**：对客户端已经收到且仍存在的尸体，复杂/ANIMATED渲染上限 **32 格**、常规STATIC **180 格**；边缘淡出，超距不渲染并记录跳过计数。按用户确认B保留服务器5区块基础跟踪，不以渲染上限扩大下发范围（玩法/清理不变）。
7. **同步**：不额外引入每tick附件/NBT写入或广播，只在归属/期限/状态变化时更新，位置同步交给原版tracking；既有服务端技能临时重组进度保持其用途，不能误报为每tickNBT/网络开销。本轮不为新增尸体重组动画创建同步状态；现有重组/清理事件必须可靠。

**验收**

- 64 尸体（STATIC，常规 180 格）：render thread CPU 比 EX-16 基线下降 ≥40%，每帧分配下降 ≥50%，GPU 不劣化。
- 256 尸体：成本 ≤2× 64；无 >100 ms 帧。
- 默认行为：所有尸体默认 `hasCorpseAnimation()==false`、静态模型、180 格；`isComplexModel()==true` 或 `hasCorpseAnimation()==true` 的类型 32 格，实测生效并记录跳过计数。
- 动画边界：实体死亡动画由实体自身逻辑播放，Horde不接管；仅已提供尸体专用动画并显式启用的类型进入动画路径。本轮按用户A不新增尸体重组动画或同步状态，默认尸体保持静态。
- 重组/死亡：施术者CLEAR1/2/3、既有反向躺倒变换、服务端重组进度与复活/清理流程保持；STATIC尸体在重组期间继续静态，不新增尸体动作。
- 外观 A/B：姿态/贴图/旋转/阴影一致；截图逐像素差在阈值内。
- 无每帧实体字段写入；尸体清理/卸载/重载缓存正确释放，无泄漏。
- 64 尸体 + 10,000 horde + 光影叠加无停显/崩溃，并记录帧率。

**回滚**：批量路径开关关闭则回到冻结姿态的每尸体渲染；再回退则恢复原实现（保留独立提交）。

---

### EX-17 W13 512 宽直线性能夹具（后续测试标准）

**背景**：用户 2026-10-25 指定后续性能测试为“宽度 512 的直线事件，x≈+160 到 x≈-160”。

**改动**

1. 新增 `HordeLineRenderScenario`（bench，id `lobecorp.horde-line-render`）或参数化现有 `HordeRenderScenario`：
   - `rules.width = 512`；直线路径 `(160, y, 0) → (-160, y, 0)`（轴向可配置）；
   - 绘制数量跟随 `HordeVisualConfig.MAX_VISIBLE` 配置（默认 10000），**不得写死、截断或静默降数量**；配置为 `-1` 时按 10000 测量并在报告中注明；
   - 相机固定在线路侧面/俯视，保证可见比例稳定；MEASURE 1200 ticks。
2. 注册 provider/config；每帧校验 `HordeClient.batches()` 总数量 == 配置值，不一致时报 workload 断言（与现有夹具一致）。
3. 产物：summary/report/screenshot/gpu/stages；记录宽度、线路端点、配置数量与可见比例。

**验收**：场景 PASSED；数量全程等于配置值；同轮 3 次帧间隔 ±5%；作为 E1/E1-拉伸 的唯一性能验收夹具；旧的 128 宽环线场景只保留兼容回归。

**注意**：宽 512 会显著增加列数与块数，必须与 W1/W4 的块预算一起验证；不得通过降低配置数量让门禁通过。

---

## 4. 实验协议（每个任务都必须遵守）

1. **同轮交错 A/B**：同一会话内切换开关/常量，至少 3 轮（A/B/A、B/A/B）。
2. **环境记录**：每轮记录 HEAD+dirty、GPU 时钟/功率/温度（每 5 s 采样）、窗口 2560×1494 最大化聚焦、VSync 关、不限帧、`throttle_reason`、`gpu_started/completed/skipped`。
3. **指标**：BenchMod `client.frame.interval`（MEASURE 全窗口）+ GameRenderer `render_gpu_ms`（30 s 子窗口）+ `stage.*.gpu_frame_sum`；报告均值/P50/P95/P99/最大。
4. **判定**：用同轮差值；环境差异 >5% 作废重跑；单轮不下因果。
5. **正确性**：截图 A/B、展开纹理 readback 哈希、夜间发光、动画帧、资源重载。
6. **夹具失败处理**：若出现 `GPU horde workload changed` 或截图全天空（位姿失效），允许重试 1 次并记录为夹具问题；连续 2 次失败则停止并上报，不计入性能结论。

---

## 5. 验收矩阵

| 场景 | 实例/实体 | 光影 | 通过条件 |
| --- | --- | --- | --- |
| E1（最终） | 数量=配置值（默认 10,000） | 开 | **≥60 FPS**，帧均 ≤16.7 ms、P99 ≤20 ms、GPU ≤15 ms；绘制数量 == 配置值；LOD 配置与说明齐备 |
| E1（阶段 2 门禁） | 数量=配置值 | 开 | ≥40 FPS、P99 ≤30 ms（W1–W4 后先到这里） |
| E2 | 2×5,000 + 64 真实 + 粒子 | 开 | ≥40 FPS、P99 ≤35 ms |
| E3 | 10,000 + 阴影 32 + 夜间 + 远程 | 开 | 不崩、不永久停显、P95 ≤50 ms |
| E4 | 任意 | 开/关/切换 | 切包/重载/关光影/改窗口/多事件均恢复；无错位 |
| EX-13 区域不可进入 | 10,000 | 开/关 | 逐帧断言无单位进入原始区域；alpha 恒 1；单 tick 位移 ≤0.75 格；无闪烁 |
| EX-14 4 帧动画 | 64 真实 | 开/关 | 帧数=配置值（默认 4）；循环 30 s 稳定；内存 −70% |
| EX-15/16 尸体 | 64 / 256 尸体 | 开/关 | CPU −40%、分配 −50%；256 ≤2×64；默认静态/无动画；尸体专用动画与重组临时动画正确；实体死亡逻辑不受影响 |
| EX-17 512 宽直线 | 数量=配置值 | 开/关 | PASSED；数量全程=配置值；同轮 3 次 ±5% |
| 回归 | 数量=配置值 | 关 | 帧间隔不高于同轮基线 +5% |
| 回归 | 64 真实实体 | 开/关 | 不高于同轮基线 +5% |

---

## 6. 汇报模板（每完成一个任务写回给用户）

```
任务：EX-0x
改动文件：<列表>
关键改动：<3 行内>
同轮 A/B 数据：
  A: frame mean/P95/P99=… ; GPU=… ; passes/copies=…
  B: frame mean/P95/P99=… ; GPU=… ; passes/copies=…
  GPU 时钟/功率/温度=…
正确性：截图/哈希/发光/动画 <通过或差异>
门禁：通过 / 未通过（原因）
回滚：已保留 / 已回退
遗留：<问题与下一步>
```

---

## 7. 停止条件（只有以下情况可以停，其余一律继续执行）

不得以“风险高”“改动大”“无法验证”“先观察”为由停下；这些不是停止条件。

1. “可接受外观”下 E1 60 FPS 仍不可达（W0+W9 已实施仍差 >15%）。
2. 任何优化导致绘制数量低于配置值、关闭发光、动画帧数低于配置值（默认 4 帧）、或把“降精度”用在近景全精度档。
3. 夹具连续 2 次失败或工作区不静止。
4. 需要修改用户未提交/暂存内容，或需要升级 Iris/NeoForge 版本。
5. 需要调整 H15 净空或玩法规则（已确认维持 3 格）。
6. Git 提交被沙箱拒绝时：只上报，不绕过。

---

## 8. 文件与证据索引

| 内容 | 位置 |
| --- | --- |
| 方案与决策 | [horde-extreme-optimization-plan.md](horde-extreme-optimization-plan.md) |
| 问题清单 | [horde-issues.md](horde-issues.md) |
| 渲染入口 | `src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java`（`MAX_WORDS` L539、块循环 L229、expand L620–641、copy L643–654、材质 L663–707） |
| 展开/运动 shader | `src/main/resources/assets/lobecorp/shaders/core/horde_iris_vertices.fsh`、`horde_motion.fsh`、`include/horde_motion.glsl` |
| 模型与帧缓存 | `HordeMeshCache.java`、`HordeIrisAnimation.java`、`HordeVertexLayout.java` |
| 光照/索引 | `HordeLightField.java`、`HordeSpatialIndex.java` |
| 基准产物 | `build/modBench/raw-results/default/client/{summary.json,report.md,artifacts/…}` |
| 历史归档 | `C:/Users/wang_/AppData/Local/Temp/horde-sodium-diagnosis-20261025-6d507349` |

**执行顺序**：EX-01 → EX-03 → EX-04 →（EX-02 / EX-13 / EX-14 并行）→ EX-05 → EX-06 → EX-07 → EX-08 → EX-09 → EX-10 → EX-11 → EX-12 → EX-17（先建 512 宽直线夹具）→ EX-16（尸体基准）→ EX-15（尸体优化）。按用户确认的A顺序，代码按实现依赖继续，实机门禁未执行不阻止后续已确认代码；只有未来获授权的实机验收通过后才标DONE。EX-17建好后，后续性能验收都以它为准。

## 9. 2026-10-25 本轮执行记录（代码已写入，运行门禁未执行）

用户已确认原有Horde WIP纳入本次提交，原5项暂存可随Horde提交消耗；其他系统改动、日志、备份和运行产物不夹带。六项关键决定均已记录，当前不再有阻止代码实施的待选项。93份授权文件已提交c21899d，原5项暂存按用户A随提交消耗，未推送；通用构建号闭包修改与历史日志保留在工作区。下面每项均为 **CODE_WRITTEN / RUNTIME_GATE_NOT_RUN**，不是DONE。编译基于完整工作区，未单独验证纯提交检出。

统一检查：37份Java/GLSL错误诊断全部analyzed，修复两处本轮错误后0错误；23份Java警告审阅69项（含借用/跨帧资源拥有者、GeckoLib Internal、既有空值/注解及未使用项），不声称0警告。补查8份原有Horde接入文件0错误。IDEA BenchMod Compile退出0，compileJava/compileBenchJava及生产包隔离通过；Data退出0，23份实际生成资源只更新两份中英文语言，另有两份缓存变更，新增9组翻译键，未删除资源。未执行JUnit、游戏、BenchMod Client/Server或Debug；IDE GLSL诊断不等于真实驱动编译。

所有任务共同的A/B记录：A、B的frame mean/P95/P99、世界/Horde总量/阶段GPU、passes/copies、显存峰值、首帧、GPU频率/功耗/温度均 **未采集**，截图/哈希/夜间发光/动画/重载/三轮一致性均 **未执行**。原因是用户禁止游戏测试，性能阈值保持原值，无60FPS结论。修改前字节/哈希/原暂存补丁及检查摘要：build/agent-backups/20261025-extreme-confirmed/；首批备份仍保留在20261025-extreme-code/。

### 任务：EX-01

- 改动文件：HordeRenderMetrics、PhotonFrameCapture、HordeRenderScenario、HordeBenchmarkEnvironment。
- 关键改动：WORLD / STAGE_SUM / HORDE_DRAW_TOTAL按共同render_frame_id分组；重复/漏绘标失配，阶段轮换按阶段组计数；异步逐调用求和，零有效，未知/忙碌/取消/失败为null。新增独立Horde draw elapsed，15%是覆盖调查阈值，不能跨组逐帧守恒。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：旧ROTATING模式及捕获入口保留。

### 任务：EX-02

- 改动文件：HordeMeshReduction、HordeMeshCache。
- 关键改动：仅剔除退化面、已证明不透明闭合体的完全内面和相反共面；限定同骨骼，透明/薄刀面保留；各动画帧复用同一面掩码。没有为凑比例移除未证实面。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：reduceHiddenFaces=false。

### 任务：EX-03

- 改动文件：HordeGpuRenderer、HordeVisualConfig。
- 关键改动：Iris工作集上限16 MiWords，显式4/8/16档，中间值下取档；每次展开/复制长度由实际容量计算。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：irisWorksetMiWords=4；内存故障自动16→8→4。

### 任务：EX-04

- 改动文件：HordeGpuRenderer.IrisVertices。
- 关键改动：两套RGBA8纹理/view/VBO轮转，工作集资源在消费完成前保留；16 MiWords两套纹理+VBO约256MiB自有分配，不是驱动峰值证明。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：irisDoubleBuffer=false；实际GPU重叠待测。

### 任务：EX-05

- 改动文件：horde_motion.fsh、horde_iris_vertices.fsh、horde_instanced.vsh。
- 关键改动：每实例缓存位置、角度、亮度、sin/cos和帧起点共8字；展开逐顶点读取，移除重复行列、帧号及三角求值，float32位置保留。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：按备份逆向辅助缓存布局与消费者，必须同步恢复。

### 任务：EX-06

- 改动文件：HordeGpuRenderer.IrisVertices。
- 关键改动：交替工作集展开→GPU复制→材质提交，CPU不映射扩展顶点，不等待回读；传输量与历史复制量分开记录。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：irisDoubleBuffer=false；未证明驱动真正重叠或copy占比<3%。

### 任务：EX-07

- 改动文件：HordeGpuRenderer.IrisVertices。
- 关键改动：在有界双槽窗口内先准备两工作集，再分别提交base/glow；跨块按兼容模型/材质合并，保留独立回退。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：irisMergeMaterials=false。全帧保留所有VBO会突破固定预算，采用双槽窗口，未宣称全帧只两个pass。

### 任务：EX-08

- 改动文件：HordeMeshReduction、HordeMeshCache、HordeGpuRenderer。
- 关键改动：全精度/中距闭合包围/远距闭合四面体三档；近48、远96格并带迟滞。刀/手/头和薄面保留，低档只合并不受run通道驱动的刚性子骨骼，代理不更省则保留原网格。按资源代际缓存与统计各档原/最终顶点，不减少实例数。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：lodEnabled=false；lodNear/lodFar显式可调。外观、切档与实际收益待验。

### 任务：EX-09

- 改动文件：HordeGpuRenderer、HordeIrisShadows、HordeClient。
- 关键改动：不支持的9/14字布局返回不可用并走原生实例化；工作集故障缩档，持续故障5秒后重试恢复。读取锁定Iris管线versionCounter识别同启用状态换包；资源/设置变化只清模型资源，保留事件和运动历史。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：Iris各结构开关可回退；无需升级依赖。故障帧的可见性不作静态保证。

### 任务：EX-10

- 改动文件：HordeLightField。
- 关键改动：区段/槽位/集合使用原生long，输入/集合不变跳过去重和常规全扫描；保留每tick装载判断、5tick亮度刷新、120绘制周期回收及原版天空光回退。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：按备份逆向long缓存改动；不改亮度协议。

### 任务：EX-11

- 改动文件：HordeMeshCache、HordeGpuRenderer、HordeClient及Region/Terrain/Spatial/IrisAnimation/UnitStats。
- 关键改动：header排队，按协作预算推进解析/骨骼/几何/动画采样和完整上传；预热增至10类管线并包含反向深度，各自失败重试；换包/重载复位代际。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：保留同步get入口。3ms是跨步骤预算，解析/驱动编译/上传不可抢占，不能静态承诺首帧≤100ms。

### 任务：EX-12

- 改动文件：horde_motion.glsl、horde_motion.fsh、HordeUnitState。
- 关键改动：motion读取稳定编号GPU位置/方向历史，不再为每个缓存分量重新走完整路线和前后切线；新增UNITS及UNIT_STATS阶段单独计入，不能只看motion下降忽略新增成本。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：布局回退须同步恢复生产/消费shader；没有CPU逐单位预混合。

### 任务：EX-13

- 改动文件：HordeUnitState、HordeUnitStats、horde_unit.glsl、horde_unit.vsh/fsh、horde_spatial.glsl。
- 关键改动：64位全局行+列标识，CPU只提交编号页和批范围；GPU双缓冲持久xyz/方向/时间，位移≤0.75×经过tick，最短弧阻尼转向。正常从区域外进入的候选拒绝；突发包围沿选定侧多tick退出，不以regionClear隐藏。实际Y重叠、原3格净空和全动画模型半径保留。GPU归约穿透最大值/拒绝数，仅异步回读8字节标量，未完成/失败为未知。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：决定B：不恢复瞬移推出。每事件历史预算32MiB，超过当前可见编号页容量会报错而不截断，超大/重叠区域及预算异常仍待验，见唯一问题清单。

### 任务：EX-14

- 改动文件：HordeMeshCache、HordeVisualConfig。
- 关键改动：完整原run周期均匀取配置帧数（默认4，4～20可调），保持duration/循环时间基，不创建每单位控制器。记录帧数/各档数据字节。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：animationFrames可调至20；原缓存14帧→4帧仅全精度同网格理论减少71.4%，三档合计内存未证明减少70%。

### 任务：EX-15

- 改动文件：EntityCorpse、EntityCorpseRenderer、FrozenCorpseMeshProvider、CorpseBatchRenderer及corpse shaders。
- 关键改动：默认静态接口、常规180/复杂或动画32；owner状态按身份/视觉revision/资源代际缓存；删除创建/读档重复owner初始化，不增加每tickNBT/同步。明确支持的普通清道夫专用尸体网格冻结一次，通过原生实例化或Iris原版材质批绘；光照/位置/选中轮廓保持动态，退出/卸载/重载释放自有缓存。未知模型、反向姿态、轮廓和阴影保留原渲染。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：corpseBatch=false。用户B保留5区块基础跟踪；用户A不新增尸体重组动画/同步。普通兜底仍有原GeckoLib骨骼提交，不宣称全部尸体零CPU骨骼。

### 任务：EX-16

- 改动文件：CorpseRenderScenario、LobecorpBenchConfig、LobecorpBenchProvider。
- 关键改动：注册lobecorp.corpse-render；64/256个有归属本地尸体，固定布局/相机，600tick测量；GPU28秒为子窗口。记录真实批绘/兜底/缓存统计和资源身份，检查失败累积量，仅清自身对象并恢复相机。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：-Dlobecorp.bench.corpseRenderCount=64或256；场景未运行，不能记PASSED。

### 任务：EX-17

- 改动文件：HordeRenderScenario、LobecorpBenchConfig。
- 关键改动：复用lobecorp.horde-render，不重复注册场景。直线x+160→-160、宽512；数量取显式maximumVisible，-1只在隔离夹具回退10000，非正值报错。记录相机/端点/实际资源/临时缺失高度样本数；不强制加载区块，原配置/相机/捕获令牌按归属恢复。
- 同轮A/B、正确性与门禁：按本节统一记录；代码已编译，运行门禁NOT_RUN，没有新增实机数据。
- 回滚与遗留：生产maximumVisible默认-1保留；未运行新夹具，不复用旧夹具数据作验收。
