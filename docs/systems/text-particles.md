# 世界文本粒子

核对日期：2026-10-13。Minecraft 26.1.2、NeoForge 26.1.2.100、Java 25；实际检查记录见[修改记录](changes.md)。

## 职责与入口

服务端保存[TextParticleInstance](../../src/main/java/org/unitego/lobecorp/particle/text/TextParticleInstance.java)
，通过[TextParticles](../../src/main/java/org/unitego/lobecorp/particle/text/TextParticles.java)加入或移除当前世界。实例不继承实体或客户端
Particle，不进行碰撞、伤害或世界存档。客户端每个 ID 对应一个真实 Particle，由粒子引擎推进 tick，通过自定义 ParticleGroup
提取和提交文字几何。

实例归创建它的 ServerLevel 所有，保存于非持久化 `TEXT_PARTICLE_LEVEL_DATA` 附件；调用方持有实例或
UUID，通过公开方法修改。服务器停止时清理，客户端登录、注销和世界卸载时清空镜像。没有自动寿命，调用方须明确 `remove()`
；移除后的实例不能复用。

## 创建与修改

`TextParticles.create(level, position, text, size)` 创建并立即发送。需要先配置多项参数时，先构造 `TextParticleInstance`
，配置后再 `TextParticles.add(instance)`，加入前的 setter 不发包。调用均在服务端主线程执行。

```java
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.particle.text.TextGlyphAnimation;
import org.unitego.lobecorp.particle.text.TextGlyphKeyframe;
import org.unitego.lobecorp.particle.text.TextParticleInstance;
import org.unitego.lobecorp.particle.text.TextParticles;
import org.unitego.lobecorp.particle.text.TextRevealMode;

import java.util.List;

public class TextParticleExample {
    public static TextParticleInstance create(ServerLevel level, Vec3 position) {
        var text = new TextParticleInstance(level, position, Component.literal("示例文字"), 0.5F);
        text.setRotation(new Vec3(0, 45, 0));
        text.setReveal(TextRevealMode.CENTER_OUT, 2);
        text.setOutlineColor(0xFF000000);
        text.setAnimation(new TextGlyphAnimation(List.of(
                new TextGlyphKeyframe(0, new Vec3(0, 0, -30), new Vec3(0, 0, 0)),
                new TextGlyphKeyframe(8, Vec3.ZERO, new Vec3(1, 1, 1))
        ), false));
        return TextParticles.add(text);
    }
}
```

示例使用 `particle.text` 下的实例、管理入口、枚举与关键帧类型，以及 Minecraft 的 `Component`、`Vec3` 和 Java 的 `List`。

| 接口                                                                        | 行为                                                                |
|-----------------------------------------------------------------------------|---------------------------------------------------------------------|
| `setText(Component)`                                                        | 接受 Component 及其 MutableComponent 子类；文字或样式改变时重新开始 |
| `setPosition(Vec3)`、`setRotation(Vec3)`、`setSize(float)`                  | 修改整段位置、XYZ 旋转及字号，保留播放进度                          |
| `setReveal(mode, intervalTicks)`                                            | 修改显示顺序和每组显露间隔，重新开始；间隔至少 1 tick               |
| `setAnimation(animation)`                                                   | 设置未单独覆盖字符的共同动画                                        |
| `setCharacterAnimation(index, animation)`、`clearCharacterAnimation(index)` | 设置或撤销指定字符的动画覆盖                                        |
| `setOutlineColor(argb)`                                                     | ARGB 轮廓颜色，0 关闭                                               |
| `restart()`、`remove()`                                                     | 从当前时刻重播或永久移除                                            |
| `TextParticles.get(level, id)`                                              | 查询该世界中的实例，缺席返回 null                                   |

文本在构造、修改、读取及生成快照时通过当前注册表上下文的 Component Codec 复制完整组件树；外部继续修改传入的
MutableComponent 不会绕过同步，须再次 `setText`。关键帧列表及单字覆盖 Map 采用独立不可变副本。数据类型提供 CODEC 和
STREAM_CODEC，但没有自动存档入口。

