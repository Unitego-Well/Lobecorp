# 屏幕伤害粒子

源码核对日期：2026-10-13。当前版本为 Minecraft 26.1.2、NeoForge 26.1.2.100、Java 25。实际编辑器、编译和 Data 结果见[修改记录](changes.md)，不以源码检查代替游戏视觉或性能验收。

## 职责与入口

- [DamageParticles](../../src/main/java/org/unitego/lobecorp/particle/damage/DamageParticles.java)：服务端消费最终伤害并向附近玩家广播；`hurtAt` 为自定义命中提供明确坐标与近战语义。
- [DamageParticleHitContext](../../src/main/java/org/unitego/lobecorp/particle/damage/DamageParticleHitContext.java)：单次同步命中调用的嵌套作用域，退出时恢复外层，不跨 tick 留存实体或位置。
- [DamageParticlePayload](../../src/main/java/org/unitego/lobecorp/network/tc/damage/DamageParticlePayload.java)：一次伤害的维度、目标／攻击者 UUID、伤害类型、固定坐标、数值、目标最大生命值快照和近战标记；沿用 `IToClientPayload`。
- [ScreenParticles](../../src/main/java/org/unitego/lobecorp/client/particle/screen/ScreenParticles.java)：客户端接收、合并、寿命、实例池及通用内容发射。
- [ScreenParticleRenderer](../../src/main/java/org/unitego/lobecorp/client/particle/screen/ScreenParticleRenderer.java)：共享相机投影、首次中心反投影、屏幕范围剔除、比例大小与震动、缩放弹出、上浮与淡出。
- [ScreenParticleRegistration](../../src/main/java/org/unitego/lobecorp/registry/particle/client/ScreenParticleRegistration.java)：GUI 图层与字体重载缓存失效登记；监听入口仍在既有 `events.client`。

系统不创建 Minecraft 实体或世界 `Particle`，不保存到世界存档，也不在客户端重新结算伤害。服务端不依赖客户端粒子类。

## 数值与接收范围

唯一 `LivingDamageEvent.Post` 入口在既有倍率、护甲、清道夫吸收及吸收生命处理之后读取 `getHealthDamage()`。只发送有限且大于零的数值；吸收部分不显示，超过剩余生命的伤害仍包含在数字内。这是结算健康伤害，不是伤害前后的生命差，不能通过死亡或图腾后的生命反推。

`PacketDistributor.sendToPlayersNear` 向固定发生位置周围 32 格的同维度玩家发送一次。攻击者 UUID 保留任意实体来源，避免多个生物攻击者被错误合并；无攻击实体的环境伤害使用空来源。当前网络版本为 19，既有 S2C 通知在 `amount` 后、`melee` 前携带 `float maxHealth`，取自本次服务端 `target.getMaxHealth()`；不兼容旧协议，不新增通知类型，不发送逐 tick 动画包或客户端回传。

## 位置与本地玩家规则

普通效果保存命中时的固定世界坐标，随后每帧按当前相机重新投影，不跟随受击实体移动。

仅当通知为近战，且攻击者 UUID 等于当前客户端玩家 UUID 时，效果首次从该玩家屏幕中心定位，随后固定世界显示锚点并随当前相机重新投影，不持续钉在屏幕中心。服务端实际命中位置另存用于距离合并，不能用居中后的显示锚点替代。其他玩家造成的近战和所有非近战伤害仍投影世界发生点；不能仅凭“来源是玩家”让所有客户端都居中。

首次有效 GUI 提取时，`ScreenParticleRenderer.centerAnchor(hitPosition)` 保留实际命中的齐次深度，将屏幕坐标置于中心，再通过相机 VP 逆矩阵反投影为世界显示锚点；该点只计算一次并由实例保存。命中在相机后方或深度无效时，按命中到相机的实际距离，在 `Camera.PROJECTION_Z_NEAR * 2` 至 16 格内取相机前方的回退深度。相机未准备好、矩阵退化或结果无效时当帧不绘制，后续提取继续尝试，不保存无效坐标。

命中位置按实际取得的数据处理：

