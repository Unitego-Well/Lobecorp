# Photon 特效 MCP API 与制作限制

核对日期：2026-10-20。当前G1仍未通过；命名制作/运行接口按下节说明，模型工程和未恢复类型继续受保护。旧模型制作段落保留历史用途，不代表对应能力当前已开放。实际能力以capabilities/schema及回执为准，检查证据见[实施进度](photon-redesign-progress.md)。版本来源见 [MCP 索引](photon-effect-mcp.md)。

## 命名制作与运行 API（2026-10-20）

界面与MCP共同使用PhotonNamedAuthoringUtil/Session；不新增第二套文件协议或场景模拟器。当前库仍为第六版，工程测试字段只存在于工程data层。原文件不自动迁移或覆盖，原生activator/control/animation/group之外的命名轨道组合继续按保护拒绝。

| 工具 | 必需参数与回执 |
|---|---|
| `effect_animation_state` | projectId；可选controllerId。返回工程revision、历史数量、各控制器librarySnbt、定义/参数/绑定/标记摘要、规则、projectPreviewSnbt及运行状态；不改变选择或工程。 |
| `effect_animation_edit` | projectId、controllerId、expectedRevision、operations；可选dryRun。共用界面制作会话，整批验证后一次提交历史，失败不提交；dryRun返回候选库和测试SNBT，不改变数据/历史。 |
| `effect_animation_control` | projectId、controllerId、expectedRuntimeId、action；target默认named，也可project_test。只控制运行实例，不写制作定义/历史；回传最新状态和句柄。 |

命名编辑操作从capabilities.animationOperations读取，每批最多256项，仍受原2MiB请求限制：

- add(name)、duplicate(animationId,name)、rename(animationId,name)、move(animationId,index)、remove(animationId)。新名称仅小写字母/下划线；改名保留UUID并更新库内名称引用，删除同时检查名称和UUID引用。
- replace_definition(animationId,snbt)：传入完整定义原树，保持其身份与名称，用于属性、参数、绑定、标记及轨道编辑。先读取librarySnbt并保留未知字段，不把摘要当作完整定义替换。类型/通道数从animationParameterTypes读取。
- patch_definition(animationId,snbt)、patch_project_preview(animationId,snbt)：按定义UUID合并明确给出的duration、autoDuration、loop、priority、completion、parameters、bindings、markers、tracks字段，其他字段保留；列表整体替换，列表内部未知字段须由调用方保留。拒绝身份、名称和未知字段补丁，测试定义不存在或UUID失效时拒绝，不隐式创建。仍共用整批revision、运行准备和文件保护。
- set_default(animation)：名称/UUID，空字符串为手动启动；set_rules(snbt)：完整`{rules:[...]}`，只接受rules这个根字段。无效默认/规则引用在整批最终验证时拒绝。
- set_project_preview(snbt)、clear_project_preview：独立保存/清除工程测试定义，不进入FX发布数据，身份不能与命名定义冲突。

新建流程：effect_create(kind=fx) → effect_preview(pause) → effect_state → effect_edit_batch(add_emitter,type从当前writable能力选择命名控制器) → effect_animation_state → effect_animation_edit。每次制作提交使用最新工程revision。文件保存/导出继续走原工具和允许目录/覆盖门禁。

运行状态返回runtimeId、revision、libraryMatchesAuthored及handles；每个句柄有handleId、animationId、原定义名称、状态、参数、制作时间、进度和循环次数。runtimeId是本次运行代次，handleId是本次播放身份；二者均不写入文件，不能用animationId代替handleId。

- named支持play(name；可选duration/restart/inputs)、pause/resume/stop(handleId)、seek(handleId,time)、parameters(handleId,inputs)、duration(handleId,duration)、trigger/signal(可选channel/name/dataSnbt)。inputs使用名称或参数UUID到浮点通道数组的映射。
- project_test支持play(可选time)、seek(handleId,time)、stop(handleId)。已有工程测试时，重新play必须显式提供它当前的handleId，不能隐式替换其他调用者的测试。
- 制作提交刷新后续播放和事件使用的库，不重启场景、不改现有句柄快照；已有同定义播放且restart=false时沿用原句柄，需要使用新定义时显式restart。改名后的旧句柄仍显示自己的原名称，并可继续按handleId控制。
- seek仅定位该动画的制作曲线/属性游标，不重放粒子出生、碰撞、拖尾或世界历史；capabilities.namedSeekSemantics对此明确标识，不能报告为完整时间轴重新模拟。
- 控制保留其他句柄；控制参数先校验，再更新目标。运行实例重建后，旧expectedRuntimeId返回RUNTIME_CONFLICT；失效/跨实例句柄返回HANDLE_EXPIRED；未启动或关闭的命名层返回RUNTIME_UNAVAILABLE。制作版本冲突仍用REVISION_CONFLICT。

