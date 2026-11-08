# 清道夫音效资源

导入：2026-11-07。原文件位于E:/Downloads/清道夫音频/音频，名称与字节未改。仅根据文件名分类，未试听判定动作内容；导入副本为OGG Vorbis、单声道48kHz、质量5，不额外裁剪或响度处理。数字后缀区分同类变种，不推断括号序号代表音质或版本。

资源目录：src/main/resources/assets/lobecorp/sounds/sweeper/；sounds.json七组资源事件已在LcSoundEvents注册并接入实体和环境调度。clear_loop资源本身不自动循环，重复由动作/环境入口控制。以下文件对应表保留导入来源，不重复放置音频。

| 分类 | 事件ID | 变种 |
|---|---|---|
| 移动 | `lobecorp:entity.sweeper.move` | 12 |
| 攻击 | `lobecorp:entity.sweeper.attack` | 4 |
| 清扫开始 | `lobecorp:entity.sweeper.clear_start` | 2 |
| 清扫进行 | `lobecorp:entity.sweeper.clear_loop` | 3 |
| 清扫结束 | `lobecorp:entity.sweeper.clear_end` | 1 |
| 飞扑起跳 | `lobecorp:entity.sweeper.jump` | 2 |
| 飞扑落地 | `lobecorp:entity.sweeper.land` | 2 |

## 2026-11-08 客户端位置音效（当前）

深夜清扫使用普通SimpleSoundInstance，SoundSource.AMBIENT受原版环境音量控制，Attenuation.LINEAR按世界坐标衰减；播放后位置固定，不跟随玩家、相机或浪潮，不使用旧环境声的手动音量曲线/遮挡缓存。每游戏刻按玩家位置选最近的已同步有效事件，在其活动波头/波尾与宽度内、最近有效点周围32格区域随机取点，避开分割空区；候选失败则跳过，不退回玩家耳边。距离筛选仍受soundRange配置约束并封顶256格，不扩展模型/粒子范围。

七组客户端资源事件直接复用现有26份OGG，未复制音频，不扩大真实清道夫实体的32～64格范围。原版根据声音资源的attenuation_distance实现以下可听范围上限，边界处衰减至零，实际听感仍受音量与声道预算影响：

| 类型 | 客户端事件ID | 范围（格） |
|---|---|---|
| 移动 | `lobecorp:event.midnight_cleanup.move` | 256 |
| 攻击 | `lobecorp:event.midnight_cleanup.attack` | 160 |
| 清扫开始 | `lobecorp:event.midnight_cleanup.clear_start` | 96 |
| 清扫进行 | `lobecorp:event.midnight_cleanup.clear_loop` | 128 |
| 清扫结束 | `lobecorp:event.midnight_cleanup.clear_end` | 160 |
| 飞扑起跳 | `lobecorp:event.midnight_cleanup.jump` | 96 |
| 飞扑落地 | `lobecorp:event.midnight_cleanup.land` | 192 |

每次3个独立位置/变种，10倍逻辑调度与基础并发×30保持；soundVoices=0仍关闭浪潮声音，正值不再限制固定实例的历史跟随槽位，实际在播数由soundMaxActive控制。退出范围/事件结束后已有短音自然结束；关闭声音、切世界与断开只停止自身实例。镜头震动仍使用原有相机距离采样，范围及强度不变。清扫/重组持续声的2.2倍节奏不变。

本轮验证单独记录于.local/sweeper-positional-audio-20261108/；未启动游戏、试听或执行性能验收。下方2026-11-07接入方式仅为历史证据，不作为当前声音行为。

## 2026-11-07 密度倍率调整（历史，倍率仍有效）

用户确认环境声每次数量×3、调度频率×10；通过每游戏刻10次逻辑调度推进现有计时，每次创建3个独立变种声音实例。声音文件音长、音调、单实例增益、范围与镜头震动不变；环境动作片段的调度间隔也随逻辑时钟缩短，不改变真实技能/移动速度。同类重入放宽，逻辑并发额度为soundMaxActive基础值×3×10，默认4→120；Minecraft自身总声道仍可能限制实际播放数量，不能承诺固定30倍听感或性能。

相同采样位置的副本共享每20游戏刻一次遮挡检查，避免副本/频率直接放大重复射线；暂停与断开仍沿原生命周期。清扫与重组技能持续声音由每12刻一次改为每60刻11次（5/6刻分布），即2.2倍；开始/结束/真实飞扑及攻击时机不变。

## 2026-11-07 接入契约（历史）

实体：移动使用脚步入口；每次攻击前摇一次；清扫/重组开始、进行、结束跟随既有技能，进行声每12tick；飞扑仅在实际设置起跳速度后播放jump，仅确认onGround的结束分支播放land，空中取消不伪造落地。均由服务端播放，客户端只消费声音，避免双侧重复。实体声音为32-64格的普通位置音效，不全部放大至256格。

