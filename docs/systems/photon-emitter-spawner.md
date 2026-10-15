# Photon 发射器发射器

核对日期：2026-10-10。基于 Photon 26.1.2.3、LDLib2 26.1.2.42；类型 ID 为
`lobecorp_emitter_spawner`。本类型按调度创建已有发射器/FX 的独立实例，自身不生成 TileParticle，也不提交几何或材质。
效果中的普通粒子、光束、拖尾仍使用 Photon 原更新、渲染与寿命逻辑。

## 编辑器使用

1. 在 Photon 原对象层级的添加菜单中选择“发射器发射器”。
2. 在监视面板直接选择“项目发射器”或“已保存 FX 资源”；不再套同名外层折叠组。
3. 项目来源：把层级中的发射器拖到“发射器模板”；按需选择是否包含其子对象。“移除”按钮清除引用，不删除模板或已有实例。
4. 资源来源：在“FX 资源”中搜索已加载资源 ID。需要带入完整 FX 全局时间轴及其轨道时使用此来源；项目模板可带入其范围内的命名动画。
5. 设置发射模式、数量/速率、延迟、周期和结束行为，再配置形状、继承与实例参数。
6. 场景信息显示存活实例数、累计生成数与最近发射状态；配置错误或数量上限不会改变原模板。

设置路径为“设置 > Lobecorp > 粒子与渲染 > 发射器发射器控件”，默认开启。
`emitter_spawner` 控件开关只管理编辑器显示，不抹除保存的数据、不禁用游戏内已保存效果。
本类型不需要设置自己的纹理、混合因子或 Renderer Order；这些属性由被发射的模板决定。
配置行使用 LDLib2 原生悬浮提示图标说明单位、取值和行为；形状、实例参数等内部子组保留原折叠结构。

## 两种来源

| 来源           | 复制内容                                           | 时间轴与引用边界                                         |
|----------------|----------------------------------------------------|----------------------------------------------------------|
| 项目发射器     | 选择的发射器、可选子树和范围内命名动画，每次深拷贝 | 不复制当前项目的全局时间轴；引用的对象须在有效模板场景中 |
| 已保存 FX 资源 | 完整 FXData 与时间轴，每次深拷贝                   | 时间轴仍由 Photon 执行；资源缺失时本次不发射             |

项目模板的顶层本地位置归零，以新实例根节点作为发射锚点；其本地旋转、缩放及子对象关系保留。
包含子对象关闭时只复制选中的发射器。新实例与原对象有独立运行态，参数覆盖不写回模板或共享 FX 缓存。
模板本身仍是原项目对象，不会因为被引用就自动停止发射；可通过其自己的激活轨道限制原对象播放。
若模板使用子树外的自定义坐标空间等依赖，优先保存成包含完整依赖的 FX，再使用资源来源。

生成实例内的新发射器保留来源链，项目引用可继续解析原模板场景；完整 FX 的引用解析其自身场景。
来源链检测自引用及 A→B→A 的异步循环，深度默认/最高 8；每个模板最多 4096 个对象。
异常模板、缺失来源或非法有限值会拒绝本次生成，并在场景信息中显示状态。
实例记录 Photon 引擎 generation；清空粒子或更换世界后，回收时强制清理旧实例，手动发射不会复用失效所属 FXRuntime。

## 调度和数量

| 参数                 | 默认值/语义                                          |
|----------------------|------------------------------------------------------|
| 启用                 | 开启；关闭只停止新增实例，已有实例继续               |
| 发射持续时间         | 100 tick，至少 1；决定一个调度周期                   |
| 循环发射             | 开启；关闭后只运行一个周期                           |
| 开始延迟             | 0 tick，只应用于首次启动，不在每周期重新延迟         |
| 每周期一次           | 每个周期开始触发一次，使用“每次发射实例数”           |
| 一次性发射           | 每次启动只触发一次，使用“每次发射实例数”，循环不重复 |
| 按间隔               | 默认每 20 tick 触发，使用“每次发射实例数”            |
| 按速率               | 默认 0.05 实例/tick，累计小数，20 tick 约生成 1 个   |
| 每次发射实例数       | 1，支持常量、随机值与曲线                            |
| 发射概率             | 1，对每个尝试独立判断                                |
| 最大存活实例数       | 64；0 表示不生成                                     |
| 总发射数量           | 0 表示不限；只累计成功实例，重启重置计数             |
| 每 tick 最大发射数量 | 16；统计尝试次数，多次手动调用和模拟子步共享预算     |

