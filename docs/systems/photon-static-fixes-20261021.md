# Photon 静态审查修复与完整轨道接线

> 2026-10-23 当前真实根为 E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon，分支为 codex/photon；以下 7903、codex/photon-performance、基线与运行 PID 均为历史身份。当前工具显式使用真实根，最新恢复入口见 [网格、光束与 G1](photon-mesh-beam-g1-20261023.md)，不沿用本页旧进程或旧产物执行新验收。

日期：2026-10-21。独立工作区：`7903/Lobecorp`；分支：`codex/photon-performance`；基线：`2559a01`。

## 授权与备份

仅修复本轮代码审查问题并补齐轨道兼容。先完成代码和修改行格式，再统一静态检查；本轮不构建、不运行Data、测试或游戏。视觉和界面交互由人工验收。原资源、实体动画和实体模型LOD不修改。

- 修改前基线：`refs/backups/before-photon-fixes-isolated-20261021`。
- 迁移前HEAD、索引和含未跟踪文件的快照分别保留在`refs/backups/photon-migration-head-20261021`、`refs/backups/photon-migration-index-20261021`、`refs/backups/photon-migration-files-20261021`；索引提交`85bf39e`、文件快照`2cf2b48`。
- 基线恢复仅针对逐文件核对的迁移缺失和旧版文件，不恢复原混合任务快照。38个旧源码遗留文件由协调任务另行归档。本轮不自动合并或推送。

## 已编写代码

1. 轨道上下文：撤销/重做和编辑入口检查上下文，片段/轨道操作检查当前草稿归属；历史恢复重新解析对象并清理拖动引用。scrub期间合并历史，完成只入栈一次，取消恢复原树；关闭在finally清理本窗口状态和检视归属。
2. 轨道类型：接通原生activator/control/animation/group/speed/signal/audio/post_process，以及模型动画、子动画和工程测试调用。命名计划采用原生TrackEditor和轨道读取器，先严格检查未知类型和属性，再解析；调用轨道只能位于工程测试容器，发布库继续拒绝。
3. 命名辅助轨道：音频实例由每个句柄独占并在暂停/停止/释放时清理；后期沿用宿主逐帧sink；速度按优先级和播放顺序覆盖，退出只恢复仍属于本混合器的贡献；信号沿用命名周期窗口、事件预算和原生监听入口。实体动画系统没有参与。
4. 文件往返：保存按原生工程格式写出完整meta/data/容器未知字段，先做原生完整容器解码对比，后台磁盘读回再比较完整容器；不再只比较data摘要。定义导出比较完整NBT，包保留其他条目，缺依赖/冲突拒绝发布。
5. MCP文件任务：客户端线程捕获独占数据和原生依赖字节，单后台线程处理文件编码、压缩、ZIP写入、OBJ转换和有界读回，队列上限8。后台不访问活编辑器、世界、GPU或注册表。提交回客户端线程复核工程身份、内容revision、预览runtime、目标路径和包锁；取消/过期结果只清理临时文件，不发布。
6. 声明来源审查：许可路径与参数在客户端捕获，后台完成读取、哈希和递归依赖解析。只读报告仍为runtimeAccepted=false，不冒充独立包验收。
7. 模型状态：普通FX内嵌模型同样省略重复SNBT和资产字节schema，每对象单独返回modelAsset；snapshotIncluded=false必须由调用方检查。revision采用保留NBT类型与键序稳定性的流式哈希，避免把大字节数组展开成SNBT，并复用本次状态快照。
8. 回归源码：新增轨道格式/工程调用隔离/模型与子片段错误输入和工程指纹用例；尚未运行。

## API与线程边界

现有MCP工具名和参数保持。effect_save、effect_export、effect_resource_audit采用后台准备，返回仍等待实际提交结果；客户端线程直接调用这三项会被拒绝，避免阻塞客户端等待自己的回调。超时发生在提交前时拒绝发布；进入提交后仍返回实际提交结果。

原生依赖捕获、工程序列化、原生对象解码与提交仍在客户端线程，未将整个FXRuntime/UI迁移到后台。大工程捕获阶段仍可能耗时；当前修改不等于可取消seek、模型求值工作池、独立GUI呈现或性能优化全部完成，也没有P95/P99收益证据。原生编辑器菜单的保存流程未统一替换为本MCP后台任务。

## 检查与验收状态

