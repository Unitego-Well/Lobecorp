# Photon、LDLib2 与 KilaGraph 依赖升级

## 2026-11-08 原始 dev 时间轴求值签名适配

Photon `26.1.2.11` 的 `TimelinePlayer.evaluate` 为 `(double time, boolean live)`。`TimelinePlayerMixin` 的两个求值注入和性能包装统一锁定 `evaluate(DZ)V`，包装在开启、关闭跟踪时都透传 `live`。时间轴结束后的 `live=false` 不再执行扩展预览，避免重新激活轨道或子效果。LDLib2 的 `FXInstanceObject.TYPE` 加载失败在本次日志中由该 Mixin 应用失败引起，不单独修改注册。

## 2026-11-08 女皇工作区升级至 26.1.2.11

当前 `codex/queen-of-hatred` 使用 Photon `26.1.2.11`、LDLib2 `26.1.2.46`，KilaGraph 保持 `26.1.0.16.a`。版本来自官方 Maven 26.1 模块元数据，IDEA 实际解析与版本目录一致；Minecraft、NeoForge 与 Java 版本不变。

本次制品已包含 `FXRuntime.sections()` 和 `SectionControl`，支持名称查询、jumpTo、queue、advance、release 等原生区段接口。下方 `.10` 无 Sections 的结论仅属于先前版本。此次只完成依赖升级与必要兼容，不迁移既有命名动画或新增 MCP 区段控制工具。

新版 `emit(effect, delay)` 转发到 `emit(effect, delay, startSection)`，真正的发射与时间轴启动已移到三参数入口。现有 FXRuntimeMixin 的命名准备、预览恢复与发射跟踪统一改为注入三参数入口，完整传递 startSection；普通发射仍经转发调用，不重复执行。通过依赖源码核对两条播放路径，并确认 LDLib2 视图菜单及缩放 Mixin 的调用目标仍存在。

验证：IDEA Gradle 同步成功；`compileJava compileBenchJava jar` 最终成功。修改 Mixin 的 IDEA 检查无错误，保留既有 Mixin 强转与参数名提示。未执行测试、Data、游戏、GUI 显示或 MCP 实际调用验收，未恢复演示资源。

## 2026-11-07 女皇工作区升级

本节仅适用于 `codex/queen-of-hatred`。Photon 从 `26.1.2.9` 升为官方 Maven 当前 26.1 分支发布的 `26.1.2.10`；LDLib2 `26.1.2.45`、KilaGraph `26.1.0.16.a`、Minecraft `26.1.2`、NeoForge `26.1.2.100` 和 Java 25 保持。版本目录与 IDEA 实际解析均指向 `.10`，不是把 1.21.1 的 2.x 版本号直接写入本工程。

依据[官方预加载接口](https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/java-api/preloading.html)及实际 `.10` 的 `FXPreloader.SyncSink` / `walk`，根效果的 `preloadNow` 已经通过 `PreloadContext.fx` 递归准备子效果。女皇准备入口改为只对每个根执行原生预加载；后续自有遍历只读取定义、核对缺失来源并准备可选编译模式，不对子效果重复执行整套原生预热。保留自有模型和 PROJECT 模板的 `preload` 钩子，不能把自定义类型首次准备全部交给上游猜测。

MCP `effect_capabilities` 直接枚举原生 `PhotonRegistries.MATERIALS` 和 `MODEL_SOURCES`，返回 `materialTypes` / `modelSourceTypes`。原生材质可通过现有 renderer SNBT/模型材质槽事务使用，原生对象和时间轴类型继续来自 Photon 注册表，不再维护一份平行类型清单。接口名称与桥接协议保持，未增加新的外部 MCP 工具。

文档中的 [Sections](https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/timeline/sections.html) 标注自 2.2.11；实际 `26.1.2.10` 的 `FXRuntime` 没有 `sections()`，依赖中也无 `SectionControl`。本轮不以该页面为由删除命名播放、参数绑定和子窗口控制，不宣称已接入区段播放。

