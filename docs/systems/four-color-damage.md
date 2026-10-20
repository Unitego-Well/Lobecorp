# 四色伤害与生物资源

2026-10-19 实现；目标为 Minecraft 26.1.2、NeoForge 26.1.2.100、Java 25。检查结果以本页末尾和[修改记录](changes.md)为准。

## 入口与类型

类型枚举为 `api.LcDamageType`，沿用旧项目命名：`PHYSICS`／`physics`、`SPIRIT`／`spirit`、`EROSION`／`erosion`、`THE_SOUL`／
`the_soul`。对应 Tag 由 `LcDamageTypeTags` 登记，属于伤害类型注册表。

以 `DamageSource.is(TagKey)` 的有效标签识别类型，包含现有动态增删与原始标签排除机制。无四色标签的来源继续走原有流程，不默认归为物理；本次没有给已有攻击或原版伤害类型添加四色标签。Data
只生成四个空 Tag。

`IDamageSourceExpand` 提供：

- `lobecorp$setLcDamageType(type)`：清除其他四色标签后设置单色，保留无关标签。
- `lobecorp$addLcDamageType(type)`／`lobecorp$removeLcDamageType(type)`：追加／移除一个类型。
- `lobecorp$getLcDamageTypes()`：读取当前有效类型的不可修改集合。

修改作用于该伤害来源实例；单次攻击应使用独立的 `DamageSource`，避免修改供其他攻击复用的缓存来源。

```text
DamageSource source = attacker.damageSources().mobAttack(attacker);
source.lobecorp$setLcDamageType(LcDamageType.PHYSICS);
source.lobecorp$addLcDamageType(LcDamageType.SPIRIT);
target.hurtServer(level, source, 10.0F);
```

此例的物理和精神通道各自使用基础值 10，随后分别计算各自的属性与减伤；并非把 10 分成两份。

## 属性与结算

所有生物（含玩家）经既有 `EntityAttributeModificationEvent` 入口补齐缺失属性，不覆盖实体已经声明的值。

| 类型 | 承伤系数属性          | 默认系数 | 固定防御属性       | 默认防御 |
|------|-----------------------|---------:|--------------------|---------:|
| 物理 | `PHYSICS_VULNERABLE`  |        1 | `PHYSICS_DEFENSE`  |        0 |
| 精神 | `SPIRIT_VULNERABLE`   |        1 | `SPIRIT_DEFENSE`   |        0 |
| 侵蚀 | `EROSION_VULNERABLE`  |      1.5 | `EROSION_DEFENSE`  |        0 |
| 灵魂 | `THE_SOUL_VULNERABLE` |        2 | `THE_SOUL_DEFENSE` |        0 |

资源 ID 为上表字段的小写形式。防御与系数范围沿用旧项目的 −1024～1024，并使用 Neo 属性同步。

先由原有 Incoming 事件应用全局 `DAMAGE_TAKEN_MULTIPLIER` 和阵营资格，再由一次原版受击流程处理命中与无敌帧。每个标记类型使用同一份有效基础值：

- 系数为 0：直接免疫，负防御也不能打破免疫。
- 系数为负：吸收 `基础值 × 系数绝对值`，跳过固定防御；物理／侵蚀恢复生命，精神恢复理智，灵魂恢复灵魂值。
- 系数为正：`max(0, 基础值 × 系数 − 对应固定防御)`；负防御提供固定增伤，防御产生的负结果不会吸收。
- 非正基础值不因负防御生成伤害，也不产生吸收。

正系数物理伤害继续计算原版护甲、韧性、保护附魔、抗性提升；侵蚀跳过护甲和韧性，也不读取物理防御，但仍受自身侵蚀防御／系数、保护附魔与抗性提升影响。精神和灵魂不进入这些生命减伤步骤，只改变对应资源。

