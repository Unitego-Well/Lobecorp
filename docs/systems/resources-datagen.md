# 资源、翻译与数据生成

核对日期：2026-10-05。本文记录生成源与资源边界；具体运行结果见 [修改记录](changes.md)。

## 输入与输出

| 路径/入口                                                                                                                                                                                      | 作用与修改边界                                                       |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| src/main/resources                                                                                                                                                                             | 手写运行资源、Mixin 配置、元数据、纹理、模型、动画与特效资源         |
| src/generated/resources                                                                                                                                                                        | Data 输出，已接入 main resources；只读，不手写修改                   |
| resources/ldlib2                                                                                                                                                                               | 编辑器导出/参考资源；是否进入发行包以实际资源引用与构建配置为准      |
| lang/en_us.json、lang/zh_cn.json                                                                                                                                                               | 仓库手动翻译文件，不自动等同于运行时语言资源                         |
| [ModGenerator](../../src/main/java/org/unitego/lobecorp/generator/ModGenerator.java)                                                                                                           | GatherData 统一添加语言、粒子、物品模型、实体标签及伤害标签 Provider |
| [TranslationKeys](../../src/main/java/org/unitego/lobecorp/util/TranslationKeys.java)、[LangHandler](../../src/main/java/org/unitego/lobecorp/generator/lang/LangHandler.java)                 | 翻译键与语言登记来源                                                 |
| [BasicLangGenerator](../../src/main/java/org/unitego/lobecorp/generator/lang/BasicLangGenerator.java)                                                                                          | 手动条目合并与 LangSet 输出                                          |
| [ParticleGenerator](../../src/main/java/org/unitego/lobecorp/generator/ParticleGenerator.java)、[ItemModelProvider](../../src/main/java/org/unitego/lobecorp/generator/ItemModelProvider.java) | 粒子描述与物品模型生成                                               |

TranslationKeys.init 在生成入口初始化集中翻译定义；LangHandler.creates 在数据生成环境登记名称，Supplier 保留注册对象延迟访问。粒子生成源覆盖
Slash、Blood 与短烟描述；其他粒子应核对其对应登记与资源，不假定均由该 Provider 生成。

## 文件命名与运行定义

游戏与开发文件名采用英文。用户以中文提出新名称时，先确认对应的英文形态，再创建或重命名文件；普通文档可使用中文文件名，被游戏读取的文档也必须使用英文文件名。特效对象、命名动画与自定义变量的新名称仅使用小写字母和 `_`；不据此批量改名已有用户资源。

本次确认“激光法术”对应 `laser_spell`，“激光”对应 `laser`，工程文件分别为 `laser_spell.fxproj`、`laser.fxproj`。工程文件保留用于编辑，跨工程的特效调用使用 `.fx`／`.mfx` 运行定义，不以 `.fxproj`／`.mfxproj` 作为运行依赖。修改工程后通过原生导出器更新对应定义，保持资源 ID、子动画 UUID、模型及材质依赖一致。

## 服务端模型与动画内容

新的服务端动画内容仓库读取 `data/<namespace>/animation/models/<path>.geo.json` 与 `data/<namespace>/animation/animations/<path>.animation.json`，对应 ID 为 `<namespace>:<path>`。模组内置默认内容位于相同 data 路径，外部数据包按标准优先级覆盖；完整 GeoJSON 与动画 JSON 按内容版本同步，运行中实例保留开始时的版本，只有显式 `editContent` 修改本实例。

现有 `assets/<namespace>/geckolib/models`、`assets/<namespace>/geckolib/animations` 与纹理资源不迁移。本地相同 ID 的模型／动画可覆盖客户端视觉，不改变服务端位置和命中；纹理仍由客户端资源提供，不加入新增内容传输。仓库、代码包装、独立显示登记与重载边界见[服务端动画、根运动与空间附着](animation-runtime.md)。本轮未新增对应 Data Provider，不直接写 generated 输出。

独立接入示例使用 ID `lobecorp:example/attachment_draw`，新增三份手写输入：