字号表示一行字符高度对应的世界格数，必须为有限正数。位置、旋转与缩放必须为有限数；旋转单位为度。整段绕排版中心旋转；单字绕固定字符格中心旋转和缩放，动画没有位置通道。缩放允许零值和负值。

## 排版、显露与动画

完整文字先按本地字体计算固定字位，每行水平居中，全部行整体垂直居中；不自动换行，显式换行另起一行。逐字过程中不重新居中或移动已有字符。

| TextRevealMode | 显露规则                                       |
|----------------|------------------------------------------------|
| DIRECT         | 所有字同时出现                                 |
| LEFT_TO_RIGHT  | 按完整排版序列从前向后                         |
| RIGHT_TO_LEFT  | 按完整排版序列从后向前                         |
| CENTER_OUT     | 从序列中心向两端扩展；偶数字符中心两个同时出现 |

字符索引按客户端翻译及视觉顺序后的 Unicode 码点连续编号，空格计入，换行不计入；多行共用同一序列。不是按 UTF-16 char
切割。直接显示时所有字符动画同时开始；逐字显示时每个字符从自己的显露时刻开始动画。首组在起始 tick 即可见。

[TextGlyphAnimation](../../src/main/java/org/unitego/lobecorp/particle/text/TextGlyphAnimation.java)要求至少一帧、首帧为
tick 0、之后时间严格递增。各旋转和缩放通道线性插值；Euler 旋转保留完整转圈，不强制最短路径。非循环动画到达末帧后保持；循环动画以末帧
tick 为周期，跨周期处可产生跳变，连续循环应使首尾姿态一致。单帧动画保持该帧。

可翻译 Component 保留客户端本地语言。不同语言或字体可能产生不同字符数、字位与单字覆盖索引含义；同步的是组件、实例参数和时间起点。需要各玩家相同文字内容时使用
literal Component，并使用相同字体资源。

混淆样式的随机字形沿用客户端字体选择，各客户端可能不同；需要固定文字内容时不启用混淆样式。

## 同步与生命周期

[TextParticleStatePayload](../../src/main/java/org/unitego/lobecorp/network/tc/text/TextParticleStatePayload.java)沿用
IToClientPayload，当前统一协议版本为 19；文本载荷在版本 17 引入，本次统一版本变化来自伤害通知增加最大生命值快照。setter 修改立即发送单实例完整状态，移除立即发送 ID；每 20 个服务端游戏 tick 向同 Level
玩家补发完整集合。没有玩家时不构造传输快照。

客户端按 UUID 和递增 revision 幂等应用；同修订心跳保留原 Particle、布局和播放进度。全量集合删除本地多余 ID，新收到实例用客户端世界时间减
startedTick 定位，之后由客户端 tick 推进。新加入或切换世界的玩家最迟在下一次周期补发时看到现存实例。服务端不逐 tick
求值动画或发送字符进度。

网络可靠顺序和既有世界切换清理负责镜像边界；不增加独立实体注册、粒子随机分发或历史事件重播。当前实现广播同世界玩家，没有距离订阅；大量常驻实例时应由调用方管理创建和移除。

## 缓存、批量渲染与自动裁剪

[客户端实现](../../src/main/java/org/unitego/lobecorp/client/particle/text/TextParticle.java)
缓存每字正文、样式装饰和描边的局部顶点、UV、颜色。普通字形在文本、样式、描边或字体变化时重建；每帧只采样可见字的旋转缩放、冻结矩阵、按字体
RenderType 合并提交。不同实例共用同一批次入口，不为每字建立实体或 TextSubmit。

描边使用原版一字体像素、八方向轮廓，并缓存生成的几何；避免每帧重新遍历文字八次，但仍有八方向的 GPU
顶点和覆盖开销。粗体、斜体、颜色、下划线及删除线保留。混淆字形的局部几何按客户端 tick 刷新，普通字形继续复用缓存。

自动范围由固定字位距离、含描边/装饰的局部顶点半径、全动画最大绝对缩放及字号计算，使用包围球的外接 AABB 覆盖任意整段和单字三轴旋转。自定义组通过
`frustum.isVisible(AABB)` 裁剪，避免原版点裁剪使中心在屏幕外的大字提前消失。无需手填范围，也不逐帧重算整段包围范围。

