# 女皇激光释放瞬间实测与优化

记录日期：2026-10-18～19。工作区：E:/xm/mc/mod/Unitego-Well/Lobecorp。本轮已实际重启客户端并完成 Debug、Spark、本地 JFR、普通 Run 与远距采样，资源工程、模型、ShaderGraph、PNG 和运行定义未改写。

当前**尚未达到包含首次施法在内的稳定 100 FPS**。普通运行第三次释放 P99 为 6.83 ms、最低一秒 285 FPS、1% low 117.92 FPS，但仍有 16.97 ms 的单帧；首次释放最长 60.93 ms，蓄力入口至释放后仍有 120.94 ms。不能用平均帧率或最后一次预热结果代替首次和最差情况。

## 环境与统计口径

最终 Q/R 均为 RTX 3060 Laptop GPU、OpenGL/NVIDIA 616.64、2560×1494、透视、真实游戏窗口最大化且前台、VSync 关闭、throttle_reason=NONE。tracker 返回 260；当前客户端实际可超过 260 FPS，该读数不作为硬限帧保证。没有打开独立编辑器。

每组 90 秒，在第 4/31/58 秒通过真实 IEntitySkillAccess 施放普通 LaserSkill，前摇/伤害/后摇/冷却保持注册值；27 秒间隔避免正常结束后冷却尚未完成。测试目标为独立 NoAI Husk，最大生命 1024，每次恢复生命并设置有效憎恶目标。只清理本轮 UUID 与标签匹配的测试实体，测试世界文件保留。粒子随机种子未固定。

帧间隔取相邻 GameRenderer.render HEAD；包括其间 tick、extract、present、swap 和 limiter。CPU/GPU 只测 GameRenderer.render，GPU 查询异步回收，排除 tick/extract/present，原生 GL 样本可能包含驱动等待，CPU 与 GPU 不能相加。P99 为最近秩分位；1%/0.1% low 为最慢对应比例帧间隔平均值的倒数；最低一秒仅统计完整的一秒滑窗。

所有窗口保留跨边界的长帧。释放窗口为客户端 OUTPUT 前 0.2 秒至后 2 秒；完整施放窗口为 CHARGE 前 0.2 秒至 OUTPUT 后 2 秒，避免把工作前移到蓄力后宣称消除卡顿。

**比较限制：**原始 B 释放窗口主要失焦，但同样没有节流；最终 Q/R 前台。B 在 81.22 秒达到旧 32768 帧容量，三次技能窗口已完整，不能称整组 90 秒成功。当前容量为 131072。调试器、编译阶段、驱动呈现与后台负载存在波动，以下是描述性对照，不计算严格因果改善百分比。

## 释放瞬间的完整前后表

| 记录 | 周期 | 最长帧 ms | P99 ms | 最低一秒 FPS | 1% low FPS | 0.1% low FPS |
|---|---|---:|---:|---:|---:|---:|
| 修改前 B | 本组第1次 | 949.08 | 949.08 | 2 | 1.05 | 1.05 |
| 修改前 B | 预热第1次 | 44.81 | 16.21 | 156 | 39.81 | 22.32 |
| 修改前 B | 预热第2次 | 26.90 | 9.76 | 222 | 73.40 | 37.18 |
| 最终 Debug Q | 本组第1次 | 61.83 | 44.55 | 57 | 19.49 | 16.17 |
| 最终 Debug Q | 预热第1次 | 20.37 | 11.66 | 218 | 66.15 | 49.08 |
| 最终 Debug Q | 预热第2次 | 16.49 | 7.14 | 290 | 106.64 | 60.66 |
| 普通运行 R | 本组第1次 | 60.93 | 49.36 | 61 | 18.62 | 16.41 |
| 普通运行 R | 预热第1次 | 44.85 | 17.26 | 159 | 39.82 | 22.30 |
| 普通运行 R | 预热第2次 | 16.97 | 6.83 | 285 | 117.92 | 58.92 |

Q 为 Debug，R 为同一运行逻辑的普通启动；两组均关闭 Spark、JFR 和额外复制检查。R 没有附加调试会话。普通启动也出现首次停顿，现有证据不支持只归因于 Debug。

## 从蓄力入口到释放后

