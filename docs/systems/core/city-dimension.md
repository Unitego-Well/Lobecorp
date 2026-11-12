# 都市-后巷-深夜清扫

维度ID：`lobecorp:city/backstreets/midnight_cleanup`。当前为独立场景基础，不自动触发深夜清扫遭遇；可通过「WARP迁跃锚-后巷深宵」地表目标预设进入。

## 生成

使用原版FlatChunkGenerator，底部起依次为一层基岩、两层泥土、一层草方块，地表站立高度Y=-60。继承主世界的高度和常规天空属性，生物群系由平原派生，但刷怪表、生成特征及雕刻器为空；结构覆盖列表为空，关闭湖泊与装饰。

NeoForge的SpawnPlacementCheck和PositionCheck在该维度返回FAIL，阻止经过这些流程的自动生成；不取消所有EntityJoinLevelEvent，因此已有实体跨维度传入、命令生成以及显式添加实体的玩法仍可使用。

## 时间

使用26.1原生WorldClock，ID与维度ID相同；该维度的default_clock、独立day/moon/villager_schedule时间线均指向它。时间由原生时钟管理器推进、保存、同步，日夜和月相不读取主世界时钟；初始状态使用原版默认值，未固定午夜。底层服务器游戏tick仍按原版运行，不改全局gameTime。

管理员可用：

```mcfunction
/time of lobecorp:city/backstreets/midnight_cleanup set 18000
/time of lobecorp:city/backstreets/midnight_cleanup pause
/time of lobecorp:city/backstreets/midnight_cleanup resume
/execute in lobecorp:city/backstreets/midnight_cleanup run tp @s 0.5 -60 0.5
```

上述命令语法已按当前原版接口核对，未进游戏执行。

## 方块保护

玩家挖掘复用MobEffectEvents中的唯一BreakBlockEvent入口，创造模式同样拦截；EntityPlaceEvent覆盖单块和多块放置。桶操作复用既有右键物品/方块事件禁止，工具改块、流体生成方块、爆炸毁块及生物破坏分别通过NeoForge对应事件处理。爆炸仍可伤害实体。

保护按维度键判断，仅影响该维度。管理员setblock/fill和直接调用setBlock且不触发相应NeoForge事件的代码不属于此事件保护边界；生成器本身正常铺设地形。

## 维护与验证

维度键：registry/world/LcLevels；时钟键：registry/world/LcWorldClocks；名称随registry/world/LcLevels中的维度声明登记；生成源：generator/CityDimensionGenerator；独有事件：events/CityDimensionEvents。生成器使用原版注册表Provider的createSerializationContext保留Holder所有权，经原版Codec派生数据，输出统一由Data生成，不手工维护JSON。

2026-11-11：compileJava与runData通过（22秒），processResources通过（4秒），五份Java的IDEA错误检查通过；已核对地层、空结构/刷怪/特征表、时钟及三条时间线引用和中文名称。未启动游戏，未验证实际维度加载、方块交互、刷怪和存档重载。
