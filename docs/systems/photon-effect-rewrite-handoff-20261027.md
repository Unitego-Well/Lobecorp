# 特效重构交接文档（2026-10-27 版）

更新：2026-10-27 14:10。读者：后续接手的 AI／人。本文是**当前有效的交接入口**，取代 `photon-effect-rewrite-execution-plan-20261026.md` 的现状部分；R1～R6 的框架与历史证据仍在旧文档中保留。

目标：即使本次会话上下文丢失，只读本文件即可继续。

2026-10-27 接续边界更新：当前 Photon 对话 `01a17d1d-80cf-74b1-a5dc-c8bd660dffc3`，旧对话排除读取与调度。当前任务优先 R2／P22／P23，暂不执行下文建议的其它三个大 FX 外部化或 R3/R4。所有编辑经 IDEA，Git 经 `git_background` 禁钩子；不复用下文历史 shell／checkout 操作。游戏／Debug／GPU／重负载构建等待新 Horde 本轮明确释放且清理，再在现有授权内完成验证。

2026-10-27 方法约束（以用户最新连续指令为准）：允许自有 Minecraft 游戏窗口最大化并维持焦点；已取消失焦硬门禁，不再仅因失焦判失败、作废或丢弃样本，焦点变化／实际补焦次数仍如实记录。允许游戏内部模拟输入、GUI／控件／坐标点击、后台定向操作和专用 MCP/API，但不得移动／捕获系统实体鼠标、注入全局输入、抢占实体输入或操控其它桌面窗口／显示器。最大化和保持焦点仅用于本轮自有游戏，不恢复显示／还原／移动窗口、切全屏或修改尺寸／分辨率的旧路径。其余相机、GPU、原始身份、最大化／非最小化等验收边界保留；含失焦区间时报告真实情况，不伪称全程聚焦。Horde→Photon 时段顺序不变，不提前启动游戏。

## 0. 一句话现状

本轮完成三件事，均已提交并验证：

1. **`.mfxlib` 特效管理工程**（提交 `38cea898`）：新项目类型，打开工程不解析任何 FX，选中条目才建一份预览。
2. **激光预热同步段治理**（提交 `38cea898`）：把子 FX 定义解析移出同步路径，同步段 867 → 329 ms（−62%）。
3. **模型资产外部化**（提交 `c30625fb`）：`laser.fx` 的 4 个内嵌模型拆成 OBJ 几何 ＋ `.mfx.nbt` 附加数据，体积 −84%，**视觉验收已通过**。

下一步优先级：把模型外部化推广到其余 3 个大特效 → 继续 R2 残余长帧 → R3 子模板缓存。

## 1. 恢复上下文必读（按顺序）

1. 本文件
2. `docs/systems/photon-workspace.md`（最新进度与能力边界）
3. `docs/systems/changes.md` 顶部两条 2026-10-27 记录（本轮范围、关键文件、实测数字）
4. `docs/issues.md`（唯一问题清单：P19、P22、P23、P04 等）
5. `docs/systems/photon-effect-rewrite-execution-plan-20261026.md`（R1～R6 框架、R2 A/B 历史数据、命令手册）
6. `docs/systems/photon-effect-architecture-rewrite-20261026.md`（目标架构与等价性契约）
7. 会话级交接：`Lobecorp/.agent/handoffs/20261027-photon-model-externalize.md`（不提交 Git）

## 2. 环境与硬约束

