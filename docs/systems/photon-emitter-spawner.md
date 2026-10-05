# Photon 发射器发射器

核对日期：2026-10-05。基于 Photon 26.1.2.2、LDLib2 26.1.2.39；类型 ID 为
`lobecorp_emitter_spawner`。本类型按调度创建已有发射器/FX 的独立实例，自身不生成 TileParticle，也不提交几何或材质。
效果中的普通粒子、光束、拖尾仍使用 Photon 原更新、渲染与寿命逻辑。

## 编辑器使用

1. 在 Photon 原对象层级的添加菜单中选择“发射器发射器”。
2. 在监视面板直接选择“项目发射器”或“已保存 FX 资源”；不再套同名外层折叠组。
3. 项目来源：把层级中的发射器拖到“发射器模板”；按需选择是否包含其子对象。“移除”按钮清除引用，不删除模板或已有实例。
4. 资源来源：在“FX 资源”中搜索已加载资源 ID。需要完整时间轴、激活/动画/速度/信号等轨道时使用此来源。
5. 设置发射模式、数量/速率、延迟、周期和结束行为，再配置形状、继承与实例参数。
6. 场景信息显示存活实例数、累计生成数与最近发射状态；配置错误或数量上限不会改变原模板。

设置路径为“设置 > Lobecorp > 粒子与渲染 > 发射器发射器控件”，默认开启。
`emitter_spawner` 控件开关只管理编辑器显示，不抹除保存的数据、不禁用游戏内已保存效果。
本类型不需要设置自己的纹理、混合因子或 Renderer Order；这些属性由被发射的模板决定。
配置行使用 LDLib2 原生悬浮提示图标说明单位、取值和行为；形状、实例参数等内部子组保留原折叠结构。

## 两种来源

| 来源           | 复制内容                           | 时间轴与引用边界                                         |
|----------------|------------------------------------|----------------------------------------------------------|
| 项目发射器     | 选择的发射器及可选子树，每次深拷贝 | 不复制当前项目的全局时间轴；引用的对象须在有效模板场景中 |
| 已保存 FX 资源 | 完整 FXData 与时间轴，每次深拷贝   | 时间轴仍由 Photon 执行；资源缺失时本次不发射             |

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

- 跟随关闭：生成时保存世界位置、旋转、缩放，之后可按实例移动速度运动。
- 跟随开启：锚点继续跟随发射器，独立运动位移叠加在跟随位置上。
- 继承旋转默认开启；继承缩放、颜色、速度默认关闭。
- 实例移动速度的单位为格/tick。方向使用形状点相对中心方向；中心点使用本地 +Z，继承旋转时转换到世界方向。
- 继承速度额外加入发射器原生世界位移速度；继承颜色沿用 Photon RGBA 乘色接口。
- 显示/活动状态从所属发射器同步。原激活行为仍可冻结并隐藏；仅停止生成则继续更新已有实例。

## 实例参数

“参数目标”按发射器名称精确匹配，留空应用到所有兼容发射器。
每个参数须开启对应“覆盖”选项；关闭时保留模板参数。

| 参数         | 支持对象                     | 作用                                         |
|--------------|------------------------------|----------------------------------------------|
| 粒子寿命     | ParticleEmitter              | 新粒子的 startLifetime，0 的意义沿用 Photon  |
| 粒子初始大小 | ParticleEmitter              | 新粒子的 startSize XYZ                       |
| 粒子初始颜色 | ParticleEmitter、BeamEmitter | 粒子 startColor / 光束 color，含 Alpha       |
| 粒子初始速度 | ParticleEmitter              | 新粒子的 startSpeed                          |
| 光柱长度     | BeamEmitter                  | 使用已有 Lobecorp 光柱长度运行槽             |
| 光柱宽度     | BeamEmitter                  | 光束 width                                   |
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

## 时间轴、保存和维护入口

原生 AnimationTrack 支持 17 个运行槽：启用、持续时间、循环、延迟、间隔、数量、速率、概率、存活上限、实例移动速度，
以及寿命/大小/颜色/速度/光柱长度/宽度/播放倍率。
参数覆盖选项仍须启用，不能把关闭的覆盖字段仅添加动画就当成开启覆盖。

配置使用 Photon/LDLib2 原持久化系统与对象类型 CODEC，系统剪贴板、深拷贝、操作历史沿用对象序列化入口。
项目模板使用 TransformRef UUID，现有剪贴板内部引用重映射可处理该引用。
序列化枚举保存稳定翻译键标识；切换语言不改变保存值。
未为旧 ParticleEmitter、BeamEmitter 或 TrailEmitter 增加必填保存字段。

- [类型注册与动画槽](../../src/main/java/org/unitego/lobecorp/registry/photon/client/LcPhotonEmitterTypes.java)
- [发射器实现](../../src/main/java/org/unitego/lobecorp/client/photon/emitter/PhotonEmitterSpawner.java)
- [配置](../../src/main/java/org/unitego/lobecorp/config/photon/emitter/PhotonEmitterSpawnConfig.java)
- [模板复制与来源链](../../src/main/java/org/unitego/lobecorp/util/photon/emitter/PhotonEmitterTemplateUtil.java)
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

已执行 IDEA 编译、Data 与静态诊断。游戏中的菜单交互、保存重开、剪贴板、继承坐标、不同播放倍率和大量实例性能尚未运行验收。
