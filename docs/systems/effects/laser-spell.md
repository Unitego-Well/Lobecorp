# 激光法术

核对日期：2026-10-18。`charge_start`、`charge_end`、`laser_start`、`laser_end` 控制蓄力和释放，另提供 `light_ball_start`、`light_ball_end` 与独立 `light_ball_burst`。当前九个对象 UUID、法阵、汇聚粒子、原六条主轨道和用户共用锚点位置保留；光球发射器改名 light_ball 并调用新定义，命名动画库共十五个动画。本文对象序号为当前工程列表的 1 起始位置，实际引用使用 UUID。

2026-10-18 已由女皇 LaserSkill 的同步阶段控制实际播放；技能起点、参数换算、取消清理及独立的精简瞬时星束见[女皇技能特效](queen-beam-skill-effects.md)。资源本身仍可独立编辑与预览。

## 文件与运行入口

- 可编辑工程：[laser_spell.fxproj](../../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/laser_spell.fxproj)。
- 运行定义：[laser_spell.fx](../../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/laser_spell.fx)。
- 独立依赖包：[laser_spell.fxpack](../../../src/main/resources/fxpacks/laser_spell.fxpack)。
- 运行 ID：`lobecorp:the_queen_of_hatred/laser_spell`；默认命名动画：`charge_start`，沿用原组合动画 UUID `1c6a9034-45e8-4066-acdf-12c97dd035aa`。laser_spell 现在是工程／定义名。
- 激光子定义：`lobecorp:the_queen_of_hatred/laser`；调用 laser_start（f16ba3c5-e41d-4df7-ab17-5264908c4e08）和 laser_end（a79046cb-458b-4a3a-8e14-d7bac360bf54）。该定义现在只含光束部分，没有完整 200t 动画。
- 光球子定义：`lobecorp:the_queen_of_hatred/light_ball`；三个阶段的稳定 ID 见[光球](light-ball.md)。原 accumulate_light_ball 工程与定义未删除或覆盖。

