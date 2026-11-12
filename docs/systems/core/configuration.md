# 配置结构与归属

更新：2026-11-01。适用原始dev及已同步本轮提交的工作区。

## 唯一入口

Lobecorp仅注册一个CLIENT与一个SERVER ModConfig。ClientConfig管理lobecorp-client.toml；ServerConfig管理世界级lobecorp-server.toml。ShieldConfig与HordeEventConfig仅贡献分组，不拥有独立Spec或文件。Photon编辑器偏好与工程内参数仍属于编辑器/工程，不合并成服务器全局规则。

按用户选择，不保留旧配置值、不提供旧键别名或旧文件自动合并。删除ClientConfig.migrateLegacy及相关文件复制逻辑；旧lobecorp-horde-events.toml、lobecorp-shield-server.toml与旧客户端分文件不再注册或读取，但不擅自删除磁盘用户文件。新文件/新键由NeoForge按默认值生成。

## 客户端

- 根corpseBatch：批量绘制冻结尸体，默认true，供CorpseBatchRenderer使用。
- events/midnight_cleanup/wave：浪潮启用。
  - display：数量、范围、Iris、动画/片距离、rowSpacing、columnSpacing。
  - sound：聚合清道夫七类音效；sound/soundVoices沿用，soundRange=256、soundMaxActive基础值4（当前按数量×3及频率×10得到逻辑并发120）；eventShake/landingShake默认true，eventShakeRange=64、landingShakeRange=24、shakeStrength=1。声音和震动分别可关闭，真实落地震动不要求存在浪潮。完整频率/距离及未验收边界见音效契约（历史引用：`docs/systems/encounters/sweeper-sounds.md#2026-11-07-接入契约`；本检出无此章节）。
  - particles：近处粒子、生成间隔/组数与范围。
  - debug：统计叠层。
- hud/statusBars：自定义生命条。
- hud/events/midnight_cleanup/timerStyle：唯一的深夜清扫“计时器样式”选项，DEFAULT/NEW，默认DEFAULT；统一控制框、数字与布局。原重复style选项已移除，保留现有timerStyle值，不迁移覆盖用户配置。
- hud/filters：受伤四色与低理智滤镜。
- damageParticles/display、merge、capacity、appearance：启用/寿命、合并、实例容量、描边。
- visualQuality/animations、models、cache：实体动画、模型、缓存；距离迟滞仍属visualQuality根。

行列间距默认1.8格、有效范围0.25至16格。客户端接收事件头、尚未完成线路准备时读取本地值，之后该事件固定使用同一组间距，避免列数、块边界与GPU编号不一致。修改间距对之后新接收的事件生效。间距用于客户端队列、避让、粒子，不改变真实参战实体的出生与战斗配额。

## 服务端

- events/midnight_cleanup/wave：durationTicks、width、corridorMargin。
- events/midnight_cleanup/combat：配额、增援/重试/退出、战斗区域与出生距离。
- events/budget：实体软预算、每刻创建与出生检测额度，共享于当前维度。
- events/network：订阅距离、迟滞、发送字节及路线节点上限。
- combat/shields：多护盾、溢出伤害免除、每级护盾值。
- debug/statistics：服务端事件统计。

HordeRules不再包含、保存或同步row_spacing/column_spacing；其余服务端规则默认值保持。客户端必须更新为读取本地间距的实现；本次不提供旧客户端兼容层。

## 清理与编辑器

移除HordeVisualConfig.LOD_NEAR/LOD_FAR：生产渲染不读取，仅旧Bench环境记录引用；同时移除该两项无效记录，不改变Bench场景ID、夹具负载与行为。保留仍有生产消费者的Iris双缓冲/材质合并等选项。

VisualQualitySettings仍应用到唯一VisualQualityConfig；按第二段路径分组、以去除visualQuality前缀的相对路径翻译，缓存标签随新cache分组更新。工程内遮挡、模型输出和播放参数未删除。

## 验证边界

ConfigStructureTest四项通过：客户端树、服务端树、间距范围和全新TOML保存/读取。HordeRulesTest十八项通过，含服务端快照无间距字段；HordeParticleSamplesTest三项通过。IDEA编译、BenchMod Compile及生产包隔离成功；Data成功生成中英文语言文件。src/generated按项目规则忽略，未手工编辑生成文件。

没有本轮配置界面截图、联机或游戏运行验收；不能以以上结果证明实际界面视觉效果。指定DS未在当前子代理接口提供，本轮未以其他型号替代DS。