盾牌只格挡物理／侵蚀；混合攻击仍为未被盾牌阻挡的精神／灵魂保留本次受击资格。原版无敌、创造模式、阵营资格及无敌帧仍由引擎处理，无敌帧可通过原有
`BYPASSES_COOLDOWN` Tag 绕过。

物理和侵蚀正伤害在各自新属性与适用的原版护甲处理后，合并经过共同的保护／抗性及既有 Pre
处理（包含清道夫生物质）。其结果与本次生命吸收量抵消后，只把非负结果交还原版生命／吸收生命流程；剩余回血经 `heal`
处理。不会先造成一次致死扣血再补回血。精神、灵魂及剩余回血在本次被接受的实际受击流程结束时提交，纯资源伤害也沿用一次受击动画、击退及既有
Post 仇恨记录。

## 资源、同步与效果入口

2026-10-21：新增四色吸收护盾，与当前四色伤害管线组合；生命通道在共同减伤及 Pre 后按通道权重分摊，
资源通道在接受命中后结算。负系数恢复不消耗护盾，世界特效渲染留空。
HUD、护盾容量／生命周期、破盾免伤配置及资源迁移边界见[玩家 HUD、滤镜与护盾](player-hud-shields.md)。
以下“本轮没有实现 HUD”为 2026-10-19 的历史检查边界。

`MAX_RATIONALITY`／`max_rationality` 和 `MAX_THE_SOUL`／`max_the_soul` 是独立、可同步的上限属性，默认均为 20，最小为
0。首次建立资源时填满；当前值始终限制在 0 与上限之间，不自然恢复。

当前值和上次上限保存在 `LcResourceState`，使用实体附件 `lobecorp:lc_resources` 持久化并通过 Neo 附件自动同步，不新增
Payload。加载已有数据保持原值，上限变化按原百分比缩放；旧上限为 0 时没有可延续的比例，新上限出现后仍保持空值。死亡重生不复制该附件，重新填满；正常非死亡实体复制沿用
Neo 附件机制。

`ILivingEntityExpand` 经 `META-INF/interfaces.json` 注入 `LivingEntity`，提供 `lobecorp$getRationality`／`getTheSoul`
、对应上限、设置和恢复方法。写入口只在服务端生效；现有 `LivingEntityEvents` 的服务端 tick 负责初始化与上限比例调整，伤害与直接读写入口也会核对最新上限。

资源实际变化后，服务端发送 `LivingResourceChangeEvent`（位于 `event.damage`
）；事件包含实体、SPIRIT／THE_SOUL、前后数值及可空来源。后续阈值、归零和恢复效果通过此入口接入。本轮没有实现精神归零、灵魂惩罚、自动恢复或
HUD。

`LivingEntityMixin` 的单次上下文在 `finally` 中恢复，嵌套受击互不覆盖；`PlayerMixin` 适配玩家自己的 `actuallyHurt`
。两个目标共用结算入口，原版扩展通过 Interface Injection 暴露。

## 本次检查

已通过 IDEA 编译，并在 Interface Injection 产物更新后完成全部本次 Java 文件的错误检查，无错误；仍有预留接口未使用、可选
final、借用世界资源等编辑器提示，未为消除提示改变修饰符或关闭世界。新建类及既有文件修改行已通过 IDEA 格式化，关闭导入优化和成员重排。

IDEA 的 Data 运行结果为 BUILD SUCCESSFUL；23 个生成文件中写入 6 个、移除 0 个，已只读核对四个空伤害 Tag 和两种语言各 10
个属性名、4 个 Tag 名。日志确认本次 LivingEntity／Player Mixin 已加载；这属于 Data
启动检查，不等于游戏战斗、存档往返或联机行为验收。生成目录沿用原有忽略规则，不手改或强制纳入提交。

未执行游戏、联机或测试，未删除文件，未另建文件备份；保留工作区已有修改和暂存，提交范围仅限本次实现及文档。既有系统文档的表格样式提示和历史失效链接不在本次修改范围。