本轮代码与修改行格式已完成。IDEA统一批分析覆盖20个Java文件及Mixin JSON，均为analyzed且输出未截断：首轮0错误/13警告；修正本次空值边界、冗余赋值和批量添加提示后为0错误/8警告；文件往返比较改为Objects.equals并完成该文件复查，无问题，合计保留6项存量提示。保留项是命名Prepared资源所有权提示、未使用listener/rejectEffect接口，以及ProjectUtil既有的编辑器空值/恒真条件/静态Mixin桥接提示，未扩大清理。批分析不含全部编辑器弱提示，不能替代编译。

四份文档均完成IDEA分析；changes.md历史段落存在旧文档/截图断链，30项输出上限发生截断，未作为全文无警告；本次新增段落另按行范围检查。其余三份文档无诊断问题，历史断链不在本轮清理范围。

Git差异确认assets/resources/src/generated、实体动画、GeckoLib/模型LOD及VisualQualityConfig没有本轮变化；git diff --check无输出。新增6项JUnit用例仅完成源码和静态分析，未执行。未执行构建、Data、自动测试、游戏或视觉验收；G1未通过。文件保护仍按字段和类型保留，未知版本、错误格式、外部模型来源和未验收旧生效渲染字段不整体放行。

后续运行验收必须覆盖完整轨道的添加/复制/历史/磁盘往返，音频暂停与周期边界，信号重复和跳转，速度多实例叠加与恢复，后期关闭清理，文件取消/过期竞争与未知容器字段，以及真实包独立加载。视觉单独交人工。

## 用户授权构建后的集中检查

用户随后明确允许在独立工作区构建和运行游戏；此授权覆盖本节，前述“不构建”记录保留为当时边界。Client Photon/Data Photon/Server Photon工作目录、modFolders和moddev参数均指向7903；客户端MCP端口19877，服务端25572。已检查参数文件中没有旧主目录或90be的build/run引用，但未启动客户端，不能宣称游戏监听或桥接连接已通过。

- 首次IDEA构建54ffdee6-c219-4664-8364-0ae51be7cda7失败：26错误。2项由本轮prepareExport返回Job后漏接PhotonModelAuthoringPanel调用造成，另24项为QueenBeamEffects依赖的实体动画契约缺失。此前静态批分析仅覆盖修改文件，未覆盖这个未修改的GUI调用方，因此无错误结果不能等同于完整编译成功。
- 修正GUI导出为exportFromEditor异步入口，共用有界文件准备/主线程提交，不阻塞GUI，也不要求人工GUI操作进入远程MCP接管模式。IDEA引用检查确认prepareExport的两个调用方、prepareSave的单一调用方；两份修改文件静态分析无错误，保留命名Prepared既有所有权提示。新增代码及修改行完成格式处理。
- 第二次IDEA构建5fdf17b5-9bf6-4810-a995-5f696ecd15d1失败：24错误，全部落在QueenBeamEffects。缺失TheQueenOfHatredAnimationDefinitions、TheQueenOfHatredBeamAnimation（含Phase与阶段参数）和LcAnimationDefinition.effectId()；原af77096备份有这些动画任务增量，2559a01基线没有。没有恢复混合快照、复制/改写实体动画，亦未删除特效调用或改成空实现绕过。
- 首轮工具回执摘要保留为backups/photon-build-followup-20261021/compile-round1-summary.json（原始构建缓存已失效，不能补取完整原始日志），第二轮完整日志为同目录compile-round2.log；跟进修改前备份refs/backups/before-photon-build-followup-20261021指向ed3857e。
- 当前不能启动含新代码的游戏或运行依赖主源码编译的测试；没有用旧产物冒充验收，没有执行Data。需要实体动画任务提供上述契约及必要关联改动的可集成版本后再继续，原资源与实体模型LOD保持不动。G1仍未通过。

### MCP恢复后复核

协调任务恢复本工作区Skill及固定端口bridge后，本任务重新读取Skill和连接参考；通过IDEA集成终端的官方MCP SDK连接7903自己的dist/cli.js，显式MC_MCP_PORT=19877，未使用launcher或另一客户端。tools/list实际发现65工具，并读取ping/status的真实空参数schema后依次调用。ping返回Mod not connected；get_minecraft_status成功返回connected=false、port=null、processAlive=false，符合未启动客户端的状态；该结果证明标准MCP协议和管理调用可用，不证明65项业务工具或游戏链已验证。

