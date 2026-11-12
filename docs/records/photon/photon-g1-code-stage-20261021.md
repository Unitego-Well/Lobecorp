# G1 全部代码优先实施记录

> 本页仅保留分阶段设计和原始检查证据，不维护当前待办；当前未解决问题统一见 [Photon 问题汇总](../../issues.md)。

> 2026-10-23 当前恢复从 [最新 WIP](photon-workspace.md) 开始。本文是 2026-10-21 代码与检查历史；“已完成”仅对应其当时范围，不代表当前高级网格／预览隔离 WIP、G1 或视觉／性能全部完成。

独立工作区的后续静态审查修复、完整轨道接线与后台文件准备见[2026-10-21续做记录](photon-static-fixes-20261021.md)。以下保留历史验证，不作为新补丁已通过的证据。

日期：2026-10-21。功能代码和格式已完成，统一正确性检查、自动验证及游戏轮次的实际结果分别记录如下。本页不宣告G1通过，不以旧轮测试替代本轮结果。

## 本次授权与边界

完整轨道工作区、旧播放兼容、资源接入及对应API/MCP先完成代码，再统一检查正确性，最后执行已授权验证。游戏测试先保留整轮问题、日志与复现步骤，再集中修改；视觉、布局和美术效果交人工审查。原资源、实体动画和实体模型LOD不动，不自动推送。

阶段备份为`refs/backups/before-photon-workspace-completion-20261021`，指向`6567bf6`。早期R0原始资源及工作区备份继续保留，其他用户修改和暂存状态不得纳入本阶段提交。

## 编写范围

| 范围 | 代码入口 | 功能边界 |
|---|---|---|
| 命名/测试轨道工作区 | PhotonNamedTimelineContext、PhotonNamedTrackDraft、PhotonNamedTrackWorkspace、PhotonNamedDefinitionFieldsPanel | 独立轨道树、原生TrackEditor、本地原树历史、复制排序删除、属性/参数/绑定/标记/规则；显式应用制作会话；主场景不替换 |
| 原树保留 | PhotonProjectNbtUtil、PhotonLosslessNbtUtil、PhotonProjectRawAccess、FXProjectLosslessMixin | 容器/元数据/工程数据及嵌套条目；无法确认未知列表归属时拒绝；工程副本和历史重新解析对象 |
| 制作预览 | PhotonNamedRuntime.previewAuthoring/seekAuthoring/ownsAuthoring | 每个窗口独立临时UUID与句柄，源定义身份另行返回；只停止自己拥有的贡献；游标不冒充粒子历史重放 |
| 旧播放字段 | PhotonLifetimeCycleUtil及六类配置/Runtime Mixin、Emitter/TileParticle年龄桥接 | 循环、分量偏移、跨周期旋转、重置年龄；仍使用原生曲线和随机缓存 |
| Beam/渲染字段 | PhotonBeamLengthAccess、BeamRuntime/BeamParticle Mixin、RendererSetting桥接及遮挡/视深适配 | 实例端点长度，不改共享BeamConfig；原生批次/目标；资源重载清理；不恢复顶点池或质量降级 |
| 模型制作与来源 | PhotonModelProject、PhotonModelAuthoringPanel、PhotonModelSourceProjectType、PhotonModelSourceUtil、PhotonModelJsonEditUtil、PhotonModelObjUtil | 当前模型工程/资产/播放/渲染事务；JSON、OBJ、内嵌/文件/资源来源；OBJ仅交换几何/UV/法线/分组与材质标签 |
| 运行/包接入 | FXHelperMixin、FXPacksMixin、PhotonModelPackExportUtil | .mfx发现、.mfxpack挂载与条目管理、隔离副本展开材质/贴图/子FX依赖；保留其他包条目，警告或缺依赖时拒绝发布 |
| 依赖报告 | PhotonResourceDependencyUtil、PhotonEffectResourceAuditUtil | 仅声明来源集合；原始来源/哈希/版本、递归候选、缺失、歧义与循环；内置/未分类引用不伪报已验证 |
| 真实游戏调用 | QueenBeamEffects、PhotonGeoEffect、既有ClientRuntimeEvents分发 | 只消费现有权威快照与effectId、原技能锚点及终点；阶段边沿控制和来源/世界/重播代次/重载清理，不改实体动画与玩法 |

