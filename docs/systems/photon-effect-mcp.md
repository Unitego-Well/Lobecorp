# Photon 特效 MCP

核对日期：2026-10-14。当前接口依赖 Photon 26.1.2.3、LDLib2 26.1.2.42 与本仓库 Minecraft MCP 子项目；版本与构建入口见 [Minecraft MCP](minecraft-mcp.md)。

## 2026-10-15 命令接口修复

本轮启动已最大化的 Client 并进入世界后，Minecraft MCP 的 `execute_command` 执行 Spark 客户端命令返回 `no command method found`，目标玩家类型为 `net.minecraft.client.player.LocalPlayer`。性能分析阶段暂通过 IDEA Debug 提交命令；随后按用户安排修复桥接源码，在原反射路径前调用 `Minecraft.getConnection().sendCommand()`。锁定 NeoForge 的原生连接先处理客户端命令，再按玩家权限、签名及连接协议发送服务端命令，不使用单机控制台替代。

IDEA 编译后，真实 Minecraft MCP 的 `/sparkc profiler info` 和 `time query gametime` 均返回 `sent=true, method=conn.sendCommand`；客户端日志分别出现 Spark 状态与“游戏时间为906685刻”，确认客户端／服务端分流实际成功。返回的 `sent` 表示已经提交，不代表任意命令都在服务端成功；多人、权限拒绝和签名聊天场景未验收。本轮没有修改 TypeScript bridge 或工具协议，无需重建 bridge；编译和 Data 的实际结果见 [修改记录](changes.md)。

## 使用顺序

AI 制作与参考视频复刻使用项目 [特效制作技能](../../.codex/skills/lobecorp-effect-authoring/SKILL.md)，按需读取其原生操作参考。技能复用本系统工具，固定操作在入口、出口及分支处确认，不增加独立制作服务器或固定效果代码配方。

1. 通过 IDEA 启动 Client Debug，让本项目 bridge 连接游戏；创建工程前进入世界。Photon 场景需要世界维度注册表，标题界面创建返回 WORLD_REQUIRED，并且不创建窗口／工程。写操作需要原有 MCP 控制模式，后端不会绕过激活条件。
2. 调用 `effect_capabilities` 读取当前发射器、参数、轨道模板、操作与允许文件目录；调用 `effect_state` 读取已打开工程。
3. `effect_create` 创建独立 `fx`／`model` 标签；已有未保存工程保留。工程 ID 是当前编辑器会话身份，关闭或替换后失效；对象使用原生 UUID。
4. 粒子 FX 先 `effect_preview pause`，再读取 `effect_state` 的 revision；以该版本调用 `effect_edit_batch`。原生 FX 主时间轴播放会更新持久变换，播放中版本可能改变。模型预览时钟与资产独立。
5. 编辑后预览、撤销／重做，确认结果再保存工程或导出定义／包。模型资产写入 FX 工程时需指定模型发射器 objectId；独立模型标签仍持有副本，保存不自动应用到来源。

## 公开工具

| 工具                    | 参数与结果                                                                                                                                                                                                   |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `effect_capabilities`   | 无参数；协议版本、8 类操作、注册发射器、默认参数、配置范围、轨道模板、目录与请求限制。                                                                                                                       |
| `effect_state`          | 可选 projectId；省略时列全部工程。返回 kind、revision、dirty、editorPath、playing、time、历史数量与 snapshotIncluded；不含模型时提供 snapshotSnbt。FX 模型对象单独返回 asset，模型工程提供 asset／playback。 |
| `effect_create`         | kind 为 fx／model，可选 name；新工程状态。                                                                                                                                                                   |
| `effect_edit_batch`     | projectId、expectedRevision、operations；可选 dryRun，默认 false。仅实际改变内容的正式批次新增一次历史；dryRun 返回 proposedSnapshotIncluded，仅为 true 时提供 proposedSnapshotSnbt。                        |
| `effect_preview`        | projectId、action 为 play／pause／stop／seek；seek 需 time（tick）。模型保留部分刻，原生 FX 模拟定位使用整数 tick。                                                                                          |
| `effect_save`           | projectId、path；overwrite 默认 false。原生工程保存、读回比较，成功后更新编辑器文件与最近工程。                                                                                                              |
| `effect_export`         | projectId、path、kind 为 definition／pack；overwrite 默认 false。返回路径及原生打包警告。                                                                                                                    |
| `effect_history`        | projectId、action 为 undo／redo；操作原历史并返回当前状态。                                                                                                                                                  |
| `effect_open`           | path；仅当前 `.fxproj`／`.mfxproj`，读取、校验后打开新标签，不替换已有工程。                                                                                                                                 |
| `effect_resources`      | 可选 projectId／category／directory；返回原生类别、Provider 路径、本地资源与 PNG，以及扫描／体积限制和截断标记。                                                                                             |
| `effect_resource_read`  | category、path，可选 projectId；读取原生资源引用或允许目录文件，返回 SNBT、JSON 与 NBT 类型。                                                                                                                |
| `effect_resource_write` | category、path、source 或 snbt 二选一，可选 projectId／overwrite；原生编解码校验后发布资源文件。                                                                                                             |
| `effect_texture_import` | path、base64，可选 overwrite；PNG 写入 `assets/<namespace>/textures/...png` 并注册纹理，返回 textureId。                                                                                                     |
| `effect_view_state`     | projectId；只读相机、显示选项、active／embedded、GUI 缩放及可截图区域，不切换标签。                                                                                                                          |
| `effect_view`           | projectId 与可选 center／zoom／yaw／pitch／orthographic／orthoRange／bloomEnabled／effectsEnabled／shapeVisible／cullBoxVisible。旋转单位为度。                                                              |

所有特效工具返回相同的 MCP text JSON 与 structuredContent：`{ok,data,error}`；成功 error=null，失败 data=null、error 含 code／message，并设置 isError。能力、工程状态、资源浏览／读取和视角状态为只读；写工具在 HTTP 入口、客户端执行前和正式提交前检查控制模式。

## Photon MCP 部分能力接入

本次按确认范围只补工程打开、资源操作、预览视角和现有截图入口；现有创建、批量编辑、时间轴、历史、预览及保存／导出继续复用。来源与改写文件见 [Photon MCP 第三方声明](../third-party/photon2mcp.md)。没有导入独立服务器、Groovy 执行器、反射工具或重复编辑入口，也没有改变模型文件格式。

`effect_open` 先按原生 ProjectType 读取并校验内容，正式提交才创建相应 FX／模型标签；世界缺失、路径越界、格式无效均返回明确错误。打开失败只清理本次新标签，已有工程与未保存内容保留。