- 工作区：`E:\xm\mc\mod\Unitego-Well\Lobecorp\.workspace\lobecorp_photon`，分支 `codex/photon`；**不推送**。
- IDEA：项目已打开；所有 MCP 调用带 `project_path=E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon`。**项目文件只能经 IDEA MCP 编辑**（Java、资源、Gradle、Markdown）。
- 构建：`JAVA_HOME=C:\Users\wang_\AppData\Local\Programs\IntelliJ IDEA\jbr`，`./gradlew.bat <task> -PphotonBench=true --offline --console=plain`。
- Git：`ops_bash` 直接执行；只 `git add` 明确文件；**不要 `git add -A`**。
- 用户 WIP，**一律不提交、不覆盖**：`.codex/config.toml`（M）、`src/test/java/.../PhotonModelPoseSamplerTest.java`（M）、`src/test/java/.../PhotonModelStagedResourcesTest.java`（A）、`logs/`（??）。
- 文档分工：项目规则／修改记录／问题清单放 `docs/` 并提交；报告／交接／计划／记忆放 `.agent/`（`Lobecorp/.agent/`，已在 `.gitignore` 忽略），新增时同步 `.agent/README.md` 索引。

## 3. 本轮实测结论（含阴性，避免重做）

### 3.1 预热同步段治理：正面，已实施

`QueenBeamEffects.prewarm()` 原在同步段调用 `FXHelper.getFX(spawner.config.fxLocation)` 解析子 FX 定义（几 MB NBT），但该结果只用于预热缓存，真正消费在 `prepareNext` 内部。改为排进预热队列。

分段日志（新增 `define`/`shell`/`child`）：

```
改动前：CASTPREWARM beamDefinitions 659ms, define=239ms, shell=74ms, child=340ms
改动后：CASTPREWARM beamDefinitions 343ms, define=257ms, shell=79ms, child=0ms
```

| 指标 | 基线（3 轮） | 改动（3 轮） |
|---|---|---|
| 同步段 | 864／1078／659 → 均值 **867 ms** | 343／269／375 → 均值 **329 ms（−62%）** |
| 帧均值 | 4.524／3.998／3.904 → **4.142** | 4.252／3.408／3.643 → **3.768（−8.9%）** |
| 帧 p99 | **17.90** | **16.14（−9.8%）** |
| 最长帧 | **175.3** | **141.8（−19.1%）** |
| >50 ms 帧 | 0 | 0 |

判读：同步段 −62% 确定可复现；帧指标方向一致但**基线自身轮间方差大**（mean 4.52→3.90），不作强证据。

### 3.2 `prewarmDepth` 从 2 降到 1：阴性，默认保持 2

| 指标 | depth=2 | depth=1 |
|---|---|---|
| spawners | 15 | 9 |
| 帧均值 | 4.524 | **6.166（+36%）** |
| 帧 p99 | 20.494 | 24.426 |

深层子模板退回施放时按需构建，反而更慢。开关 `-Dlobecorp.photon.prewarmDepth` 保留，默认 2。

### 3.3 只同步解析第一个特效：阴性，已回退

4 个动画只映射到 2 个特效（LASER／LASER_PHASE_TWO → `laser_spell`；STAR_BEAM／STAR_BEAM_PHASE_TWO → `star_beam_spell`）。尝试只同步解析第一个：

| 指标 | 同步 2 个 | 同步 1 个 |
|---|---|---|
| 同步段 | **329 ms** | **340 ms** |
| prepared | 2 | 1 |

同步段没降，说明 `define` 主要来自**解析单个特效本身**，不是解析了几个；而 `prepared` 从 2 降到 1 增加了 star_beam 未准备的风险。已 `git checkout` 回退。

### 3.4 内嵌模型改外部 `.json`（不拆几何）：阴性，已回退

`laser.fx` 500 KB → 94 KB，但 `define` 为 264／290／262 ms，改动前 209／248／266 ms，**没有下降**。

原因：`PhotonModelOutput` 反序列化时**不调用 `prepare()`**，模型资产不在 `FXHelper.getFX()` 阶段解析。那 260 ms 花在 FXData 对象树、材质配置、时间线上，与内嵌模型体积无关。

### 3.5 模型资产外部化（OBJ ＋ NBT）：已实施，视觉通过

见 §5。

## 4. 已确认事实（本轮核对，非推测）

