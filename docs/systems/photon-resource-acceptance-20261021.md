# G1 全量资源往返与独立包验收

日期：2026-10-21。基线提交：3f935eab70ce3b9bb8aeec0bc38da0b834e0b88e。当前阶段：已按用户要求保存并停止；完整复测未完成，G1未通过。

## 已按用户要求停止（2026-10-21）

用户指令为“保存文档然后停止”。不再启动构建、Data、JUnit、游戏或后续修复；下列为停止时真实状态，G1未通过。

- 全量基线501条：496条原始结构/字节往返已通过，5条历史编辑器缓存来源缺失。
- 最新MCP制作轮18份输入：16份完整通过；light_ball.fxproj在编辑时REVISION_CONFLICT，queen_star_beam.fxproj在撤销后未恢复预期完整revision（undo_full_data）。分别记F11/F12，根因未确认、未修复，不降低断言。18份均完成自有工程关闭，cleanupFailures为空。回执：run/g1_resource_acceptance_20261021/ldlib2/assets/g1_outputs/20261021131151003_45156/project_round_trips.json。
- 第二轮20个源包/缓存包均有独立运行回执：8个通过本轮开始/运行检查，12个被F10依赖报告越界引用异常中断；20个均记录closedOwnedInstances=true。未执行新导出包的独立运行、真实缺PNG包副本负例及最终501矩阵合并，不能将20份回执算20通过。
- 生产Archive校验已在真实FML客户端执行15个正反变体，15/15通过，覆盖6类测试场景；独立证据：g1_reports/archive_runtime_39844516071200/results.json。普通包校验JUnit仍是F04启动前置受阻（0通过、1配置错误、6跳过），不能改写为JUnit通过。
- 已完成构建与静态检查、Reader26/隔离负例2/输入2项测试，证据见上节。最后F10补丁仅子代理静态检查：DependencyUtil 0错误0警告，ReaderTest 0错误7存量警告；新增越界引用unresolved负例未运行，补丁未构建/游戏复测。普通JUnit所需官方testFixtures/Gradle启动改造未实施，不新增依赖。
- F10仅将非法引用候选保留为unresolved，不放宽normalize、不读开发目录。旧字段/模型身份适配、独立包屏蔽、MCP关闭及验收脚本代码已保留；不宣称全部G1功能已验收。
- 子代理已停止且无临时对象。第三轮Node PID45156（2026-10-21T13:11:51.017Z）报告已finished且PID不存在、无残留直接子进程。客户端PID64056（2026-10-21T13:04:14.479Z）已退出，IDE Debug会话为空、保存世界并停止工作线程的日志已保留；临时行断点已删除，用户两个禁用异常断点保留。第二轮pauseOnLostFocus未在退出前单独恢复，未操作用户原游戏配置；后续复用隔离目录时先检查其选项。
- 备份、隔离输入、各轮输出和日志保留在本页记录的backups/run路径。原资源、原存档、实体动画和模型LOD逻辑未改，视觉交人工，不自动推送。

恢复入口：先读取本节与F04/F10/F11/F12回执，基于现有备份检查最后两文件差异和启动配置；统一检查/构建后复测F10，再定位两份工程revision/撤销问题，最后执行全部包及新导出包独立运行、缺依赖负例和501条矩阵汇总。恢复前不重复修改原件或整体解除文件保护。

## 备份和边界

- HEAD/index/worktree备份：refs/backups/before-photon-full-resource-{head,index,worktree}-20261021；分别3f935ea、7266ca2、9266cb8。
- 原资源只读。所有输入副本、修改、另存、重开和导出写入run/g1_resource_acceptance_20261021；源码中的assets、实体动画与模型LOD不改，不推送。
- 当前子代理仅改明确分配的资源隔离/包校验文件，主代理独占MCP和Debug；不用归档AI。代码和修改行格式全部完成后统一检查，再执行获授权的构建和集中验证。视觉交人工。

## 输入身份与覆盖

历史基线501条：source_assets144、source_packs176、pack_cache176、editor_state5。源包/缓存包分别10个物理包（9fxpack+1mfxpack），176包含包本体及包内条目，不按名称去重；相同包体SHA可以记录共享来源，但不伪造另一个原始文件已被读取。

