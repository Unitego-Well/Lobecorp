# Horde 问题汇总

更新日期：2026-10-24。范围仅为 `.workspace/lobecorp_horde`、`codex/horde` 当前完整工作区，包含原有暂存和未暂存实现。本页是唯一的问题清单；已完成且已验证的事项移至[修改记录](changes.md)，进度见[总览](horde-index.md)，原始基准见[BenchMod](benchmod.md)。

前轮已执行IDEA编译、20项Horde回归、三轮Data、基准制品编译、Minecraft MCP+Debug及两轮当时代码的Sodium客户端复测。测试通过不代表全部问题解决。用户要求不再主动询问：以下未定事项记录并沿用保守替代，只有用户要求时再提问，不将等待确认伪装成已解决。

## 本次追加需求与证据级别

2026-10-24用户补充：分割必须像水一样平滑、连续；单个生物范围缩小，楼上/高空生物不在地面制造缺口；间距更疏，左右移动更大且更随机；保留基础速度，只随机加减速；单目标真实参战上限10；事件生成的真实清道夫不参与分割。本次明确不启动游戏，以下代码变更尚无本次实机验收。

当前写入：默认纵横间距1.8格；GPU个体相位、频率与幅度变化，纵向扰动最大1.8格、左右最大1.5格，基础速度来源不变；单体AABB水平外扩至少3格，较大体型保留三倍余量、Y保持实际高度，再按1格外取整，不再使用战斗余量8格或8/16/32格扩张实际缺口；渲染CPU提交六整数目标和稳定身份，GPU持久保存中心/半尺寸/速度并进行阻尼更新、软方向和多区域连续上包络；按目标Y区间与当前地面到实体高度区间判断，让高处目标影响归零。事件成员继续复用已有同步附件排除，不把全部普通清道夫排除。

“上限10”按每目标有效真实参战成员与预约合计解释：初始、死亡增援、定时增援、预约、转派均受限制；旧超额成员加载后按既有淡出流程退出，不瞬间销毁。新建事件默认从1逐步增援至10；旧事件已有快照不强制改写为新间距或增援规则。该解释及旧数据边界保留记录，不主动询问。

证据标记：**实测缺陷**有运行报告；**源码风险**仅确认实现路径；**待验收**表示尚无相应运行证据；**人工边界**表示已有取舍。前轮1.2格、旧流场数据仅作历史对照，不能当作本轮性能结果。下列验收标准是待执行清单，不是已运行结果。

| 优先级 | 问题 | 当前证据与影响 |
| --- | --- | --- |
| P1 | H24 Iris大负载GPU转接过慢 | 历史10000实例约14.71FPS；合批/GPU帧缓存已写入，本轮无性能数据 |
| P1 | H15 连续分割、小范围与高度隔离 | 三级索引、同源地形平滑及3格净空已写入；连续性与高度隔离待实机 |
| P2 | H11 首次模型准备卡顿 | 同步烘焙仍在首次绘制准备路径，历史稳定阶段最大555ms |
| P2 | H07、H16 每tick扫描及完整性能证据 | 客户端固定场景已重复采样，真实多事件/多玩家服务器压力未覆盖 |
| P2 | H14 玩法、持久化与联机链路 | 一tick清理已实测，完整配额/增援/转派/重连仍未验收 |
| P2 | H26 事件尸体200tick与清扫粒子 | 来源期限及客户端潮内多区分级粒子已写入；动态验收未执行 |
| P2 | H25 独立服务端基准启动受阻 | 客户端依赖ReRetro误载，缺少MultiBufferSource；无server测量结果 |
| P2 | H23 指挥家退出异常 | 两轮新基准收尾再次出现鼠标恢复异常，Horde资源已提前清理 |
| P2 | H03 同域规则归属 | 保留首次建档规则，未擅自改变共享配额玩法 |
| P3 | H10、H19 图形API与内部依赖 | RGBA8重复求值、GeckoLib内部入口及公开协议保留字段 |
| P3 | H17、H20 历史证据及提交归属 | 缺失旧快照不伪造恢复；混合源码不能连带提交用户改动 |
| 人工边界 | H21 需求取舍 | 保留原有资源准备、出生预算及旧事件快照；不主动提问 |

## 2026-10-24追加优化：代码与无游戏检查完成