环境：复用HordeAmbient，从已同步ClientEvent与当前活动波头/波尾取路线采样，避开分割空区，不依赖可见渲染batches。默认2个位置采样，soundVoices仍可调；总在播默认4。move每7-13tick、attack每30-70tick，长攻击未结束不重入；清扫小段每160-240tick开始，进行声3-6次、每8-16tick，结束一次；飞扑对每240-400tick，模拟落地在起跳结束后衔接。所有频率是环境总量，不乘虚拟单位数；满额时落地/收尾优先，普通声跳过而非积压。

既有Source扩为原版AbstractTickableSoundInstance，更新位置和音量；只保留本功能在播引用，自然结束/关闭/切世界时结束并停止自身，不stopAll。环境使用位置声方向、无原生距离衰减，再按相机到采样点的距离做平滑增益与遮挡衰减，volume不靠大于1扩大范围；最大默认256格，实际仍受收到事件范围限制，不增加可见模型/粒子数。没有额外全局音效管理器或逐虚拟单位音源。

震动：复用NeoForge ComputeCameraAngles，仅叠加渲染yaw/pitch/roll，不改变玩家朝向、位置、FOV或输入。浪潮默认64格内按距离连续轻震；真实落地默认24格、6tick（0.3秒）短促震动。客户端通过PlaySoundEvent消费服务端真实落地声，排除环境Source的模拟落地，不加网络包或相机Mixin；手动播放同一land声音也视为同一表现事件。多次落地取有界最大余量，不按数量无限叠加。

配置均在events.midnight_cleanup.wave.sound：sound、soundVoices沿用；新增soundRange=256、soundMaxActive=4、eventShake=true、landingShake=true、eventShakeRange=64、landingShakeRange=24、shakeStrength=1。声音与震动独立开关；落地震动开关作用于真实清道夫，不要求存在浪潮。暂停沿游戏时间冻结，恢复不补播积压。所有范围/强度仅为初始建议，未听觉/画面调校。

Java25主/Bench/测试源码编译、Data与jar通过，Data23文件写2删0，10份变更Java的IDEA检查无错误；调度复核后再次编译打包通过。未执行单元/集成、游戏、实际声音/震动或性能验收，不声称效果达标。证据.local/sweeper-audio-integration-20261107/。

## 文件对应

| 外部原名 | Mod副本 |
|---|---|
| footstep (1)_[cut_0sec].wav | `sweeper/move1.ogg` |
| footstep (1)_[cut_0sec] (1).wav | `sweeper/move2.ogg` |
| footstep (1)_[cut_0sec] (2).wav | `sweeper/move3.ogg` |
| walk_[cut_0sec].wav | `sweeper/move4.ogg` |
| walk_[cut_0sec] (1).wav | `sweeper/move5.ogg` |
| walk_[cut_1sec].wav | `sweeper/move6.ogg` |
| run_[cut_0sec].wav | `sweeper/move7.ogg` |
| run_[cut_0sec] (1).wav | `sweeper/move8.ogg` |
| run_[cut_0sec] (2).wav | `sweeper/move9.ogg` |
| run (1)_[cut_0sec].wav | `sweeper/move10.ogg` |
| run (1)_[cut_0sec] (1).wav | `sweeper/move11.ogg` |
| run (1)_[cut_0sec] (2).wav | `sweeper/move12.ogg` |
| scrape attack 1_[cut_1sec].wav | `sweeper/attack1.ogg` |
| scrape attack 2_[cut_0sec].wav | `sweeper/attack2.ogg` |
| scrape attack 3_[cut_3sec].wav | `sweeper/attack3.ogg` |
| scrape attack quiker_[cut_1sec].wav | `sweeper/attack4.ogg` |
| clear 1_[cut_1sec].wav | `sweeper/clear_start1.ogg` |
| clear 1 (1)_[cut_1sec].wav | `sweeper/clear_start2.ogg` |
| clear 2_[cut_0sec].wav | `sweeper/clear_loop1.ogg` |
| clear 2_[cut_0sec] (1).wav | `sweeper/clear_loop2.ogg` |
| clear 2_[cut_0sec] (2).wav | `sweeper/clear_loop3.ogg` |
| clear 3_[cut_1sec].wav | `sweeper/clear_end1.ogg` |
| 飞扑起跳.wav | `sweeper/jump1.ogg` |
| 飞扑起跳 (2).wav | `sweeper/jump2.ogg` |
| 飞扑落地.wav | `sweeper/land1.ogg` |
| 飞扑落地 (2).wav | `sweeper/land2.ogg` |

26份输出已检查Ogg/Vorbis标识、声道/采样率并解码验证；声音事件引用与文件一一对应。不代表音量、音色、动作时机或游戏内听觉验收。原始SHA、输出SHA与映射在忽略的.local/sweeper-audio-import-20261107/。

## 2026-11-08 用户验收补充

用户在1～8项实机核对中确认第7项音效距离衰减、分类可听范围与镜头震动成功。本轮代理未运行游戏，不将该确认扩大为量化音频/性能数据；当前确认范围与其他项见[事件验收记录](horde-events.md#2026-11-08-用户实机验收记录)。