1. 自定义调用 `DamageParticles.hurtAt` 的明确位置优先，仅匹配同一目标和同一 `DamageSource` 实例。
2. 原版投射物 `hitTargetOrDeflectSelf` 的 `EntityHitResult` 保留碰撞几何点，仅用于同一直接投射物与命中目标。箭等沿该入口处理；绕过该入口的自定义投射物需要显式入口。
3. `MagicStarProjectile` 的移动轨迹直接命中使用实际 `EntityHitResult.getLocation()`；静止接触使用身体中心，不把 `new EntityHitResult(entity)` 的默认实体位置称为精确碰撞点。爆炸范围的其他目标没有独立碰撞点，仍走中心回退。
4. 普通玩家近战包只有目标 ID，没有客户端原始命中坐标。服务端以视线射线与目标 AABB 的交点估算；无交点使用身体中心。估算点不是模型表面或原始准星坐标。
5. 其余伤害使用受击时身体包围盒中心。命中框现有相交算法只返回布尔值，没有从 GJK 结果伪造接触点。

玩家普通攻击、横扫和刺击的实际伤害调用明确标记近战，自定义伤害类型也可覆盖；附魔后置额外伤害不因处于同一次攻击方法中就被泛化为近战。没有上下文时，只将直接来源与攻击者为同一服务端玩家的 `PLAYER_ATTACK`、`SPEAR`、`MACE_SMASH` 作为保守回退；排除荆棘、投射物和爆炸。自定义入口可显式传入近战标记。

所有作用域都在 `finally`／try-with-resources 中释放；取消、无敌、反弹、未扣血或异常路径都不等待 Post 来清理，因此不会污染下一次伤害。

## 合并与配置

[DamageParticleConfig](../../src/main/java/org/unitego/lobecorp/config/client/DamageParticleConfig.java) 为独立 NeoForge CLIENT 配置，默认文件为 `config/lobecorp-client.toml`，字段位于 `[damageParticles]`。配置在各客户端生效，不联网。

| 字段               | 默认值      | 含义                                              |
|--------------------|-------------|---------------------------------------------------|
| `enabled`          | `true`      | 屏幕粒子开关                                      |
| `mergeEnabled`     | `true`      | 伤害合并开关                                      |
| `mergeRadius`      | `1`         | 距首次实际命中位置的最大距离，单位为格            |
| `lifetimeTicks`    | `30`        | 新建或每次合并后的剩余显示时间，单位为客户端 tick |
| `maxActive`        | `256`       | 活跃上限；超限回收最旧效果，使新命中可见          |
| `poolCapacity`     | `256`       | 空闲实例壳上限；零表示不保留，与活跃上限独立      |
| `outlineColor`     | `-16777216` | 有符号 ARGB 黑色；零关闭数字描边                  |

合并键只包含同一攻击者、同一目标与同一伤害类型注册 ID，不包含中心／世界显示模式。显示寿命内，在同键且未到期的实例中选择首次实际命中位置距离新命中最近、且不超过 `mergeRadius` 的一簇；距离使用三维世界坐标，恰在半径边界也可合并，等距时保留既有顺序。每个键可同时持有多个空间簇，找不到符合距离的簇时新增实例。首次命中位置始终固定，不按最近一次命中接力扩展合并范围。

每次合并立即累计伤害，并将剩余寿命刷新为当前 `lifetimeTicks`；保留首次显示锚点、弹出和上浮年龄，不重新弹出或把上浮位置拉回起点。旧的 `mergeWindowTicks` 已由距离配置 `mergeRadius` 替换，没有独立的命中间隔窗口；半径设为零时只合并相同实际命中点。到期、容量回收时仅移除当前实例所在簇，最后一簇移除后再删除该键，避免旧实例到期时误删同键其他簇。关闭合并会解除旧索引；重新开启不追溯合并已经独立显示的效果。

内部用 double 累加，仅格式化后的数字或描边变化时重新创建文本内容；数字最多两位小数，不每帧构建 Component 或解析文本。客户端暂停不推进时钟，F1 只隐藏绘制，正常游戏 tick 中寿命仍推进。