## API 与 MCP

- `effect_animation_edit`新增`edit_list`：`animationId`、`path`、`action`、`index`，可选`target=named/project_test`。路径以`tracks/parameters/bindings/markers`开头，使用字段名和整数；支持insert/patch/remove/move，轨道列表另支持duplicate，重建子窗口局部UUID而保留目标/调用身份。
- `effect_animation_control`新增`target=authoring`：play使用`animationId`、`definitionTarget`、`expectedRevision`及可选time，返回createdHandleId；seek/stop使用该handleId。运行代次expectedRuntimeId仍必需。其他测试不因此停止。
- `effect_edit_batch`接回模型事务：set_model_asset(asset)、patch_model_asset(patch)、edit_model_value(path,value)、set_model_playback(values)、set_model_renderer(snbt)、import_model_obj(text,flipV)。FX内嵌模型需objectId；仍经过整批revision、独立候选、一次历史及dryRun。
- `effect_export`增加json/obj交换；模型定义/包使用.mfx/.mfxpack。交换输出不是完整工程备份；默认拒绝覆盖，输出仍限许可根目录。
- 新增只读`effect_resource_audit(sources,roots?)`：sources按优先顺序给出`{path,logicalPath}`，包内键来自实际ZIP条目；不自动扫描开发目录、不挂载、不写文件。closed只描述本报告的候选依赖图，runtimeAccepted始终false，不能用于声称独立包游戏验收通过。
- Bridge本轮新增一个只读工具，实际构建及stdio发现65项，其中19项特效工具；真实游戏写入另行验收。

## 保护与后续检查

### 第一轮集中自动验证