三个工具保持原`{ok,data,error}`、text/structuredContent及isError一致性；写工具仍经过HTTP、客户端执行前与提交前控制门禁。MCP工程data快照的测试字段按其工程位置检查，放行不延伸到独立FX或其他位置。

IDE验证入口为`:minecraft-mcp:probeEffectAnimation`（stdio），或显式设置LOBECORP_MCP_LIVE=true后通过IDEA运行PhotonNamedMcpIntegrationTest。默认普通测试跳过游戏集成；集成只创建独立测试工程，不保存到原资源目录。Bridge发现已确认64项工具，其中18项特效工具；游戏端完整调用结果以本轮实施进度为准。

## 使用顺序

AI 制作与参考视频复刻使用项目 特效制作技能（入口缺失：`../../.codex/skills/lobecorp-effect-authoring/SKILL.md`），按需读取其原生操作参考。技能复用本系统工具，固定操作在入口、出口及分支处确认，不增加独立制作服务器或固定效果代码配方。

1. 客户端已运行时复用现有会话；需要启动时通过已授权的 IDEA Client Debug，让本项目 bridge 连接游戏。创建工程前进入世界。Photon 场景需要世界维度注册表，标题界面创建返回 WORLD_REQUIRED，并且不创建窗口／工程。写操作需要原有 MCP 控制模式，后端不会绕过激活条件。
2. 调用 `effect_capabilities` 读取当前发射器、参数、轨道模板、操作与允许文件目录；调用 `effect_state` 读取扩展编辑器中已打开的工程。仅操作 `lobecorp:photon_editor` 窗口与 PhotonEffectEditor／模型子类；原生 `/photon_editor` 中的工程不被枚举或接管。
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

接口接入时的源码提供 15 项特效工具，连同 46 项原生工具共 61 项；当时的编译、Data、bridge 发现与诊断结果见 [修改记录](changes.md)。旧 54 项工具与游戏案例见[历史记录](photon-effect-authoring-history.md)，不代表新增能力已通过本次游戏验收；当前集合以本轮实际发现为准。

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

2026-10-16 修正混合轨道列表增删／重排的 NBT 模板选择：原下标对应另一 type 时，优先查找同 type 的原生模板，保留 target／clipTarget 的 UUID 数组等类型；未带 type 的列表维持原转换方式。原问题表现为 set_timeline 完整往返校验失败，并非无效对象引用。女皇变体的删层、新增轨道和变量绑定已在同一原生事务提交成功，未放松精确往返或引用校验。

### 模型状态去重与视角保持

模型资产的 JSON 在原生 NBT 中保存为字节数组。此前状态同时返回完整 SNBT、parameters、逐项 schema 和解析后的模型，四个模型的实测响应达到 103,512,451 字节，超过通道 67,108,864 字节上限。现在含模型的工程返回 `snapshotIncluded=false`，不重复展开完整快照；FX 模型对象从临时 parameters／parameterSchema 中省略 `config.assetJson`，完整几何、UV、材质和动画改由该对象的 `asset` 返回。原生文件、内容版本、完整候选校验及撤销历史保持原有格式。

调用方必须先读 snapshotIncluded／proposedSnapshotIncluded，不能假定每个工程都有 SNBT。模型修改优先使用 set_model_asset／patch_model_asset，其他已有字段使用 set_parameter；set_project_data 仍要求调用方持有完整原生 SNBT，不接受删去模型字节的部分快照。普通参考星轨与原生 Beam 配方不含模型，既有 SNBT 路径继续可用。

批量编辑重建 FX 场景和 effect_preview seek 先保存当前视角，再恢复 center、zoom、yaw、pitch、投影和四个显示开关；暂停状态也在编辑后保留。棋盘场景内容及浮动面板不属于这些字段，重建场景可能恢复它们。用户要求的透视预览与游戏内宿主继续适用。

## 生命周期、文件与依赖

业务入口为 [PhotonEffectAutomation](../../src/main/java/org/unitego/lobecorp/client/photon/editor/automation/PhotonEffectAutomation.java)，客户端注册为 [LcPhotonMcpCommands](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonMcpCommands.java)，工具分派为 effectHandlers.ts（历史引用，当前工作区缺失：`../../modules/minecraft-mcp/packages/minecraft-mod-mcp/src/mcp/effectHandlers.ts`）。MCP common 的通用 McpExtensionRegistry 保留 JSON 层次，不依赖 Photon；Lobecorp 只在 mcpmod 存在时通过唯一客户端初始化入口注册。