伤害比例为当前实例的累计合并伤害除以最近一次命中携带的目标最大生命值快照；每次合并重新计算，不累加各次比例，也不读取客户端当前实体生命。最大生命值无效、非有限数或不大于零时，伤害数字仍保留，比例回退为零。

数字的伤害基础大小倍率只保留 0% 对应 0.7 倍、100% 对应 2 倍两个端点，中间线性插值；达到或超过 100% 后基础倍率保持 2，不再设置 10% 或 50% 的独立节点。该基础倍率仍与原有弹出动画相乘，弹出峰值为基础倍率的 1.25 倍，因此最高基础倍率对应瞬时 2.5 倍，回落后为 2 倍；2 倍上限仅限制基础倍率。震动继续将伤害比例限制在 0 至 2。

震动只作用于数字自身的 GUI 平移与旋转，不移动相机。新建数字与每次合并都从最近命中时刻重新触发冲击，在 10 个客户端 tick 内线性衰减；强度按累计伤害比例计算，比例在 2 封顶，对应每轴最多 4 GUI 像素及最多 6 度旋转。三个通道采用不同正弦频率，规则值集中在渲染器常量中。冲击年龄与首次弹出、漂移年龄独立，因此合并可以重新震动，同时保持已有上浮进度。通用非伤害内容不使用比例大小或伤害震动，保留原有弹出表现。

## 内容包装与缓存

[IScreenParticleContent](../../src/main/java/org/unitego/lobecorp/client/particle/screen/IScreenParticleContent.java) 提供 `width()`、`height()` 和 `extract(graphics, opacity)`。尺寸以 GUI 像素为单位，局部原点位于内容中心；世界锚点、二维动画和寿命归管理器。

- [ScreenTextContent](../../src/main/java/org/unitego/lobecorp/client/particle/screen/ScreenTextContent.java) 接受 `Component`，自然支持 `MutableComponent`；构造时通过 Component CODEC 深复制完整文本与样式，外部后续修改不改变已发射内容。含注册表引用的复杂样式使用当前客户端世界的序列化上下文。
- [ScreenTextureContent](../../src/main/java/org/unitego/lobecorp/client/particle/screen/ScreenTextureContent.java) 接受原生 `Identifier` 纹理、GUI 宽高和可选 ARGB tint，绘制完整 UV。其他内容可实现同一接口，不必伪装为文本。

文本按行准备一次，复用 `TextGlyphMesh.Capture` 捕获局部几何，正文和八方向一像素描边使用同一次字形选择。按 GUI pipeline 与纹理聚合；常见单材质数字把轮廓和正文合在同一个状态，先轮廓后正文；多材质依赖准确 bounds 和原生 GUI 层排序。不逐字提交 `TextSubmit`，不为每个数字强制 `nextStratum`。

字体／语言／Unicode／日文字形选项变化及 FONTS 重载后重建缓存；混淆样式按客户端世界 tick 刷新。纹理视图或动态字体材质变化会重新分组。八方向描边仍有额外顶点，未测量帧率或分配改善幅度。

提取时冻结二维矩阵、透明度、bounds、材质和稳定网格引用，延迟绘制不捕获可回收的池实例。自定义内容同样必须冻结提交状态，不能把随后会被修改或回收的数据直接交给渲染器。

## 对象池与生命周期

活跃队列和空闲 `ArrayDeque` 分开管理，只池化私有实例壳。到期、禁用、容量缩减时先从活跃与合并索引解除，再清空内容、实际命中位置、显示锚点、首次居中标记、键、累计值、伤害比例、格式化缓存、颜色和全部时间字段（含最近命中时刻）。非伤害内容的比例恢复为 `NaN` 标记，使用普通尺寸且不施加伤害震动。池容量为零时直接释放引用；不池化 Component、纹理或 GPU 对象，不关闭原版资源管理器拥有的资源。

登录、注销和客户端世界切换清空全部实例与池，不能跨世界合并。资源重载使文本布局失效，保留当前表现实例；配置文件有独立生命周期，不随会话清理删除。

## 调用示例

服务端自定义命中可以保留明确位置；自动 Post 监听负责发包，调用方不要再发送一次：