调度使用半开时间窗口 `[start, end)`，避免小数时间步与循环边界重复触发。
速率的单位是实例/tick，20 实例/秒对应 1 实例/tick。
数量超额不积压到以后补发；异常输入不造成无界创建。
“每周期一次”搭配循环关闭可实现单次爆发，搭配循环开启可实现周期爆发。
“一次性发射”使用独立运行标记：首次满足开始延迟后触发一次，循环、暂停/继续或停止/继续发射不重置标记；
显式重启、激活重启和预览从头模拟会重置。触发即消耗本次机会，概率失败、零数量、模板缺失或达到预算不会自动重试。
手动 emitNow 不受自动调度的一次性标记限制。新模式追加稳定枚举标识 ONE_SHOT；原 ONCE、INTERVAL、RATE 标识及行为不变。

## 形状、跟随和继承

点、盒、球、圆使用原生枚举/向量配置控件；盒为体积分布，球为体积分布，圆为 XZ 平面面积分布。
形状范围分别表示半边长或半径，向量可形成椭球/椭圆；本地位置偏移单独配置。
发射位置由发射器的当前本地到世界变换计算。
选中本对象并开启原场景“形状显示”开关时，复用 ParticleEmitter 的 drawEditorAfterWorld 入口及黄色范围线；盒／球／圆轮廓由原生形状提供，点按原 Dot 不绘制范围线。
盒的配置是半边长，原生 Box 绘制传全边长；Sphere／Circle 使用单位半径再乘配置尺寸，圆仍在 XZ 平面。范围乘完整父级 localToWorldMatrix，不额外叠加相机位置或第二遍世界变换。
范围只用于编辑器，不消耗随机数、不改变发射采样、继承规则或实例材质。过滤非有限／零长边，避免原线绘制对零长方向归一化产生无效数值。
维护时核对原生显示开关、选中对象回调和尺寸含义；继承旋转／缩放是子实例规则，不应据此过滤形状范围的发射器变换。实际各方向、父级缩放及零尺寸的显示仍待游戏验收。

- 跟随关闭：生成时保存世界位置、旋转、缩放，之后可按实例移动速度运动。
- 跟随开启：锚点继续跟随发射器，独立运动位移叠加在跟随位置上。
- 继承旋转默认开启；继承缩放、颜色、速度默认关闭。
- 实例移动速度的单位为格/tick。方向使用形状点相对中心方向；中心点使用本地 +Z，继承旋转时转换到世界方向。
- 继承速度额外加入发射器原生世界位移速度；继承颜色沿用 Photon RGBA 乘色接口。
- 显示/活动状态从所属发射器同步。原激活行为仍可冻结并隐藏；仅停止生成则继续更新已有实例。

## 实例参数

“参数目标”按发射器名称精确匹配，留空应用到所有兼容发射器。
每个可编辑参数须开启对应“覆盖”选项；关闭时保留模板参数。

| 参数         | 支持对象                     | 作用                                         |
|--------------|------------------------------|----------------------------------------------|
| 粒子寿命     | ParticleEmitter              | 新粒子的 startLifetime，0 的意义沿用 Photon  |
| 粒子初始大小 | ParticleEmitter              | 新粒子的 startSize XYZ                       |
| 粒子初始颜色 | ParticleEmitter、BeamEmitter | 粒子 startColor / 光束 color，含 Alpha       |
| 粒子初始速度 | ParticleEmitter              | 新粒子的 startSpeed                          |
| 实例播放倍率 | 整个生成实例                 | 同时推进实例时间轴与对象模拟，0 冻结，默认 1 |

参数在每次生成时采样为快照，之后改源配置/API 覆盖只影响后续实例。
常量函数按实例缓存，tick/frame 回调在时间轴和原执行器回调之后重新应用，子对象重启也不会丢失覆盖。
显式参数覆盖优先于目标实例对同一运行槽的时间轴动画；模板定义始终不变。
粒子初始参数只影响新粒子，已有粒子继续按自己的生命周期运行。

实例倍率与发射器的层级模拟倍率相乘，有效实例倍率最多 16，沿用 Photon 每 tick 积分预算。
所属发射器暂停时实例时间轴与模拟一起暂停；信号与音频使用原 Photon 时间轴接口，不添加第二套派发系统。

## 结束、重启与激活轨道

发射器的周期结束与生成实例的结束分开处理：