资源操作由 [PhotonEffectResourceUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectResourceUtil.java) 组合原生 Resource／ResourceInstance 与既有文件事务。无 projectId 时使用 FX 原生资源集合，不创建编辑器。读取支持完整原生 Tag，写入要求原生编解码往返一致，不把非 Compound 资源错误改为 Compound。资源／纹理文件写入不属于 FX 撤销历史；正式提交后更新原生缓存与资源面板，不执行全局资源重载。默认拒绝覆盖，沿用允许根目录与符号链接限制。GPU／缓存刷新属于提交后的外部操作，不能把文件发布与 FX 撤销视为同一事务。

浏览最多返回 1000 个原生条目及 1000 个文件；每类别 Provider 扫描和目录扫描均有 10000 项预算，目录最多 16 层，达到限制回传 truncated 标记。资源编码／响应上限 2 MiB；PNG 原始编码最多 1 MiB，边长最多 4096 像素。实际限制同时由 `effect_resources` 返回。

视角入口为 [PhotonEffectViewUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/automation/PhotonEffectViewUtil.java)，复用 FX／模型共用的原生 Scene；先校验全部参数再提交，不创建 FX 历史。视角变化不修改工程资产或内容版本。隐藏、非当前标签或独立 OS 宿主不提供局部截图区域。

原有 `screenshot`／`screenshot_to_file` 增加 region（game／editor／scene）、projectId 与 maxSize；局部截图需要当前可见、嵌入游戏的指定工程。默认保留完整游戏画面，maxSize 仅等比缩小。MCP 客户端唯一 RenderFrameEvent.Post 入口等待两次完整帧，再通过当前 Minecraft 的异步 Screenshot 回调取得真实 GPU 像素，裁剪／缩放后返回原生 sourceWidth／sourceHeight 和实际 capture 区域；渲染线程不等待未来回调。局部抓取前再次核对工程与布局，GUI 取整的右／下边缘按真实帧缓冲截断，真正越界拒绝。该接入不新增第二个 Photon 截图工具。历史 854×480 与实际 GPU 尺寸不一致的记录仍是旧实现的现象，本轮源码修正不能代替真实帧验收。

当前源码提供 15 项特效工具，连同 46 项原生工具共 61 项；本轮编译、Data、bridge 发现与诊断结果统一见 [修改记录](changes.md)。下文的 54 项工具与游戏案例属于此前检查证据，不代表本轮新增能力已通过游戏验收。

编辑请求示例（ID 与版本从当前状态取得）：

```json
{
  "projectId": "<当前工程 ID>",
  "expectedRevision": "<当前 revision>",
  "operations": [
    {"op": "set_model_playback", "playback": {"playbackSpeed": 0.75}}
  ],
  "dryRun": true
}
```

## 批量操作与参数

- `add_emitter`：FX 工程，type 从 emitterTypes 选择；可选 name／parameters，parameters 对原生 data 的已有顶层字段做局部替换。
- `remove_emitter`：objectId。仍有子节点或被轨道／绑定引用时拒绝；删除叶节点同时维护父节点子列表。
- `set_parameter`：objectId、path、value；path 是相对 parameters 的 JSON Pointer，只修改已有字段并保留 NBT 类型。transform 也走原生参数路径。
- `set_model_asset`：完整 asset JSON；FX 工程需模型发射器 objectId。
- `patch_model_asset`：path／value，FX 可指定 objectId；相对资产 JSON，null 删除字段／数组项，`/-` 追加数组项。材质和着色器参数仍用资产原有格式。
- `set_model_playback`：局部 playback；enabled、duration、startDelay、looping、paused、clip、playbackSpeed、animationOffset，时间为 tick。
- `set_timeline`：FX 原生 timeline JSON，按当前状态或轨道模板构造，要求完整 JSON 往返。
- `set_project_data`：参数 `snbt` 传入状态中的完整 snapshotSnbt，支持命名动画等高级原生字段，要求精确 NBT 往返；同样执行候选工程校验。

parameterSchema 描述原生 NBT 结构；configurationMetadata 基于当前持久化键／展开规则、ConfigNumber、ConfigSelector、原生枚举名称、NumberFunction 限制及模型真实配置器提供范围。每项标明 path、javaType、nativeType、editable、value、min、max、unit；无法确定的范围／单位是 null，不能当作没有业务限制。能力中的 valueRole=default，当前值以 effect_state 为准。函数候选同时提供 Java 类型与真实注册名，wrapper 的 type 枚举使用注册名。

revision 为实际工程内容的 SHA-256，人工编辑同样使旧请求失效。批量操作先修改独立副本，统一检查已知参数范围、对象身份、父链与轨道／绑定引用，通过后才提交；失败恢复原快照，dryRun 不产生历史。合成运行时 ROOT 可被引用，实际对象不能占用其 UUID。

### 模型状态去重与视角保持

模型资产的 JSON 在原生 NBT 中保存为字节数组。此前状态同时返回完整 SNBT、parameters、逐项 schema 和解析后的模型，四个模型的实测响应达到 103,512,451 字节，超过通道 67,108,864 字节上限。现在含模型的工程返回 `snapshotIncluded=false`，不重复展开完整快照；FX 模型对象从临时 parameters／parameterSchema 中省略 `config.assetJson`，完整几何、UV、材质和动画改由该对象的 `asset` 返回。原生文件、内容版本、完整候选校验及撤销历史保持原有格式。

调用方必须先读 snapshotIncluded／proposedSnapshotIncluded，不能假定每个工程都有 SNBT。模型修改优先使用 set_model_asset／patch_model_asset，其他已有字段使用 set_parameter；set_project_data 仍要求调用方持有完整原生 SNBT，不接受删去模型字节的部分快照。普通参考星轨与原生 Beam 配方不含模型，既有 SNBT 路径继续可用。

批量编辑重建 FX 场景和 effect_preview seek 先保存当前视角，再恢复 center、zoom、yaw、pitch、投影和四个显示开关；暂停状态也在编辑后保留。棋盘场景内容及浮动面板不属于这些字段，重建场景可能恢复它们。用户要求的透视预览与游戏内宿主继续适用。

## 生命周期、文件与依赖