- `.fx` = **gzip 压缩的 NBT**。结构：`{ version, fxData: { fxObjects: [ { type, data: { name, transform, config, ... } } ] } }`——**`type` 在对象顶层**，不在 `data` 内。
- `laser.fx` 解压 500,119 B、13 个对象；84.4% 体积来自 4 个 `lobecorp_model_emitter` 的 `config.assetJson`。
- 4 个模型的 `assetJson` SHA256 **各不相同**，全部 `source=EMBEDDED`，**没有可去重的重复内容**——「共享同一份子特效定义」对激光无效。
- `PhotonModelOutputConfig.Source` 有 `EMBEDDED / RESOURCE / FILE`；**`RESOURCE` 加载分支早已实现**（`PhotonModelSourceUtil.load()` 走 `getResourceManager().getResource()`），此前判断「无实现」是搜索方式错误。
- `PhotonModelFileValidationUtil.emitterData()` 原只允许 `EMBEDDED`，由 `11c575f8`「恢复已验证的模型文件流程」引入，属 G1 来源闭合验收保护。**已获用户授权放开 `RESOURCE`**（`FILE` 仍拒绝）。
- Photon 自带 6 个 OBJ（`assets/photon/models/`），全是基础图元（quad 183 B、sphere 136 KB），不含骨骼动画。
- `PhotonModelObjUtil.read` 支持的指令仅 `v vt vn o g usemtl f s mtllib`，**遇到其它指令直接抛异常**；不支持负数索引。
- MC 26.1 NBT API：`CompoundTag.get(String)` 返回 `@Nullable Tag`；`getString(String)` 返回 `Optional<String>`；`Tag.asString()` 返回 **`Optional<String>`**。

## 5. 模型资产外部化（当前实现）

### 5.1 资源布局

```
assets/lobecorp/models/photon/the_queen_of_hatred/beam/
  laser_body.obj      17,447 B    ← 几何，外部工具可直接编辑
  laser_body.mfx.nbt   4,765 B    ← 骨骼/蒙皮/形变/动画/材质参数
  dust_column.obj     14,413 B  / dust_column.mfx.nbt   2,535 B
  reverse_muzzle.obj  14,061 B  / reverse_muzzle.mfx.nbt 1,527 B
  source_dust.obj      6,425 B  / source_dust.mfx.nbt   2,140 B
```

`laser.fx` 的 4 个 emitter：`source=RESOURCE`、`resource=lobecorp:models/photon/the_queen_of_hatred/beam/<name>.obj`、`assetJson` 清空、`snapshotSource` 置空。

### 5.2 职责划分

| 载体 | 承载 |
|---|---|
| OBJ | 顶点、面、面角 UV／法线、`g` 部件分组、`usemtl` 材质名 |
| `.mfx.nbt` | `referenceLength`／`referenceWidth`／`forwardAxis`／`parts`／`bones`／`weights`／`morphs`／`clips`／材质 `texture`／`materialData`／`tint`／`overlay` |

配对规则：`foo.obj` ↔ `foo.mfx.nbt`（`PhotonModelSplitUtil.dataPath()` 推导）。

### 5.3 OBJ 生成规范（生成器必须遵守）

1. **定点十进制**，不用科学计数法（外部工具兼容性）。
2. **UV／法线按取值去重**后复用索引；`laser_body` 从 656 份降到 188／24 份。
3. **只输出白名单指令**：`v vt vn o g usemtl f s mtllib`。
4. **精度无损**：用可精确回读的最短表示再展开，不做四舍五入（`0.005898047` 完整保留）。
5. 同一面要么全带 `vt`/`vn`、要么全不带——`PhotonModelObjUtil` 拒绝混用。

### 5.4 代码

- `util/photon/model/PhotonModelSplitUtil`（新增）：`merge(objText, data, flipV)` 合并、`dataPath()`、`companionData(Identifier)`、`readData(Tag)`（兼容顶层字段与 `model` 包裹）。
- `util/photon/model/PhotonModelFileValidationUtil#emitterData`（改）：`RESOURCE` 要求 `resource` 非空，几何与片段引用延后到首次使用时校验；`playback` 的 `definition` 参数可空。

