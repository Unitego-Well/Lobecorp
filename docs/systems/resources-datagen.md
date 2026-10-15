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