业务入口为 [PhotonEffectAutomation](../../src/main/java/org/unitego/lobecorp/client/photon/editor/automation/PhotonEffectAutomation.java)，客户端注册为 [LcPhotonMcpCommands](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonMcpCommands.java)，工具分派为 [effectHandlers.ts](../../modules/minecraft-mcp/packages/minecraft-mod-mcp/src/mcp/effectHandlers.ts)。MCP common 的通用 McpExtensionRegistry 保留 JSON 层次，不依赖 Photon；Lobecorp 只在 mcpmod 存在时通过唯一客户端初始化入口注册。

请求在客户端线程经历 QUEUED／RUNNING／COMMITTING／DONE。排队和副本准备超过 8 秒可取消，取消后晚到任务不能提交；进入 COMMITTING 后等待真实结果，不假报取消。请求最多 2 MiB、256 个操作，单次 seek 最多 1200 tick。保存／导出编码在 RUNNING 只写独占临时文件，正式提交才发布目标；失败或取消清理临时文件，原生资源编码仍可能耗时。

允许根目录由当前 LDLib2 assets 与 Photon FXPacks API 返回；相对路径基于 assets，绝对路径也必须在允许根内，拒绝越界和符号链接。默认拒绝覆盖与被锁定包。模型后缀为 .mfxproj／.mfx／.mfxpack；粒子 FX 保持原生 .fxproj／.fx／.fxpack。文件格式与共享着色器依赖规则见 [模型特效](photon-model-effects.md)，没有新增旧模型格式兼容。

## V3 持续发射与参考首爆修订（2026-10-15）

当前可编辑工程仍为 `run/client/ldlib2/assets/lobecorp/tutorial_shockwave/v3/tutorial_shockwave_v3.fxproj`，总长 200 tick：0～20 tick 蓄力，20 tick 首爆，26 tick 起持续输出，180～200 tick 终结。当前修订为 28 个对象、28 条轨道；空命名动画控制器保留，本轮不增加激光或战斗接入。

持续发射间歇已确认由每粒子延迟与数量上限共同造成：旧配置每刻 8、startDelay=26、maxParticles=160，现场队列 150 个中 82 个仍在延迟。当前版本的 TileParticle.setup 将 startDelay 赋给每颗新粒子，ParticleEmitter 在计数未达上限时才发射；这不是整个发射器只等待一次。对照原生默认值：Exacting、每刻 0.5、distanceRate=0、无 bursts、startDelay=0、maxParticles=2000。

V3 用原生 ControlTrack 安排出生起点，所有粒子 startDelay=0；片段覆盖粒子尾部寿命，发射持续时间单独控制停止新增。普通火花每刻 1，星粒每 4 刻 1，均为 Exacting、无 burst；持续火花上限 128，后向局部 -Z 圆锥半角 60°，遵守用户确认的不超过 90°。40 刻按粒子身份差集统计的新生数全部为 1，延迟数为 0，末次存活 11；该次方向样本最大半角约 58.214°。前轮“每刻均有年轻粒子”只能说明存在年轻粒子，不能证明每刻稳定出生。

星粒采用私有 warm_star 图，主体偏黄并提高不透明度，不覆盖 refined 的共享 spark_star。紫色汇聚已按用户修订从 Billboard 换成 99 顶点／128 三角面的弯曲模型，复用发射器发射器每 2 tick 生成一个独立实例；Morph 移动各段中心线向内汇聚并保持带宽，独立片段旋入、显现和退场，20 tick 后退出。10 tick 现场累计生成 5 个模型实例，私有 purple_model 图有效。

球周围三组粉环保留隐藏模型模板与独立发射源，横向带宽仍为此前的 2.5 倍，本轮整体尺寸缩为上一版的 60%，保留原参考尺寸换算。三个源从 6／9／12 tick 开始，间隔 3 tick，发射时长为 9／6／3 tick，实例寿命 12 tick，控制片段在 26 tick 结束；持续阶段不再出现粉环。各实例放大、淡入再消失，保持出生朝向，不绕球旋转。出生朝向在制作时按可稳定读回的离散三轴组合随机采样并保存，重播复用该序列。

私有 electric 图由平滑条纹改为 10 组独立生灭的电弧，每条含 6 段锯齿及短分叉，增加宽度与黄色亮度；私有 waves 图改为频率 22／17 的双向粉色流动和生灭遮罩。两图继续用球面三维方向采样，避免重新引入 UV 接缝。波壳和电弧的模型局部半径收至 0.37／0.39，蓄力与终结保留原对象；持续阶段新增两个隐藏模型模板及两个发射源，分别每 5／4 tick 生成一个、寿命 12／10 tick，实例半径逐渐扩大到 0.6 并淡出。这里的半径是资产局部数值，仍经过原参考尺寸换算，不等同于世界格数。出生朝向固定，持续段各实例独立交替生灭，不用单个模型重复开关。

保留蓄力的大幅脉动；核心持续段加入约 ±4% 的小幅尺寸波动，辉光波动更小。径向暗能量层的起始尺寸从 1.8 收至 0.9，使外围扇叶更贴近光球。80 tick 现场确认粉环实例全为 0，粉色流动／电弧分别存活 2／3 个，两个着色图均有效。

首爆椭球使用真实立体载体与逆转置法线，当前片段以 20 tick 为起点压缩为原来的一半，26 tick 时不可见。十字使用私有 burst_cross 图和正常 Alpha 混合，寿命从 16 缩为 10 tick，按最新要求先变大、变黑时同步缩小、再变亮变大、逐渐变细消失；实际查看 22 tick 大亮星、23 tick 小黑星、24／25 tick 复亮变大、27 tick 细长星、29 tick 不可见。尺寸曲线已按颜色插值对齐，不把未插值的生命周期进度直接当作 GPU 当前状态。

原“粒子分阶段出生”控制轨道拆为各目标的独立控制轨道；模板显隐和出生朝向各有独立轨道，保留已有片段的起点、种子及持续火花参数。保存曾因出生朝向四元数归一化产生极小浮点差异而被精确读回拒绝，已改为经当前原生旋转／读回保持稳定的朝向关键帧；没有修改通用比较或放宽校验。最终原生保存通过、dirty=false；.fx／.fxpack 重新导出，包 13 文件、warnings=[]。此前一次包调用未得到有效 JSON 回执，重试后取得成功回执。

本轮查看 16／26／84／180／200 tick 阶段，200 tick 无可见残留；随后收近暗能量、增加实例半径增长，并查看最新 80／86 tick 画面。最新截图为 `build/mcp/dense-arcs-growing-80.png` 与 `dense-arcs-growing-86.png`，电弧近景检查为 `dense-arcs-close-centered-80.png`；近景及阶段截图早于最后两项数值调整。旧截图保留为历史。最新工程保存后 dirty=false，定义与包重新导出，包 13 文件、warnings=[]；运行定义与包仍为 `v3/charge/tutorial_charge_v3.fx／.fxpack`。与原教程的贴图、角色比例、环境照明及完整激光仍有差异，未做全视角、世界附着或性能验收；没有修改 Java，也未重新编译或运行 Data。