- [完整模型](../../src/main/resources/data/lobecorp/animation/models/example/attachment_draw.geo.json)：`data/lobecorp/animation/models/example/attachment_draw.geo.json`。
- [服务端动画](../../src/main/resources/data/lobecorp/animation/animations/example/attachment_draw.animation.json)：`data/lobecorp/animation/animations/example/attachment_draw.animation.json`。
- [客户端动画覆盖](../../src/main/resources/assets/lobecorp/geckolib/animations/example/attachment_draw.animation.json)：`assets/lobecorp/geckolib/animations/example/attachment_draw.animation.json`。

示例没有同名 assets 模型，客户端直接使用同步模型，并复用原版白混凝土纹理。两份动画均长 6 秒，客户端版本只增加 2.25 秒手部抬高并加倍头部摆动频率，保留 1.5／3 秒交接端点和根运动消费契约；数据包仍可覆盖服务端同 ID 底稿。以上文件不是 Data 输出，未新增纹理传输或 Provider；命令、代码包装、刀的客户端物品显示与清理见[独立拔刀接入示例](animation-runtime.md#独立拔刀接入示例)。

## 手动语言合并的实际路径

BasicLangGenerator.mergeManualEntries 使用 user.dir 的父目录，再拼接 lang/<locale>.json。build.gradle.kts 的 Data 工作目录配置为
run/data；若实际 user.dir 等于该目录，候选位置为 run/lang，而不是仓库根 lang。

此处是源码路径公式和条件推导，尚未通过运行期读取证明手动文件已被合并。不要据此删除根 lang 文件或修改生成结果；需要调整合并逻辑时另行确认范围并检查实际
Data 运行配置。

## 修改前检查

先确认资源是否由 Provider 生成、是否被运行时注册/API 引用、资源 ID 是否集中定义。修改翻译键应改 TranslationKeys、LangHandler
或对应生成源，再通过 IDEA Data 重生成并检查 Git 结果。用户提供 JSON、NBT、第三方和生成输出不执行自动格式化。

Mixin 类迁移需要保持 lobecorp.mixins.json 的客户端分组、注入目标与相对类名有效；资源名称、NBT 字段和协议不能因包整理改变。Photon
与模型资源分别见 [编辑器](photon-editor.md)、[粒子渲染](particle-rendering.md)、[动画](animation.md)。

## 当前边界

只读取受版本管理的输入及生成入口，不读取运行世界、日志凭据或环境秘密。本次不新增 Provider，也不手工改写生成目录。修改后登记实际编译/Data
退出状态和生成差异，不能沿用旧一轮成功结果。

## 开发环境源码资源直读（2026-10-15）

[DevelopmentResourceUtil](../../src/main/java/org/unitego/lobecorp/util/resource/DevelopmentResourceUtil.java) 仅在 Platform.isDevEnv() 为 true 且源码 assets 目录存在时启用。标准 Client／Data 的 run/client／run/data 工作目录向上两级定位 src/main/resources；自定义运行目录可通过 JVM 属性 lobecorp.developmentResources 指定包含 assets 的源码资源根目录。显式配置不存在时抛出异常；发行环境不读取该属性或源码目录。

- LDLib2 可编辑根目录为 src/main/resources/assets；原生 CustomResourcePack 的 ldlib2 挂载根指向 src/main/resources，使 GUI、纹理、模型和 Photon 定义走同一资源管理器。
- Photon 的开发用 FXPack 目录为 src/main/resources/fxpacks，仍使用原生发现、挂载、导出和缓存；本机 fxpack_cache 仍在 run。
- FilePath 将源码资产保存为相对游戏目录的 ./../../src/main/resources/assets/...；正式运行定义使用 file(assets/...)，依赖通过资源 ID 读取。没有旧 tutorial 工程格式或旧路径回退分支。
- EditorLayoutStore、EditorProjectStore、ResourceTabLayoutStore、UIDebuggerWindowState、DialogSizeStore 的本机状态仍指向 run 下的 ldlib2 目录，不能随 getAssetsDir 的开发重定向写进源码。

### 开发目录分类

开发环境的 AssetBrowser 与原生打开／保存文件弹窗提供 `run`、`src/main/resources` 两个独立目录入口。资源管理器初次使用原生 ldlib2 默认根时切到源码根；选择 run 后只浏览 run/client，选择 src 后只浏览源码资源目录，不将两边条目混入同一树。显式指定工作区外的目录保持原生行为。保存弹窗切换入口时保留文件名，并复用原生导航更新树根、选中项与确认操作。

`DevelopmentResourceUiUtil` 只在开发源码目录有效时增加选择器；发行环境不增加入口，也不改变默认打开、保存和资源浏览行为。原生 AssetBrowser 构造器直接使用 gamePath/ldlib2，未经过 getAssetsDir；仅重定向 getAssetsDir 无法让资源管理器显示 src，这是此前遗漏的入口。

开发资源管理器在源码包之前额外挂载 run/client/ldlib2，保留 run 中资源的运行时读取；相同资源 ID 由源码目录优先。Photon MCP 的允许目录同步包含源码 assets／fxpacks 与 run/client/ldlib2/assets、run/client/photon/fxpacks，相对路径仍默认基于源码 assets。Photon 原生 run 包目录为 photon/fxpacks，不是 ldlib2/fxpacks。本机状态目录不变，不增加每帧文件扫描或复制同步。

本次通过 IDEA／IDEA Debug 调用原生 NBT 和 Photon API 迁移 54 份文件，并复用 17 张字节完全相同的女皇纹理；额外生成 queen_star_beam 的正式定义和依赖包。当前目录与使用入口见 [女皇星束资源](queen-star-beam.md)。仅移除教程特效的废弃版本、旧导出、旧备份及迁移后副本；用户工程、独立参考特效和本机编辑器状态保留。根 resources/ 的历史参考输入未纳入这次 run 迁移。

build.gradle.kts 的 sourceSets.main.resources 排除 **/*.fxproj、**/*.mfxproj、**/*.zip、**/*.bbmodel；工程仍可在源码目录中编辑，但不进入主资源输出。运行需要的 .fx、.mfx、.fxpack、.mfxpack、材质、着色器图和纹理保留，不扩大用户指定的排除范围。

维护要点：不能仅替换 getAssetsDir，也要同步资源包根、相对路径和本机状态存储。模型的 asset／assetJson 是 UTF-8 字节数组，仅扫描 NBT 字符串会漏掉模型内部引用；迁移必须同时更新结构化材质依赖与内嵌 JSON，并用原生 NBT 回读核对。资源新增后如需新命名空间／新包发现，沿原生资源重载生命周期处理，不在每帧扫描磁盘。

### 源资源包装与原始文件同目录（2026-10-16）

能明确对应原始 PNG、模型或着色器文件的包装资源，与原文件放在同一目录，按实际引用确定位置。新增同类包装沿用此规则，原始资源 ID 保持稳定；迁移时备份原文件，并同步资源目录 provider、工程中的路径和内嵌 asset／assetJson 引用。

当前 14 份女皇纹理材质包装均位于 assets/lobecorp/textures/particle/the_queen_of_hatred，与各自 PNG 并排；原有 13 份之外新增 laser_tail.material.nbt，对应同目录 laser_tail.png。例如 [particle.material.nbt](../../src/main/resources/assets/lobecorp/textures/particle/the_queen_of_hatred/particle.material.nbt) 对应同目录 particle.png。counterattack 的 OBJ、lcmodel.json 和 mfxproj 原已同目录。ShaderGraph 是原始图资产，material.meta.nbt 是资源管理器配置；这两类按各自用途保存。

本轮只读检查 64 份当前源资产后，确认实际改动为 13 份包装移动、五个工程的九处材质路径，以及 material.meta.nbt 的两个 provider 目录。保留原包装字节、PNG 内容、显示名称和用户工程其他数据，使用原生 NBT 按原压缩格式保存并回读；运行定义与依赖包继续由原生导出器生成。备份见[迁移记录](../../backups/queen-arcana-star-sprites-20261016-012856/README.md)。