- H15：GPU生成32格粗候选位图，密度超过8再细分16/8格；六整数实际区域不扩大到分档格。每级最多16MiB、三档最多约48MiB，位图包含全部活跃区域；容量不足退回粗档，域外或粗档不足时完整查询，不截断候选或减少浪潮。CPU只在目标版本/活跃数变化时准备整数域与容量，不构建候选表。
- H15：地形样本高度/速度及绕行位移/速度常驻GPU，与分割共享hordeGapPush/hordeDamped。包围盒水平外扩至少3格，保留较大体型所需三倍余量；GPU再加完整动画的模型半径，并用原始区域安全遮罩避免过渡期穿入净空。Y不扩大，避免高处目标投影到地面。突发目标允许局部隐藏，平滑观感仍未验收。
- H15：同层、非退场、靠近既有目标的事件成员并入该事件战斗包围，不单独开缺口；始终以原目标种子判断距离，防止成员链式把包围扩到整片潮。服务器战斗保护/清理规则未改，完整联机仍属H14。
- 整潮抖动/消失：公共时钟接收header时连续校正，最大校正速率0.25tick/tick、外推上界40tick；GPU异常不再全局clear或等待数秒，只限流日志、保留有效资源/历史并下一帧重试。源码确认这些旧路径可能造成跳变/停显，但没有实机确认用户现象的唯一根因；GeckoLib缓存getter返回稳定引用，未将它误判为每帧换代。
- H24：在GPU按资源代际一次生成Iris位置/法线/切线帧缓存，每顶点每动画帧10字，逐实例采样与旋转；没有新增CPU切线或顶点处理。沿用既有工作集合批、中心UV和材质合并，保留数量、LOD、发光、20FPS动画与原资源。复制总字节、驱动同步成本和整体帧率没有新实测，H24不关闭。
- H26：原版环境粒子只在客户端发射，默认每tick尝试近3/中2/远1个不同区域，选区格8/16/32、距离带0～8/8～16/16～32，同时受默认24格particleRange限制。每区烟雾/碎屑/刀光为2/4/2、1/2/1、1/1/1，清扫提示最多另补1区；从实际提交的潮内行列随机取点，检查头尾、加载、视锥、遮挡和目标/模型安全包围，每粒再按最终出生坐标检查距离/视锥和准确相机射线，避免烟雾高度偏移越界。原版引擎仍管理粒子生命周期，不宣称全GPU模拟，不改变用户粒子设置。
- 统计增加不随帧/捕获重置的累计失败序号，基准在稳定/预热/测量/验证阶段检查，避免瞬时失败被下一帧清零掩盖；10项统计回归包含跨帧序号保留，真实基准未运行。统计扩展terrain/spatial/iris_prepare，记录spatial_words、spatial_levels、iris_frame_words及最近tick的粒子选区/提交量。GPU轮换样本仍非全帧总和，忙时缺测为null；索引字数不是命中次数，粒子逐帧值不能累计为独立出生次数。
- 实际检查：13份Java、9份GLSL错误诊断均完成且0错误；必要引用核对为updateGaps 1处、includeCombat 4处、HordeIrisAnimation 7处，未遗留旧签名。编译通过，新空间/时钟/间距/帧容量9项、区域4项、既有缓存/统计10项共23/23通过。Data退出0，23份实际资源written=0，29份含缓存文件SHA256不变。GPU拥有者误报按精确缓存归属处理；HordeClient既有2项空值提示保留。
- 用户随后取消游戏测试：没有启动、重启、接管或停止游戏，没有MC MCP、Debug或BenchMod Client/Server运行；没有本轮光影/无光影FPS、GPU执行时间、画面或抖动验收，历史数据只保留原含义。
- 备份与审阅：C:/Users/wang_/AppData/Local/Temp/horde-spatial-smooth-20261024-124157-e4bdef48，含开始快照、旧原始基准归档、生成哈希、编译/Data日志与checks.json。原5项暂存raw对象未变，源码混合WIP按H20不整份提交；仅独立进度文档可提交，不自动推送。

## DS报告复核（2026-10-24，只读核对，不重跑）

原报告保留为[测试前诊断](horde-pretest-optimization.md)、[测试后报告](horde-posttest-optimization.md)，它们是特定代码快照的证据，不作为另一份长期问题清单，也不把报告内实施建议视为用户新增授权。