继续只读核对QueenBeamEffects，IDEA报告23处无法解析的动画契约引用，未截断；与前轮编译的24项错误计数来源不同，不能互相替代。没有重复启动已知失败构建，也未将此归因于MCP。协调者拥有的.codex/config.toml和bridge consts.ts改动保持原有未暂存状态，本任务没有编辑或提交这些文件。依赖集成仍须保持实体动画/LOD的任务边界。

## 最小依赖协调与新验证轮次

### 最终功能复测（2026-10-21）

- 最后短窗口修复IDEA分析无问题、构建7b4116ce-fd6a-41fd-827a-a6eac3645bce成功。真实星束的子laser_start在Window.complete断点已controlled=true、progress=1，长度18.858274、宽度0.6944444与父实例一致，子光球burst也为受控终点；不再只看active标记。
- 同一隔离存档真实目标生命1024→1016.128→1008.256，对应两次星束；激光后降到929.5365，第二次激光后850.817。没有修改伤害、命中策略、射程或持续时长；这里是功能观察，不作为全量战斗平衡或性能成绩。
- 激光真实ACTIVE快照phase=1时执行现有ConductorController.updateLaserAim/releaseLaserControl正常返回，结束后服务端动画清空。测试实体按本轮UUID移除，原存档/原实体未编辑。
- 原样法阵的getter读回rotation=true/40、size=true/20、XYZ=.2/.2/.3；真实Roll曲线含整周期累计的弧度39.5t=6.212207803、40t=6.283185307、40.5t=6.354163211，跨周期连续。这是配置/数学采样与真实资源结合的证据，最终视觉交人工。
- 实际退出世界后level=null、singleplayerServer=null、client snapshots/PLAYING/RETIRED均0，临时断点全部移除，仅保留用户原有两个禁用异常断点；临时pauseOnLostFocus恢复，关闭本工作区客户端。
- 二阶段补测：按源码35%阈值，将本轮测试女皇设到30%并经真实hurtServer进入phase2；真实指挥家两次cast分别得到star_beam_phase_two与laser_phase_two。客户端两者均OUTPUT/active=true/alive=true，星束length4.8036118、激光length1.2527978/width1.25（同存档近墙截断），没有改变阶段规则。二阶段测试实体移除后退出，level=null且snapshots/PLAYING/RETIRED均0；证据queen-phase-two-debug.json与queen-phase-two-final.log。原40%准备偏差不计失败或通过。
- 最终轮日志queen-concentrated-final.log中FX加载/女皇实例接入失败计数0；KilaGraph/Iris格式兼容警告等保留，不修改Shader/画质。二阶段准备曾使用40%生命，未达真实35%阈值，已标为测试准备偏差，后续补测不将其误报为实现故障。

G1总体仍未通过：全量原资源逐文件及包内版本、独立包无开发兜底运行、完整磁盘往返矩阵、多人晚追踪/全部旧字段组合、视觉及性能P95/P99仍不可由本轮局部结果替代。

### 集中首轮问题台账

- GQ01运行事实：真实指挥家星束cast=true，实例641c4c77-75a9-4c60-bbb6-cd12d9ba40c6；onEnd同刻duration=0、queen age=3507、快照phase=2且phase_started_age=3507，客户端也收到phase=2。尚未证明渲染可用，因为GQ04先阻断实例创建。
- GQ04：激光cast=true（实例79737ba3-6be8-4c6c-a521-e1c8bc9a5264），星束亦cast=true；但分别加载laser.fx与star_beam_spell_beam.fx时被字段保护拒绝，随后报光束/法阵锚点缺失。子代理逐份NBT确认为renderer/modelRenderer内fixedRendering=1、viewDepthSort=1及occlusion._enable=1/alphaCutoff≈0.99组合，非嵌入模型来源错误。将先核对渲染实现和保护边界再作逐字段适配。原资源不修改。

本轮通过隔离存档内临时g1_queen_dependencies数据包加载原样两份服务端资源，包版本从运行JVM读取为101.1；源码原样副本blob分别c1dfae5670866afc6611069fcab7a1eb6bcfcc86、09edf5a266cd3f2f4f082b3298b7def147a6e6d4，与af77096完全相同。