归档af77096可核实全部144源资产、176源包条目；缓存包通过包体SHA匹配源包，另176条保持独立身份。最初git archive的15个文本换行变体曾被暂记为版本差异；直接Git原始字节已纠正：142项原始blob精确匹配，另2项恢复CRLF后精确匹配。counterattack JSON/OBJ均有准确原始blob，不能由mfxproj资产替代。所有输入必须在写副本前重新对比基线SHA，不通过校验即保留阻断。

editor_state五条原始缓存不在af77096归档，列source_unavailable，不将其计为资源运行成功，不删除或覆盖当前用户状态。当前开发目录的新增合成测试输出另记，不自动当成用户原始资产。

## 问题台账：先记录，再统一修复

- F01（源码确认）：开发环境自动源码重定向和额外run资源包可掩盖缺依赖；离线audit的developmentFallback=false不代表游戏隔离。增加默认关闭的验收属性，配合全新运行目录、唯一FX包筛选、实际挂载/sourcePackId/字节检查及负向canary。未设置属性时保留原行为，不改正式发行资源语义。
- F02（源码确认）：MCP包导出仅检查可解压与条目数量，没有逐项比较计划定义、依赖资源及增量保留的旧条目；需要在临时输出提交前补完整结构/字节和旧内容保留校验。
- F03（工具缺口）：现有制作脚本验证合成夹具，不能覆盖15组原工程、模型工程及JSON/OBJ独立版本；新增逐文件驱动和逐项结果，不把raw NBT往返、打开成功或单个模型显示计为G1通过。

## 首轮实际检查

12文件统一IDEA分析首轮0错误/22警告；局部修正新空值/资源所有权/方法引用后0错误/11警告（既有ProjectUtil/Prepared提示、SDK条件导出定位提示与一个冗余null样式提示）。IDEA构建0a8bc667-4aab-49e5-9c52-89c329ce9235成功，Bridge与脚本语法检查通过，Data BUILD SUCCESSFUL、written0/removed0。

隔离输入准备与全量raw结构/依赖字节往返JUnit2项通过：完整501条清单中496条哈希核实且结构或字节读回一致，5条历史编辑器缓存来源缺失。完整记录run/g1_resource_acceptance_20261021/g1_reports/raw_1792585597944/raw_round_trips.json。此结果不替代原生编辑器/运行验收。

F04（先记录）：包提交校验6项JUnit为0通过/2失败/4错误，根因为Native PackMetadataSection客户端codec初始化聊天组件时BuiltInRegistries未bootstrap（Not bootstrapped: minecraft:game_event），随后测试共享类初始化失败。保留原回执pack-tests-round1.json，不降低生产包完整性校验；待集中首轮结束统一修复测试启动前置。

F05（先记录，脚本准备缺陷）：counterattack工程/JSON/OBJ三份输入的输出stem重名，首个可成功输入保存后其余可能被FILE_EXISTS正确拒绝；固定输出目录也不能作为重复执行的无冲突用例。后续使用独立runId和root/kind/identity命名，不覆盖原输出。

F06（先记录，脚本准备缺陷）：FX测试使用set_parameter(name)被现有合法路径规则拒绝；应使用真实可编辑的config/transform字段，不能为测试重命名而解除参数路径保护。

F07（首轮真实MCP）：18份制作输入首轮1通过/17失败，原生FX工程存在removed_runtime_data拒绝；counterattack.mfxproj在原生加载后完整data比较返回INVALID_DATA；JSON导入完成原生编辑/历史/保存重开及定义/包/JSON/OBJ导出，OBJ导入编辑历史通过但保存撞F05。所有失败保留逐文件回执，不将准备错误与资源不兼容混为一类，先完成独立包首轮再统一修复。

F08（源码复核）：复用源基线包装包时尚未重新校验144条字节；首次CREATE_NEW由已核实输入构建，重复使用需要验证，不可假定旧包正确。失败临时PhotonGeoEffect也须在错误发生时立即关闭，不能等下一包。

