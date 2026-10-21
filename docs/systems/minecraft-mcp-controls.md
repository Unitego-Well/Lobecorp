# Minecraft MCP 结构化操控

2026-10-21：原始项目 dev 分支新增四个工具。代码已接入；实际检查结果见本次交付记录。桥接必须重新构建、客户端必须加载新 Mod 代码，当前运行的其他工作区不会自动获得这些接口。

## 通用约定

- `mode=direct` 调用按钮或本地集成服务端 API；`mode=simulate` 通过游戏内 Screen、KeyMapping 和 MultiPlayerGameMode 交互，不发送操作系统鼠标键盘。
- 所有写操作须处于 MCP 接管模式，并在实际执行线程重新检查。只读查询不要求接管；`player_control/release` 可随时释放本工具持有的输入。
- 直接修改玩家、实体、方块只支持本客户端的集成服务端。多人客户端的模拟交互走正常网络和服务器规则；没有新增远程管理员协议或客户端伪造世界状态。
- 返回 `{ok,data,error}`。GUI、模拟交互的 `accepted` 表示回调处理或动作提交，不证明服务端最终接受或任务完成。超时／中断可能已执行，不自动重试写操作。
- UUID 标识实体；只读实体列表和方块读取来自客户端已加载数据，不代表整个世界的权威查询。直接写入在服务端线程进行，GUI 和输入在客户端线程进行。

## gui_control

先 `{"action":"list"}` 获取 `screenId`、`widgetId`、标签、类型、位置、大小、可用性和文本框当前值。写操作必须带当前 `screenId`，屏幕已切换则拒绝。

| 模式 | action | 主要参数 |
|---|---|---|
| direct | click | widgetId；仅可见、启用的 AbstractButton |
| direct | set_text | widgetId、text；仅 EditBox，返回实际接受的值 |
| direct | focus | widgetId |
| simulate | click | x、y、button（0/1/2） |
| simulate | drag | x、y、endX、endY、button |
| simulate | scroll | x、y、scrollX、scrollY |
| simulate | type | text；逐 Unicode 码点交给当前 Screen |
| simulate | key | GLFW key 数值、modifiers；发送按下和释放 |
| simulate | close | 调用当前 Screen 的关闭流程 |

坐标 `coordinateSpace=gui` 为 GUI 单位；`framebuffer` 为截图原始像素，经当前屏幕尺寸换算。文本上限 4096 UTF-16 单元，树遍历上限 2048 节点。旧的 click、拖动、键盘工具继续保留。

## player_control

- `get`：位置、朝向、生命、饥饿和选中栏位；`inventory` 另返回物品栏条目。
- direct：`teleport(position)`、`set_state(state)`、`gamemode(gamemode)`、`set_slot(slot,item,count)`、`select_slot(slot)`。
- state 支持 health、food、saturation；按玩家实际生命上限和食物范围校验。物品以注册 ID 创建，不写入任意 NBT，数量不超过物品自身最大堆叠。
- simulate：`input(keys,ticks)`、`release`、`look(yaw,pitch)`、`select_slot(slot)`、`attack`、`use(hand)`。
- keys 支持 forward、backward、left、right、jump、sneak、sprint、attack、use。每个请求键独立保持 1～200 客户端 tick；false 提前释放指定键。到期、打开 GUI、玩家更换、离开世界或退出接管时释放本工具持有的键。
- 不用 Thread.sleep 阻塞客户端线程。按键上下文由 MCP 模块单一 ClientTickEvent.Pre 入口清理，到期状态由单一 ClientTickEvent.Post 入口推进。

示例：

```json
{"action":"input","mode":"simulate","keys":{"forward":true,"jump":true},"ticks":20}
```

## entity_control

- `list`：center、radius（最大128）、type、limit（最大256）；返回 entities 和 truncated。`get` 使用 uuid。
- direct：`spawn(type,position)`、`teleport(uuid,position)`、`set_state(uuid,state)`、`navigate(uuid,position,speed)`、`stop(uuid)`、`remove(uuid)`。
- state 支持 name、health、noAi、invulnerable、glowing。导航只支持 Mob，speed 为 0.01～4；remove 直接移除而非死亡掉落。直接实体修改拒绝玩家目标，玩家只通过 player_control 修改本地玩家。
- 生成／传送只接受当前服务端维度内已加载位置。生成沿用原版创建与 Mob 初始化流程，并检查世界是否接受该实体。
- simulate：`attack(uuid)`、`interact(uuid,hand)`。必须是当前准星目标、在真实攻击／交互距离内且无遮挡；攻击使用主手，交互可指定 main/off。

## block_control

- `get(position)`：方块 ID、properties 和是否有方块实体。
- direct：`set(position,block,properties)`、`fill(position,to,block,properties)`、`break(position,drops)`。fill 包含两端，最大4096方块；修改前核对整个区域边界与区块加载情况，不主动生成区块。保留正常更新与邻居通知，返回 requested 与 changed；不是撤销事务，不保证邻居事件的外部副作用原子性。
- simulate：`start_break(position)`、`continue_break(position)`、`stop_break`、`use(position,hand)`、`place(position,hand)`。位置必须是当前准星指向的方块并在交互距离内。放置使用当前手持物品、命中的面和正常交互结果；生存模式挖掘须随 tick 推进继续请求，不能把一次 start_break 当作已经破坏。
- 不提供任意方块实体 NBT 写入。

示例：

```json
{"action":"set","mode":"direct","position":{"x":0,"y":64,"z":0},"block":"minecraft:oak_log","properties":{"axis":"y"}}
```

## 检查与运行边界

本轮按用户约定仅进行 IDEA 静态检查。11 个 Java／TypeScript 文件未报告错误，保留 3 条借用游戏 Level 对象的资源归属提示，不关闭游戏持有的 Level；文档中既有失效链接与桥接脚本的本地依赖定位提示未扩大修复。Git diff --check 无输出。未执行桥接构建、Java 编译、Data、游戏或多人验收。工具定义由原65项扩展至69项；现有工具发现脚本同步检查新增工具的输入、输出和变更元数据。源码中的69项定义不等于旧连接已经刷新。

修改前 Git 基线保存在 `refs/backups/before-mcp-world-controls-20261021`。其他聊天工作区的桥接与游戏实例未修改。