```java
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.particle.damage.DamageParticles;

public class DamageHitExample {
    public static boolean hit(ServerLevel level, Entity target, DamageSource source, float amount, Vec3 hitPosition) {
        return DamageParticles.hurtAt(level, target, source, amount, hitPosition, false);
    }
}
```

客户端可发射普通文本或非文本内容；此入口不自行广播，不参与伤害合并：

```java
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.client.particle.screen.ScreenParticles;
import org.unitego.lobecorp.client.particle.screen.ScreenTextContent;
import org.unitego.lobecorp.client.particle.screen.ScreenTextureContent;
import org.unitego.lobecorp.config.client.DamageParticleConfig;

public class ScreenParticleExample {
    public static void show(Component component, Identifier textureId, Vec3 position) {
        ScreenParticles.spawn(new ScreenTextContent(component, DamageParticleConfig.OUTLINE_COLOR.get()), position, false);
        ScreenParticles.spawn(new ScreenTextureContent(textureId, 16, 16), position, false);
    }
}
```

## 投影与已知限制

每个 GUI 帧只读取一次主相机 VP 矩阵和位置，先以 double 做相机相对减法，再转换 float；复用投影向量。需要初始化中心锚点时，同帧共享一次逆矩阵。拒绝相机后方、无效数值和设备深度范围外的锚点，再按比例大小、震动、弹出和漂移全部变换后的完整内容矩形剔除。GUI 宽高已含 GUI scale，不重复除以缩放。

使用原版主相机投影，支持当前相机 FOV 和第三人称视角；公开矩阵不包含世界绘制阶段额外的受伤晃动、视角摇晃和传送门／恶心变形，因此这些情况下不保证逐像素贴合世界画面。GUI 在世界之后绘制，不使用世界深度遮挡，未加入额外方块射线检测。

现有 Re-Retro Damage Indicators 依赖及 `damage_particles_enabled` 配置保持原样；其旧世界伤害数字可能与本系统同时显示。本次没有擅自关闭第三方功能或血条。

以下为 2026-10-13 协议 18、六 tick 合并窗口及持续屏幕居中实现的历史运行证据，不代表协议 19 的距离合并、固定世界显示锚点、比例大小和震动已通过验收。历史检查在隔离新世界通过 IDEA Debug 触发真实 `hurtServer` 与当前玩家 `attack`，并在服务端发包前检查：两次 generic 伤害分别为 2、1，发生位置为受击牛的身体中心；当前玩家的 player_attack 为 1，携带当前玩家 UUID 和 `melee=true`。目标生命由 1000 降至 996，未以模拟 Payload 代替真实伤害链路。

第二次 Debug 会话因 JVM native crash 结束，第三次沿原配置启动后补齐以下基础运行检查：

- 重做真实 generic 2 + 1 和玩家近战 1，客户端恰有两个实例，分别为 amount=3／centered=false 和 amount=1／centered=true；MCP 截图同时看到牛身体位置的 3 与屏幕中心上浮的 1。渲染断点确认 GUI 427×240 的中心为 (213.5,120)，上浮后 y=102。
- `hurtAt` 显式传入 (-8.0,57.25,-1.5) 并真实结算 2.5，客户端 Payload 和 Entry 保留完全相同坐标，melee=false；退出调用后命中上下文为 null，截图显示该位置的 2.5。原版箭、MagicStar 移动命中和近战 AABB 交点仍未逐项实测。
- 受控客户端通知中，其他攻击者 UUID + melee=true，以及当前玩家 UUID + melee=false，均得到 centered=false。关闭合并后，相同通知连续两次各新增一个 amount=5 实例。这些是客户端门控与配置检查，不是第二位玩家的联网验收。
- 两个真实伤害实例自然到期后进入池；首个对象的内容、位置、键、格式化值、颜色与时间字段均已清空。后续真实伤害复用首个池对象，前后 identityHashCode 均为 1507040262。
- 普通数字正文和轮廓的 batches.size() 为 1，轮廓与正文共用字形来源，轮廓顶点颜色数为正文的 8 倍。相同数字跨帧的 batches／pieces 标识保持 140043681／1234012283；这是缓存复用证据，没有测量 CPU、分配或 GPU 性能提升。
- 通过 ScreenTextureContent 发射原版 diamond 纹理，MCP 截图确认非文本内容可见。检查时临时延长寿命为 200／2000 tick，结束后恢复 lifetimeTicks=30、mergeEnabled=true，清空活跃队列与池并移除测试牛；磁盘配置保持默认值。