- IDEA编译成功：658969ab-56fe-480d-b4fc-a0701dbd65bf，success=true/aborted=false，编译计数null不作零错误/警告结论。
- 11组JUnit集中完成后再处理失败：共68通过/1失败。新增PhotonG1AuthoringOperationsTest 10/10，NamedApi 5/5、NamedAuthoring 5/5、ProjectPreview 5/5、ActivationState 9/9、NamedHandle 5/5、ChildTrack 3/3、ModelCodec 3/3、ModelTopology 3/3、ResourceReader 12通过/1失败、ResourceBaseline 8/8。
- V01：PhotonResourceReaderTest.fileGuardStillBlocksLegacyAndAllowsPlainNativeData第168行旧断言失败。先保留整轮结果与定位，再核对关闭字段的新契约；未将失败删除、跳过或写为通过。Data与Bridge本轮结果另记，尚未开始游戏测试。
- V01定位：旧断言要求fixedRendering=false仍阻断，与本轮明确关闭态可保留且无运行影响的兼容契约不一致；扩充为关闭态允许、IntTag伪布尔/未知旧字段/启用态继续阻断，模型后缀仍保护。用例未删除或跳过，回归结果另记。
- 本轮Data日志ij_run__Data_8815643960729641000.log为BUILD SUCCESSFUL，23输出written=2/removed=0；生成目录随后Git比较无净差异。Bridge日志ij_run__Minecraft_MCP_Bridge_1014847818584515911.log为BUILD SUCCESSFUL，TypeScript/stdio工具发现65项通过，含19项特效工具；不代表真实游戏MCP写入通过。
- V01修正后Reader整组13/13通过，连同本轮同一生产代码上的其他56项，共69项自动检查覆盖通过；原首轮68通过/1失败保留，不伪写为一次无失败运行。最后Java生产代码未因该用例调整而改变，只扩充测试契约。
- 最终格式处理后统一IDEA批分析完整覆盖90文件，0错误/23警告，无超时/跳过；随后收尾1项无用导入及1项等价布尔表达式，并对两文件重新格式/诊断为无问题。其余提示包括SDK条件导出解析、借用/回调所有权、@Local命名及既有未使用接口，不按零警告报告。
- 已经通过IDEA Client Debug启动同一“新的世界 (8)”的集中游戏验证，日志ij_run__Client_7639870207759948051.log；当前仅启动回执，未确认世界/GUI/MCP或G1通过，后续问题先集中记录。原3用户断点保留，只使用普通无条件行断点。
- 启动安全项目栈的普通无条件断点已命中：真实GLFW主窗maximized=1，模型类型已注册；当时world=false、control=false，尚在自动进入既有存档。已按确切ID移除唯一代理临时断点，再恢复运行，保留原3用户断点，不让暂停阻塞HTTP/MCP。
- G01：首轮Client进入世界HUD绘制路径后崩溃，`Invalid atlas texture id: minecraft:gui`，调用栈GuiUtil.blitSprite(34)→HorizontalStatusBar.drawLayer(101)→RationalityBarLayer/PlayerStatusHud；不是本轮特效路径的异常证据。游戏GUI、完整文件往返、旧播放、指挥家调用和真实MCP写入均标记受阻，未执行项不计通过。HUD代码/贴图属于其他工作区改动，未擅自修复；先评估可逆隐藏HUD测试设置。
- 完整首轮Client日志、crash、Data及Bridge日志已复制至backups/photon-g1-code-20261021，文件名分别client-round1-hud-blocked.log、crash-round1-hud-blocked.txt、data-round1.log、bridge-round1.log。显式LIVE未开启的MCP集成测试被跳过，不计真实连接或写入通过。
- 下一轮复用同一存档，仅以现有运行时hideGui选项跳过不相关的HUD阻断；BasicHudLayer第11行已有该条件。该选项不写配置文件、不改HUD源码/资源，测试结束恢复；此条件下的功能结果不能冒充HUD或完整默认界面视觉通过。

### 本轮统一静态检查记录

首轮IDEA批分析完整覆盖84文件，errorCount=0、warningCount=101，无跳过/超时；该模式不含编辑器弱提示。另独立解析Mixin JSON确认EmitterMixin重复登记。以下是代码审查问题，不冒充游戏测试结果：

| 编号 | 已确认问题 | 修正状态 |
|---|---|---|
| C01 | 同一EmitterMixin重复登记 | 已删除重复项，待统一复查 |
| C02 | 轨道类型、模型NBT和运行实例存在缺少显式空值边界的路径 | 已补校验/稳定局部引用；框架所有权提示分别说明，待复查 |
| C03 | 导出直接序列化底层FX会绕过工程原树层 | 新增PhotonExportDefinition，对导出副本的已知变化做原树合并；输出读回与预算校验，待验证 |
| C04 | 视深标记按值比较可能误关联另一绘制项 | 改身份映射，补帧结束/重载清理，待验证 |
| C05 | 包摘要只计主定义，漏计递归子定义 | 改为最终包assets条目计数，待验证 |
| C06 | 失败实例退役仅按UUID，可能阻止同UUID的新重播代次 | 改为UUID+playbackRevision，待验证 |
| C07 | 模型原生属性/轨道变化未进入正式工程保存 | 补emitterData/definitionSource原树同步、原生轨道恢复及单模型范围约束；复制刷新基线，待验证 |
| C08 | 制作预览工程调用被旧来源限制拒绝，同名调用可能替换另一测试 | 接受本宿主拥有的制作预览；工程调用使用独立句柄与稳定源定义身份，待验证 |
| C09 | 库改名后，保留旧名称快照的句柄可能不匹配更新后的名称规则 | 规则同时通过当前别名解析到稳定源UUID，待验证 |

