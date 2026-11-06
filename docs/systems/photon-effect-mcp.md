# Photon 特效 MCP

## 2026-10-28 命名与文件事务维护

PhotonEffectNamedUtil.Prepared.commit 在 dry-run 时验证草稿，实际提交直接复用 PhotonNamedAuthoringSession.commit 的内部校验；准备到提交之间的版本、控制器与原树保护继续存在。有变化才新增一条历史，无变化不新增历史，不增加跨阶段缓存。

PhotonEffectFileUtil 四条 prepare 失败路径共用临时文件清理；删除失败附加为 suppressed，首个编码/重读/校验异常继续向调用方传播。发布、目标覆盖、文件锁、重读和取消边界保留，不把错误隐藏成成功结果。

核对日期：2026-10-18。当前接口依赖 Photon 26.1.2.3、LDLib2 26.1.2.42 与本仓库 Minecraft MCP 子项目；版本与构建入口见 [Minecraft MCP](minecraft-mcp.md)。

2026-10-20新增命名制作/运行通道：effect_animation_state、effect_animation_edit、effect_animation_control。API/MCP与功能实现同步推进，界面交由人工审查；当前开放范围、句柄/版本、保护及验证入口见命名API（历史路径：`photon-effect-mcp-api.md#命名制作与运行-api2026-10-20`；当前 dev 未保留）。G1与全部资源兼容仍未通过。