### 5.5 验证

三轮基准（单施法场景、每轮重开客户端、plain、探针开、spark 关）：

| 轮次 | built | spawners | failed | 加载失败 | cast-tick | status |
|---|---|---|---|---|---|---|
| H1 | 50 | 15 | 0 | 0 | 76.3 ms | PASSED |
| H2 | 50 | 15 | 0 | 0 | 114.2 ms | PASSED |
| H3 | 50 | 15 | 0 | 0 | 72.4 ms | PASSED |

`built=50, spawners=15` 与改动前 `EMBEDDED` 版本完全一致。放开闸门前该场景 `Failed to load fx` 计数 1、`built` 掉到 38、`status=FAILED`。

**历史人工视觉确认：已由用户确认正常**（原交接指向 `agent-backups/photon-r3-20261027/compare/`）。2026-10-27 接续核对：该备份目录、比较图、工具脚本与旧 re-audit 目录在当前工作区未找到；保留此较晚确认记录，不将其当作本轮独立像素／生命周期验收。现存原始样本已归档至本工作区 `.agent/evidence/first-cast-20261027-existing/`，其源码 dirty／粒子 seed／Spark 条件不能满足本轮正式配对要求。

**无性能收益**：`define` 仍约 300 ms，与改动前持平。本轮目标是可维护性，不是性能。

## 6. 本轮新增：`.mfxlib` 特效管理工程

- `client/photon/model/project/PhotonEffectLibraryProject`：`.mfxlib` 工程类型，只登记条目引用与共享子特效 ID，自身不含对象树。
- `client/photon/model/project/PhotonEffectLibraryPanel`：停靠面板，扫描资源根列出对象数／发射器数／子发射器数／估算渲染类型／子特效链接／递归深度／解压体积，按开销降序。
- `util/photon/compat/PhotonEffectInventoryUtil`：只读 NBT 索引，不建运行态。
- `client/photon/editor/PhotonEditorTools#previewEffect`：选中才建预览，切换即释放，同一时刻只一个运行态。
- 注册：`mixin/photon/editor/client/FXEditorMixin`。
- 测试：`PhotonEffectInventoryUtilTest` 4/4。

**未做游戏内交互验证**（新建工程、扫描、选中预览、切换释放、关闭清理均未在客户端点过）。

## 7. 命令手册

### 7.1 单施法基准（每轮重开客户端）

```bash
JAVA_HOME=<jbr>
JAVA_TOOL_OPTIONS="-Dlobecorp.photon.packedSnapshot=false -Dlobecorp.photon.bench.shader=false \
  -Dlobecorp.photon.bench.probes=true -Dlobecorp.photon.diag.cast=true"
./gradlew.bat prepareBenchClientOptions -PphotonBench=true -PphotonShader=false -PphotonSpark=false --offline
./gradlew.bat runBenchClient -PphotonBench=true -PphotonShader=false -PphotonSpark=false \
  -PphotonRequireFocus=false -PphotonScenarios=lobecorp.photon-00-first-cast --offline --console=plain
```

**两个必须注意的坑**：

- 必须传 `-Dlobecorp.photon.bench.shader=false`，否则场景报 `实际光影状态与本轮预期不符：true` 直接 FAILED（该属性默认 `true`）。
- `runBenchClient` **不包含** `prepareBenchClientOptions`，Iris 配置不会刷新，需先单独跑该 task。

### 7.2 帧对比

```bash
python agent-backups/photon-r3-20261027/tools/frame_compare.py "A=path/a.json" "B=path/b.json"
```

### 7.3 模型拆分与重新链接

```bash
# 从 .fx 抽出内嵌模型 JSON
python agent-backups/photon-r3-20261027/tools/fx_externalize.py <in.fx> <out-dir> <prefix> [out.fx]
# 拆成 OBJ + .mfx.nbt
python agent-backups/photon-r3-20261027/tools/model_split.py <model.json> <out-dir> <stem>
# 把 .fx 的模型发射器指向外部 OBJ
python agent-backups/photon-r3-20261027/tools/fx_link_models.py <in.fx> <out.fx>
```