F09（首轮真实运行）：独立目录、禁源码且只挂载一个FX包仍不足以隔离同ID纹理。laser和laser_spell分别被particle.png、magic_circle_1.png来源/字节断言拒绝；原生FX包优先级低于基础mod。不得放宽来源断言。后续仅在显式验收环境过滤非目标包中的分发包资源ID及.mcmeta伴随项，保留原生包顺序、引擎和实体/HUD资源；增加目标包缺共享PNG仍不可从base读取的负例。\n\n首轮20包计划已记录6包：light_ball、模型probe、queen_star_beam均原生start、持续多个世界tick、子播放错误为空且临时实例关闭；reference_star_trail定义加载失败；laser/laser_spell阻断于上述来源检查。第7包在Debug返回读取时出现“已搜集对象”，后续14包未完成，不能视为通过。逐包回执为g1_reports/round1_00至05.json及backups目录runtime-round1-00至06.json。所有本任务实例已关闭、临时行断点已删除、pauseOnLostFocus恢复，PID29900所属IDE Debug会话已STOP。\n\n## 首轮后的集中修复（尚待统一检查和复测）

- 脚本按runId/root/kind/来源摘要区分输出，保留首轮文件；FX改用合法config.duration事务，不解除路径限制。记录原始SHA、模型首次生成身份的适配回执。
- 包复用重新验证全部144源条目和元数据；失败实例即时close，关闭失败保留追踪并阻断后续包。来源失败条目先加入报告，保留实际sourcePackId/字节。
- 有限批次由客户端执行器顺序资源重载、开始和延迟收尾，不在UI线程join/get；每包至少两个真实秒窗口并记录实际世界tick。报告与运行失败分开，未执行包保持not_run。
- 独立矩阵报告器逐条合并raw、原生工程编辑及独立包回执；源文件和包内同名版本不合并，无回执不记通过，依赖闭包和视觉另外列出，报告器不自动宣告G1通过。

## 第二轮静态与测试检查

18个代码/脚本统一静态检查0错误/21警告；修正新增验收工具跨方法玩家可空提示后该文件0问题，剩余20项存量/SDK索引/冗余null样式提示。IDEA构建f94690b0-8332-48c8-8034-ae9c58378ae9成功（errors/warnings未提供数值，不写0），两份Node脚本语法检查通过。

JUnit：Reader26/26、隔离资源负例2/2、全量输入/往返2/2通过。包提交6项未执行：新增BeforeAll在SharedConstants初始化时需要FMLLoader，普通JUnit无当前Loader（There is no current FML Loader），1类配置错误、6项skip。继续记录为F04启动前置未解决，回执tests-round2.json；不得将其计为包校验通过或降低生产codec。

第二轮MCP：18输入中模型工程/独立JSON/OBJ共3项完整通过，15项FX全部打开后由脚本非JSON Pointer路径config.duration拒绝，F06仍为脚本问题；API契约实际使用/config/duration。全部自建工程已逐项effect_close，不触碰用户工程。回执位于g1_outputs/20261021130656514_21612/project_round_trips.json。随后仅修脚本路径，不放宽API路径规则。

F10（第二轮包检查，先记录）：新增递归依赖报告在shader图的历史file(./../../src/main/resources/assets/...)引用上抛逻辑路径异常，先于本包运行检查而中断。不能为验收读取开发目录或放宽越界保护；修复应将不可信引用保留为unresolved并继续生成报告，运行/来源断言与依赖结论分开。

## 验收阶段与证据规则

每条记录区分staging、原始格式结构往返、原生工程修改/历史/另存重开、定义/包导出、独立运行；未执行保持not_run。未知字段、UUID/引用、实际来源和允许的编码差异分别核对。所有失败保留原始回执，先完成一轮再统一修复，不降低断言。

独立运行使用全新工作目录，禁用开发源码并筛选唯一完整fxpack ID。每次切换执行原生包仓库和资源重载，旧实例先关闭；检查只有目标FX包、目标条目的sourcePackId及原始字节符合，原生内置依赖显式登记。不存在于目标包、但在另一源/包中的FX必须不能解析。不能只靠挂载或离线closed=true宣告通过。