字体、语言、Unicode 字体及日文字形设置变化会使布局失效。资源重载监听在 FONTS 和 PARTICLE_RESOURCES
之后执行，使旧字体纹理失效并将现存粒子重新加入已清空的引擎队列，保留播放进度。动态玩家头像字体按帧解析当前材质，继续复用固定
UV 几何。

以上为已实现的缓存与批量机制；IDEA 诊断、编译和 Data 的实际结果见修改记录。

## 2026-10-13 Debug 与 Minecraft MCP 实测

通过 IDEA Debug 启动客户端，在隔离新世界创建 cyan literal `CENTER 0123456789`，字号 0.4 格、整段 Y 旋转 180 度，MCP 截图确认世界文字方向和黑色描边可见；切换为 LEFT_TO_RIGHT、间隔 20 tick 后，截图捕获到仅显露 `CENTER` 的中间状态。传入 MutableComponent 在实例构造后追加的 `MUST NOT APPEAR` 未进入已存文本，Debug 比较确认快照独立。

连续两次相同 revision 的周期同步中，Particle 与 layout 对象标识保持相同，客户端 playback 从 620 增长到 648；本轮确认心跳没有重复创建或重播。17 字符的 CENTER_OUT 排名为中央索引 8 排名 0、索引 7 排名 1，符合奇数字符的中心顺序；未将该数值检查等同于完整动画视觉验收。

上述首轮未完成全部方向、单字三轴动画、资源重载和自动范围边缘，下方补测记录覆盖其中已执行的场景。运行日志提示 TextGlyphMesh.Capture 未实现 Sodium 的优化顶点写入路径，属于缓存构建路径的已观察警告；没有性能测量，不能据此量化帧率影响或宣称已达到最佳性能。

## 2026-10-13 补充动态验收

本轮通过 IDEA Debug 的服务端 tick API 在测试存档 `新的世界 (5)` 创建四个独立实例，使用 `ABC\nDEF\nGHI`、字号 0.5、黑描边和 200 tick 显露间隔，以便 Minecraft MCP 捕获中间画面：

- DIRECT 同时显示三行；LEFT_TO_RIGHT 首先显示 A，随后推进到下一行；RIGHT_TO_LEFT 首先显示 I，再向前扩展；CENTER_OUT 首先显示 E，随后向序列两侧展开。两次截图中的既有字符字位固定。
- 中心模式改为偶数文本 `AB\nCD\nEF` 后，截图首先显示 C、D。多行按完整序列展开，不能将此结果描述为每行独立中心展开。
- `XYZS\n....` 的前三字分别循环绕 X、Y、Z 转动，第四字在非均匀缩放关键帧间变化；多张截图确认形变不同，下行静止参照未移动。播放 tick 789 时，三轴分别采样到 350.1 度，第四字缩放为约 (0.3325,0.26375,1)。字符动画只有旋转与缩放通道。
- 整体旋转改为 (15,180,20)、字号从 0.8 改为 1.2 后，截图确认倾斜与放大；服务端 startedTick 前后均为 17108，未重播。
- 清除单字覆盖后使用 24 字长行，分别将中心移到左右视口外；MCP 仍能看到屏幕内的尾部和首部，没有整段提前消失。字号 0.8、中心 (4.5,59,3.5) 的 AABB 为约 (-2.268,52.232,-3.268) 至 (11.268,65.768,10.268)，覆盖完整排版。
- 调用原版资源重载后，边缘文字仍可见；同一 Particle 标识 1160140245 保留，layoutEpoch 从 1 变为 2，playback 从 1398 增长到 2764。移除后服务端查询和后续客户端镜像均为空。

只移除本轮四个文本实例与测试牛，恢复玩家原位置和朝向、伤害配置及既有断点，保存退出到标题界面并关闭 MCP 控制。多客户端一致性、上下视口边缘、第三人称／GUI scale 组合及帧率、分配、GPU 描边性能没有在本轮测量。