| 结束行为                   | 结果                                                             |
|----------------------------|------------------------------------------------------------------|
| 保留已有实例（默认）       | 发射器不再新建实例，已有实例继续按自己的资源寿命/时间轴运行      |
| 停止实例发射并等待残留结束 | 对已有子 FX 调用 destroy(false)，停止其时间轴/发射并保留原生残留 |
| 清理所有实例               | 对已有子 FX 调用 destroy(true)，立即清理                         |

“实例存活时间”默认 0，沿用原效果；大于 0 按实例播放时间计，到时柔和停止。
无限循环或无限寿命实例在“保留”策略下会继续存在，数量上限仍然有效；需要结束时使用实例时限、停止或清理。
柔和停止不会为无限寿命粒子自动增加淡出。

重启时清理默认开启，关闭可保留此前实例；显式 API/激活轨道的清理选择优先。
激活轨道的“仅停止生成”停止本类型的新实例，已生成实例继续更新；轨道“清理”清除生成实例。
原 ControlTrack 保留原行为。新类型直接复用 PhotonEmissionControlAccess，不复制激活状态机。
暂停与手动停止分开保存，时间轴每 tick 更新激活状态不会错误地取消手动停止。
所属源时间轴结束且对象已不活动时清理被隐藏冻结的实例，避免其失去更新后残留。

## 客户端 API

### 被发射实例的命名动画

项目模板复制所属场景的单个控制器，保留各命名动画的独立原生 Timeline、形参和默认动画。分组递归保留复制子树内的动画／激活／速度目标、绑定和控制片段，信号、音频和后处理等无对象目标的流程保留。子动画轨道只过滤／映射父发射器目标，不改写子资源动画 UUID 或输入形参 UUID。

完整 FX 使用源资源控制器，无控制器来源保持原行为。生成时执行默认动画及简化 START 入口；每个子 FXRuntime 和每次动画播放均有独立参数值。时钟服从实例倍率、发射器层级倍率及活动／暂停状态。显式实例参数覆盖仍优先于同槽动画，不修改模板资源。

通过 instances(runtime, name) 查询实例 UUID，随后使用 PhotonEmitterSpawnerUtil.animations(runtime, name, handle) 获取可空子 PhotonAnimationRuntime。play 返回该次 PhotonAnimationPlayback，再按句柄读取／设置形参；同名动画重启后旧句柄失效。该运行态属于子 FXRuntime，外界只借用，不调用 close。finish 只结束当前子 FX，停止命名动画只移除其贡献。

旧共享参数表和复杂动作链不再运行；旧资源必须先完成第六版迁移审查。无法等价转换时保留原 definition、报告问题并阻止新格式保存／导出，不因为模板或 FX 加载失败而丢弃原数据。用法与迁移边界见 [命名动画](photon-named-animations.md)。

### 由组合动画统一调用

组合动画在同一个原 TL 添加“子动画”轨道，目标选择发射器发射器，片段选择子动画及受控／独立模式。不会另建时间轴视图或靠规则操作链调用子动画。

受控片段把完整子动画映射到父片段窗口，支持当前进度定位、整数／小数时间和完成 HOLD／RELEASE。独立片段在窗口中对各已有实例触发一次，子实例按自己的时钟推进。窗口内后来生成的实例可接入；窗口外不补发。片段只调用已有实例，不隐式发射，且不依赖当前编辑器预览。

输入使用目标子动画与当前父动画各自的形参 UUID。固定值和默认来源值在调用时复制，可选择实时跟随父播放句柄；父句柄结束或被重启替换后冻结最后值，不自动跟随新句柄。父场景基础 Timeline 没有命名形参签名，仅提供固定输入。