工作区另有HUD贴图变更，不属于本任务，保留且不提交；原特效/模型/材质/ShaderGraph/粒子贴图及包目录对既有G1前备份的只读比较无差异，实体动画和实体模型LOD没有本轮编辑操作。

生效旧字段、模型文件及尚未验收的组合继续受保护；严格关闭状态和无降质提示有独立类型判定，避免新增默认字段阻断普通原生工程。不能通过统一关闭保护迎合测试。放行仍需按格式、类型和字段组合的实际证据推进。

新增自动化测试已执行，实际结果见本轮集中自动验证。功能阻断、视觉问题和性能结果分别记录；无CPU/GPU性能收益结论。

## 当前交接状态

本轮代码、统一正确性检查及自动验证已按顺序执行；G1仍未通过，禁止将代码完成等同于全部资源兼容通过。上文“待验证”涉及真实游戏行为，仍保留对应文件保护。高级响应、多线程及独立GUI呈现未实施。

- G02：第二轮同存档客户端仍在运行，hideGui仅在内存设为true以避开G01，未保存设置。真实stdio集成测试已连接游戏，但effect_create返回CONTROL_REQUIRED：Enter MCP control mode before calling this command。该轮不能计为MCP写入通过；需用户通过游戏暂停界面的MCP入口激活，不通过调试器绕过控制状态。
- 待验证：轨道工作区实际交互与历史、完整磁盘往返/独立包、旧激活与循环组合、模型工程编辑/导出、指挥家激光/星束及停止卸载。视觉另交人工，HUD问题未越界修改。
- 第二轮日志client-round2-control-required.log及真实集成日志mcp-round2-control-required.log保留在同一备份目录。恢复默认HUD状态需结束本轮隐藏HUD测试后处理，当前尚未恢复。

## 后续集中游戏测试与开发控制默认值

- 本次恢复时原客户端已经退出，IDEA无Debug会话；通过现有Client配置重进同一存档。MCP曾返回pong，随后再次遇到G01图集异常，日志ij_run__Client_9057780628777897985.log、crash-2026-10-21_08.39.47-client.txt；后续请求transport_error，不能计为特效验收失败或通过。
- 用户授权修复已发现问题后，IDEA依赖源码确认AtlasIds.GUI是逻辑图集ID，而SpriteId使用图集纹理地址。GuiUtil的两个入口改为与原生GUI相同的getAtlasOrThrow(AtlasIds.GUI).getSprite；保留裁切/缩放/浮点绘制。修改行已格式化，IDEA无错误、4项既有未使用接口警告。备份refs/backups/before-photon-g1-hud-fix-20261021。重新以普通Client运行后，默认HUD条件下进入原存档，MCP取得pong、玩家信息及effect_capabilities；effect_state为工程列表空。该结果只证明此启动路径不再立即触发已知崩溃，不替代视觉验收。
- 本轮代理普通无条件断点均已移除，原3用户断点保留。后续普通Client不附加Debug。电脑控制被用户Escape停止，未完成新客户端窗口最大化确认，也未创建或保存工程。原特效资源、实体动画和实体模型LOD没有本轮编辑。
- 用户随后明确要求开发环境默认激活MCP。ModDevMcpMod仅在Dist.CLIENT且!FMLEnvironment.isProduction()时注册一次FMLClientSetupEvent，enqueueWork在客户端线程调用现有enterMcpControlMode；不将NeoForge依赖放入common，不改HTTP监听、文件许可或逐命令检查。手动退出不被逐帧覆盖；正式环境不自动进入。Bridge工具说明同步开发环境例外。
- 修改前备份refs/backups/before-mcp-development-control-20261021。Java及TypeScript错误检查无问题；首次TypeScript完整检查超时，后续错误检查完成，不能把超时当作完整检查通过。IDEA编译success=true/aborted=false，计数null不报零警告。当前旧客户端未重启，自动进入及手动退出行为尚未游戏验证；G1仍未通过。
- 本次Data日志ij_run__Data_11331088982182637309.log为BUILD SUCCESSFUL，23输出written=0/removed=0，src/generated无Git差异；Bridge日志ij_run__Minecraft_MCP_Bridge_11554690430195009977.log为BUILD SUCCESSFUL，实际stdio发现65工具。本轮没有新增翻译键、依赖或资源。