- 已核对现存原始summary：client-1792810299088，光影轮整体FAILED；64真实实体PASSED，均值11.052ms；10000实例夹具TIMED_OUT，failure为Phase STABILIZE exceeded 2400 ticks，workloadCorrect=false，未进入MEASURE。2065个STABILIZE帧均值57.897ms、P95 93.565ms，不能当作正式测量或可见性通过。客户端日志确认实际加载ComplementaryReimagined_r5.9.3。
- DS最初无光影轮与追加光影轮不是同一次运行；当前raw-results已对应光影轮，不能把现存文件再次当成无光影原始产物。报告记录并发编辑与负载变化，因此跨轮差值不是单变量因果证据，不能直接宣称代码回退2.7倍。
- H24机制校正：本地GlCommandEncoder确实绑定GL_PIXEL_PACK_BUFFER并调用glReadPixels，随后将同一个缓冲作为顶点输入；当前创建usage没有CPU映射位，BufferStorage不会为它建立持久CPU映射。由此只能确认驱动管理的像素到缓冲路径及资源依赖，不能证明必经主机内存、再次上传或发生固定次数的CPU同步。
- [Khronos ARB_pixel_buffer_object规范](https://raw.githubusercontent.com/KhronosGroup/OpenGL-Registry/main/extensions/ARB/ARB_pixel_buffer_object.txt)明确给出渲染结果经glReadPixels写入缓冲后直接作为顶点源的用途，实际驻留位置由实现决定。DS“584MB PCIe往返＋93次固定延迟解释全部耗时”是未证实模型；此前将物理传输保证为纯GPU复制也过于绝对，准确边界是“应用未CPU映射/取数，驱动驻留及同步成本需测”。逐批复用同一附件/缓冲仍有依赖与性能风险，H24.1分阶段计量仍优先。
- H15部分条目过时或读法有误：当前已是体型3倍宽深，非0.5格余量；updateGaps明确含!previousRegions.equals(regions)分支，不能说没有旧端点收敛触发；当前种子回绕为1024行，不是512行。整表两端点、一tick过渡、零尺寸端点位置及反向流场抵消仍是合理的待优化/待验收项，不因这些校正关闭H15。
- H15建议不能直接照搬：CPU预混合区域违背既定GPU处理约束；只把过渡时长拉长、只去偏置、只求和后饱和，均不能单独证明持续运动、稳定分流和不穿透。须同时设计GPU历史/区域身份、连续约束与验收曲线；不擅自降低LOD、数量、发光或改规则择主。
- H07新增优先候选：源码确认HordeLightField.prepare每次构造distinct块列表，并在逻辑刻或块集合变化时重建requiredSections；后者逐区段创建Section，槽位/排序/更新仍有全量遍历。DS的JFR热点与此路径吻合，支持优先测量缓存/分配；具体百分比和“翻倍完全由它造成”未在本轮重新计算，不能作为已闭合因果。
- 已核对Minecraft源码：只有framerateLimit<260才执行帧率限制，本版260表示不限帧。DS对此校正成立；旧“上限260”表述不能解释历史高于260的计数。CPU提交、GPU执行和帧间隔有重叠及不同窗口，不能简单相加或相减来分摊成本。
- DS本轮未成功启动的规则测试不覆盖或推翻此前已记录的其他测试运行；源码中HordeParticipant与HordeRules的已有直接测试也使“全部零JUnit”表述过宽。HordeRuntime/存档/同步完整集成覆盖仍不足，继续保留H14。
- 本轮未改代码、未启动游戏、未构建或重跑测试。两份报告及当前仍存在的光影raw-results、专服bundle已归档至C:/Users/wang_/AppData/Local/Temp/horde-ds-review-20261024-d2f64f03；覆盖前共20文件约26.3MB。该归档不代表恢复了已被后续运行覆盖的无光影原始产物。

建议执行次序：先将batches/draws/gpuCopy/区域数与各阶段计时落盘，再在固定快照下处理光照集合缓存、GPU连续分割和Iris批次依赖；H23退出与H25类路径单独修复和验收。所有未完成项仍按原问题编号维护。

## H24 [P1／部分修复] Iris可见性已恢复，大负载性能仍不合格

根因一：自定义horde_gpu管线没有Iris覆盖，日志明确Missing program。根因二：Iris在世界渲染期间将ENTITY从36字节扩展为56字节，旧转接按36字节写入，造成后续顶点属性错位。

当前按实际管线选择9或14个32位字；扩展布局补中性实体标识、面中心UV及切线，均在GPU生成。RGBA8附件经CommandEncoder.copyTextureToBuffer直接复制至GPU顶点缓冲，再使用已有Iris映射的ENTITY_CUTOUT/EYES；无CPU映射、逐单位运动或OpenGL旁路。100及10000实例已实际可见，夜间虚拟遮罩和真实清道夫发光已取证。首次GLSL导入格式与保留词错误也已修正。

剩余严重问题：RTX3060 Laptop、2560×1494、Iris1.11.4/ComplementaryReimagined_r5.9.3、前台最大化、Vsync开启/上限120、限帧原因NONE；10000实例30秒442帧，间隔均值67.98ms/P95 85.74ms，GPU均值67.89ms，约14.71FPS；每帧GPU复制292192256字节、186次主体/发光绘制。不能以100个负载约115FPS或数量计数证明大负载合格。单工作集约32MiB只限制工作集，不自动减量。

下一步须减少顶点展开/复制与重复管线工作，并独立测量展开、复制和材质绘制；当前26.1.2图形API无浮点颜色附件，不能未经设计改用CPU逐单位变换、低精度位置、LOD或关闭发光掩盖问题。原始数据与截图在本轮备份iris-layout56-10000.json及iris-layout56-*.png。任意光影包、阴影和透明排序仍未验收。

### H24 详细优化任务

| 编号 | 代码位置／证据 | 需要优化或补证 | 完成条件 |
| --- | --- | --- | --- |
| H24.1 | [IrisVertices.draw](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java)；前轮292192256字节/帧 | 阶段统计已接入，用户取消实机后尚未采样；需确认展开/复制/主体/发光占比，不能把旧67.89ms全部归因复制 | 原始分阶段GPU计时、总帧时间及环境能互相核对；无计时查询错误 |
| H24.2 | 同上；186次主体/发光绘制 | 工作集合批与连续材质合并已写入，256小批限制已取消；仍需实测展开、复制与提交成本 | 同实例数/模型/贴图/发光下，展开量、复制字节或提交成本有可重复下降，未漏画或减量 |
| H24.3 | [horde_iris_vertices.fsh](../../src/main/resources/assets/lobecorp/shaders/core/horde_iris_vertices.fsh) | 中心UV缓存、GPU一次位置/法线/切线帧缓存已写入；正确性及实际取样成本下降仍需光影验收 | 同一动画帧、法线、UV和发光外观保持一致；资源重载后正确；三轮性能记录可比较 |
| H24.4 | [HordeIrisShadows](../../src/main/java/org/unitego/lobecorp/client/horde/HordeIrisShadows.java) | 复核光影开关/重载时36与56字节布局切换、输出目标及深度方向 | 无错位、透明幽灵、残留顶点或资源泄漏；Iris关闭也保持正确 |
| H24.5 | 前轮仅一个光影包可见性证据 | 补不同视角、夜间、遮挡、更多包及版本的兼容矩阵；阴影仍未实现，不包装为已支持 | 支持范围有截图与版本记录，未支持范围明确；GPU预算另行记录，14.71FPS不能只因可见而关闭问题 |

## H15 [P1／代码已实现待实机] 三级索引、连续分割与地形绕障

当前GPU持久保存区域中心/半尺寸/速度，地形高度与绕行也有持续状态；两者共享临界阻尼和连续排斥函数，不在CPU求逐单位插值。水平净空取体型三倍余量与3格的较大值，再计入模型半径；Y保持真实层次。事件成员不单独分割，只能扩展已有、同层且近处的目标战斗包围。以上为源码状态和无游戏回归证据，不代表用户画面已验收。

剩余为GPU/画面验证及粗高度局限：薄墙、屋檐、多层拓扑仍不能完整表达；64格位移上界和旧事件原采样范围保留。新目标/瞬移的原始区域安全遮罩可能局部隐藏，不能把这个净空优先取舍描述为无闪动。用户取消实机后，不关闭这些待验收项。

| 编号 | 当前入口 | 已写入与仍须验证 |
| --- | --- | --- |
| H15.1 | [RegionBox.forEntity/includeCombat](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) | 至少3格水平外扩、保留较大体型三倍余量、Y不扩大；近处同层成员并入已有战斗包围。规则回归通过，动态移动/不同体型观感待验收 |
| H15.2 | [共享流场](../../src/main/resources/assets/lobecorp/shaders/include/horde_flow.glsl)、[location](../../src/main/resources/assets/lobecorp/shaders/include/horde_motion.glsl) | 同侧连续上包络、前后切线朝向；横穿、反向、重叠目标交接与加速度连续性仍需实机 |
| H15.3 | [HordeRegionState](../../src/main/java/org/unitego/lobecorp/client/horde/HordeRegionState.java) | 六整数目标+稳定代次，GPU持久阻尼/速度、退场40tick和重入身份保留；身份/复用回归通过，30/60/144FPS硬件验收未执行 |
| H15.4 | 同一GLSL与[HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java) | 真实目标Y与当前地面到实体高度判断；5/16/32格高处不误分割、地形更新后的层次隔离仍需画面 |
| H15.5 | [isGapTarget/HordeParticipant](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) | 事件成员不单独创建缺口，仅参与其已有目标战斗包围；首次追踪、重进范围、重载和普通来源区别仍待联机 |
| H15.6 | [HordeTerrainState](../../src/main/java/org/unitego/lobecorp/client/horde/HordeTerrainState.java)、[地形shader](../../src/main/resources/assets/lobecorp/shaders/core/horde_terrain_update.fsh) | 与分割相同的GPU阻尼和排斥，地形高度/绕行持续更新；粗采样不能承诺薄墙、屋檐、坑洞、窄道与多层精确碰撞 |
| H15.7 | movingLocation与[采样覆盖](../../src/main/java/org/unitego/lobecorp/util/horde/HordeSamplingUtil.java) | 间距1.8、横摆最大1.5、纵向扰动1.8，基础速度和20FPS动画不变；批次/视角变化后的随机身份稳定性待验收 |
| H15.8 | [HordeSpatialIndex](../../src/main/java/org/unitego/lobecorp/client/horde/HordeSpatialIndex.java) | GPU 32/16/8位图与密度细分，容量/域外完整回退；容量、远坐标、部分字回归通过，GPU候选完整性和实际加速仍未验收 |
| H15.9 | [HordeRenderClock](../../src/main/java/org/unitego/lobecorp/client/horde/HordeRenderClock.java)、GPU绘制异常路径 | 时钟接续/单调回归通过，异常保留缓存并下一帧重试；整潮抖动/停显真实触发条件及恢复画面仍待验证 |

## H11 [P2／未解决] 首次模型烘焙仍可能卡顿

HordeGpuRenderer.prepare/geometry同步调用HordeMeshCache.prepare，首次解析、完整帧烘焙与上传仍位于绘制准备路径。基线稳定阶段最大帧555ms；当前两轮固定场景稳定阶段最大224/162ms，不能证明首次卡顿已消除。

后台线程会触及共享GeoBone/BoneSnapshot临时挂载；挪到tick也不能消除同步工作。需设计资源预准备或分阶段上传，并保持既有动画/模型和资源代际语义，独立记录首次显示与重载耗时。

### H11 详细优化任务

| 编号 | 代码位置 | 需要优化或补证 | 完成条件 |
| --- | --- | --- | --- |
| H11.1 | [HordeMeshCache.prepare](../../src/main/java/org/unitego/lobecorp/client/horde/HordeMeshCache.java) | 分开记录资源解析、骨骼采样、帧数组分配和上传；不能直接认定历史555ms全是烘焙 | 冷启动、首个事件、第二事件、重载各有阶段耗时和最大帧 |
| H11.2 | [HordeGpuRenderer.geometry](../../src/main/java/org/unitego/lobecorp/client/horde/HordeGpuRenderer.java) | 评估资源预准备与有界分阶段上传；不能只把同步工作挪到tick | 首次显示延迟与单帧阻塞都可测；准备期间无半成品绘制或永久failed |
| H11.3 | BoneSnapshot及共享模型挂载 | 后台化前确认独占性、资源代际和取消机制；保持20FPS原动画 | 多事件共用模型、资源重载中断、退出后无共享状态污染和悬挂任务 |

## H07 [P2／待压力测量] 每tick玩家模拟范围扫描的CPU成本

旧节点轮转和scanTicks/scanNodesPerTick设置已移除，当前每tick按玩家所在区块的模拟距离范围收集已加载实体，UUID去重，成员存活/目标、区域及生成请求不分区域错峰；无活动事件时跳过查询。出生安全工作预算和原有重试/宽限仍保留。

剩余：模拟范围包含全建筑高度，多玩家重叠与高实体密度仍有查询、去重及生命周期遍历成本；不能用隔离虚拟渲染夹具证明真实HordeRuntime成本。增加实体硬上限会改变即时发现要求，当前不截断、不暗中限量；需结合H16取得实体数、玩家数和server tick证据。

### H07 详细优化任务

| 编号 | 代码位置 | 需要优化或补证 | 完成条件 |
| --- | --- | --- | --- |
| H07.1 | [HordeRuntime.scan/clearDebris](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) | 测玩家范围实体查询、重复覆盖和UUID去重；评估复用查询结果，保留每tick语义 | 单/多玩家、重叠/分离范围和高实体数的server tick P95/P99、分配及查询数完整 |
| H07.2 | [ClientEvent.updateGaps](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java)、EventData.updateRegions | 两份区域快照增加整数上传和临时集合，量化实际成本；避免为未变化区域反复重建 | 记录目标数、区域数、上传字节/次数、CPU分配；静止目标稳定后不持续上传 |
| H07.3 | [GLSL区域循环](../../src/main/resources/assets/lobecorp/shaders/include/horde_motion.glsl) | 当前每次location仍遍历区域；研究GPU空间索引/分档，不能重新扩大单体轮廓 | 0/1/16/64及更高目标数成本曲线，跨桶不漏目标；32/16/8若复用只作为索引而非大缺口 |

## H16 [P2／部分验证] 多事件、服务器与分阶段性能证据不足

当前两轮Sodium无光影夹具均PASSED：64实体间隔均值4.84/4.31ms，10000虚拟实例2.41/2.30ms，后者GameRenderer GPU均值2.08/1.93ms。此前H22的12.04ms真实实体回退未复现，已从活动缺陷中移除；单轮扰动原因未获因果证明。

剩余：夹具截图大量天空，提交10000不等于全数可见。BenchMod帧间隔与20/30秒GameRenderer CPU/GPU捕获的窗口和事件口径不同；本版本配置值260表示不限帧；采样帧数仍不构成稳定FPS承诺。64实体渲染线程分配均值约1.70/1.83MB每帧，尚无本轮之前的同口径分配基线，不归因Horde。

需分别覆盖真实事件、长线路、多目标/玩家、多事件、显式上限与无限配置，拆出extract/prepare/motion/draw/复制/显存和server tick；H24是已实测失败项，独立服务端当前受H25阻断。不得以减少实体、关闭发光或放宽环境门禁制造通过。

### H16 详细补证任务

| 编号 | 任务 | 记录内容与判定 |
| --- | --- | --- |
| H16.1 | 为本轮1.8格、新随机运动与新分割建立新基线 | 固定代码/未提交差异、配置、显卡/驱动、分辨率、相机、可见比例、实体/区域数量；旧1.2报告保留为历史 |
| H16.2 | 分离指标口径 | 同时保留BenchMod帧间隔与GameRenderer CPU/GPU原始窗口；提取、tick、swap/限帧、分配和GPU复制独立命名，禁止互相替代 |
| H16.3 | 单变量、多轮对照 | 每组至少三轮；改变一项算法时保留同场景对照，记录首次/稳态P50/P95/P99/最大值；不以平均值掩盖卡顿 |
| H16.4 | 场景覆盖与资源上界 | 0/100/1000/10000虚拟实例、64真实实体、单/多事件、目标数与视距变化、显式上限/-1、重载和退出；记录峰值显存与资源回落 |

本次用户明确不启动游戏，上述客户端、服务端、GPU与视觉场景均不在本轮执行；当前只能关闭静态/规则检查已覆盖的子项。

## H14 [P2／部分验证] 完整玩法、存档和联机仍缺验收

已实测未加载远处事件创建、新事件spacing=1.2、一tick删除区域外物品/清道夫尸体、保留战斗保护物品和普通牛，目标/成员1→2。调试Gizmo及配置迁移/旧值比对也已有证据，不再列为待办。

剩余：完整配额1→2→10、死亡增援、转派、取消/超时、区块卸载与重载、关服恢复、成员归属及多玩家订阅。本轮21项规则/区域回归与前轮单机Debug不能覆盖这些链路；旧Region辅助类的连续性测试也不能证明新GPU过渡画面通过。完整build历史Photon资源/基线失败未在本轮修复，不声称全项目测试通过；每日构建编号策略保留，不为配置缓存复用旧编号。

### H14 详细验收任务

| 编号 | 场景与入口 | 通过条件 |
| --- | --- | --- |
| H14.1 | [配额/预约/转派](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java)；本轮新增上限10 | 新规则初始1，定时/死亡增援逐步至10；第11个有效成员或预约不建立；多事件共享、同时转派、请求取消不穿透上限 |
| H14.2 | [HordeSavedData](../../src/main/java/org/unitego/lobecorp/world/horde/HordeSavedData.java)、HordeTargetAssignment.load | 卸载/重载、退出/恢复、无效规则、旧超额成员淡出均保持归属和配额；不把区块卸载算作死亡增援 |
| H14.3 | [HordeSync](../../src/main/java/org/unitego/lobecorp/world/horde/network/HordeSync.java)与同步附件 | 多玩家进出/维度切换、丢页重发、完整快照及首次实体追踪正确；entity_height正确；事件成员不进入分割对象 |

## H26 [P2／已实现待验收] 事件尸体200tick与近处清扫粒子

当前范围：单体水平外扩至少3格并保留较大体型三倍余量，再按1格向外取整；Y不扩大，保留GPU连续过渡及高处隔离。事件生成清道夫死亡、以及它作为伤害来源击杀Mob时，在服务端死亡事件记录来源；创建EntityCorpse时设绝对世界期限为当前时间加200tick，并保存HordeCleanupAt。事件结束不会取消该期限；区块卸载后重载沿用原期限，不重新计满10秒。缺少期限但已有事件附件的旧尸体首次tick补200tick安全保留期，无法识别来源的旧普通尸体不猜测删除。

战斗保护优先：到期尸体仍在战斗保护AABB内时不由事件清理，移出保护后才清理；普通自然腐烂或正常战斗伤害不属于该自动清理。原版死亡过程中的清道夫本体不再直接被区域discard，确保能够正常创建尸体。未过期的事件尸体不被沿路清理抢先删除；路径掉落物在非战斗区域仍按既有规则处理。

自动审批拒绝了把isDebris扩大到所有EntityCorpse，理由是可能不可逆删除未授权的普通尸体。已采用更安全的明确来源/期限筛选，仅处理事件成员及其击杀产生的尸体；普通无来源尸体不扩大清除。没有通过其他路径执行被拒绝的全体普通尸体删除，也不主动询问扩大授权。

粒子实现：保留particleIntervalTicks默认1及particleRange默认24格（1～32），每次在潮内尝试近3/中2/远1个不同区域，烟雾/碎屑/刀光分级批量发射，基础频率详见上方追加实现。复用CAMPFIRE_COSY_SMOKE、BLOCK粒子和已有短/长/双斩资源。只选择已加载、玩家范围内、视锥可见且相机射线无遮挡的位置；从实际提交行列随机采样潮内位置，检查波头/波尾、目标安全包围与模型半径，不再只在块中心发射。实际清扫位置通过原版粒子协议的horde_cleanup发射器通知，客户端按格去重、最多保留一秒旧提示并优先发射；该发射器使用registerSpecial，不创建贴图JSON、不复制造型或战斗伤害。

GPU边界：主浪潮分割、位移和动画仍在GPU；本轮指定的原版烟雾/碎屑及原有刀光复用原版客户端粒子引擎，出生筛选、生命周期与碰撞并非全GPU模拟。没有把原版GPU绘制等同于全GPU生命周期。严格仅CPU提交区域的完整粒子替代仍未实现，作为人工边界记录。

| 编号 | 入口 | 待验收／下一步 |
| --- | --- | --- |
| H26.1 | [HordeCleanupUtil](../../src/main/java/org/unitego/lobecorp/util/horde/HordeCleanupUtil.java)、[HordeGameplayEvents](../../src/main/java/org/unitego/lobecorp/events/HordeGameplayEvents.java) | 验证事件成员自身、直接/间接击杀、非事件来源、死亡取消及原尸体创建链；普通来源不误删 |
| H26.2 | [EntityCorpse](../../src/main/java/org/unitego/lobecorp/world/entity/EntityCorpse.java) | 验证创建后199/200tick边界、保存重载、未加载期限、事件已结束及旧标记尸体；确认保留期不被路线扫描提前截断 |
| H26.3 | [HordeRuntime.clearDebris/protectedFromCleanup](../../src/main/java/org/unitego/lobecorp/world/horde/HordeRuntime.java) | 战斗区域中到期尸体与掉落物仍保留，移出保护后清理；同时多目标/成员分配不漏保护 |
| H26.4 | [HordeAmbient](../../src/main/java/org/unitego/lobecorp/client/horde/HordeAmbient.java) | 正常频率、不同间隔、正面/背面/遮挡/远处/未加载与暂停测试；三类粒子可见且没有超过配置范围生成 |
| H26.5 | [粒子注册](../../src/main/java/org/unitego/lobecorp/registry/particle/client/RegisterParticleProviders.java) | 验证发射器协议、清扫刀光位置、多人追踪、提示合并及过期；对比主体CPU/GPU和粒子分配成本 |
| H26.6 | [RegionBox.forEntity](../../src/main/java/org/unitego/lobecorp/client/horde/HordeClient.java) | 至少3格净空/大体型余量与真实Y、不同体型/负坐标、移动目标平滑合流；GPU粒子替代不计为已完成 |

本轮IDEA错误诊断11份文件均为零，BenchMod Compile退出0，HordeCleanupTest3/3、HordeRegionTest4/4通过；Data退出0，23份实际资源仅中英文语言两份更新，另有两份生成缓存变化。本次未启动、重启、接管游戏，未运行MC MCP、Debug或真实基准；上述动态验收不因静态诊断/规则回归通过而关闭。

## H25 [P2／环境受阻] BenchMod Server未能进入测量

本轮IDEA执行BenchMod Server（2cd82417-bd40-4a0c-870d-9feef175646e）退出1，无server summary。专服启动时ReRetro Damage Indicators加载客户端MultiBufferSource失败，Photon/LDLib反射同时记录Particle类缺失。当前依赖由既有implementation/internalTestMods引入，未擅自移除第三方Mod或改变公共依赖。

需为专服基准建立正确的客户端依赖隔离，再运行64实体AI与真实事件压力场景；该失败不是Horde服务端性能结论。日志和崩溃报告保存在本轮备份server-start-failure。

### H25 解阻与验证任务

1. 通过现有Gradle/运行配置核对专服类路径，隔离仅客户端的ReRetro等依赖，不直接删除公共依赖或掩盖类加载失败。
2. 核对Photon/LDLib反射缺类是否仍阻断，保存修正前后加载日志；真实进入测量并生成server summary后，才能报告服务端场景结果。
3. 先运行64真实实体AI基线，再补真实Horde事件、每tick扫描和多玩家负载；AI场景不能代替事件调度成本。

## H23 [P2／范围外记录] 指挥家退出时鼠标恢复异常

最新两轮客户端基准退出0、场景PASSED，但收尾再次出现Trying to return to in-game GUI during disconnection：Minecraft.setScreen → MouseHandler.grabMouse → ConductorControls.exit:518 → clearSession:401 → ClientRuntimeEvents.onClientLevelUnload；随后有异步任务拒绝。

本轮已将HordeClient.clear放在其他系统会话恢复之前，避免阻断本系统GPU释放；ConductorControls不属于本轮Horde修改，未改其逻辑。需由对应系统修复断开过程鼠标恢复，不能将正常测量结论扩展为退出无异常。

### H23 后续处理与验收

入口：[ConductorControls.exit/clearSession](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorControls.java)与[卸载事件](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)。需要在对应系统授权范围内处理“断开时恢复鼠标/界面”的顺序；不能只吞异常。验收包含正常退世界、退出客户端、基准自动退出及指挥模式退出；无GUI重入异常、无Horde GPU资源残留，且不改变用户原有鼠标状态。

## H03 [P2／规则异议] 同域事件规则与配额归属

多个同域事件规则快照不同时，目标保留首次建档规则。已修复无效/空闲目标回收及直接assignment查表；剩余是共享配额、冷却与持久化择主语义。当前沿用已有规则，不重置仍受另一事件覆盖的目标；将此取舍记录为人工边界，不主动提问。

### H03 人工规则与后续验证

当前仍采用首次建档规则，单目标硬上限10不受后续事件提高。若后续要改变规则择主，先把“重叠事件不同配额/冷却、某事件结束、存档重载”的期望写成规则表，再实现和验证；本轮不默认为较大配额优先，也不通过重建目标绕过冷却。此项不是已确认性能缺陷。

## H10 [P3／API约束] RGBA8运动缓存重复求值

当前运动缓存每实例5个原始float值，普通location约6次求值；已减少数据量但不同输出像素仍各自计算位置。26.1.2 TextureFormat无可用浮点颜色附件，跨片元共享或更高效展开需要受支持的多输出/新管线方案。无光影GPU采样并不证明该重复成本为零；与H24优化一并评估。

### H10 后续优化任务

- 定量测motion pass随单位数/区域数增长的耗时；分别评估位置、前后切线与光照的重复求值。源码约6次location不是实测GPU预算。
- 先核对当前Blaze3D支持的输出、缓存和材质能力，再设计共享求值；只接受保持float32位置、发光和既有动画的方案。任何新旁路或后端绑定都应作为独立取舍记录，不能伪称纯接口替换。

## H19 [P3／接口与依赖异议] 公开协议及GeckoLib内部入口

旧扫描/过渡配置已移除，快照保留字段不再代表当前扫描间隔。公开snapshot/revision/scope/clock等字段不能因当前无内部调用就删除。GeckoLib5.5.2 apply/cleanup仍是骨骼临时挂载路径，IDEA标记Internal，无经验证的稳定等价替代；固定版本与清理语义保留，升级时专项处理。Iris转接还直接核对IrisVertexFormats扩展布局，属于版本相关内部入口；已验证1.11.4，不视为所有版本兼容。GPU渲染器本轮全诊断0问题，缓存拥有者误报已按精确生命周期处理；HordeClient保留既有2项空值提示。仍不应为消除借用提示立即close共享对象。每目标规则副本与每tick存档脏标记优化还需尊重H03及持久化语义。

### H19 维护与资源优化任务

- 公开字段、快照和保留计时字段先做调用/兼容边界清单，不能以“本项目未调用”直接删除；新增实体高度与区域双快照的布局需有生产/夹具一致性检查。
- 整理GeckoLib/Iris内部入口的版本表及失配行为；分别核对事件移除、世界退出、资源重载、准备异常时GPU资源各释放一次。IDE缓存资源提示不是泄漏定论，不能在借用位置立即关闭共享对象。

## H17 [P3／历史证据缺口] 旧Debug与配置恢复无法完整追溯

历史两次Debug提前结束原因未确认；旧引用.backups/horde-retest-20261022/before-runtime-config.json在2026-10-23检查时缺失。不能从后来正常运行推断旧失败根因，也不能凭空恢复旧换行。本轮新的Debug、PID、原始配置及SHA证据已独立保存，不替代缺失历史记录。

### H17 证据补全边界

只追溯能定位到原始日志/快照的旧故障；无法找到的历史文件维持“证据缺失”，不逆推配置、不重新制造同名备份。新运行每轮保存时间、代码、进程归属与原配置哈希，分别证明新行为；这不能消除旧证据缺口。

## H20 [P3／提交归属受阻] 混合源码不能连带提交

三方临时索引拆分已失败，本轮Java/着色器/基准修改依赖原有未提交实现；horde_motion.fsh、horde_motion.glsl和HordeRenderScenario.java等在HEAD中不存在但属于用户原有新增暂存。直接整份提交会连带原有内容或改变原暂存归属。

按项目“仅提交本次授权内容，保留用户其他修改及暂存状态”执行；保留原5项暂存，不覆盖、不重置、不自动推送。独立总览/问题汇总/修改记录可单独提交，其余源码等待明确归属；按用户要求不再重复询问。修复补丁和拆分证据保存在horde-fixes-20261023-lhyy6_x_/this-task.patch及split-check.txt，不能把历史补丁当作当前未提交全量清单。

### H20 交付整理任务

在不动原5项暂存的前提下，为本轮与前轮混合改动导出可审阅差异和源文件清单；没有归属依据不整份提交。独立问题汇总、总览、修改记录可以单独提交，源码未提交就如实说明，不自动推送。

## H21 [人工边界／已采用替代] GPU范围、预算及旧数据

CPU提交动态六整数目标与稳定身份，GPU保留平滑状态，不在CPU计算区域插值或逐绘制块影响；既有模型烘焙、线路/高度准备、光照上传与粗块筛选仍保留，因此不声称整个渲染系统只有CPU区域提交。若字面要求连既有资源准备全部取消，需要单独GPU资源架构，当前保留可运行能力。

每tick解释为目标发现/成员存活/区域/生成请求的即时响应，出生安全预算仍保留；未加载处以输入高度暂代并等待加载，不强制加载。旧事件快照的间距和高度宽度不自动改写，新建事件使用1.8默认值和扩大采样。全亮遮罩不等于动态照亮环境，GPU整数小包围、三级查询索引、连续状态和粗高度的未解决边界见H15。疑问全部记录，只有用户明确要求时再询问。

### H21 必须保留的取舍

- 视觉范围按最新指令改为体型3倍宽深、真实Y和1格整数包围；8/16/32大轮廓已取消，后续若研究GPU索引必须继续保持小缺口与连续过渡。
- 单目标10指有效真实参战成员加预约，旧超额通过既有退场释放名额；不把“所有Mob同时立即删除”或“全局只有10只”作为替代需求。
- 默认间距1.8与增援至10只作用于新规则；既有活动事件快照不强改，需要重建后观察新参数。基础速度未降低；20FPS动画采样、LOD及自动减量规则未改。
- 静态模型/线路/高度与光照CPU准备仍在；粗高度不能完成真实多层路径求解。未验证项继续保留，只有用户要求时再询问。

本轮尸体与粒子备份：C:/Users/wang_/AppData/Local/Temp/horde-cleanup-particles-20261024-5761ddab。普通无来源尸体扩清理被自动审批拒绝的操作未执行；来源和GPU粒子边界见H26。

## 证据与维护

本轮备份根为C:/Users/wang_/AppData/Local/Temp/horde-rewrite-20261023-7e6b6a3d，包含baseline-client、after-client-1/2/3、运行前配置、Iris原始报告与截图；文档收尾前另存closeout-docs。旧文档合并备份horde-docs-consolidation-20261023-kwqkdh3p及旧修复备份horde-fixes-20261023-lhyy6_x_保留。本次追加文档、源码/配置快照及用户截图另存C:/Users/wang_/AppData/Local/Temp/horde-optimization-doc-20261024-991df925。问题解决且完成适用验证后从本页移除，不维护第二份待办。