## 8. 恢复检查清单

1. `git -C <workspace> log --oneline -5` 确认 HEAD 至少含 `c30625fb`（模型外部化）与 `38cea898`（特效管理工程＋预热治理）。
2. `git status --short` 确认用户 WIP 四项仍在（`.codex/config.toml` M、`PhotonModelPoseSamplerTest.java` M、`PhotonModelStagedResourcesTest.java` A、`logs/` ??），**一律不提交、不覆盖**。
3. 读 §1 列出的文档。
4. 构建：`JAVA_HOME=<jbr> ./gradlew.bat benchClasses -PphotonBench=true --offline --console=plain` 应 BUILD SUCCESSFUL。
5. 跑一次单施法基线（§7.1），期望：
   - `CASTPREWARM beamDefinitions ≈300～400ms, prepared=2, queued=true, depth=2, define≈250～300ms, shell≈70～100ms, child=0ms`
   - `CASTPREWARM render done, built=50, spawners=15, failed=0`
   - `run_complete status=PASSED`
   - 日志中 `Failed to load fx` 计数为 **0**
6. **不要重做**已验证的阴性方向：§3.2 `prewarmDepth=1`、§3.3 只同步解析第一个、§3.4 内嵌模型改外部 JSON、以及旧文档的 R1 池化。

## 9. 下一步（按建议顺序）

### ① 把模型外部化推广到其余 3 个大特效

`queen_star_beam.fx`（1.83 MB）、`queen_arcana_beam.fx`（1.64 MB）、`light_ball.fx`（1.28 MB）。流程已跑通（§7.3 三个脚本），风险点是这些文件有更多模型发射器、可能有重复资产（需先跑 `fx_assets.py` 查 SHA256 是否重复，重复的可以共享同一份 OBJ）。**每推广一个都要视觉验收**。

### ② 继续 R2 残余长帧

交接文档 `20261026` §13.1 ① 的记录：施放后 +0.08～1.37 s 仍有 52～167 ms 长帧。需用 `FirstCastDeep.java` 对新 artifacts 做 self 归因，确认是未覆盖的实例化变体、首次模拟类加载，还是 tick 侧工作。

### ③ R3 子模板缓存

`prepareNext` 18～38 ms/次是后续每次施法的稳态大头。关键约束（本轮已核实）：`FXRuntime.initRuntime()` 会 `setScene(this)` 把对象实例绑到 runtime，`IFXObject.shallowCopy()` 默认等于 `deepCopy()`（Codec 往返），**库层没有廉价克隆路径**——要落地必须先解决「模板复用」与「实例独占对象树」的矛盾，否则会退化成 R1 池化（已阴性）。

### 不要做的事

- 在 R2 残余没定位前同时开 R3／R4。
- 用关闭阴影、降画质、删原路径来换数字。
- 把单轮最好值当稳态收益。
- 未做视觉验收就宣称模型外部化「已完成」。

## 10. 未决问题

- `docs/standards/agent-docs.md` 被项目规范引用，但**文件不存在**。
- `.mfxlib` 工程只做了编译与单测，**未在客户端验证交互**（新建、扫描、预览切换、关闭清理）。
- 模型外部化只覆盖 `laser.fx`；其余 3 个大特效未动。
- `PhotonModelObjUtil.read` 不支持负数索引与 `l`／`p`／`vp`，外部工具产出的 OBJ 可能被拒。
- 测试夹具把 4 个模型 JSON 全文放进 `src/test/resources`（约 25,000 行），后续可考虑压缩存放。
- `laser.fx` 从 33,331 → 7,544 字节（压缩后），但**磁盘总量基本不变**（模型移到独立文件）；收益在可维护性，不在体积。
