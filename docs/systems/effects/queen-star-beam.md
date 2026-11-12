# 女皇星束资源

核对日期：2026-10-15。由教程 V3 正式命名迁移，当前运行 ID 为 `lobecorp:the_queen_of_hatred/queen_star_beam`。

此工程作为可复用模板保留。2026-10-16 的粉白黑女皇变体已改名为 [laser](queen-arcana-beam.md)，并接入[激光法术](laser-spell.md)，使用独立专用图，不覆盖本模板的工程、定义、包或共享着色器。

## 文件入口

| 用途             | 仓库路径                                                                                           |
|------------------|----------------------------------------------------------------------------------------------------|
| 可编辑工程       | src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/queen_star_beam.fxproj                   |
| 运行定义         | src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/queen_star_beam.fx                       |
| 独立依赖包       | src/main/resources/fxpacks/queen_star_beam.fxpack                                                  |
| 9 份蓄力／持续图 | src/main/resources/assets/lobecorp/shaders/photon/queen_star_beam/charge                           |
| 7 份激光／首爆图 | src/main/resources/assets/lobecorp/shaders/photon/queen_star_beam/beam                             |
| 火花图           | src/main/resources/assets/lobecorp/shaders/photon/queen_star_beam/particles/spark.shader_graph.nbt |

工程保留 31 个对象，200 tick（10 秒）时序及原有几何、UV、粒子配置和动画。命名动画名称为 `queen_star_beam`；length 默认 12、width 默认 0.952、intensity 默认 1、enabled 默认 true，沿用迁移前的形参绑定，不改变战斗命中、射程或实体技能行为。

## 编辑与加载

开发客户端的原生资源浏览根为 src/main/resources/assets。进入世界后，在嵌入游戏窗口的 Photon 编辑器打开上述 .fxproj；MCP effect_open 的 path 可使用完整工程路径，或相对资产根的 lobecorp/fx/the_queen_of_hatred/queen_star_beam.fxproj。原控制模式、覆盖和路径门禁仍适用。工程文件不进入主资源输出；原生 FXHelper 按新运行 ID 读取 .fx。

工作区目录、路径编码及发行环境边界见 [资源与生成](../core/resources-datagen.md#开发环境源码资源直读2026-10-15)。定义中的图引用采用 file(assets/...)，不会依赖接收方机器上的源码目录；包包含对应定义和 17 份内容寻址图，原生导出 warnings=[]。

再次导出此包须保留完整 ID：原生 FXPackExporter.exportInto 的 namespace 使用 lobecorp，fxName 使用 the_queen_of_hatred/queen_star_beam。本轮通过 IDEA Debug 调用该 API；GUI 原导出按包文件名决定 namespace，MCP 原导出按文件名决定 fxName，不能把这两个默认值误当作当前嵌套 ID。本轮未扩展它们的命名接口。

## 同时保留的资源

- 用户女皇 .fxproj 保存在 assets/lobecorp/fx/the_queen_of_hatred；counterattack 模型工程、OBJ 和 JSON 保存在 assets/lobecorp/models/photon/the_queen_of_hatred。
- 13 份用户材质包装与对应 PNG 并排保存在 assets/lobecorp/textures/particle/the_queen_of_hatred；17 张原始纹理继续复用原文件。目录约定见[资源说明](../core/resources-datagen.md#源资源包装与原始文件同目录2026-10-16)。
- 三份 conductor GUI 文件保存在 assets/lobecorp/gui；两个独立 reference 特效的工程／定义保留在 assets/lobecorp/fx，相应包及模型验收包保留在 fxpacks。
- 本机布局、最近项目、窗口位置仍留在 run，根 resources/ 的历史参考输入未清理。旧 tutorial_shockwave、refined、v3 的活动文件已移除；历史文档仍保留当时路径作证据。

## 实际检查

已通过 IDEA 编译及 Client Debug 的新 ID 加载／列举、31 对象、全部 17 图按资源 ID 读取、GUI／OBJ 读取、源码根和布局／最近项目路径检查。迁移前 144 文件备份逐文件校验通过；迁移采用原生 NBT 往返检查，并更新模型 asset／assetJson UTF-8 字节数组中的引用。未在本轮重新进行原视频外观、完整编辑器交互或发行环境运行验收。

最终 Data 退出 0，19 文件写入 0／删除 0；主资源输出没有 fxproj、mfxproj、zip、bbmodel，运行 .fx 已进入输出。Minecraft MCP 实际列出源码目录全部 17 图，无截断。完整 JAR 未另行构建检查。
