# G1 全部代码优先实施记录

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