MCP 新建、打开和枚举工程仅使用 `/lobecorp photon_editor` 对应的扩展窗口身份，原生 `/photon_editor` 工程不进入此接口；最小化窗口按原生恢复流程复用，打开失败保留旧标签。界面与文件拦截边界见[编辑器入口](photon-editor.md#入口与隔离)，本轮尚未游戏验收。

本文仅作路由；API、案例与历史分开维护，不默认加载整份历史。当前工程是 queen_star_beam（历史路径：`queen-star-beam.md`；当前 dev 未保留），开发直读与打包边界见[资源与生成](resources-datagen.md#开发环境源码资源直读2026-10-15)。

| 当前问题 | 读取 |
|---|---|
| 制作方法、参考复刻、变量或交付 | 制作 Skill（历史路径：`../../.codex/skills/lobecorp-effect-authoring/SKILL.md`；当前 dev 未保留） |
| 工具参数、事务、资源、视角、保存 | API 对应章节（历史路径：`photon-effect-mcp-api.md`；当前 dev 未保留） |
| 连接、截图、控制与 Debug | Minecraft MCP Skill（历史路径：`../../.codex/skills/lobecorp-minecraft-mcp/SKILL.md`；当前 dev 未保留） |
| 当前工程路径、形参、依赖及限制 | 女皇星束（历史路径：`queen-star-beam.md`；当前 dev 未保留） |
| 经验与故障模式 | 制作经验（历史路径：`effect-authoring-lessons.md`；当前 dev 未保留） |
| 旧参数、采样或当时验收 | 历史记录（历史路径：`photon-effect-authoring-history.md`；当前 dev 未保留）对应标题 |

## 2026-10-15 命令接口修复

此节是历史证据；当前资产见女皇星束（历史路径：`queen-star-beam.md`；当前 dev 未保留）。读取当时记录（历史路径：`photon-effect-authoring-history.md#2026-10-15-命令接口修复`；当前 dev 未保留）。

## 使用顺序

创建前进入世界；写入遵守控制门禁。FX 编辑采用暂停 → state/revision → edit_batch，实际成功后再保存或导出。 读取对应 API（历史路径：`photon-effect-mcp-api.md#使用顺序`；当前 dev 未保留）。

## 公开工具

按本轮 capabilities/schema 选择工具和参数，不凭旧清单判断能力。 读取对应 API（历史路径：`photon-effect-mcp-api.md#公开工具`；当前 dev 未保留）。

## Photon MCP 部分能力接入

工程、资源、纹理与视角复用原生入口；实际暴露与游戏验收分别判断。 读取对应 API（历史路径：`photon-effect-mcp-api.md#photon-mcp-部分能力接入`；当前 dev 未保留）。

## 批量操作与参数

精确 NBT 往返、版本冲突、模型大快照省略和视角保持详见对应 API 章节。 读取对应 API（历史路径：`photon-effect-mcp-api.md#批量操作与参数`；当前 dev 未保留）。

### 模型状态去重与视角保持

snapshotIncluded 与模型编辑约束（历史路径：`photon-effect-mcp-api.md#模型状态去重与视角保持`；当前 dev 未保留），不要解析被省略的快照。

## 生命周期、文件与依赖

线程、事务阶段、允许目录、覆盖授权及原生文件格式见 API。 读取对应 API（历史路径：`photon-effect-mcp-api.md#生命周期文件与依赖`；当前 dev 未保留）。

## V3 持续发射与参考首爆修订（2026-10-15）

此节是历史证据；当前资产见女皇星束（历史路径：`queen-star-beam.md`；当前 dev 未保留）。读取当时记录（历史路径：`photon-effect-authoring-history.md#v3-持续发射与参考首爆修订2026-10-15`；当前 dev 未保留）。

## V3 性能优化（2026-10-15）

此节是历史证据；当前资产见女皇星束（历史路径：`queen-star-beam.md`；当前 dev 未保留）。读取当时记录（历史路径：`photon-effect-authoring-history.md#v3-性能优化2026-10-15`；当前 dev 未保留）。

### 持续生成模型的进一步精简

读取历史记录（历史路径：`photon-effect-authoring-history.md#持续生成模型的进一步精简`；当前 dev 未保留）。

## 参考特效与批处理入口

此节是历史证据；当前资产见女皇星束（历史路径：`queen-star-beam.md`；当前 dev 未保留）。读取当时记录（历史路径：`photon-effect-authoring-history.md#参考特效与批处理入口`；当前 dev 未保留）。

## 粉色 3D 模型激光

此节是历史证据；当前资产见女皇星束（历史路径：`queen-star-beam.md`；当前 dev 未保留）。读取当时记录（历史路径：`photon-effect-authoring-history.md#粉色-3d-模型激光`；当前 dev 未保留）。

## 教程冲击波工程（2026-10-14）

此节是历史证据；当前资产见女皇星束（历史路径：`queen-star-beam.md`；当前 dev 未保留）。读取当时记录（历史路径：`photon-effect-authoring-history.md#教程冲击波工程2026-10-14`；当前 dev 未保留）。

### V3 双层模型激光（2026-10-15，历史）

读取历史记录（历史路径：`photon-effect-authoring-history.md#v3-双层模型激光2026-10-15历史`；当前 dev 未保留）。

### V3 一秒汇聚、持续输出与终结爆炸（2026-10-14，历史）

读取历史记录（历史路径：`photon-effect-authoring-history.md#v3-一秒汇聚持续输出与终结爆炸2026-10-14历史`；当前 dev 未保留）。

### V3 蓄力重做与分层时序（2026-10-14，历史）

读取历史记录（历史路径：`photon-effect-authoring-history.md#v3-蓄力重做与分层时序2026-10-14历史`；当前 dev 未保留）。

### 雾与体积效果入口（2026-10-14）

读取入口与限制（历史路径：`photon-effect-mcp-api.md#雾与体积效果入口2026-10-14`；当前 dev 未保留）。

### 初版记录（历史）

读取历史记录（历史路径：`photon-effect-authoring-history.md#初版记录历史`；当前 dev 未保留）。

## 实际检查与限制

此节是历史证据；当前资产见女皇星束（历史路径：`queen-star-beam.md`；当前 dev 未保留）。读取当时记录（历史路径：`photon-effect-authoring-history.md#实际检查与限制`；当前 dev 未保留）。

> 2026-10-23 当前接入：本工作区消费 lib 中的外部 MCP JAR／bridge，端口 19877；源码注册 7 个读操作与 13 个写操作。当前 [API 索引](photon-effect-mcp-api.md) 已按源码补齐，工作区与未完成项见 [Photon 入口](photon-workspace.md)。下文旧子项目、工程案例和阶段检查为历史；缺失专题明确标记，不作为可用资源或本轮运行证据。

核对日期：2026-10-18。该历史阶段接口依赖 Photon 26.1.2.3、LDLib2 26.1.2.42 与当时的 Minecraft MCP 子项目；当前已经改用 lib 外部制品，版本与构建入口见 [Minecraft MCP](minecraft-mcp.md)。

2026-10-20新增命名制作/运行通道：effect_animation_state、effect_animation_edit、effect_animation_control。API/MCP与功能实现同步推进，界面交由人工审查；当前开放范围、句柄/版本、保护及验证入口见[命名API](photon-effect-mcp-api.md)。G1与全部资源兼容仍未通过。

MCP 新建、打开和枚举工程仅使用 `/lobecorp photon_editor` 对应的扩展窗口身份，原生 `/photon_editor` 工程不进入此接口；最小化窗口按原生恢复流程复用，打开失败保留旧标签。界面与文件拦截边界见[编辑器入口](photon-editor.md#入口与隔离)，本轮尚未游戏验收。

本文仅作路由；API、案例与历史分开维护，不默认加载整份历史。当前工程是 queen_star_beam（历史路径 `queen-star-beam.md`，本工作区不存在），开发直读与打包边界见[资源与生成](resources-datagen.md#输入与输出)。

| 当前问题 | 读取 |
|---|---|
| 制作方法、参考复刻、变量或交付 | 制作 Skill（当前分支缺失，原路径：`../../.codex/skills/lobecorp-effect-authoring/SKILL.md`；见[问题汇总](../issues.md)） |
| 工具参数、事务、资源、视角、保存 | [API 对应章节](photon-effect-mcp-api.md) |
| 连接、截图、控制与 Debug | Minecraft MCP Skill（当前分支缺失，原路径：`../../.codex/skills/lobecorp-minecraft-mcp/SKILL.md`；见[问题汇总](../issues.md)） |
| 当前工程路径、形参、依赖及限制 | 女皇星束（历史路径 `queen-star-beam.md`，本工作区不存在） |
| 经验与故障模式 | 制作经验（历史路径 `effect-authoring-lessons.md`，本工作区不存在） |
| 旧参数、采样或当时验收 | 历史记录（历史路径 `photon-effect-authoring-history.md`，本工作区不存在）对应标题 |

## 2026-10-15 命令接口修复

此节是历史证据；当前资产见女皇星束（历史路径 `queen-star-beam.md`，本工作区不存在）。读取当时记录（历史路径 `photon-effect-authoring-history.md#2026-10-15-命令接口修复`，本工作区不存在）。

## 使用顺序

创建前进入世界；写入遵守控制门禁。FX 编辑采用暂停 → state/revision → edit_batch，实际成功后再保存或导出。 [读取对应 API](photon-effect-mcp-api.md)。

## 公开工具

按本轮 capabilities/schema 选择工具和参数，不凭旧清单判断能力。 [读取对应 API](photon-effect-mcp-api.md)。

## Photon MCP 部分能力接入

工程、资源、纹理与视角复用原生入口；实际暴露与游戏验收分别判断。 [读取对应 API](photon-effect-mcp-api.md)。

## 批量操作与参数

精确 NBT 往返、版本冲突、模型大快照省略和视角保持详见对应 API 章节。 [读取对应 API](photon-effect-mcp-api.md)。

### 模型状态去重与视角保持

[snapshotIncluded 与模型编辑约束](photon-effect-mcp-api.md)，不要解析被省略的快照。

## 生命周期、文件与依赖

线程、事务阶段、允许目录、覆盖授权及原生文件格式见 API。 [读取对应 API](photon-effect-mcp-api.md)。

## V3 持续发射与参考首爆修订（2026-10-15）

此节是历史证据；当前资产见女皇星束（历史路径 `queen-star-beam.md`，本工作区不存在）。读取当时记录（历史路径 `photon-effect-authoring-history.md#v3-持续发射与参考首爆修订2026-10-15`，本工作区不存在）。

## V3 性能优化（2026-10-15）

此节是历史证据；当前资产见女皇星束（历史路径 `queen-star-beam.md`，本工作区不存在）。读取当时记录（历史路径 `photon-effect-authoring-history.md#v3-性能优化2026-10-15`，本工作区不存在）。

### 持续生成模型的进一步精简

读取历史记录（历史路径 `photon-effect-authoring-history.md#持续生成模型的进一步精简`，本工作区不存在）。

## 参考特效与批处理入口

此节是历史证据；当前资产见女皇星束（历史路径 `queen-star-beam.md`，本工作区不存在）。读取当时记录（历史路径 `photon-effect-authoring-history.md#参考特效与批处理入口`，本工作区不存在）。

## 粉色 3D 模型激光

此节是历史证据；当前资产见女皇星束（历史路径 `queen-star-beam.md`，本工作区不存在）。读取当时记录（历史路径 `photon-effect-authoring-history.md#粉色-3d-模型激光`，本工作区不存在）。

## 教程冲击波工程（2026-10-14）

此节是历史证据；当前资产见女皇星束（历史路径 `queen-star-beam.md`，本工作区不存在）。读取当时记录（历史路径 `photon-effect-authoring-history.md#教程冲击波工程2026-10-14`，本工作区不存在）。

### V3 双层模型激光（2026-10-15，历史）

读取历史记录（历史路径 `photon-effect-authoring-history.md#v3-双层模型激光2026-10-15历史`，本工作区不存在）。

### V3 一秒汇聚、持续输出与终结爆炸（2026-10-14，历史）

读取历史记录（历史路径 `photon-effect-authoring-history.md#v3-一秒汇聚持续输出与终结爆炸2026-10-14历史`，本工作区不存在）。

### V3 蓄力重做与分层时序（2026-10-14，历史）

读取历史记录（历史路径 `photon-effect-authoring-history.md#v3-蓄力重做与分层时序2026-10-14历史`，本工作区不存在）。

### 雾与体积效果入口（2026-10-14）

[读取入口与限制](photon-effect-mcp-api.md)。

### 初版记录（历史）

读取历史记录（历史路径 `photon-effect-authoring-history.md#初版记录历史`，本工作区不存在）。

## 实际检查与限制

此节是历史证据；当前资产见女皇星束（历史路径 `queen-star-beam.md`，本工作区不存在）。读取当时记录（历史路径 `photon-effect-authoring-history.md#实际检查与限制`，本工作区不存在）。

## 当前教程工程入口（2026-10-15）

V3 已迁移为 src/main/resources/assets/lobecorp/fx/the_queen_of_hatred/queen_star_beam.fxproj，运行 ID 和命名动画均改为 lobecorp:the_queen_of_hatred/queen_star_beam／queen_star_beam。保留 31 对象、原有 200 tick 时序及 length／width／intensity／enabled 形参；配套图收敛为 shaders/photon/queen_star_beam 下的 17 份当前依赖，废弃教程版本和中间备份退出活动目录。

开发模式下，MCP 工作区沿 LDLib2.getAssetsDir／FXPacks.getFxPacksDir 自动使用源码根；没有另建 MCP 文件路径分支。原保存、导出、越界与覆盖门禁保持。工程及正式定义／包的路径和变量默认值见 女皇星束资源（历史路径：`queen-star-beam.md`；当前 dev 未保留）；源码直读与发行边界见 [资源与生成](resources-datagen.md#开发环境源码资源直读2026-10-15)。

本轮 Client Debug 真实读取新 ID、列举新 ID、加载 31 对象及按资源 ID 读取全部 17 图成功，GUI／OBJ 源码资源可读，原生包导出 18 文件、warnings=[]。这是目录迁移及读取检查，没有重新进行视频外观、完整游戏内编辑交互或发行环境验收。