其余候选按实际 `.10` API 核对后的边界：

| 自有职责 | 原生能力 | 本轮处理 |
|---|---|---|
| 材质和模型来源类型发现 | `PhotonRegistries.MATERIALS` / `MODEL_SOURCES`，包括原生 `kila_material` | MCP直接枚举，现有事务继续使用原生Codec；不另写Kila材质实现 |
| 动态模型与可编辑 `.mfxproj` | `IDynamicMesh` 提供拓扑、revision、CPU/GPU几何，`DynamicMeshSource` 是运行覆盖 | 保留编辑、持久化、部件片段和事务能力；动态网格接口本身不能等价替代文件与编辑契约 |
| 子效果生成与独立生命周期 | `FXInstanceObject` 放置来源效果、保留覆盖与嵌套时间轴 | 保留 `PhotonChildSpawner` 的 PROJECT模板、间隔/速率生成、实例配额和独立寿命；不把静态放置当成等价生成器 |
| 命名播放、阶段保持与参数绑定 | 本分支无Sections；普通Timeline与Signal仍可用 | 保留现有功能和资源，不做缺乏对应API的删除 |

GUI无需外观调整，但格式、生命周期、revision和MCP写入保护仍是功能契约，不能因降低外观验收标准而移除。

验证：IDEA 同步成功，`compileJava compileBenchJava jar` 成功；两份修改 Java 文件无错误，保留既有未使用方法、Stream 写法和借用 Prepared 等警告。用户要求游戏搁置，本轮不启动客户端，不进行 GUI 外观、实际 MCP 调用、游戏视觉或性能验收。API 编译和静态接线不能替代实际调用。

## 2026-11-01 主工作区版本同步

按用户澄清，仅同步Photon工作区的PhotonMod版本升级及必需API兼容，不合入整个工作区、资源调度架构、性能工具或分支进度。版本目录三项增量来自acfbf85b，通过Git apply --3way --index应用，其他依赖版本保持。

| 依赖 | 主根原版本 | 当前版本 |
|---|---|---|
| Photon | 26.1.2.3 | 26.1.2.9 |
| LDLib2 | 26.1.2.42 | 26.1.2.43 |
| KilaGraph | 26.1.0.15 | 26.1.0.16.a |

Minecraft26.1.2、NeoForge26.1.2.100、Java25及Maven模块坐标不变。主根IDEA依赖模型已重新导入并确认解析至Photon26.1.2.9。

必要兼容限定为：Occlusion绘制作业保留新增backFacesFirst/records及不支持绑定的回退；撤除上游已替代的copySceneObject及Camera射线补丁；保留光束长度覆盖。项目保存命令转向LDLib2五参dispatchCommand，FXData guard锁定三参反序列化、Emitter批次锁定八参执行层，模型资源回退转至readTag/fileLocation并清理invalidate后的模型ID缓存。FXProject读取预览和原树保留跟随新deserialize/readProject路径，并保留knownAs；导出通知转到实际FXFileActions成功路径，保留上游资源包解析状态与重载选项。没有引入租约/组合/事件准备接口。

本次只做静态检查、依赖解析、编译/生产包隔离及Data；游戏测试继续暂停，未验证客户端Mixin应用、导出通知画面或性能。原始六份snake暂存和.gitignore未混交，Photon工作区原WIP保持。以下发布日期与“最新”措辞只属于2026-10-10历史记录。

## 2026-10-10 历史升级记录

核对日期：2026-10-10。本页记录 Minecraft 26.1.2／NeoForge
分支的发布证据、更新范围、项目依赖声明与适配检查。功能职责见 [Photon 编辑器](photon-editor.md)、[模型特效](photon-model-effects.md)、[命名动画](photon-named-animations.md)
和[修复对照](photon-ldlib2-fixes.md)。

## 发布版本与证据