| 记录 | 周期 | 最长帧 ms | P99 ms | 最低一秒 FPS | 1% low FPS | 0.1% low FPS |
|---|---|---:|---:|---:|---:|---:|
| 修改前 B | 本组第1次 | 949.08 | 716.14 | 2 | 1.20 | 1.05 |
| 修改前 B | 预热第1次 | 255.82 | 22.86 | 88 | 10.51 | 3.91 |
| 修改前 B | 预热第2次 | 57.22 | 9.76 | 222 | 40.93 | 17.48 |
| 最终 Debug Q | 本组第1次 | 130.15 | 44.55 | 57 | 13.37 | 7.68 |
| 最终 Debug Q | 预热第1次 | 48.00 | 8.77 | 218 | 58.91 | 28.47 |
| 最终 Debug Q | 预热第2次 | 53.42 | 7.02 | 290 | 69.71 | 25.53 |
| 普通运行 R | 本组第1次 | 120.94 | 49.36 | 61 | 14.29 | 8.27 |
| 普通运行 R | 预热第1次 | 44.85 | 15.68 | 159 | 41.85 | 22.30 |
| 普通运行 R | 预热第2次 | 54.74 | 6.79 | 285 | 61.25 | 22.37 |

普通 R 的重复蓄力峰值分配为 23.68/22.99 MiB，重复释放为 9.28/9.04 MiB；此前 I 对应蓄力约 39.08/38.40 MiB、释放约 14.15/13.92 MiB。分配量是渲染线程帧间差，不含其他线程、原生内存或显存，也包含采样记录的少量成本。复制和参数查找优化减少了实际分配，长帧仍未全部解决。

## 持续输出与空场

| 普通运行 R | 最长帧 ms | P99 ms | 最低一秒 FPS | 1% low FPS | 渲染CPU P99 ms | 渲染GPU P99 ms |
|---|---:|---:|---:|---:|---:|---:|
| 第1次持续输出 | 18.06 | 10.38 | 188 | 80.86 | 4.78 | 5.52 |
| 第2次持续输出 | 17.39 | 7.34 | 252 | 109.04 | 3.95 | 4.34 |
| 第3次持续输出 | 15.86 | 8.13 | 202 | 101.64 | 4.58 | 5.10 |

普通 R 开始空场 0.5～3.5 秒 P99 3.55 ms、最长 5.22 ms、最低一秒 515 FPS；结束空场 75～88 秒 P99 2.15 ms、最长 9.09 ms、最低一秒 702 FPS。空场可稳定超过目标，剩余首次和阶段切换尖峰需要继续定位，不能解释为整台机器恒定无法达到 100 FPS。

## 实际保留的修改

- 女皇代码动作使用连续显示时钟，每个近距渲染帧采样本地曲线，再用共享纯 aimPose 合成插值的瞄准俯仰，避免整个 up_body 被服务端 tick 姿态替换。基础控制器继续推进；蓄力按 windup 混入，后摇按 recovery 平滑混回基础姿态，头部俯仰按相同权重交接。没有延长技能或伤害阶段。
- 默认模型/动画/特效近距离阈值统一为 50 格，当前开发 TOML 同步。阈值以内立即完整，迟滞不跨入近距范围，特效负载降级也不影响近处。原远距 Native 控制器缓存策略保留；女皇代码动作在远处按 30/12 FPS 缓存，继承实体与 action 控制器豁免。缓存归播放 Frame，结束自然释放，资源代、播放版本、阶段及 FPS 变化失效。
- 首 circle 对齐激光身体中心沿朝向前 1 格，或星束现有 staffOrigin 基准；不再减去光球父锚点把法阵后移。保留工程内的相对布局，并单独算实际光束起点与剩余投影长度，墙体截断到源后方时长度归零。伤害原点与射程未变。星束仍是原服务端法杖姿态估计，没有新增客户端骨骼 locator。
- 加载准备本地曲线、模型 CPU 资产、纹理和图/Bloom 管线。限量 16 份未发射实例只独占消费一次；当前三次激光共命中 7 次、余 3 份，不回收已播放实例、不后台补建。世界退出、卸载或管线失效清理。
- 复制作用域内按类复用字段与持久化方法描述；保留原继承顺序、实际序列化回调与配置 setter，作用域退出即释放元数据，下一次热替换重新读取。只对已核对的临时序列化读取与精确 CustomDirectAccessor 类型使用无深快照引用；网络托管引用和反序列化仍走原类型，未知类型回退。
- 参数查找保持列表优先级和规范 UUID/名称匹配，移除逐候选 UUID 字符串及 Stream；运行控制模式的独占库直接移交，不重新编码整份 NBT。独立材质工作副本读取也复用复制作用域，材质可变参数仍独占。
- 图内容哈希复用最多 64 个不可变管线描述，原源码注册和材质引用计数继续执行，设备清缓存后失效；当前 17 个描述，普通 R 命中 714 次。
- 世界当前尺寸的原生 Bloom 链保持活跃，原生闲置 120 帧驱逐不再让间隔施法重复分配；额外预编译六条 Bloom 管线。Q/R 均观察到同一条链跨三个周期。当前分辨率约保留 39 MiB 显存，属于空闲时的代价；换尺寸的旧链、退出或关闭/改变相关配置后仍按原生规则回收。
- 本帧未写 UBO 且没有旧缓冲待清理时跳过轮转和 fence；已写入的缓冲仍执行原同步。
- 独立零步长数学样本链接原生发射计算和包装调用，使用独立随机源；不注册宿主、不调用 emit/tick，不预播放用户特效。