- GA01：制作脚本仍从FX对象parameters.config.assetJson读取资产，但当前API已明确使用modelAsset；实际嵌入资产存在且修改成功。修复时改为完整内容相等、身份/revision/历史不变式检查，不降低断言。
- GB01：11类轨道草稿中10类通过添加/复制/撤销/重做、过期引用拒绝及关闭拒绝；工程测试调用轨道lobecorp_animation_call被Timeline.readTrack的发布保护误拒绝，嵌套group同样受影响。全局保护继续保留，拟在草稿集中按projectTest安全解码。
- GQ03：复开独立客户端PID23560、端口19877、真实最大化后，在同一隔离存档通过ConductorController.cast触发真实星束，抛NoSuchElementException(lobecorp:entity/the_queen_of_hatred)。缺两份服务端data骨架/动作副本；当前子代理确认af77096原件与HEAD assets正文一致，仅末尾换行不同。通过IDEA原样接入两份最小依赖，不修改assets或动画逻辑，再继续首轮。

问题先记录再统一修复。

### 第二轮新问题（先记录）

- GQ06：第二次真实星束已创建star_beam_spell_beam子实例，但出生时仍是默认独立laser_start（length12、controlled=false）。该断点读数本身不证明已推进完整模拟tick；子代理进一步确认源码漏接路径：父窗口t=start无子目标，发射器随后生成，下一evaluate首次t=end时因previous==start而crossed=false，直接complete，不再发现迟生成子实例。因此需在既有未完成窗口首次结束评估中发现目标、绑定follow、驱动终点再关闭；不新增无限迟接窗口、不改技能时长。

- GQ05：第二轮真实星束可建立CHARGE实例且retired=0，0tick恢复快照已补一次OUTPUT、立即设置length/width；首个补输出断点实例72921612-b47e-4d50-9fa6-7553932ccffb、句柄18d72fdf-03bc-425f-a3e7-4763eafd3eb2。随后PhotonChildSpawner真实创建star_beam_spell_beam子runtime。视觉仍交人工。
- 继续递归加载发现magic_circle.fx被保护拒绝：两个particle_emitter v2使用已开启rotationOverLifetime周期40t和sizeOverLifetime周期20t（首对象XYZ偏移.2/.2/.3），对应Setting/Runtime/Mixin采样已有实现，但旧保护只放行关闭状态。必须继续按模块槽和字段组合验证，不整体放开循环字段；本轮仍不计G1通过。

### 补充复测证据

- GA01修正后真实制作MCP完整30项通过（独立模型与普通FX内嵌模型保存/重开/定义/包），命名17项通过。
- 11类原生轨道在真实游戏编辑器的独立上下文内添加/复制/撤销/重做、拒绝旧引用、关闭拒绝均通过；主场景轨道仍0/0。嵌套group+工程调用保留futureGroup/futureCall、复制子条数正确，命名作用域仍拒绝工程调用。此为功能后端验证，非人工GUI视觉/点击验收。
- GQ05修复后23项资源保护JUnit通过；IDEA构建249ae31e-a452-4942-bc36-e1944f097d58成功。第三客户端PID39400实际读取10个递归FX均guard=false且FXHelper加载非null。周期getter首次求值因使用了省略lobecorp$前缀的方法名失败，未计为通过，待更正后读回。
- IDEA Data实际BUILD SUCCESSFUL，23输出written23/removed0，生成目录Git无净差异；日志ij_run__Data_18085379955445584542.log。原资源未手写修改，Data输出只读。

### 首轮修复与重建

当前子代理通过IDEA完成GB01两文件作用域解码和GQ04严格渲染字段验证；主代理完成GA01当前modelAsset完整内容/省略schema断言，以及GQ01 Photon消费端已见蓄力实例的单次输出初始化（立即参数化，下一客户端tick恢复，不补晚追踪旧输出）。保留服务端duration=0和原资源。六文件统一静态分析0错误/13警告（包括guard新增反向布尔调用样式提示1项，其余存量）；git diff --check无内容错误，node --check通过。IDEA构建880dda6e-d445-4f91-8f33-22ec219fe5de成功，编译计数null；资源保护JUnit 20/20通过（原17+新增3），后续真实游戏复测尚在进行。当前工作树备份6de34a1：refs/backups/before-photon-queen-validation-fixes-20261021；两份原始data另存queen-animation-resources-af77096.zip。技能duration=0不改，视觉交人工审查；调试暂停造成的长帧不计性能成绩。