## 集中MCP复测与模型预览修正

本节是71615ec之后的实际执行记录；剩余G1代码和验收未全部完成，不将保护拒绝写为直接可用。

- 同一存档重新通过IDEA启动。只读进程/线程与/api/status最终确认游戏监听9876正常，Node工具进程回环访问返回EACCES；早先“端口未监听/线程停留”的判断撤回，不能据此认定游戏HTTP启动失败。改用IDEA既有运行配置执行标准stdio MCP；必要的单项调用仍通过同一SDK和Bridge，仅允许本机回环访问，不改协议/端口。
- 开发环境默认控制已取得两类证据：无人工激活的真实effect_create及后续写事务成功；普通无条件断点读取production=false/control=true。主窗在编辑器打开后重新用Debug最大化并确认maximized=1；MCP视角状态active=true/embedded=true/orthographic=false。
- Minecraft MCP Animation首轮及小数预览修正后的复测均17项通过：dry-run、句柄控制、运行代次/过期引用拒绝、库刷新保留句柄、UUID引用删除保护、工程测试隔离、原树历史及原工程保留。Runtime只读检查通过，9类发射对象。二者不替代GUI或文件验收。
- 模型制作首轮在model_state失败：状态缺少playback摘要；补有效原生配置的8项播放字段，测试set_model_playback同步当前values契约。第二轮10项通过后在preview_seek失败，确认旧实现将5.5截为5。
- 新增PhotonPreviewTimeAccess及PhotonParticleManagerMixin：暂停时钟与Shader时间、粒子提取统一使用持久帧内小数，原生整数模拟不替换；play/clear/setTime清理小数。seek提交小数，非有限时间在模拟前拒绝。第三轮12项通过，包含preview_seek与fractional_model_time；没有降低断言。原生实时播放仍以整数模拟步进。
- 第三轮下一项effect_save被模型保护拒绝（INVALID_ARGUMENT/已移除功能提示），项目0ecedc79-2b24-4fd5-a41e-3aa86950bea9，目标为run/client/ldlib2/assets/mcp-authoring-3fd74900-c01c-45ad-af00-b195681cf4c9/model.mfxproj。没有回写原件，也没有将拒绝计为完整往返通过。测试输出根目录已改为隔离的运行资产目录，缺该许可根则失败，不回退源码assets。
- Debug实际打开命名工作区，MCP加号能展开注册轨道菜单；选择轨道后未观察到新增行，尚未定位完成，不能标为制作验收通过。另确认TICK成功轮询会清空操作错误，已改为后台成功不清空、前台成功才清空；该最后修正IDEA检查/编译通过，尚未重启验证。
- 视觉记录交人工：工作区撤销/重做显示原始翻译键、标题/文字与控件布局待审查；截图g1-named-workspace-20261021.png、g1-track-menu-scaled-20261021.png、g1-track-added-20261021.png均在run/client/screenshots，未因视觉记录扩改资源。MCP点击工具说明称screen pixels，但实际回执/命中使用GUI坐标，本轮按guiScale=2重新定位，不把首次未命中算作轨道实现失败。
- 本轮有一次进入世界前构造FXEditor的Debug探针因dimension_type注册表缺失而失败，另一次含lambda的探针编译失败；两次均为验证设置失败，不作为功能通过。清理/恢复执行自动审批曾超时，单次重试后已移除代理断点并确认running，原3用户断点保留。用户限定Debug/MCP后未再使用电脑界面控制。
- 备份refs/backups/before-photon-model-state-20261021指向71615ec。原始失败/复测/Client/Data日志保存在backups/photon-g1-concentrated-20261021；最后IDEA编译success=true/aborted=false，errors/warnings为null。相关7项代码/配置错误检查无问题；ProjectUtil原有3项警告不作零警告报告。最后Data日志data-final.log为BUILD SUCCESSFUL，23输出written=0/removed=0，生成目录无Git差异；无新增依赖、翻译键或原资源修改。

