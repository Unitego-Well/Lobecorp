# 激光

核对日期：2026-10-18。由 `queen_star_beam` 模板复制制作，模板工程、定义、依赖包及 17 份共享图保持原样。参考为[阿卡纳光破斩](https://www.bilibili.com/video/BV1QUghz7ELE/)，本版为拆分后的独立光束，配色以粉色为主；不是视频逐像素复刻验收。

## 文件与打开方式

| 用途       | 路径                                                                                         |
| ---------- | -------------------------------------------------------------------------------------------- |
| 可编辑工程 | [laser.fxproj](../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/laser.fxproj) |
| 运行定义   | [laser.fx](../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/laser.fx)         |
| 依赖包     | [laser.fxpack](../../src/main/resources/fxpacks/laser.fxpack)                                |
| 专用着色器 | src/main/resources/assets/lobecorp/shaders/photon/queen_arcana_beam/                         |

游戏内 Photon 编辑器打开上述工程；开发环境选择 `src/main/resources` 入口，再进入 assets/lobecorp/fx/the_queen_of_hatred。MCP 相对打开路径为 `lobecorp/fx/the_queen_of_hatred/laser.fxproj`。目录分类和发行环境边界见[资源目录](resources-datagen.md#开发目录分类)。

运行 ID 为 `lobecorp:the_queen_of_hatred/laser`。当前仅提供 laser_start（f16ba3c5-e41d-4df7-ab17-5264908c4e08）与 laser_end（a79046cb-458b-4a3a-8e14-d7bac360bf54）；默认入口为 laser_start。原完整 200t laser 动画已从该工程移除，模板和修改前备份保留。光球、汇聚和首爆移入[light_ball](light-ball.md)，组合调用见[激光法术](laser-spell.md)。

## 层次、方向与阶段

共 13 个对象：空间锚点、控制器、内光柱、尘埃外柱、源头尘雾、反向花瓣模板及生成器、粉/黄/蓝三层星粒、发射尖刺、白线与 laser_tail。laser_start／laser_end 分别 40／23 条轨道；普通主时间轴已清空，由命名入口控制，没有固定的总输出时长。

laser_start 的入场制作时长为 20t，然后 HOLD 保持输出；laser_end 为 20t 收尾，停止持续生成并清理。结束复用开始阶段的长度、宽度和强度。同一个运行实例由调用方决定启动和结束时机；同名 TRIGGER 规则也可调用。

光柱沿本地 +Z，从源头用 1t 推进至全长；锚点等比缩放 1.2，位置 [0, 1.5, 0]。反向花瓣开口沿 -Z，每 1t 生成一份、存活 2t、最多 3 份；轴向从 0 拉长，1.5t 达到 muzzle_length 后淡出。模型、UV、共享图、颜色、粒子配额和修复渲染配置沿用拆分前资产。

## 使用变量

| 名称           | 默认值 | 范围        | 含义                                                   |
| -------------- | ------ | ----------- | ------------------------------------------------------ |
| length         | 12     | 0.05～64    | 内外光柱长度，单位格                                   |
| width          | 0.952  | 0.05～4     | 外柱参考宽度；内柱按模板原有比例绑定                   |
| intensity      | 1      | 0～1        | 光柱及源头壳层的不透明度倍率，不是全局 HDR 曝光        |
| enabled        | true   | false／true | 光柱、源头尘雾、反向壳层及光束附属粒子的开关 |
| muzzle_radius  | 2.8    | 0.05～8     | 花瓣的本地基准半径；再乘实例放大曲线与整体 1.2         |
| muzzle_length  | 2.2    | 0.05～8     | 花瓣拉长的本地目标长度；每次出生从 0 开始              |
| muzzle_enabled | true   | false／true | 花瓣独立开关，与 enabled 相乘                          |

变量属于命名动画播放实例。默认播放使用默认值，重播不会沿用前一实例的临时参数。光球的阶段和尺寸由独立 light_ball 工程控制；length／width 不是整个法术的统一缩放。

长度与宽度变量均为本地值，最终尺寸乘整体锚点的 1.2 倍；默认光柱长度为 14.4 格。花瓣生成器开启 inheritScale，与直接光束层一同放大。粉、黄、蓝三层序列粒子沿光束周围的盒壳分布，shape 的长度和中心随 length、横向范围随 width 绑定；每层 0.4 粒子／tick、上限 16。laser_tail 在末端按 1×4 帧播放，每 2 tick 一颗、寿命 6 tick，位置随 length、大小随 width 更新，材质包装与原 PNG 同目录。

white_lines 每 tick 4 颗、上限 32，初始大小 0.02～0.033、拉伸系数 140，沿 +Z 运动；寿命按 length / 3 绑定。首爆 launch_spikes 在 1 tick 内生成 28 颗前冲尖刺、上限 32，原 burst_sparks 改为每 tick 16 颗持续 2 tick、上限 48。普通时间轴和命名动画的生成、参数与开关轨道同步。

以下章节保留改名前各轮操作记录，仅作历史证据；当前路径、参数与轨道数量以上述内容为准。

## 三色序列星粒与资源目录（2026-10-16）

新增 31 粉色、32 黄色、33 蓝色三个独立持续星粒层，采用 particle.png、particle2.png、particle3.png。三张原图分别为 16×112、16×112、16×80，UV 使用 1×7、1×7、1×5；在粒子寿命内单次播放缩小序列，末帧不回绕。三层均随持续阶段从 20 tick 起播放，使用全向球形发射、Exacting、每层 0.25 粒子／tick、上限 32；保留原图颜色与原有两层持续粒子。

材质通过 UIResourceMaterial 引用同目录包装文件，可继续在编辑器中编辑。13 份同类材质包装已全部与对应 PNG 并排放在 assets/lobecorp/textures/particle/the_queen_of_hatred；PNG 资源 ID 保持原样。资源目录配置和五个用户工程中的 11 处旧路径已同步，目录约定见[资源说明](resources-datagen.md#源资源包装与原始文件同目录2026-10-16)。

本轮通过 IDEA Debug 的原生复制、校验与可撤销事务创建三层，原生保存 dirty=false。运行定义更新 38 处便携路径，依赖包完整 ID 保持不变，包含 23 文件，warnings=[]。IDEA 编译通过，Data 退出 0；未新增视觉、性能、联机或发行环境验收。修改前工程、未保存快照、13 组原始资源和受影响引用文件均有[备份](../../backups/queen-arcana-star-sprites-20261016-012856/README.md)。

## 持续粒子与反向长度（2026-10-16）

10 持续向外输出和 17 持续星粒改为全向球形发射，覆盖全部空间方向，发射半径仍为本地 0.25。两层发射率分别由 1、0.25 提升为 2、0.5 粒子／tick，保持 Exacting 累计模式、每粒子无延迟和无突发；数量上限仍为 128、32，原有寿命与速度保留。

反向半椭圆本地长度、主时间轴基值、命名动画基值及 muzzleLength 默认值同步设为 0.5 / 1.2，最终长度为 0.5，最终直径仍为 2.2。原生保存时 dirty=false；收尾回读为 31 tick 暂停、dirty=true，保留保存之后的编辑器状态。首次导出回执超时，已核对实际定义和包均已写入，并通过原生反序列化读到 28 对象及本地长度 0.41666666；本轮未取得导出 warnings 回执，未执行编译、Data、性能或新增视觉采样。

修改前磁盘工程、定义、包和含未保存修改的实时工程备份于 backups/queen-arcana-particles-20261016-011008，见 [live-before.fxproj](../../backups/queen-arcana-particles-20261016-011008/live-before.fxproj)。

## 2026-10-16 尺寸调整

本轮仅修改整体缩放、五个模型生成器的缩放继承和反向开口宽度，连同主时间轴基值、命名动画基值及 muzzleRadius 默认值同步调整；保留当前工程未保存的其他修改。原生保存 dirty=false，批量编辑后时钟停在 0 tick，保留用户透视相机。定义与依赖包重新导出，包 ID 保持完整，17 文件、warnings=[]。本轮没有修改着色器或 Java，也没有执行编译、Data、测试或新增视觉采样。

修改前的磁盘工程、定义、包，以及包含未保存编辑的工程已备份至 backups/queen-arcana-resize-20261016-005840，其中 [live-before.fxproj](../../backups/queen-arcana-resize-20261016-005840/live-before.fxproj) 保留本轮修改前的实时编辑内容。

## 初版交付的保存、导出与实际检查

工程已通过原生保存及回读比较，dirty=false。定义导出时将 35 个源码路径字段转换为可发布的 file(assets/...)，同时处理模型 UTF-8 字节数组中的引用；当前工程仍保留源码制作路径。定义已由 FXHelper 按完整 ID 实际加载，返回 28 个对象，16 份便携图引用均通过原生编译。

依赖包使用原生 FXPackExporter.exportInto，namespace 为 lobecorp、fxName 为 the_queen_of_hatred/queen_arcana_beam；输出 17 文件、warnings=[]，包内 ID 已列举核对。默认 GUI／MCP 包导出仍受[模板文档的命名限制](queen-star-beam.md#编辑与加载)约束；本轮没有改写导出命名接口。

已通过 IDEA Debug 检查默认值、缩短／变窄／降低强度、两类开关与重播恢复：长度 6、外柱宽 0.5、强度 0.4、反向半径 1.4、长度 0.6 的组合生效，problems=[]。属性采用运行时组合值查询，不能以采样作用域之外的基础 config 值判断变量失效。16 图的原生编译错误均为空。

已查看 10、23、80、195、200 tick 的透视截图和侧向角度，200 tick 无可见残留。尚未进行发行环境启动、实体技能接入、联机或独立性能采样；截图中的后台 FPS 不作为性能结论。20 份模板原文件与修改前备份逐份 mismatch 一致，见[备份](../../backups/queen-arcana-and-resource-roots-20261015/README.md)。

最终 IDEA Data 退出 0，生成目录无 Git 差异；主资源输出包含新定义、依赖包及 16 份字节一致的图，不包含新 fxproj。相关七个 Java 文件与 Mixin 配置诊断无错误／警告，新文档两张表仅局部对齐后诊断为空。收尾时游戏主窗 maximized=1、编辑器 embedded=true、透视、80 tick 暂停且 dirty=false；资源管理器已定位新工程所在源码目录，鼠标已释放并退出 MCP 控制模式。