用户明确授权先备份，协调动画任务提供最小依赖并接入独立工作区，不改实体动画/模型LOD逻辑，再构建与MCP集中验证，问题先记录再修复。修改前保存HEAD 6f168d5、索引1c37b97、工作树5943b8b，对应refs/backups/before-photon-minimal-deps-head/index/worktree-20261021。协调者的配置/consts.ts未暂存修改保持不动，未跟踪文件检查为空。

按用户最新要求使用本聊天子代理queen_minimal_dependencies只读审查，区分构建闭包与真实技能生命周期闭包，并排除旧快照中的射线起点、时长、预热和LOD增量。不再联系或采用归档AI结论，不整体恢复af77096。初步闭包为两个原有女皇类、effectId元数据以及automaticStop的既有支持；真实技能生命周期接线另按源hunk核对。

原测试存档“新的世界 (8)”已复制到本工作区run/client/saves/g1_validation_20261021，复制前后源level.dat与副本哈希一致：610B29824FD532926044BB0E1B8EAF8AC568D8ADCF17B2263672A60807BA4E25；主目录没有游戏进程。原存档不直接用于运行。

准备阶段问题T01（源码已确认，尚非游戏失败）：check-effect-tools.mjs创建StdioClientTransport时没有env，而当前SDK默认继承列表不包含MC_MCP_PORT。因此仅给测试Node进程设独立端口不能传给bridge；现有脚本也只在动画模式先ping。修复计划：显式传递已有MC_MCP_PORT，所有真实游戏模式先ping，禁用启动时自动发现，保持同一标准MCP服务和工具参数不变。先记录此问题，再修改测试入口。

子代理确认待验证风险GQ01：星束durationTicks=0，技能状态机可能同tick完成OUTPUT与RECOVERY，客户端只收到RECOVERY快照，而Photon消费端仅在OUTPUT激活输出。此时真实伤害与输出视觉可能分离；这是源码推导，尚未运行确认。不导入af77096的durationTicks(2)，保留技能时序，集中游戏轮次先验证记录，再决定Photon消费端最小修复。

依赖实现按子代理清单接入：两个女皇类全文取自af77096；LcAnimationDefinition只接effectId元数据/Builder，LcAnimationInstance只接既有automaticStop默认true及setter/结束条件，Lobecorp只加定义init。Laser/Star只接开始/激活/恢复/停止回调，并移除原粒子重复发布；命中框、伤害、射程、起点、endpoint及所有技能持续/冷却值保持当前基线。未导入客户端动画插值、姿态混合、预热、渲染器/数据票据或模型LOD增量。新依赖类中的aim/aim2和aimPose均为来源快照原实现，未重新设计。

本轮实际结果：依赖接入后IDEA构建5c0f8e02-0cc0-4a5c-84f0-424e44e36146成功，包含compileJava/compileTestJava；编译计数null，不作零警告声明。结构化JUnit确认轨道兼容5/5、工程指纹1/1、资源读取保护17/17，共23项通过。早先三个源码运行入口只有exitCode=0且无测试树，未将其计数；上述计数来自随后ide_run_tests的真实测试树。脚本node --check及65工具发现通过。

Client Photon经IDEA Debug启动，实际PID24188、mcp.port=19877；主窗GLFW_MAXIMIZED=1，使用普通无条件行断点完成环境核对并移除。已进入复制的隔离存档，MCP pong与端口19877一致。首轮运行接口通过；命名MCP17项通过。问题GA01：模型制作24项通过后fx_set_model_asset断言失败，独立模型保存/重开/定义/包导出已在本轮通过；保留失败，不降断言，待全部首轮验证完成再统一定位/修复。日志在backups/photon-minimal-deps-20261021/mcp-{runtime,animation,authoring}-round1.log。

测试准备问题GQ02：新工作区不含用户未提交的女皇FX/材质/贴图，原主目录现有源码资源也不足以直接作为完整测试来源；af77096保留这些原始文件。为避免把资源缺失当作播放语义缺陷，将从该明确版本的资源白名单建立本工作区run/client/ldlib2/assets测试副本，记录来源与哈希，不恢复混合业务快照、不修改/覆盖原资源，不导入实体模型/动画资源；副本加载后另记资源保护/依赖失败。