游戏内嵌入的 Photon 编辑器从 src/main/resources 入口打开工程，使用透视。跨工程发射器只引用运行定义 ID；不在运行期打开 fxproj、mfxproj、OBJ 或 lcmodel.json。工程内模型模板的 PROJECT 引用仍按对象 UUID 工作，不能改成文件路径。名称与文件约定见[资源规范](../core/resources-datagen.md#文件命名与运行定义)。

## 当前对象索引

| 序号 | 对象名                        | UUID                                 | 父对象                      | 定义或职责                 |
| ---- | ----------------------------- | ------------------------------------ | --------------------------- | -------------------------- |
| 1    | circle                        | 07a7c183-7e59-4599-a6a8-128cc793fa5f | 根                          | magic_circle.fx            |
| 2    | circle2                       | ad1adb30-2be6-42e2-8a06-9ae2b7157e01 | 根                          | magic_circle2.fx           |
| 3    | circle3                       | bdf0c4aa-7019-4e96-b1a0-19c96f6574c0 | circle_empty3               | magic_circle3.fx           |
| 4    | lobecorp_animation_controller | eeadba9d-32a1-4a94-97e6-d20108b540aa | 根                          | 命名动画与组合控制         |
| 5    | light_ball                    | 8cd80510-1bba-4261-914b-50b3dda04a01 | accumulate_light_ball_empty | light_ball.fx              |
| 6    | accumulate_particle           | 83780b63-f4f7-4cfd-8d54-bf43b7b0bdc3 | accumulate_light_ball_empty | accumulate_particle.fx     |
| 7    | circle_empty3                 | d59a4674-944b-40ba-90dd-89aef23c0a85 | 根                          | 第三法阵空间锚点           |
| 8    | accumulate_light_ball_empty   | c36abdf7-2d1c-4964-b134-b10318f0598a | 根                          | 光球、粒子与激光的共用锚点 |
| 9    | laser                         | 8f57e152-3480-4e0c-bd51-29ce0340437b | accumulate_light_ball_empty | laser.fx，单实例           |

以上六个跨工程 ID 均在 `lobecorp:the_queen_of_hatred/` 下。laser 与 light_ball 的本地位置均为 [0, -1.5, 0]，抵消各自工程锚点的 [0, 1.5, 0]，共用用户原空间锚点；激光朝本地 +Z 发射，继承父节点旋转和缩放。

## 四个独立入口

四段独立调用，整体时长由调用方决定。开始动画的制作时长为 20t，完成入场后使用 HOLD 保持状态；结束动画为 20t，结束时使用 CLEAR 清理对应子实例。四段没有固定时长的自动串联；原来用户清空的组合轨道没有恢复。

| 动画         | UUID                                 | 行为                                   |
| ------------ | ------------------------------------ | -------------------------------------- |
| charge_start | 1c6a9034-45e8-4066-acdf-12c97dd035aa | 20t 展开法阵、光球与汇聚粒子，然后保持 |
| charge_end   | d47fa735-1c5a-4cbc-9f5c-a67826ac8958 | 收束蓄力，20t 后清理蓄力子实例         |
| laser_start  | b7678798-39a1-4e35-a1a1-5d92d2d5a089 | 光球独立爆发并启动光束，持续输出至结束 |
| laser_end    | e609c08c-7e5d-4997-9f96-f6dd37a36742 | 光球与光束同时结束，20t 收尾后清理     |

charge_start／charge_end 各十条轨道，复用三法阵与汇聚粒子的原子动画，光球调用改为新定义的启动／结束；laser_start／laser_end 各四条轨道，分别调用光球爆发／结束与光束启动／结束，使用 INDEPENDENT 子调用。

独立光球辅助入口 light_ball_start／light_ball_end 沿用原辅助动画 UUID 810dfdfd-aa23-456f-a21d-78a8fe4f1160／5e93b8d2-82d9-4784-b731-e8a473a69c26，各两条轨道。新 light_ball_burst 的 UUID 为 4796b23f-e57a-44b0-869d-463af128c67a，同样为两条轨道；重复触发不清空已有光球。激光启动与此爆发入口使用 RESTART + clearOnRestart=false：重置 ONE_SHOT 调度计数，保留现存实例；已结束时可生成新实例，避免 CONTINUE 无法补回光球。六个跨工程发射器均为 ONE_SHOT、最多一个实例、instanceLifetime=0；停止由阶段动画负责。laser_start 转发七个参数，laser_end 无需传参，也不覆盖开始阶段的尺寸和强度。修改命名动画或参数时同时更新 UUID 引用。

同一个 PhotonAnimationRuntime 持续接收各阶段调用，结束动画操作其已有子实例。可重播开始动画再次启动；不再使用整个法术实例时，由所属系统调用 finish(true) 释放宿主。示例中的间隔由技能时机决定：

- 蓄力开始：`animations.play("charge_start")`。
- 蓄力完成：`animations.play("charge_end")`。
- 激光开始：`animations.play("laser_start", Map.of("length", new float[]{12}, "width", new float[]{0.952f}, "intensity", new float[]{1}))`。
- 输出结束：`animations.play("laser_end")`。

四阶段及新增 light_ball_burst 已配置同名 TRIGGER 规则，可用 trigger(name, data, inputs) 调用；上述运行采样使用 play 接口。变量属于 laser_start 的播放实例。当前 HOLD 来源完成后，子调用使用完成时的参数快照；本轮没有扩展保持阶段的动态参数跟随。

## 可传参数

| 名称           | 默认值 | 含义                                         |
| -------------- | ------ | -------------------------------------------- |
| length         | 12     | 光束本地长度；末端粒子及三色粒子生成范围同步 |
| width          | 0.952  | 光束本地宽度；末端粒子及三色粒子横向范围同步 |
| intensity      | 1      | 原光柱及源头模型的不透明度倍率               |
| enabled        | true   | 光柱、花瓣及新增粒子的发射开关               |
| muzzle_radius  | 2.8    | 花瓣的本地基准半径                           |
| muzzle_length  | 2.2    | 每份花瓣从 0 拉长到的本地目标长度            |
| muzzle_enabled | true   | 花瓣独立开关，与 enabled 相乘                |

激光整体缩放为 1.2；参数描述本地尺寸，单位格。花瓣每 1 tick 一份、存活 2 tick，轴向在出生时为 0，至 1.5 tick 达到目标长度，随后淡出至消失。光束为 13 个对象、光球为 24 个对象，共享图和粒子配额见[激光](queen-arcana-beam.md)与[光球](light-ball.md)。

## 命名动画预览注意

整工程替换会重建场景，但已经打开的命名动画工作区仍可能持有旧动画库。必要时先保存和捕获快照，再关闭并重开该动画工作区，不覆盖未保存工程。本轮已重建工作区，保留用户透视相机。动画列表切换用于单段编辑预览；连续阶段在同一个运行实例中调用。

暂停状态下直接 MCP effect_preview seek 可能只推进场景时钟，命名动画仍冻结在旧时间。检查单段时通过工作区 scrub(time) 与 sceneView.simulateTo(time) 同步；检查连续调用时，只向前推进同一个场景。本轮蓄力保持 481t 后五个蓄力发射器各一个实例；结束蓄力后归零。激光保持 481t 后仍为一个实例，结束阶段沿用同一个 handle，20t 后归零，并已再次启动两类阶段；各采样 problems=[]。使用 length=6、width=0.5、intensity=0.7 开始后，结束时读取长度仍为 6、内柱宽仍为 0.50420165、强度仍为 0.7。已查看粉白光束、花瓣与粒子画面；没有修改 MCP／编辑器接口的 Java 实现。

## 定义导出与限制

本轮更新 laser／laser_spell 工程、同目录定义及依赖包，并新增 light_ball 的三份文件。原生校验、保存回读、三个完整 ID 加载及包内对象数量检查通过。包分别为 12／17／50 个文件；法术包包含 8 份定义，其中旧蓄力光球定义作为原包遗留依赖保留，新调用只使用 light_ball。自定义 FX 发射器的子定义由原有依赖包与两个新包合并补齐；没有把原生导出 warnings=[] 当作自定义依赖闭包已完整的证据。

最终正式嵌入场景连续调用 charge_start → charge_end → laser_start → light_ball_burst → laser_end → 再次启动：蓄力五个源各一份，释放时光球/光束各一份，重复爆发仍为一份，结束后两份归零。length=6 的光束及开始/结束参数继承检查通过，父子 problems=[]；已查看透视蓄力/激光截图，不作为性能或全部角度验收。整工程更新后关闭并重开命名工作区，避免旧库继续控制新运行树。

本轮资产字节备份、实时法术工程、原生阶段回执与包回执见拆分备份（历史引用，当前工作区缺失：`../../backups/laser-light-ball-split-20261018`）。未修改共享模型、ShaderGraph、PNG、模板或实体技能。随后已实现技能绑定与精简星束，实际改动及未进行实机验收的边界见[技能特效](queen-beam-skill-effects.md)。

此前 counterattack.mfxproj 缺少 renderer/playback 的导出限制、四阶段及首次集成历史见四阶段备份（历史引用，当前工作区缺失：`../../backups/laser-spell-phases-20261016/README.md`）与首次接入备份（历史引用，当前工作区缺失：`../../backups/laser-spell-integration-20261016/README.md`），本轮没有处理该模型工程。