多客户端、专用服务器、当前距离与寿命合并边界、容量压力、分辨率／GUI scale 切换、资源重载及性能验收仍需分别记录实际结果；不能由上述历史单客户端结果宣称所有玩家画面完全一致。

## 2026-10-13 协议 19 运行检查

以下为基础字号简化为 0.7 至 2 倍之前的运行记录，旧分段字号的采样结果不代表当前曲线已经运行验证。本次数值规则调整仅做 IDEA 静态诊断，未重新编译、运行 Data 或进行游戏验证。

- IDEA 项目编译成功，Data 退出码 0；生成目录 25 份文件（含缓存）的路径集合和 SHA-256 与运行前一致。
- 服务端 `hurtAt` 真实结算玩家来源近战 100，测试牛最大生命 1000、生命 1000→900。客户端收到 amount=100、ratio=0.1，实际命中点为 (-8.5,56.7,-1.5)，首次居中后的固定显示点为 (-8.5,57.61999988555908,-1.5)。MCP 截图确认居中上浮；视角从 yaw=0 改为 20 后，数字随世界位置移到左侧，锚点没有重新居中。
- 客户端受控通知在距首次命中 1679 tick、但仍存活时，于恰好一格处追加 400，得到单实例 amount=500、ratio=0.5、剩余寿命 2000 tick，显示锚点不变。1.001 格处新建 amount=200；返回首次位置追加 50 后，两个簇分别为 550、200，旧簇索引没有被覆盖。
- 分别改变伤害类型、攻击者和目标后，原两个空间簇增长为五个实例、四个合并键。受控到期边界后再次命中创建新实例；回收得到一个池壳，显示锚点为空、比例恢复为 NaN。这些属于客户端边界检查，不等同于第二位真实玩家的联网验收。
- 基础字号函数在比例 0、0.05、0.1、0.3、0.5、0.75、1、2 时分别返回 0.5、0.75、1、1.5、2、2.5、3、3。渲染同一帧的 10%、50%、100%、200%、300% 样本，impactAge 均为约 2.70 tick，平移和旋转震动依次增强；200% 与 300% 均截为比例 2，得到完全相同的 scale、shakeX、shakeY 和 shakeAngle。该帧 200% 的位移约 (2.468,-2.609) GUI 像素、旋转约 -0.044 弧度。
- 检查时临时将寿命延长为 2000／200 tick，结束恢复 lifetimeTicks=30、mergeEnabled=true、mergeRadius=1；磁盘配置已核对。测试牛和伤害实例已移除，恢复玩家原位置与朝向，临时断点全部移除，原三个断点及启用状态恢复；客户端保留在标题界面，MCP 控制关闭。

本轮未执行多客户端、专用服务器、容量压力、全分辨率和性能基准；没有以调试断点和截图推断帧率提升。原版箭、MagicStar 精确命中仍沿用前述源码边界，未在本轮新增实测。

## 编译检查与维护

2026-10-13 协议 18 实现的历史编译记录：首轮编译及 Data 在 Java 编译阶段受阻，已确认原因是既有 `MagicStarProjectile.tick()` 中只有类型名的孤立 `Component` 行，并非伤害位置适配调用。用户授权后仅删除该行，通过 IDEA 项目编译及原 Data 配置（退出码 0）；生成目录 25 份文件（含缓存）集合与内容不变。当前协议 19 改动的编译及运行结果另按实际记录。

维护时先检查失败阶段和具体编译诊断，不能把未进入数据生成的运行视为生成成功，也不能据此手改生成资源。与当前功能无关的既有代码需确认修改范围后处理；本次仍保留原有导入及其他修改，不扩大清理。