## 逐帧显示与远距验证

近距 Q 每次完整技能实际经过 2061/3391/3479 个姿态渲染样本；普通 R 为 2430/3376/3441。时间、蓄力权重、后摇权重回退计数均为 0。该计数确认处理频率与单调性，不等同于所有运动/网络条件的视觉验收。

远距 S 相机缩放 8，约 85 格，临时实体显示距离为 2 以保证女皇实际绘制；三次完整技能分别 138/138/139 个姿态样本，蓄力 11/12/12、后摇各 6，符合 12 FPS，回退均为 0。显示距离在流程结束还原。50 格边界的完整精度及中距 30 FPS 为源码核对，未另做完整来回切档、豁免或多人测试。

实际近距截图已查看：首法阵位于女皇前方，粉白光柱、三法阵与花瓣可见。截图不证明所有角度、星束或二阶段姿态已验收。实际技能采样使用普通激光，星束与二阶段由用户继续视觉验证。

## Spark 与 JFR 的证据

Spark 使用已安装 1.10.172 的 JAVA 执行采样，4 ms，--save-to-file；未上传，也没有打开远程 viewer。90 秒聚合桶为 60 秒，不能声称其中调用比例精确属于两秒释放窗口。本地 `profile-2026-10-18_23.42.47.sparkprofile` Render thread 权重 90000 ms、11352 个节点，遍历一致且负 self 为 0；glBindFramebuffer self 16.96%、glFenceSync 9.68%、writeMesh 2.19%。FFM LambdaForm 的主要路径是 glfwSwapBuffers，不能将其叫作泛型 Java 反射热点。服务端主要等待，不能用总权重推算活跃 CPU。

JFR 为显式开发诊断，200/s ObjectAllocationSample、GC 与编译事件；共 4050 个渲染线程分配样本。重复蓄力的反射 Method.copy 调用栈代表权重约 36.84 MiB，另见字段克隆与 MutableDirectRef 的 JavaOps codecMark；持续播放见 parameter 查找中的 UUID.toString/Stream 及模型数组。这支持反射作用域缓存、临时读取和参数查找的优化。采样 weight 是统计代表字节，不是逐对象精确分配量，不能与逐帧 allocated_bytes 直接相等。

当次 GC 暂停最大 24.29 ms 在收尾/空闲组，释放阶段也有约 9.53 ms 暂停；记录 5298 个编译事件，不将后台编译时长简单累加成主线程暂停。分配、首次链接与模型/材质初始化仍是后续候选；当前未取得每一个残留长帧的精确执行栈，不能说原因已全部解决。

JFR 原文件与摘要：`run/client/performance/release_jfr_20261019_j_allocation_173412409117300.jfr`、`release_jfr_20261019_j_allocation_summary_173412709972000.json`。带 Spark/JFR 或额外复制检查的组不作为最终首次稳定成绩。

## 试验、失败与记录索引