受控子动画的时间和音频暂停随父片段传播，暂停标记只作用于当前 token 仍拥有的播放；恢复时按父音频派发策略继续。独立触发保持自己的运行状态，不因父动画暂停而静默。静默重模拟中的延迟实例仍接入内部编排，外部信号回调和音频关闭；不能把主制作实例的 previewOnly 状态扩散到所有子实例。已确认根因与维护要点见 [命名动画问题记录](photon-named-animations.md#已修复问题与维护要点)，实际音频及窗口交互尚未游戏验收。

显式重启可以替换仍活动或已保持末帧的同名受控播放，旧句柄／token 失效后无法驱动、完成或停止新播放。父片段停止、关闭或回退仅清理自己仍持有的受控贡献，独立播放继续运行，但父 FX 销毁、发射器活动状态和实例时限仍服从原生命周期。零长度窗口即时完成／一次触发，不能产生 NaN。

外部只调用组合动画并传入该动画的形参；子片段再按各子动画签名传递值。子资源不存在、无控制器、引用无效、输入维度／范围错误或缺少来源时报告问题，不能当作已有实例成功调用。详细步骤和 API 示例见 [组合动画调用子动画](photon-named-animations.md#组合动画调用子动画)。

2026-10-08 整合源码通过 IDEA 编译、独立回归 253 项及已加载完整回归 5391 项；正常 Data 退出码 0，生成中英文各 845 键、与最终运行前逐键相同。子片段检查包含晚生成实例、受控暂停／完成／停止、独立继续、形参跟随及旧 token 隔离，仍不等同于实际粒子、音频或 GUI 验收。旧资源只读审查的 3 个受阻文件保留原件，具体路径与处理边界见命名动画保存段。

### 发射控制

[PhotonEmitterSpawnerUtil](../../src/main/java/org/unitego/lobecorp/util/photon/emitter/PhotonEmitterSpawnerUtil.java)
作用于指定 FXRuntime 内按名称找到的发射器。调用须在客户端线程，FXRuntime 需已经 emit 且仍有效。

```java
import com.lowdragmc.photon.client.fx.FXRuntime;
import org.unitego.lobecorp.client.photon.emitter.runtime.PhotonSpawnOverrides;
import org.unitego.lobecorp.util.photon.emitter.PhotonEmitterSpawnerUtil;

@SuppressWarnings("unused")
public class EmitterSpawnerExample {
    public static void control(FXRuntime runtime) {
        // 示例数值仅用于演示；业务调用应使用自身配置。
        PhotonEmitterSpawnerUtil.parameters(runtime, "spawner",
                new PhotonSpawnOverrides("beam", null, null, null, null, 12f, 0.4f, 1f));
        PhotonEmitterSpawnerUtil.emit(runtime, "spawner", 3);
        PhotonEmitterSpawnerUtil.stop(runtime, "spawner"); // 仅停止新增实例
        PhotonEmitterSpawnerUtil.resume(runtime, "spawner");
        PhotonEmitterSpawnerUtil.pause(runtime, "spawner", true);
        PhotonEmitterSpawnerUtil.pause(runtime, "spawner", false);
        PhotonEmitterSpawnerUtil.restart(runtime, "spawner", false); // 保留旧实例并从头计时

        PhotonEmitterSpawnerUtil.clear(runtime, "spawner");
    }
}
```

`PhotonSpawnOverrides` 中 null 表示不覆盖；大小/颜色向量按值复制，输入检查有限值和范围。
`parameters(..., null)` 恢复按编辑器配置采样。参数覆盖与手动发射共用数量限制和发射概率。
`stopInstance(runtime, name, handle, force)` 可按 UUID 单独结束生成实例；force 控制柔和停止/强制清理。
名称须唯一；找不到或重复时不操作。局部 restart 仅用于引擎仍保留的对象。
已结束的所属 FXRuntime 或已从引擎回收的发射器不通过局部 restart 复活，调用方应重新创建完整 FXRuntime。

## 光柱参数的兼容边界

2026-10-07 起，发射器发射器不再提供光柱长度、宽度及其覆盖开关的专用检视入口；新增时间轴属性菜单也不显示这两个参数。
制作新效果时使用被发射 BeamEmitter 的原生属性或现有代码 API，原生 Beam 的宽度、端点、碰撞及渲染功能保持不变。

四个旧字段以原字段名继续持久化，旧文件开启的覆盖仍按原规则生效。`parameters.beamLength`、`parameters.beamWidth` 转为隐藏兼容绑定，旧项目、`.fx` 和命名动画轨道仍通过原路径解析。
现有 `PhotonSpawnOverrides` 及光柱运行参数 API 保留。查询旧属性时，无论是否已播放或存在采样缓存，都可回退到运行绑定解析。
旧标签翻译保留；隐藏编辑入口不会替旧文件关闭覆盖，新文件默认不覆盖这两项。

维护时不要直接删除旧字段的持久化注解或隐藏绑定，也不要为了属性查询重新把兼容绑定加入作者列表。编辑枚举与旧路径解析必须分别维护。
2026-10-07 本次修改经 IDEA 范围格式化、诊断与编译；正常 Data 退出码 0，19 个生成资源内容不变。隐藏作者入口、持久化及运行绑定回退已核对源码；实际旧文件加载、保存往返和粒子显示仍待游戏内验收。

## 时间轴、保存和维护入口

原生 AnimationTrack 的新增属性菜单提供 15 个运行槽：启用、持续时间、循环、延迟、间隔、数量、速率、概率、存活上限、实例移动速度，
以及寿命/大小/颜色/速度/播放倍率。
参数覆盖选项仍须启用，不能把关闭的覆盖字段仅添加动画就当成开启覆盖。

配置使用 Photon/LDLib2 原持久化系统与对象类型 CODEC，系统剪贴板、深拷贝、操作历史沿用对象序列化入口。
项目模板使用 TransformRef UUID，现有剪贴板内部引用重映射可处理该引用。
序列化枚举保存稳定翻译键标识；切换语言不改变保存值。
未为旧 ParticleEmitter、BeamEmitter 或 TrailEmitter 增加必填保存字段。

- [类型注册与动画槽](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonEmitterTypes.java)
- [发射器实现](../../src/main/java/org/unitego/lobecorp/client/photon/emitter/PhotonEmitterSpawner.java)
- [配置](../../src/main/java/org/unitego/lobecorp/config/photon/emitter/PhotonEmitterSpawnConfig.java)
- [模板复制与来源链](../../src/main/java/org/unitego/lobecorp/util/photon/emitter/PhotonEmitterTemplateUtil.java)
- [项目模板命名动画复制](../../src/main/java/org/unitego/lobecorp/util/photon/emitter/PhotonEmitterAnimationUtil.java)
- [实例生命周期](../../src/main/java/org/unitego/lobecorp/client/photon/emitter/runtime/PhotonSpawnedEffect.java)
- [参数覆盖](../../src/main/java/org/unitego/lobecorp/util/photon/emitter/PhotonEmitterSpawnParameterUtil.java)
- [编辑器与激活规则](photon-editor.md)、[粒子与渲染](particle-rendering.md)、[实际修改记录](changes.md)

本轮还增加以下配套文件：

- 参数与形状配置：[PhotonEmitterSpawnParameters](../../src/main/java/org/unitego/lobecorp/config/photon/emitter/PhotonEmitterSpawnParameters.java)、[PhotonEmitterSpawnShape](../../src/main/java/org/unitego/lobecorp/config/photon/emitter/PhotonEmitterSpawnShape.java)。
- 调度和动画槽：[PhotonEmitterSpawnSchedule](../../src/main/java/org/unitego/lobecorp/client/photon/emitter/runtime/PhotonEmitterSpawnSchedule.java)、[PhotonEmitterSpawnRuntime](../../src/main/java/org/unitego/lobecorp/client/photon/emitter/runtime/PhotonEmitterSpawnRuntime.java)。
- 参数快照与实例集合：[PhotonSpawnOverrides](../../src/main/java/org/unitego/lobecorp/client/photon/emitter/runtime/PhotonSpawnOverrides.java)、[PhotonSpawnedEffects](../../src/main/java/org/unitego/lobecorp/client/photon/emitter/runtime/PhotonSpawnedEffects.java)。
- 形状采样与翻译：[PhotonEmitterSpawnShapeUtil](../../src/main/java/org/unitego/lobecorp/util/photon/emitter/PhotonEmitterSpawnShapeUtil.java)、[PhotonEmitterSpawnerTextUtil](../../src/main/java/org/unitego/lobecorp/util/photon/editor/PhotonEmitterSpawnerTextUtil.java)。

既有 Java 仅接入：[PhotonEditorSettings](../../src/main/java/org/unitego/lobecorp/config/photon/PhotonEditorSettings.java)、
[PhotonEditorTools](../../src/main/java/org/unitego/lobecorp/client/photon/editor/PhotonEditorTools.java)、
[TranslationKeys](../../src/main/java/org/unitego/lobecorp/util/TranslationKeys.java)、
[PhotonActivatorTimelineUtil](../../src/main/java/org/unitego/lobecorp/util/photon/runtime/PhotonActivatorTimelineUtil.java)。

2026-10-07 形状预览两文件已执行 IDEA 修改范围格式化与静态诊断，无错误/警告；整合编译成功，正常 Data 退出码 0。具体参数编辑及生成检查见 [修改记录](changes.md)。本轮未运行 Client 或独立测试，各方向形状显示、父级缩放与零尺寸仍待游戏验收。

既有菜单交互、保存重开、剪贴板、继承坐标、不同播放倍率和大量实例性能也尚未运行验收。