## V3 性能优化（2026-10-15）

持续流动与电弧的四个球壳资产从每个 960 个三角面减到 224 面；核心与首爆椭球保留 528 面。参考尺寸、局部半径、发射间隔、实例寿命、颜色及时长保持，实例仍从 0.37／0.39 扩大到 0.6 并淡出。私有 electric 图保持 10 组、六段锯齿和两处分叉，主弧改为按球面角度直接求所在段，减少逐像素线段遍历；waves 噪声从三层降为两层并补偿幅度，双向纹路频率不变。electric／waves／impact_ellipsoid 在全透明时跳过重计算，三图实际编译有效。该着色计算是近似优化，不宣称逐像素相同。

以同一工程、种子及 110～150 tick 连续 40 次 `tickInternal` 的调试求值采样比较：旧代码／旧几何平均 31.274 ms，旧代码／精简几何 8.399 ms，新代码／旧几何 11.648 ms，新代码／精简几何 3.998 ms。四次每刻粒子数序列完全一致，范围 41～47，没有靠减少发射量取得该结果。旧版与最终版的排序第 39／40 个样本分别为 110.845／12.143 ms；这是少量调试采样的高位值，不作为严格 P95 或长期帧率承诺。跨客户端重启与预热差异保留，未做独立受控微基准。