| 标签 | 用途 | 帧记录状态 | 帧数 | 第1次释放最长帧/P99 ms | 后两次释放 P99 ms |
|---|---|---|---:|---:|---:|
| release_before_20261018_b | 原始基线；容量停止，三周期完整 | CAPACITY | 32768 | 949.08 / 949.08 | 16.21 / 9.76 |
| release_after_20261018_a | 共享图/管线/纹理准备 | DURATION | 13808 | 87.21 / 86.81 | 14.29 / 10.52 |
| release_after_20261018_b | 含GPU状态的分步补建队列；已撤回 | DURATION | 18399 | 177.34 / 177.34 | 29.86 / 55.21 |
| release_after_20261018_c | 队列撤回与过渡修复；该组前面已有Spark预热 | DURATION | 40482 | 24.64 / 12.49 | 9.96 / 9.81 |
| release_after_20261019_d | 连续显示时钟和UBO空轮转门禁 | DURATION | 37475 | 392.08 / 130.33 | 14.46 / 10.42 |
| release_after_20261018_e | 本地视觉曲线预解析；锚点未落盘，位置验收无效 | DURATION | 13846 | 51.19 / 50.44 | 17.80 / 12.03 |
| release_after_20261018_f | 锚点实际落盘，省去动画库重编码 | DURATION | 14165 | 54.26 / 51.36 | 15.06 / 12.11 |
| release_after_20261019_g | 限量首次未发射实例缓存 | DURATION | 14274 | 53.87 / 35.60 | 11.71 / 15.49 |
| release_after_20261019_h | 私有GPU状态预建；无稳定收益，已撤回 | DURATION | 13966 | 69.14 / 54.31 | 16.47 / 13.15 |
| release_after_20261019_i | 保持世界Bloom链并预编译Bloom管线 | DURATION | 14201 | 65.46 / 51.27 | 12.05 / 12.20 |
| release_after_20261019_k | 复制反射缓存及参数查找；额外等价检查预热 | DURATION | 14153 | 62.66 / 40.45 | 17.73 / 11.93 |
| release_after_20261019_m | 临时引用优化；关闭额外验证 | DURATION | 58236 | 53.93 / 38.69 | 12.92 / 7.75 |
| release_after_20261019_q_debug | 最终Debug近距；含材质读取作用域及代码远距缓存 | DURATION | 56047 | 61.83 / 44.55 | 11.66 / 7.14 |
| release_far_20261019_s_debug | 约85格代码姿态降频验证 | DURATION | 55828 | 60.12 / 53.97 | 15.06 / 10.43 |
| release_run_20261019_r | 同逻辑普通Run近距；没有附加Debugger | DURATION | 58541 | 60.93 / 49.36 | 17.26 / 6.83 |


含 GPU 状态初始化的补建队列单步最高 16.31 ms，曾使表现退步，已撤回；未发射实例只保留首次消费。H 私有 GPU 状态预建未取得稳定收益，也已撤回。用户澄清后，远处 Native 控制器的插值试验已撤回，原 LcAnimationLodCache 保持，当前只给代码动作增加对应远距采样策略。

早期两次 JBR 原生崩溃记录在 `run/client/hs_err_pid66944.log`、`hs_err_pid22188.log`；发生在条件断点/异步 Debug 表达式准备过程中，不能据此确认 JVM 崩溃的因果，也不计入帧率基线。随后使用编译的流程并静音断点，避免采样期间暂停或执行 Debuggee 异步表达式。早期 A 因第二次 ON_COOLDOWN 提前结束，只有局部 Spark，不能当三周期基线。

E 曾遇到 IDEA 文档已改变但磁盘未保存，截图仍旧位置；后改用文档编辑接口落盘并核对文件时间、内容，F 起位置截图有效。一次验证启动短暂同时保留两个本项目测试客户端，旧实例已通过 Debug 停止；该验证只比较数据，不计帧率。所有有效近距采样均只运行一个本项目测试客户端，其他系统负载未完全隔离。

各标签的原始帧 JSON 和 *_marks_*.json 位于 run/client/performance，summary.started_ns 与客户端阶段标记用于对齐；分析入口为 [analyze_laser_release.py](../../scripts/analyze_laser_release.py)。最新普通 R 标记为 `run/client/performance/release_run_20261019_r_frames_marks_177700590142900.json`；最新 Debug Q 为 `run/client/performance/release_after_20261019_q_debug_frames_marks_177106254695700.json`；远距 S 为 `run/client/performance/release_far_20261019_s_debug_frames_marks_177458789149400.json`。

## 可复用开发流程