剩余：模型保存/重开/定义与独立包组合的逐项放行及全量验收，GUI轨道新增/编辑/拖动/历史/关闭清理，完整旧激活/循环/生成器/渲染组合，真实指挥家激光/星束及来源/停止/卸载清理，冷/热/多实例和拖动性能证据。高级多线程与独立GUI呈现未实施。实体动画和实体模型LOD由其他AI负责，本轮不编辑。

## 核心文件能力续做与人工GUI交接

本节以4d2ad64为代码基线。用户最新明确GUI有功能入口即可，视觉、布局和界面交互交人工；不再以截图或点击验收推进本轮。核心API、文件与运行语义继续验证，G1仍未通过。

- 轨道新增收进PhotonNamedTimelineContext.addTrack，GUI调用同一事务。游戏Render线程实际验证added=1、undone=0、copied=2、mainTracks=0；这是原生制作上下文的功能证据，不冒充GUI点击验收。撤销恢复和关闭仅清理该上下文拥有的检视内容，保留其他工作区的检视。已有菜单改为Dialog内部子节点，工作区尺寸受宿主限制；这些界面接线留给人工审查。
- PhotonModelProject.document在原数据缺少emitterData时也保存当前UUID及原生对象数据，避免另存重开生成新身份；已有emitterData按已知变更合并，保留未知数据。对缺失对象身份的当前工程，另存增加该字段属于本次明确记录的结构补全，原件不自动覆盖。
- 文件门禁从模型后缀一律拒绝改为内容校验：.mfxproj容器版本1.0、资产版本1、内嵌模型；.mfx发布容器版本5，包内逐条递归检查。版本5通过IDEA核对当前Photon 26.1.2.3声明，纯校验层不读取会初始化编辑器图标的FXProject静态字段。FILE/RESOURCE来源未闭合、未知版本、无效拓扑/UUID/播放字段及未放行的生效旧字段继续拒绝；没有整体关闭保护或改原资源。
- 新PhotonModelFileValidationUtil检查拓扑和播放字段，UUID兼容既有字符串与原版整数数组编码。关闭遮挡的原生编码是_enable:0b，已修正保护只识别enable而误拒绝默认配置的问题；启用态、伪布尔、未知字段及非有限/越界阈值仍拒绝。
- MCP模型状态只返回一份模型JSON，去掉parameters.config中重复展开的资产字节；add_emitter的parameters对象递归合并、列表/数组整体替换，保留未涉及的默认设置。测试脚本补实际三角形资产、未知JSON字段及重开UUID/资产/播放一致断言，输出仍在隔离运行目录。
- 资源测试经历两次已记录失败：首次引用FXProject.VERSION触发无FML Loader的图标初始化；改为明确格式常量后，字符串UUID被仅接受整数数组的校验误拒绝。均修正而未删除断言，随后15/15通过；补关闭遮挡回归后，通过IDEA索引测试回执a7d43e3a-50c8-4918-a322-353cf8117436确认16/16通过、exitCode=0、无超时。编辑器检查保留原有弱提示，不报零警告。
- 真实MCP本轮已推进到23项通过：创建/编辑/历史/小数播放、模型保存干净状态、重开保持UUID/资产未知字段/播放、.mfx与.mfxpack导出、重复文件和越界拒绝。源项目e3b5de43-88c2-4656-be5a-bceb24b3ae9b，目录run/client/ldlib2/assets/mcp-authoring-89a0c045-290e-4112-a64a-3e193d64ce21。下一项普通FX工程新增模型失败，已确认部分config整段替换丢掉默认渲染设置，修正为递归合并；修正后的整链结果另记，不把23项写成整组通过。
- 曾有一次Client在ConductorPortraitCache$State缺失时启动失败；IDEA确认现有内部类和源码诊断正常，State.class产物时间晚于报错，随后重编译和串行启动恢复。指挥家源文件有其他任务改动，本轮未编辑。后续先结束JUnit/编译/Data再启动游戏，不在运行验证时重写Java产物。正常关闭使用已核对的Minecraft.stop()，只清理本轮普通无条件断点，用户原3断点保留。
- 阶段备份before-photon-workspace-transaction-20261021、before-photon-model-file-adapter-20261021均指向4d2ad64代码基线；旧R0资源与工作区备份继续保留。编译、Data和Bridge有成功回执，最后新增参数合并修正的编译68594899-cebf-4cd5-aa33-fde53752c363为success=true/aborted=false；Data与后续MCP整链结果按最终日志补充。