| 依赖      | 升级前    | 本次目标  | 发布日期   | 上游分类与官方发布页                                                                             |
|-----------|-----------|-----------|------------|--------------------------------------------------------------------------------------------------|
| Photon    | 26.1.2.2  | 26.1.2.3  | 2026-09-22 | Beta；[CurseForge 8945195](https://www.curseforge.com/minecraft/mc-mods/photon/files/8945195)    |
| LDLib2    | 26.1.2.39 | 26.1.2.42 | 2026-10-05 | Beta；[CurseForge 9065121](https://www.curseforge.com/minecraft/mc-mods/ldlib/files/9065121)     |
| KilaGraph | 26.1.0.14 | 26.1.0.15 | 2026-09-21 | Beta；[CurseForge 8941619](https://www.curseforge.com/minecraft/mc-mods/kilagraph/files/8941619) |

这三个目标文件均标注支持 Minecraft 26.1.1／26.1.2，是本次核对时适用于当前 Minecraft 版本的最新已发布 NeoForge
文件；“已发布”不等于上游标记为稳定 Release。26.2 文件属于另一 Minecraft 分支，本次不升级 Minecraft、NeoForge 或其他无关依赖。

LDLib2 的中间版本 26.1.2.41 发布于
2026-09-21，官方日志见 [CurseForge 8941255](https://www.curseforge.com/minecraft/mc-mods/ldlib/files/8941255)。本轮取得
.41 与 .42 的独立发布日志，没有另行核准 .40 的独立文件及日志，不为该版本补造条目。

版本值统一维护在 [libs.versions.toml](../../gradle/libs.versions.toml)。Maven 模块继续使用
`com.lowdragmc.photon:photon-neoforge-26.1`、`com.lowdragmc.ldlib2:ldlib2-neoforge-26.1` 和
`com.lowdragmc.kilagraph:kilagraph-neoforge-26.1`。

## Photon 26.1.2.3 官方更新摘要

以下按领域归纳 [26.1.2.3 发布日志](https://www.curseforge.com/minecraft/mc-mods/photon/files/8945195)
，不是本项目已实现或已验收的新功能清单。

- 模型与几何：glb 可导入带骨架动画；增加动画选择、逐粒子相位与可选帧混合，以及供其他模组提交变化几何的动态网格入口；模型朝向新增类似
  Unity RenderAlignment 的模式。
- 材质与着色：普通纹理和 Sprite 材质支持可选软粒子；图节点纹理参数保留采样环绕和过滤。
- 变换修复：GPU 实例化在缺少材质时仍应用模型变换；模型粒子继承父旋转，欧拉旋转顺序改正为 Unity 的 ZXY。
- 播放与时间轴：FXRuntime 的播放倍率同时影响对象模拟和时间轴；传输栏增加图标、步进和跳转末尾；修复刻度两端文字裁切及延迟期间首帧数据。
- 编辑器：增加 Gizmo、视图开关和播放控制快捷键。
- 依赖：上调 LDLib2 与 KilaGraph。

源码差异可查[旧版至该发布提交的比较](https://github.com/Low-Drag-MC/Photon/compare/8ceb79e680ba977f9b38cfa660b9053d073935fa...92578f8a56de97bb93002609bb532eb8deb6b431)，包含 35 个提交、93 个文件，生产 Java 文件为 51 个。本项目通过实际发布 JAR 复核网格、时间轴、根回调和原生传输栏，避免把后续开发分支变化混入本轮结论。

这些变化与本项目模型动态几何、父变换、独立播放时钟、时间轴传输栏和快捷键扩展直接相关。是否改用新入口，必须以新发布 JAR
的类型、调用点和字节码为依据；发布日志本身不能证明旧 Mixin 已失效或应当删除。

## LDLib2 官方更新摘要

### 26.1.2.41

以下来自 [.41 发布日志](https://www.curseforge.com/minecraft/mc-mods/ldlib/files/8941255)。

- 输入：编辑器增加可重绑组合键、上下文及设置页；工具栏提示当前绑定；修复文本输入向容器快捷键传播。
- 图编辑：增加分层、网格和力导向布局、嵌套背景框布局、四种连线路径；拖入可写变量时选择读或写；吸附使用四边、中心及参考线，并按图类型保存偏好。
- 节点与样式：支持筛选可编辑字面值、端口提示、隐藏检视选项，以及 LSS 精灵环绕、短横线枚举值和圆角分段。
- 资源与窗口：资源页签可调整大小、换行、拖动排序和四边停靠；修复 Provider 崩溃、无 ID 窗口最小化、不同编辑器混用最近项目及搜索候选不完整。
- 正确性：修复多元素拖动间距、空物品配置、常量与端口类型、首次打开连线偏移，以及客户端连接未装 LDLib 的服务器和测试早期窗口。

### 26.1.2.42

以下来自 [.42 发布日志](https://www.curseforge.com/minecraft/mc-mods/ldlib/files/9065121)。

- 窗口与文件：编辑器可弹出到独立系统窗口；对话框记住大小并适配屏幕；文件对话框支持路径跳转和系统目录选择。
- 资源目录：增加全局搜索、文件夹 Provider、搜索排序、项目自定义打开方式和菜单，以及跨视图拖入文件夹；减少重复刷新和顺序读取。
- 编辑交互：UI 元素增删、粘贴和移动支持撤销；运行模拟可用 F12 调试；图视图支持全选；快捷键从焦点元素向视图冒泡；Gizmo
  右键取消拖动，轨迹球按下不立即旋转。
- 配置：枚举选项可翻译；配置项保留字段、继承分组菜单并显示覆盖状态；禁用面板阻止修改，保留折叠、滚动和选择。
- 修复：无效重命名不再删除资源，重建路径、文件夹识别、Provider 更新和拖拽路径更可靠；页签跟随重命名；修正嵌套节点、动态端口检视、变量类型、菜单宽度及系统剪贴板粘贴。
- 测试设施：UI 测试使用独立并清空的游戏目录。

两版会影响本项目快捷键、资源目录、窗口生命周期、原生配置组件和文本输入补丁。新增原生能力需要与现有扩展逐项核对；例如修复系统剪贴板并不自动证明本项目所有剪贴板入口和序列化流程已通过验收。

## KilaGraph 26.1.0.15 官方更新摘要

以下来自 [.15 发布日志](https://www.curseforge.com/minecraft/mc-mods/kilagraph/files/8941619)。

- 数学节点：增加四元数及轴角、欧拉、方向间旋转、组合、逆、插值、旋转向量、角度和 Vec4
  转换；补充向量方向、长度、插值、垂直向量、环绕、维数转换，以及吸附、阶跃、平滑、反向插值、角度差、趋近、近似比较、波形和双曲函数。
- 运行与变量：节点声明能否离开游戏线程执行；增加变量存储访问 API，设置变量改为指向声明。
- 采样器：绑定 Sampler2D 时同时提交纹理与采样参数。
- 编辑与类型：常量类型从声明推导；修复动态端口顺序、无访问器端口的自定义编辑器类型及加载后常量类型。

KilaGraph 是 Photon 的间接必要依赖；本项目没有直接引用其 API。本次随 Photon 升级它，不新增另一套图运行系统。实际 Photon .3 JAR 要求 LDLib2 ≥26.1.2.41、KilaGraph ≥26.1.0.15，均为 required／AFTER／BOTH；KilaGraph .15 要求 LDLib2 ≥26.1.2.41、required／NONE／BOTH。本项目使用 .42 的新 API，因此自己的 LDLib2 下界使用 26.1.2.42。

## 元数据来源与资源展开

[gradle.properties](../../gradle.properties)集中维护模组名称、作者、描述、Logo、致谢、许可证与 URL 等元数据，沿用已有
`mod_id`／`mod_version`。空 URL 与空致谢保留原值，不填造项目地址。

[neoforge.mods.toml](../../src/main/resources/META-INF/neoforge.mods.toml)保留声明结构并引用这些属性；`issueTrackerURL`
位于顶层，与 `license` 一起引用对应属性。依赖版本取自版本目录，避免在 TOML 重复维护。

[build.gradle.kts](../../build.gradle.kts)的现有 `processResources` 收集替换值，使用 `inputs.properties` 跟踪输入，并仅对
`META-INF/neoforge.mods.toml` 执行 `expand`。不增加平行模板任务；`build/resources` 是展开输出，不能手工修改。

三个 LowDrag Maven 组独占路由到 Firstdark snapshots；LDLib2 .42 新增的 `dev.vfyjxf:taffy:1.1.5` 独占路由到 Maven Central。此前 IDEA 编译已确认向 NeoForge 仓库请求这些模块发生连接超时；官方 Firstdark 的三个发布模块及 Central 的 Taffy POM／JAR／module 均核对可用。Taffy 在 Firstdark 不存在，不能把所有传递依赖一并路由过去。

依赖范围沿用现有区间规则：Minecraft 精确版本，其他 required 项以当前目录值为下界。区间允许更高版本，不代表本项目已验证未来版本；升级时仍需复核
API、Mixin 目标及行为。

## required 依赖审计

下表区分本项目源码需要、保留的既有要求和 Photon 的间接要求；不按 Gradle `implementation` 机械推导 required。

| 依赖                                              | TOML 处理与 side                  | 当前依据                                                                             |
|---------------------------------------------------|-----------------------------------|--------------------------------------------------------------------------------------|
| Minecraft、NeoForge                               | 保留 required／BOTH               | 项目运行平台                                                                         |
| GeckoLib                                          | 保留 required／BOTH               | 通用实体实现 GeoEntity，动画控制器直接引用                                           |
| Curios                                            | 保留已有 required／BOTH           | 原有声明保留；本轮主源码及手写资源搜索没有发现直接引用，不据此擅自取消               |
| ParticleStorm                                     | 保留已有 required／BOTH           | PhotonGeoEffects 使用 WithCurrentEntity，客户端 GeckoLibHelperMixin 复用其关键帧分发 |
| Photon                                            | 补充 required／CLIENT             | 客户端特效、编辑器及 Mixin 直接引用，无可选加载门控                                  |
| LDLib2                                            | 补充 required／CLIENT             | 指挥家 HUD 与编辑器使用其类型，客户端注册和 UI Mixin 无可选加载门控                  |
| KilaGraph                                         | 补充 required／CLIENT；≥26.1.0.15 | Photon .3 实际 JAR 的间接必要依赖；本项目源码无直接 API 引用                         |
| AnvilCraftLib、JEI、Jade、ReretroDamageIndicators | 不新增 required                   | 本轮主源码及手写资源未发现直接引用；开发类路径和内测包配置不是强制依赖的充分证据     |
| Iris、Sodium、Spark、IMBlocker                    | 不新增 required                   | lib 目录中的开发运行辅助 JAR，主源码无直接引用                                       |
| MixinSquared                                      | 不要求用户另装                    | 现有 jarJar 配置内嵌                                                                 |

`CLIENT` 表示本项目在客户端要求该依赖；不等于该库本身只能安装客户端，也不覆盖该库自己的依赖声明。`BOTH`
表示该声明在客户端和专用服务器都生效。本轮不顺便收窄既有 Curios／ParticleStorm 条目的发行侧。

审计源码入口包括 [ClientRuntimeEvents](../../src/main/java/org/unitego/lobecorp/events/client/ClientRuntimeEvents.java)、[ConductorHud](../../src/main/java/org/unitego/lobecorp/client/conductor/ConductorHud.java)、PhotonGeoEffects（历史路径：`../../src/main/java/org/unitego/lobecorp/client/photon/runtime/PhotonGeoEffects.java`；当前 dev 未保留）
和 [Mixin 配置](../../src/main/resources/META-INF/lobecorp.mixins.json)。第三方 Mixin 位于 client 数组；没有
IMixinConfigPlugin 或 ModList 条件门控，不能当作可选集成。

## 适配与验证记录

本轮通过 IDEA 解析到三个新版本的主 JAR 与源码，并核对发布 JAR 的 neoforge.mods.toml、源码和字节码。实际改动如下：

- 模型运行时与材质预览改用新版 `geometry`／`attributes`／`indices`；法线从几何数组读取，UV 从属性数组读取。按 `quadPaired` 复原原四边面，独立三角形沿用退化四边形提交，保留变换、颜色、透明度、光照和阴影路径。
- 文本组件粘贴 Mixin 从 `onKeyDown` 迁移到 `onExecuteCommand(UIEvent)`；SAVE 路由从 `keyPressed` 迁移到 `dispatchCommand(String,int,int,int)`。原设置判断、文本过滤、历史与当前项目归属处理保持。
- 历史快照的原生 serialize 调用移到静态 `save(ValueIOSerializable)`，录制基础姿态保护随之迁移并改为静态处理器，保留上游自定义 snapshotter 的语义。既有模型测试同步分别检查几何和属性数组的有限性；本轮没有新增或运行测试。
- 原生时间轴 `evaluate`／`localTime` 改为 double，Invoker／Accessor／注入回调及恢复求值同步调整，不再向下取整。模型时间轴复用新增原生传输组件，提供模型播放状态、步进与小数末尾跳转；模型项目的原生播放快捷键指向实际模型时间轴并保留用户绑定。
- 命名动画接管新增逐帧和跳末尾入口，使用独立 playhead 与现有 previewTime／seek，不误操作 FX 主时钟。跳末尾读取原生 contentMax 的小数值；两入口清除排队播放命令并暂停，普通 FX 和模型项目继续各自原路径。
- 新版 FXRuntime 每 tick 恢复根对象倍率；Geo 效果在此入口将已有独立模拟倍率与原生 rate 相乘，避免旧实例速度被覆盖。保持该独立倍率不推进 Timeline 主时钟，保留随后速度轨道的原生覆盖顺序；不对已替换根回调的生成效果重复应用倍率。

修改行通过 IDEA 格式化，关闭导入优化和成员重排。IDEA 全范围诊断覆盖 244 份相关主源码／测试源码，complete=true、0 错误、31 条未使用成员、接口可见性、局部变量及原有空值风险等提示；未扩大范围清理。TOML 模板保留 IDE 对占位符 Logo／模组 ID 的解析警告，展开文件无占位符，Data 实际识别三个新版本及项目元数据。

IDEA 统一编译成功，主源码与测试源码均完成编译；最终确认编译再次成功。Data 配置退出码 0，完整输出 BUILD SUCCESSFUL，19 份资源 written=0；生成目录 25 份文件（含缓存）前后 SHA-256 完全相同，无新增、删除或内容变化。未手工修改生成输出。已核准 Photon 的 61 个目标 Mixin、48 个目标类、67 个宿主方法与 81 个字符串目标及相关 ordinal／slice／局部变量，并检查 LDLib2 窗口、文本、命令、历史、检视、资源、场景和 Gizmo 目标。语义引用及过时调用检索未发现遗留旧网格／长整数求值入口。

本轮没有运行测试，不启动、重启或热替换 Client；界面、渲染外观和专用服务器未验收。Data 中的第三方 JarJar、refmap、可选类与图形驱动提示保留，不能把 Data 成功等同于真实界面验证。未新建备份；保留原有工作区和暂存内容，新文档由 IDEA 自动加入索引，未手动暂存、提交、推送或重置。

官方发布日志说明上游宣称的变化；GitHub 开发分支可能领先发布文件、滞后或采用不同实现。发布日志、分支源码、发布 JAR
和本项目运行结果是不同证据，不能互相替代。适配时以 IDEA 解析的新发布 JAR 为准，不把分支上的方法签名直接当作已发布 API。