在 IDEA 的 Client 配置中为本次启动临时设置以下环境变量，未设置请求变量时不启动流程。无需终端控制游戏或逐次点击/读取状态。

```text
LOBECORP_LASER_PROFILE=release_measurement
LOBECORP_LASER_PROFILE_SINGLE_CAPTURE=true
LOBECORP_LASER_PROFILE_SPARK_FIRST=false
LOBECORP_LASER_PROFILE_CAMERA_SCALE=1
LOBECORP_LASER_PROFILE_VERIFY_COPY=false
LOBECORP_LASER_PROFILE_VALIDATE_ONLY=false
LOBECORP_LASER_PROFILE_JFR=false
```

SINGLE_CAPTURE 不启用时自动再采一组相反的 Spark 状态；Spark 指令固定本地保存。JFR 单独启用，额外验证单独启用 VERIFY_COPY/VALIDATE_ONLY。相机缩放大于 1 时只在本轮临时提高实体显示距离，结束恢复原值。流程最大化真实游戏窗口、使用 Neo Create Test World、透视、关闭失焦/AFK 限制，自动布置自有实体、施放、写记录、截屏和清理；不会删除世界或原资源，也不保存运行配置的临时环境覆盖。

复制等价验证 L 在独立验证启动中完成：10 份真实定义标准复制与作用域复制再序列化相等，problems=0。正式 Q/R 不执行额外验证，避免提前预热首次成绩。

## 检查、备份与尚未完成

最终 27 个相关 Java 文件完成 IDEA 诊断：0 个错误，保留 14 条未使用 API、注解与风格建议；两条新增静态建议分别为 Mixin 参数 holder 与目标参数 instance 的名称差异，以及参数匹配条件中的 null 检查可简化；均不影响编译或已完成的客户端加载，未另作无关清理。最终源码已编译并由 Q/S/R 加载，正常 Data exitCode=0，日志确认 19 个生成文件、written=0、removed stale=0。原有两个禁用异常断点和第 62 行启用的用户断点保留，全局静音已恢复 false，本项目附加 Debug 会话归零。普通运行客户端保留，流程 COMPLETE 后已恢复临时运行选项和鼠标/窗口焦点。Q/R/S 都按 90 秒 DURATION 完整结束；没有执行本轮 JUnit，也未手改生成资源。只读 Git 状态检查返回 must be run in a work tree，未取得该检查结果，也未据此修改 Git 配置或暂存区。

仍未完成：首次及第二次释放的稳定 100 FPS、重复蓄力的 43～55 ms 尖峰、全部长帧精确归因、多实例/动态相机/网络抖动长期测试、星束真实法杖位置和二阶段视觉验收。后续应对开始复制、控制器绑定、emit/reset、释放播放、模型/材质首次解析分别记录时段，再与精确执行栈和 GC 关联；不靠删除首帧或降低 50 格内画质达标。

[本轮文本备份与修改索引](../../backups/laser-release-profile-20261018/README.md)。

### 本轮相关文件

