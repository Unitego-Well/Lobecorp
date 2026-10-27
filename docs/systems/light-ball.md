# 光球

核对日期：2026-10-18。从当前 laser 拆出的独立光球工程，与光束共享已有模型和着色器；没有覆写 queen_star_beam 模板、accumulate_light_ball 或用户的法阵资产。

## 文件与入口

- [可编辑工程](../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/light_ball.fxproj)。
- [运行定义](../../src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/light_ball.fx)。
- [独立依赖包](../../src/main/resources/fxpacks/light_ball.fxpack)。
- 运行 ID：`lobecorp:the_queen_of_hatred/light_ball`。

| 动画 | UUID | 行为 |
|---|---|---|
| light_ball_start | 62d889de-0a4f-4d65-8acf-22f9960456f1 | 20t 汇聚入场，然后保持光球与持续层 |
| light_ball_end | c5fef93c-8c36-4b39-8bb9-9bcf98f7fd78 | 20t 收尾，停止持续生成并清理 |
| light_ball_burst | 8cb63d9f-2a52-47f2-9626-276b6441ded4 | 独立首爆，可重复触发；波壳、十字星、火花结束后光球继续保持 |

默认入口为 light_ball_start，三个同名 TRIGGER 规则可用。入口使用当前 PhotonAnimationRuntime 的 `play(name)` 或 `trigger(name, data)`；启动与结束在同一个运行实例上调用，阶段之间没有固定总时长的自动串联。爆发不是结束，不销毁宿主。

## 内容与坐标

共 24 个对象，包括光核、内外辉光、汇聚模型及其生成器、粉色带模板与生成器、流动壳、持续散射、十字星、椭圆冲击波、首爆及结束火花。保留原几何、UV、材质、配额和渲染修复配置；没有光柱、花瓣、光束白线或末端粒子。

light_ball_anchor 保持原激光锚点的位置 [0, 1.5, 0] 与等比缩放 1.2。laser_spell 中的 light_ball 发射器使用 [0, -1.5, 0] 抵消此偏移，与激光源头对齐；不要再把共用锚点的位移计入第二次。

24 个对象与激光的 13 个对象只共用一个空间锚点及一个控制器的结构副本，其余 33 个原对象分属两侧。对象 UUID 在各独立 FX 作用域内保留，PROJECT 模板只引用本工程内对象。

## 检查与交付边界

工程与压缩定义已通过原生校验及保存回读；独立包 12 个文件、1 份 FX 定义，导出 warnings=[]。原生根对象 tick 的隔离检查覆盖保持、重复爆发、结束与重启；正式嵌入场景进一步验证了法术蓄力、释放、独立爆发、结束及重启，problems=[]。截图仅证明透视场景中的可见结果，不作为帧率或参考视频复刻验收。

法术调用和对象索引见[激光法术](laser-spell.md)，光束部分见[激光](queen-arcana-beam.md)。修改前的字节资产、实时法术工程、阶段回执与导出副本见拆分备份（历史引用，当前工作区缺失：`../../backups/laser-light-ball-split-20261018`）。