请求在客户端线程经历 QUEUED／RUNNING／COMMITTING／DONE。排队和副本准备超过 8 秒可取消，取消后晚到任务不能提交；进入 COMMITTING 后等待真实结果，不假报取消。请求最多 2 MiB、256 个操作，单次 seek 最多 1200 tick。保存／导出编码在 RUNNING 只写独占临时文件，正式提交才发布目标；失败或取消清理临时文件，原生资源编码仍可能耗时。

允许根目录以当前 LDLib2 assets 与 Photon FXPacks API 为基础；开发源码目录有效时，额外允许 run/client/ldlib2/assets 与 run/client/photon/fxpacks，源码与 run 的界面入口分开，详见[开发目录分类](resources-datagen.md#开发目录分类)。相对路径仍基于首个 assets 根，绝对路径必须在允许根内，拒绝越界和符号链接；发行环境维持原生两类目录。默认拒绝覆盖与被锁定包。模型后缀为 .mfxproj／.mfx／.mfxpack；粒子 FX 保持原生 .fxproj／.fx／.fxpack。文件格式与共享着色器依赖规则见 [模型特效](photon-model-effects.md)，没有新增旧模型格式兼容。

## 命令提交

execute_command 通过连接的 sendCommand 提交，锁定 NeoForge 先处理客户端命令，再按权限与签名发送服务端命令。`sent=true` 只证明已经提交，不等于命令业务成功；旧反射故障、实际检查与未验收边界见[命令修复历史](photon-effect-authoring-history.md#2026-10-15-命令接口修复)。

## 雾与体积效果入口（2026-10-14）

本轮通过 IDEA Debug 读取实际 ModList 与 Photon 类的 CodeSource：Photon=26.1.2.3、LDLib2=26.1.2.42、KilaGraph=26.1.0.15，Photon 来自 Gradle 解析的同版 JAR。Iris=1.11.4+mc26.1.2，getCurrentPackName()=(off)、getCurrentPack().isPresent()=false；当前 shaderpacks 目录仅发现 ComplementaryReimagined_r5.9.3.zip。运行版本与版本目录一致，没有证据表明当前启用了另一套 Photon 光影包。

- KilaGraph 的 `ApplyFogNode`／`FogUboNode`／`TotalFogValueNode` 提供距离雾：按球形／圆柱距离及原版雾参数混色；ApplyFog 在 Iris 图注入路径直接透传，避免与光影包后续雾重复。它们不能直接证明局部三维体积散射已完成。
- 当前 Photon 注册九类 FX 对象，内置 shader_graph 为 default_particle，fullscreen_graph 为 passthrough／invert，render_graph 包括 bloom、景深和调色等 22 项；本轮枚举未发现现成的独立点光、体积光或雾模板。这是当前已加载入口的结果，不将名称负检索扩大为整个图系统不具备表达能力。
- 可复用入口为 RenderGraph 的 `SceneDepthInputNode` → FullscreenShaderGraph 显式 SAMPLER2D 变量 → 原始深度采样 → `ScreenToWorldNode` → 自定义片元计算 → RenderGraph 输出。`ScreenToWorldNode` 源码明确支持全屏图，并通过 KG_Transforms 的 IProjMat／IViewMat 与分拆相机位置重建绝对或相机相对位置；编辑器离屏场景使用其自身相机。rawDepth=1 对应天空／远平面，须单独处理，不能无条件当作有限表面。
- `FullscreenShaderGraph.EXCLUDED_NODES` 排除逐次绘制绑定的 ApplyFog／FogUbo／光照及隐式 SceneColor／SceneDepth 节点；允许 Camera／KGTransformsUbo。应由 RenderGraph 显式传入颜色／深度纹理，而不是在全屏图中强接被排除的雾节点。局部雾、射线体积积分及动态 RGB 表面照明是不同实现，均未在当前案例中完成。

源码核对入口为 `com.lowdragmc.photon.client.postfx.shadergraph.FullscreenShaderGraph`、`com.lowdragmc.photon.client.shadergraph.nodes.ScreenToWorldNode`、`com.lowdragmc.photon.client.postfx.graph.nodes.SceneDepthInputNode` 与 `com.lowdragmc.kilagraph.rendertype.nodes.fog.ApplyFogNode`。官方[后期说明](https://low-drag-mc.github.io/LowDragMC-Doc/en/photon2/post-processing/)用于职责参考；当前签名及排除规则以本地 26.1.2.3 JAR 为准。Unity 的 Volume 配置容器、Bloom、原版距离雾和体积散射分别记录，不能因名称含 Volume 就写为已实现体积光。

本轮只读取运行环境、资源及源码并补充文档，未改写特效资产、启用光影包或创建新着色图。工程仍为 refined 版、16 对象、70 tick 暂停、dirty=false；真实主窗最大化为 1，本轮临时断点已显式移除，保留用户三断点并恢复游戏运行。