- [src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)
- [src/main/java/org/unitego/lobecorp/client/photon/performance/QueenBeamPerformanceTrial.java](../../src/main/java/org/unitego/lobecorp/client/photon/performance/QueenBeamPerformanceTrial.java)
- [src/main/java/org/unitego/lobecorp/util/photon/runtime/QueenBeamPrewarmUtil.java](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/QueenBeamPrewarmUtil.java)
- [src/main/java/org/unitego/lobecorp/client/photon/emitter/model/PhotonModelEmitter.java](../../src/main/java/org/unitego/lobecorp/client/photon/emitter/model/PhotonModelEmitter.java)
- [src/main/java/org/unitego/lobecorp/client/photon/performance/PhotonFrameCapture.java](../../src/main/java/org/unitego/lobecorp/client/photon/performance/PhotonFrameCapture.java)
- [src/main/java/org/unitego/lobecorp/util/photon/render/PhotonShaderPipelineCacheUtil.java](../../src/main/java/org/unitego/lobecorp/util/photon/render/PhotonShaderPipelineCacheUtil.java)
- [src/main/java/org/unitego/lobecorp/mixin/kilagraph/client/RenderTypeFactoryMixin.java](../../src/main/java/org/unitego/lobecorp/mixin/kilagraph/client/RenderTypeFactoryMixin.java)
- [src/main/java/org/unitego/lobecorp/mixin/minecraft/client/GlDeviceMixin.java](../../src/main/java/org/unitego/lobecorp/mixin/minecraft/client/GlDeviceMixin.java)
- [src/main/java/org/unitego/lobecorp/client/photon/runtime/PhotonGeoEffect.java](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/PhotonGeoEffect.java)
- [src/main/java/org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects.java](../../src/main/java/org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects.java)
- [src/main/java/org/unitego/lobecorp/client/photon/animation/PhotonAnimationController.java](../../src/main/java/org/unitego/lobecorp/client/photon/animation/PhotonAnimationController.java)
- [src/main/java/org/unitego/lobecorp/util/photon/emitter/PhotonEmitterTemplateUtil.java](../../src/main/java/org/unitego/lobecorp/util/photon/emitter/PhotonEmitterTemplateUtil.java)
- [src/main/java/org/unitego/lobecorp/config/client/VisualQualityConfig.java](../../src/main/java/org/unitego/lobecorp/config/client/VisualQualityConfig.java)
- [run/client/config/lobecorp-visual-quality.toml](../../run/client/config/lobecorp-visual-quality.toml)
- [src/main/java/org/unitego/lobecorp/util/photon/render/PhotonQualityUtil.java](../../src/main/java/org/unitego/lobecorp/util/photon/render/PhotonQualityUtil.java)
- [src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/TheQueenOfHatredBeamAnimation.java](../../src/main/java/org/unitego/lobecorp/world/entity/abnormalitie/the_queen_of_hatred/TheQueenOfHatredBeamAnimation.java)
- [src/main/java/org/unitego/lobecorp/client/animation/LcClientAnimations.java](../../src/main/java/org/unitego/lobecorp/client/animation/LcClientAnimations.java)
- [src/main/java/org/unitego/lobecorp/client/animation/LcAnimationBoneAdapter.java](../../src/main/java/org/unitego/lobecorp/client/animation/LcAnimationBoneAdapter.java)
- [src/main/java/org/unitego/lobecorp/mixin/geckolib/client/RenderPassInfoMixin.java](../../src/main/java/org/unitego/lobecorp/mixin/geckolib/client/RenderPassInfoMixin.java)
- [src/main/java/org/unitego/lobecorp/client/entity/abnormalitie/the_queen_of_hatred/renderer/TheQueenOfHatredRenderer.java](../../src/main/java/org/unitego/lobecorp/client/entity/abnormalitie/the_queen_of_hatred/renderer/TheQueenOfHatredRenderer.java)
- [src/main/java/org/unitego/lobecorp/client/animation/LcClientAnimationResources.java](../../src/main/java/org/unitego/lobecorp/client/animation/LcClientAnimationResources.java)
- [src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonRuntimeCopyUtil.java](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonRuntimeCopyUtil.java)
- [src/main/java/org/unitego/lobecorp/mixin/ldlib2/client/PersistedParserMixin.java](../../src/main/java/org/unitego/lobecorp/mixin/ldlib2/client/PersistedParserMixin.java)
- [src/main/java/org/unitego/lobecorp/config/photon/animation/PhotonAnimationDefinition.java](../../src/main/java/org/unitego/lobecorp/config/photon/animation/PhotonAnimationDefinition.java)
- [src/main/java/org/unitego/lobecorp/client/photon/performance/QueenBeamAllocationCapture.java](../../src/main/java/org/unitego/lobecorp/client/photon/performance/QueenBeamAllocationCapture.java)
- [src/main/java/org/unitego/lobecorp/mixin/photon/client/PhotonBloomAccessor.java](../../src/main/java/org/unitego/lobecorp/mixin/photon/client/PhotonBloomAccessor.java)
- [src/main/java/org/unitego/lobecorp/mixin/minecraft/client/DynamicUniformStorageMixin.java](../../src/main/java/org/unitego/lobecorp/mixin/minecraft/client/DynamicUniformStorageMixin.java)
- [src/main/java/org/unitego/lobecorp/util/photon/model/editor/PhotonModelMaterialUtil.java](../../src/main/java/org/unitego/lobecorp/util/photon/model/editor/PhotonModelMaterialUtil.java)
- [src/main/resources/META-INF/lobecorp.mixins.json](../../src/main/resources/META-INF/lobecorp.mixins.json)
- [scripts/analyze_laser_release.py](../../scripts/analyze_laser_release.py)