未完成：全量现有文件逐项往返/依赖与独立包运行，尚受保护的来源/旧字段组合、子播放/生成器、真实指挥家调用/停止/卸载和性能对照。GUI由人工验证，不以视觉调节替代这些核心工作；高级扩展仍未实现。

### 本轮后续修正与当前验证阻断

- 在已通过模型文件链23项之后，修正add_emitter的嵌套参数合并；下一轮通过fx_add_model_emitter，累计24项后在fx_set_model_asset断言失败。API当时返回成功，但配置复制绕过了自定义资产快照序列化，实际资产未更新。PhotonEffectModelAuthoringUtil.applyEmbedded现显式复制assetSnapshot，覆盖模型替换、局部模型编辑和OBJ导入的共同提交路径；该最后修正尚未运行复测，不能报整链通过。
- 对真实原件counterattack.mfxproj的只读Debug结果为meta{name:photon.lobecorp.model.project.type,suffix:.mfxproj,version:1.0}，data仅有asset。PhotonModelProject.restore与纯文件校验已支持缺省playback/renderer，在内存应用原生默认值，类型错误仍拒绝；document不因读取而无故补写空playback。增加asset-only断言，原件没有写入。
- 17项版本的资源测试runId=652c007d-35c6-458e-82d5-f7cec1ad84ff在before-launch阶段失败，未启动测试进程。最后整体验证编译c4647f5f-883d-4c3d-90e5-e9d813375ddf报告world/horde的6个错误：canTargetInLevel、SWEEPER、HordeRuntime及HORDE_PARTICIPANT。指定仅构建本任务文件后，委托构建仍报告HordeSync等8个外部错误，说明不能据此完成隔离编译；未通过命令行绕过，也未修改或排除Horde代码。
- 当前候选各修改文件的IDEA错误检查无问题；此前16/16是关闭遮挡修正后的实际通过回执，不作为最新asset-only及嵌入资产补丁的重新验收。最后成功Data日志ij_run__Data_508130266748186305.log为BUILD SUCCESSFUL，23输出written=0/removed=0；发生在上述最后补丁之前，生成目录无本轮Git差异。最后补丁后的编译、Data及游戏验证均未通过/未执行，等待外部构建恢复后按原断言继续。
- 当前没有本轮Debug会话，代理断点已移除；保留原3用户断点。GUI与视觉留给人工，实体动画、实体模型LOD及其他任务的Horde/指挥家改动不纳入本轮提交。
- 本节原始日志已复制到backups/photon-g1-files-20261021，共7份，包含初始化/UUID失败、15项通过的JUnit原始日志，23项和24项后的MCP失败日志、最后补丁前Data及Client日志。16项结构化测试回执按上文runId定位；最新17项没有实际执行结果。
