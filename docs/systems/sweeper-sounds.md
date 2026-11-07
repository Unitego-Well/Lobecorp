# 清道夫音效资源

导入：2026-11-07。原文件位于E:/Downloads/清道夫音频/音频，名称与字节未改。仅根据文件名分类，未试听判定动作内容；导入副本为OGG Vorbis、单声道48kHz、质量5，不额外裁剪或响度处理。数字后缀区分同类变种，不推断括号序号代表音质或版本。

资源目录：src/main/resources/assets/lobecorp/sounds/sweeper/；sounds.json定义七组可用资源事件，无Java触发逻辑。clear_loop仅表示清扫进行阶段，不自动循环；何时触发/随机/循环由后续动作逻辑明确。

| 分类 | 事件ID | 变种 |
|---|---|---|
| 移动 | `lobecorp:entity.sweeper.move` | 12 |
| 攻击 | `lobecorp:entity.sweeper.attack` | 4 |
| 清扫开始 | `lobecorp:entity.sweeper.clear_start` | 2 |
| 清扫进行 | `lobecorp:entity.sweeper.clear_loop` | 3 |
| 清扫结束 | `lobecorp:entity.sweeper.clear_end` | 1 |
| 飞扑起跳 | `lobecorp:entity.sweeper.jump` | 2 |
| 飞扑落地 | `lobecorp:entity.sweeper.land` | 2 |

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