锁定 Photon 的“CPU 时间”统计粒子 tick，场景“帧时间”只覆盖提取／提交，不包含后续 `afterRender` 中的 bake／drain，更不是 GPU 时间。Minecraft 的 GPU 利用读数现场为 0，未取得有效独立 GPU 计时，不把它解释为 GPU 无负载，也不把模拟耗时降幅当成 CPU／GPU 占用率降幅。系统缓存和失效规则见 [模型运行缓存](photon-model-effects.md#运行缓存与采样开销2026-10-15)。

原工程与三张着色图已备份在 `v3/performance_backup/`；恢复旧外观时需同时恢复对应着色图，备份工程仍引用原 charge 路径。热替换退出后使用备份重新打开工程、重新应用几何优化，恢复透视视角并按真实 GLFW 主窗口确认 maximized=1。最终工程保存成功、dirty=false，定义与包重新导出，包 13 文件、warnings=[]；最终截图为 `build/mcp/v3-performance-final-80.png`。用户资源和共享 refined 着色器保留。

### 持续生成模型的进一步精简

后续按用户要求只处理六个重复生成的模型模板：

| 模板 | 顶点数 | 三角面 |
|---|---:|---:|
| 03 紫色汇聚 | 99→34 | 128→32 |
| 02、13、14 粉色宽环，每个 | 287→63 | 160→80 |
| 23 粉色流动、24 黄色电弧，每个 | 153→62 | 224→120 |
| 合计 | 1,266→347 | 1,056→512 |

宽环移除未引用顶点并保留曲线关键截面；紫色条带去掉线性冗余的横向中列，Morph 下标随顶点重排。球壳降为 12×6 分段并焊接重合位置，球面接缝 UV 仍按面角保留。六份材质、动画片段、参考尺寸和发射源配置均核对不变，其余模型资产与时间轴不变。

相同透视视角下已查看 10／73 tick 前后截图，主要轮廓保持，球壳略有低分段棱角；截图位于 `build/mcp/v3-low-poly-before-10.png`、`v3-low-poly-before-73.png` 及对应 after 文件。此项是几何复杂度降低，不是新的利用率／帧率结论，没有重做 Spark 或 GPU 采样。

修改前的当前工程通过原生保存备份到 `v3/performance_backup/model_complexity_before_20261015_620ece2e.fxproj`。当前原工程已保存，定义／包重新导出，包 13 文件、warnings=[]；未改 Java、着色器或用户其他资产，未编译或运行 Data。

## 参考特效与批处理入口

本轮制作脚本为 [create-reference-effects.mjs](../../modules/minecraft-mcp/packages/minecraft-mod-mcp/scripts/create-reference-effects.mjs)
，配方分别位于 [star-trail.mjs](../../modules/minecraft-mcp/packages/minecraft-mod-mcp/scripts/effects/star-trail.mjs)
和 [variable-laser.mjs](../../modules/minecraft-mcp/packages/minecraft-mod-mcp/scripts/effects/variable-laser.mjs)。IDEA
的 `Minecraft MCP Reference Inspect／Create／Save` 顺序创建临时工程、应用完整配方、保存工程与导出定义／包。当前会话仍通过已构建的新
bridge 调用，不能据此宣称旧会话的工具注册表已刷新。生成诊断位于 `build/mcp`，不手工修改其内容。

星星拖尾由一条紫粉色 Trail 与粉白、黄色、蓝色三个世界空间粒子发射器组成，复用已有七帧星点纹理。基础工程不保存固定飞行轨迹；运行时移动
FX 根节点即可形成拖尾，演示轨迹使用运行时深拷贝，不能写回生产工程。

激光由白色核心、粉色主体、紫色外层与命名动画控制器组成，动画名为 `laser`。四个实例参数为：

| 参数        | 默认值 | 范围与含义                                         |
|-------------|--------|----------------------------------------------------|
| `length`    | 12     | 0.01～64 格，沿局部负 Z 轴                         |
| `width`     | 0.8    | 0.01～8 格，主体完整直径；原生 Beam 半宽由绑定换算 |
| `intensity` | 1      | 0～1，控制可见 Alpha                               |
| `enabled`   | true   | 布尔值，控制可见开关                               |

通过 `PhotonAnimationUtil.of(runtime).play("laser", parameters)` 获取播放句柄，使用 `setParameter／setParameters`
更新当前实例。参数值沿既有 API 使用 `float[]`，布尔值为 1／0。`enabled=false` 隐藏激光但继续实例时钟，结束生命周期仍使用已有停止／销毁入口。
`intensity` 不等同于独立 HDR 或 Bloom 能量。宽度与长度分别绑定 `config.width` 和 `transform.scale`，三层 Alpha 使用一条
enabled×intensity 绑定，避免同属性重复绑定。

模块开关只允许当前 `/_enable` 对应模块内部由原生补齐或省略默认字段，开关值、模块外数据、UUID 与类型仍严格往返。实际运行已确认原生
UV／生命周期颜色模块可以启用。另已定位 Constant 的默认整数 NBT 会使合法小数在 JSON 模板转换中被拒绝；参考脚本使用新建工程的等值原生浮点模板准备，保留完整
SNBT 往返校验，没有放宽全局整数、UUID 或协议类型规则。

本轮两套成品已各自保存 `.fxproj`、导出 `.fx` 与 `.fxpack`，共 6 份文件，目录为 `run/client/ldlib2/assets/reference-effects/`；文件名分别是 `reference_star_trail` 和 `reference_variable_laser`，定义／包警告均为空。原生 RenderTarget 截图位于 `build/mcp/reference-laser-native.png` 与 `build/mcp/reference-star-arc.png`。现有 MCP 截图读到的 854×480 与本轮真实 Window／framebuffer／RenderTarget 的 2560×1494 不一致；改用原生 `Screenshot.takeScreenshot` 回调保存完整帧，没有据此修改 MCP 旧截图实现。

## 粉色 3D 模型激光

2026-10-14 按用户要求删除本任务的模型激光试稿、源码着色器、集中配方与专用入口，并移除 `model:laser`、`createModelLaser` 和对应 IDEA 运行配置的 Gradle 声明。旧交付清单、失败诊断、效果截图和构建资源副本一起清理，不再作为当前可执行流程。通用特效 MCP、模型编辑能力及星轨／原生参考效果入口保留；后续模型与着色器制作使用游戏内资源工作流。本轮仅清理，未创建新效果或启动游戏，旧运行检查仍只代表当时版本。

## 教程冲击波工程（2026-10-14）

此前按用户要求仅清空 V3 工程内部内容：`run/client/ldlib2/assets/lobecorp/tutorial_shockwave/v3/tutorial_shockwave_v3.fxproj` 通过游戏内 MCP 移除全部 17 个对象和 3 条原生时间轴轨道并保存，当时返回对象 0、轨道 0、dirty=false。工程文件、着色器、此前导出的 `.fx`／`.fxpack` 及旧版本均保留；旧导出仍是清空前内容。后续仅重做蓄力，见下节；原 V3 的整束导出不作为蓄力版入口。

完整复核已按同一视频的 27 个分解步骤建立 [对应表](../../.codex/skills/lobecorp-effect-authoring/references/tutorial-shockwave-breakdown.md)。续作工程为 `run/client/ldlib2/assets/lobecorp/tutorial_shockwave/refined/tutorial_shockwave_refined.fxproj`，已保存同名 `.fx`／`.fxpack`。当前包含 6 模型、9 粒子、1 控制器、22 份图、44 条绑定和 3 条原生场景后期轨道；原工程、原九份图及 `before-refinement-20261014.fxproj` 均保留。双蓄力辉光、环带、宽拖尾、拉伸球能量体、暗波／暗能量、六层束体、沿束火花和地面层已进入组合预览；真实点光与局部雾／体积散射尚未接入，可复用入口见下节，不能记为完美复刻。

当前变量仍为 length=12、width=2.4、intensity=1、enabled=true，时长 140 tick。width 同时带动源头、火花范围与速度；length 控制主束及沿束火花的纵向范围／中心。intensity 乘算模型与粒子 Alpha，不控制独立 HDR 或场景后期。实测运行槽 6／1.2／0.5、沿束范围 [0.5,0.5,6] 与中心 [0,0,3]、关闭及重触发通过，problems=[]。22 图编译有效；保存 dirty=false，两类导出 warnings=[]，包 23 文件，fxId=lobecorp:tutorial_shockwave_refined。细节和检查边界见对应表。

### V3 双层模型激光（2026-10-15，当前）

十字星与冲击提速后续调整：16 的消失段整体提前 2 tick，生命周期 Alpha 阈值由 0.65／0.82 改为 0.4502／0.6202（10 tick 寿命、Alpha 0.001→1），使用新私有 `burst_cross_early_end.shader_graph.nbt`，前段形态和出生时刻保留。27／28／29 使用五份 `*_rush` 私有图，材质时钟乘以 2；释放到位的 22 tick 内／外柱横向峰值为 1.3／1.2，24 tick 回到稳定比例，源头尘雾提前展开并短促回弹。仅调整现有材质与曲线，没有增加对象或发射数量；原工程及两类导出已保存，warnings=[]。本次未新增运行截图或性能证据，原图及撤销记录保留。

15 首爆椭圆冲击波后续修正：基础网格的 X／Y 直径原为 1.24／2.5，因此仅同步动画缩放系数仍会压扁 X。已将 X 按 1.25／0.62 放大，使横截面基础直径均为 2.5，并按逆转置比例修正面角法线；Z 范围仍为 [0,0.8]，部件、UV 和全部动画未变。检查比例时须同时检查网格尺寸与动画倍率，不能仅比较 scale.x／scale.y。工程和两类导出已更新，warnings=[]；本次没有新增截图或运行测试，原生撤销与既有备份保留。

当前原工程仍为 `v3/tutorial_shockwave_v3.fxproj`，共 31 对象。在已精简的蓄力工程中新增 27 内层模型束、28 外层粉紫尘雾光柱、29 源头前开口半椭圆尘雾；没有恢复被放弃的旧模型工程，也未修改用户资源或共享图。三者合计 441 顶点／760 三角面，均为单实例，内层包含粉色主体、金橙等离子和细亮核三个材质组；外柱使用流动噪声、可见法线扰动与破碎遮罩。源头半椭圆没有前端封盖，开口朝局部 +Z，轴向延长至 1.4。此处尘雾是模型材质表现，未实现真实体积散射。

总时长仍为 200 tick，20 tick 首爆。内外柱由根端沿 +Z 在 20～22 tick 推进到完整长度；持续火花、金星、粉色流动与电弧的控制片段均从 26 提前到 20 tick，相应发射持续时间增加 6 tick，保留原发射终点。暗能量起始尺寸从 0.9 改为 2.1。光柱参考总宽 0.952，约为稳定亮球直径 0.56 的 1.7 倍；源头尘雾罩独立使用 1.2 宽的展开口，不计入束体宽度。

椭圆冲击波网格前移，使后端顶点位于 Z=0；所有动画平移均为零，固定靠球的一端向前拉长。X／Y 始终采用相同缩放系数，拉长时同步收细。按最后指令，将此前 5 tick 拉长／2 tick 收缩的方案更新为 20～22 tick 高亮、22 后立即开始噪声蚀除、24 tick 结束；蚀除阶段继续原展开姿态，不额外收缩，25 tick 之后隐藏网格。Alpha 在该图中表示阶段进度，不能当作普通淡出值。十字星起始尺寸 2.7→3.8，形状顺序为中细→小粗（保留黑色阶段）→中细→大细→消失，初始与末段臂宽进一步收细。

命名动画为 `tutorial_laser_v3`，默认不循环，38 条轨道／11 条绑定，运行定义默认启动此动画；普通编辑场景仍使用原生场景轨道。形参 `length=12`（0.05～64）同时控制两根光柱；`width=0.952`（0.05～4）按比例控制两柱与源头尘雾罩；`intensity=1`（0～1）乘算三者模型 opacity，不是独立 HDR；`enabled=true` 只控制这三个新模型，原蓄力、冲击波及粒子不随此开关关闭。动画测试或运行时通过 `PhotonAnimationUtil.of(runtime).play("tutorial_laser_v3", inputs)` 播放，并使用返回句柄更新实参。

维护要点：绑定必须使用 `config.length／width／opacity／enabled` 等完整属性路径，并在同一命名动画建立对应原生 AnimationTrack 属性；仅向 bindings 添加记录不会被采样。本轮首次运行值仍为默认值时据此补齐轨道，再实际推进实例验证。新增发射器保持原生 UUID，先读取返回 ID，再设资产及参数；不得在 add_emitter 中替换原生身份。图资源保留真实节点和端口，写入新私有路径，未新增固定特效 Java 配方。

实测 length=6／width=0.476／intensity=0.5 时，内柱运行值为 6／0.48／0.5，外柱为 6／0.476／0.5，尘雾罩为 1.4／0.6／0.5；三者关闭均成功，再触发推进到 23 tick，problems=[]。六份模型材质实际编译错误均为空。冲击波 20.4／22／23 tick 顶点采样 Z 范围分别为 [0,0.8]／[0,2.4]／[0,3.36]，根端始终固定。已查看 `build/mcp/v3-laser-phase-20.5.png`、21.5／22.5／23.5／24／70／200 同前缀透视截图；200 tick 无可见残留，暂停帧仍受原生前后刻插值影响，不等同于全部对象销毁证明。

原工程已覆盖保存，dirty=false；`v3/beam/tutorial_laser_v3.fx` 与 `.fxpack` 导出 warnings=[]，fxId=`lobecorp:tutorial_laser_v3`，包 18 文件。修改前备份为 `v3/beam/before-beam-20261015.fxproj`；此次原 charge 图未覆盖，备份仍可引用原资源。新私有图位于 `v3/beam/`，此前蓄力版导出保留为历史内容。本轮未修改 Java、执行编译／Data 或独立 CPU／GPU 性能采样；镜头、参考原始纹理、局部彩光及真实雾仍未完成逐项一致性验收。

### V3 一秒汇聚、持续输出与终结爆炸（2026-10-14，历史）

当前工程共 18 对象：8 模型、8 粒子、1 施放锚点及保留的空动画控制器。总时长 200 tick（10 秒），0～20 tick 汇聚蓄力，20 tick 小爆发，26～180 tick 持续向外输出，180～200 tick 终结爆炸。尚未制作激光或接入战斗、伤害、角色动画、真实局部点光与雾。

汇聚使用原生球面发射与负径向速度，粒子实际向中心运动；当前寿命 8 tick、radial=-30、orbital Z=±4。用户确认向后为局部 -Z，持续输出改为原生 Cone、35° 半角、底半径 0.25，Shape 的 X 旋转 -90° 将原生 +Y 轴转向 -Z；起始速度 4～6、寿命 10～16 tick、每刻发射 8、上限 160，缩短拉伸长度减弱离散段感。小爆发速度 5～7，终结星火 6～8。以上数值保存在原生发射器配置，单位遵循当前 Photon 实现。

已确认环带和紫带怪异的原因包括根及子部件动画的整体缩放、压扁与持续尺寸呼吸。两者固定 scale=[1,1,1]，材质时钟为 1.8 倍；粉环以 opacity 驱动沿 UV 显现，紫带以 opacity 驱动移动的 UV 窗口从外端旋入内端，在 20 tick 后窗口越过 UV 尾端而不可见，不再参加持续或终结阶段。紫带不是整体缩放或仅淡出，opacity 在该图中表示路径进度。粉环旋转为 -0.10 rad/tick，紫带蓄力旋转为 0.15 rad/tick；短间隔设键避免四元数长间隔绕回。终结粉环通过 radial_release Morph 将各宽度列沿相同径向位移，保持带宽。

按用户“新增两条”保留原粉环，再增加不同旋转平面的粉带 A／B，复用 ring 图、固定宽度及独立片段。首次小爆发增加 20 tick 发射的 Billboard 十字星，复用 spark_star；椭圆冲击波为闭合双面模型，使用 expand Morph 扩张、21～34 tick 显现并消散，新增私有 impact_ring 图去除开口端遮罩。没有新增固定特效 Java 配方或手写仓库 GLSL。

球面硬扇形边缘已确认与极点／U 接缝处非连续二维噪声有关：core、waves、electric 改为由 UV 重建球面方向的连续三维噪声，极点三角 UV 的 U 取同面邻点均值。实际查看无 Bloom 的侧面截图，旧硬楔形边缘未再出现；不扩大为所有角度或性能验收。

最新实际查看 10／18／24／80／200 tick 透视截图，路径为 build/mcp/charge-cone-<tick>.png；交付画面为 charge-cone-delivery.png。6 个模型图运行时编译错误为空，80 tick 所检查的 157 个队列粒子速度均指向 -Z，最大半角 34.8657°；连续推进四刻均存在年轻粒子。该计数不等同于每刻出生数或性能证明。200 tick 画面无可见残留，不将不可见等同于全部对象已销毁。游戏主窗 GLFW 最大化=1、编辑器 embedded=true；临时断点已删除并恢复运行，用户三断点保留。此前 fixed-width 截图和计数只属于前一版证据。

原 V3 .fxproj 已覆盖保存，dirty=false；v3/charge/tutorial_charge_v3.fx 与 .fxpack 已重新导出，warnings=[]，包 11 文件，fxId=lobecorp:tutorial_charge_v3，最终停在 24 tick、控制模式退出。特效修改只涉及自建工程、私有图和导出，未删除用户资源或旧版本，未创建额外备份；原生事务保留撤销历史。Cone 新字段缺少既有 NBT Float 模板时，按已确认流程在 Debug 工程副本构造原生 Cone，再以 set_project_data 的 prepare/commit 事务写入，未绕过校验。同期 MCP 鼠标和通知代码修复、编译与 Data 结果见对应系统文档；特效本身未新增 Java。下节 60 tick 记录仅是此前版本证据。

### V3 蓄力重做与分层时序（2026-10-14，历史）

依据用户提供的《复现分析报告》及 F44／F60／F69 原尺寸帧，仅在上述 V3 工程制作蓄力。当前包含 5 个模型、4 个粒子、1 个施放锚点，另保留期间出现的既有空命名动画控制器，共 11 对象。金核、粉色流动波壳、细电弧已拆为独立模型，开口粉环与三条宽紫曲面拖带分别使用自己的片段；没有喷发、主束、角色动画、真实 RGB 点光或雾。

模型片段均为 `charge`、60 tick、不循环。火花从 22 tick 开始；亮核约 25 tick 建立，双柔光从 29／30 tick 开始，波壳从 30 tick、环带与电弧从 33 tick、拖带从 34 tick 逐渐加入。火花先停止发射并自然耗尽；亮核／拖带／电弧／波壳／环带的名义 Alpha 终点依次为 52／53／54／55／56 tick，外柔光最后到 58 tick。各层使用独立成长、旋转及淡出曲线，不再共享同一进退包络。实际暂停帧受前后刻插值影响，60 tick 截图已确认画面清空。

本轮新增 7 份原生 ShaderGraph，位于 `v3/charge/`：core、waves、electric、ring、trails、outer_glow、inner_glow；两类火花复用 refined 的 spark／spark_star。新图实际编译错误列表为空；关闭 Bloom 时的轮廓、金核／波壳／电弧组合、开口环、紫带的隔离画面，以及分层进退的 30／34／40／48／52／56／60 tick 画面均已查看。最终截图为 `build/mcp/charge-v3-stagger-<tick>.png`，仍是外观待评审初稿，未完成角色比例、原始贴图细节、局部彩光或世界附着验收。

工程覆盖保存原 V3 `.fxproj`，dirty=false；蓄力版另行导出到 `v3/charge/tutorial_charge_v3.fx` 与 `.fxpack`，fxId=`lobecorp:tutorial_charge_v3`，包 10 文件、两类 warnings 均为空。旧整束导出与用户资源保留。编辑器保持游戏内嵌、透视与 40 tick 暂停；本轮临时断点已移除，用户三断点保留，控制模式已退出。

维护注意：本轮从 constant 直接用 JSON 改为粒子 size 曲线时被原生精确往返校验拒绝；未知曲线字段缺少既有 NBT 数值类型模板。通过已授权 IDEA Debug 在工程副本建立原生带类型曲线，再调用同一 `PhotonEffectEditUtil.prepare/commit` 的 `set_project_data` 事务完成写入；没有绕过事务或修改 Java。模板建立后，后续曲线调整已由常规 `effect_edit_batch` 完成。预览相机需按实际 FX 根偏移取源头位置；当前 LDLib2 相机水平向量为 `(cos(yaw),0,sin(yaw))`，不能套用另一套朝向约定。

### 雾与体积效果入口（2026-10-14）

本轮通过 IDEA Debug 读取实际 ModList 与 Photon 类的 CodeSource：Photon=26.1.2.3、LDLib2=26.1.2.42、KilaGraph=26.1.0.15，Photon 来自 Gradle 解析的同版 JAR。Iris=1.11.4+mc26.1.2，getCurrentPackName()=(off)、getCurrentPack().isPresent()=false；当前 shaderpacks 目录仅发现 ComplementaryReimagined_r5.9.3.zip。运行版本与版本目录一致，没有证据表明当前启用了另一套 Photon 光影包。

- KilaGraph 的 `ApplyFogNode`／`FogUboNode`／`TotalFogValueNode` 提供距离雾：按球形／圆柱距离及原版雾参数混色；ApplyFog 在 Iris 图注入路径直接透传，避免与光影包后续雾重复。它们不能直接证明局部三维体积散射已完成。
- 当前 Photon 注册九类 FX 对象，内置 shader_graph 为 default_particle，fullscreen_graph 为 passthrough／invert，render_graph 包括 bloom、景深和调色等 22 项；本轮枚举未发现现成的独立点光、体积光或雾模板。这是当前已加载入口的结果，不将名称负检索扩大为整个图系统不具备表达能力。
- 可复用入口为 RenderGraph 的 `SceneDepthInputNode` → FullscreenShaderGraph 显式 SAMPLER2D 变量 → 原始深度采样 → `ScreenToWorldNode` → 自定义片元计算 → RenderGraph 输出。`ScreenToWorldNode` 源码明确支持全屏图，并通过 KG_Transforms 的 IProjMat／IViewMat 与分拆相机位置重建绝对或相机相对位置；编辑器离屏场景使用其自身相机。rawDepth=1 对应天空／远平面，须单独处理，不能无条件当作有限表面。
- `FullscreenShaderGraph.EXCLUDED_NODES` 排除逐次绘制绑定的 ApplyFog／FogUbo／光照及隐式 SceneColor／SceneDepth 节点；允许 Camera／KGTransformsUbo。应由 RenderGraph 显式传入颜色／深度纹理，而不是在全屏图中强接被排除的雾节点。局部雾、射线体积积分及动态 RGB 表面照明是不同实现，均未在当前案例中完成。

源码核对入口为 `com.lowdragmc.photon.client.postfx.shadergraph.FullscreenShaderGraph`、`com.lowdragmc.photon.client.shadergraph.nodes.ScreenToWorldNode`、`com.lowdragmc.photon.client.postfx.graph.nodes.SceneDepthInputNode` 与 `com.lowdragmc.kilagraph.rendertype.nodes.fog.ApplyFogNode`。官方[后期说明](https://low-drag-mc.github.io/LowDragMC-Doc/en/photon2/post-processing/)用于职责参考；当前签名及排除规则以本地 26.1.2.3 JAR 为准。Unity 的 Volume 配置容器、Bloom、原版距离雾和体积散射分别记录，不能因名称含 Volume 就写为已实现体积光。

本轮只读取运行环境、资源及源码并补充文档，未改写特效资产、启用光影包或创建新着色图。工程仍为 refined 版、16 对象、70 tick 暂停、dirty=false；真实主窗最大化为 1，本轮临时断点已显式移除，保留用户三断点并恢复游戏运行。

### 初版记录（历史）

参考为 [Unity3D 冲击波制作思路分解](https://www.bilibili.com/video/BV15vq1B2EUY/)。原生工程位于 `run/client/ldlib2/assets/lobecorp/tutorial_shockwave/tutorial_shockwave.fxproj`，同目录保存运行定义 `.fx` 与依赖包 `.fxpack`。四个模型分别处理等离子主束／包边、蓄力球、旋转拖线与源头半球烟雾；五个粒子发射器提供辉光、四角星闪、吸入／喷发火花和内核柔光，第十个对象为命名动画控制器。未修改用户 `the_queen_of_hatred` 目录中的工程。

模型、动画和材质通过游戏内原生对象及资源事务写入，未重新增加仓库模型配方或 GLSL 资源。九份可编辑 ShaderGraph 为 plasma、orb、ribbon、charge_ribbon、smoke、flash、glow、spark、filament；主束用一份材质合成白金核心与紫红包络，外部破碎条带和蓄力拖线使用不同的噪声／透明参数。透明图关闭深度写入，条带的分段 UV 使用 fract 后的端部遮罩，避免奇数分段消失。

打开 FX 工程，在动画场景选择 `tutorial_shockwave` 即可编辑整段；在右侧“运行动画测试”选择它并输入临时实参后重启。普通场景保留同一组原始时序，默认不启动命名绑定；调变量应使用动画测试或实际运行实例。

| 形参      | 默认值 | 范围与作用                                                  |
|-----------|--------|-------------------------------------------------------------|
| length    | 12     | 0.01～64 格，主束沿局部正 Z 轴伸缩                          |
| width     | 2.4    | 0.01～8 格，主束参考宽度，同时按比例调整源头模型与粒子尺寸  |
| intensity | 1      | 0～1，乘算模型透明度和粒子生命周期 Alpha，不是独立 HDR 能量 |
| enabled   | true   | 0／1，切换全部九个表现对象的可见性，继续模拟时钟            |

动画长 140 tick、默认不循环；约 28 tick 蓄力，49 tick 星闪／喷发，101～120 tick 收束消散。控制轨道重新激活和重置各对象，同一 FX 实例可以重新播放整段。命名播放把五个粒子的延迟放在控制片段起点，并通过属性曲线将该播放实例的 startDelay 设为 0；普通场景的原始延迟保留。已确认直接沿用粒子内部延迟时，待发射粒子保留出生颜色，开关变化后首次可见帧可能出现旧颜色；用控制片段安排发射避免此残留。粒子颜色和尺寸仍按原生模拟刻及前后值插值更新，暂停时只改实参不等于推进模拟。

代码复用现有接口：`PhotonAnimationUtil.of(runtime).play("tutorial_shockwave", parameters)` 返回实例句柄，`setParameter／setParameters` 更新实参，值仍为 float[]；再次 play 默认重启整段。停止动画与销毁 FX 仍是两个生命周期操作，没有接入女皇战斗、命中框或伤害逻辑。

本轮实际检查：默认与缩短／缩窄的透视画面、39／50／65／110 tick 阶段、四变量、限值和同实例重触发。关闭后四个模型的 opacity、五类粒子的颜色函数和现场 17 个粒子的插值 Alpha 均为 0，问题列表为空；着色图编译检查无错误。输出和截图为可评审版本，仍与参考原始贴图、烟雾细节、后期及镜头有所差异，不作为逐像素复刻或用户外观验收。

本次对话的工具目录未暴露 effect_view，视角设置通过已授权 Debug 在客户端安全入口调用 PhotonEffectViewUtil.prepare／commit；只读 effect_view_state 与区域截图实际可用。源码声明的工具与当前会话暴露的能力分别核对，不因缺少注册而重复实现视角系统。

## 实际检查与限制

IDEA 编译成功，Data 退出码 0；25 份生成文件集合与内容均未变化。TypeScript 检查、ESM／CJS／声明构建与真实 stdio 工具发现成功，54 项工具中包含全部 8 项特效工具。Client Debug 已启动，ping 返回 pong；新 bridge 实际读取能力与状态成功，协议 1、9 类发射器、初始工程数 0。

独立模型工程的真实 stdio → HTTP → 客户端检查已完成：新建／暂停、dryRun 不改变版本和历史、正式编辑只加一次历史、旧版本拒绝、撤销重做、播放／暂停／停止、5.5 tick 定位、保存后 dirty=false、定义及包导出、三种目标拒绝覆盖、越界拒绝、原工程保留，共 20 项；packWarnings 为空。首份输出保留在 `run/client/ldlib2/assets/mcp-authoring-36ca4d9a-239e-45a3-a496-97775d749f5e/`。随后完整重跑并增加 FX 工程中的模型发射器检查，共 29 项通过；确认真实注册名、新 UUID、单次历史、暂停保持、模型资产赋值、定位／停止、`.fxproj` 保存与 `.fx` 导出。最终模型与 FX 示例文件保留在 `run/client/ldlib2/assets/mcp-authoring-8fded43c-ccec-49e2-9bde-38d964e2e94b/`，模型包导出仍无警告。这验证默认白模接口，不代表着色器 GPU 外观、全部发射器参数、多客户端或性能已经验收。

首次在标题界面创建失败，原生场景缺少 dimension_type 注册表；已确认创建前需世界上下文。修复在创建任何窗口／工程前返回 WORLD_REQUIRED，随后重新编译／Data 并进入既有隔离测试世界 `新的世界 (5)` 完成上述检查；没有伪造世界或修改 Photon。维护时保持只读能力查询可在标题界面使用。

收尾通过 Debug 确认三个本轮工程均 dirty=false，调用原生 closeWindow 后工程数为 0；随后 pauseGame(false)／resume 时客户端结束。日志记录 17:08:33 的 Stopping 与全部维度保存完成，退出原因未确认，不能归因为 native crash 或本接口。未取得标题界面／显式退出控制模式的最终回执；临时断点已删，原三个断点及状态保留。运行日志另有原生 PassSource／PassSize 未注册 serializer 的警告，未扩展修改依赖。

新增 Java／接入类与 TypeScript 工具文件的 IDEA 检查无错误／警告；探针脚本仍有 4 项 SDK StdioClientTransport 导入解析警告，已核对当前 SDK 的通配 exports 与实际目标文件，Node stdio 与两种运行检查均成功，保留 IDE 与运行证据差异，不隐藏告警。根 Gradle 及旧文档的既有告警保留。原 MCP 的按钮重复、坐标、输入、默认端口降级等既有问题保持原状，见 [Minecraft MCP 限制](minecraft-mcp.md#源码纳入前的实测与保留限制)。
