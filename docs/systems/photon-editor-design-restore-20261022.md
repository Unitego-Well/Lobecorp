# Photon 原始编辑器设计恢复（2026-10-22）

> 本页仅保留分阶段设计和原始检查证据，不维护当前待办；当前未解决问题统一见 [Photon 问题汇总](../issues.md)。

> 2026-10-23 后续高级网格、材质与骨骼／morph WIP 已记录在 [最新续做页](photon-workspace.md)。本页仍为 2026-10-22 布局恢复的来源与当时检查，不据后续代码反写当时的验收结论；当前导航见 [Photon 工作区](photon-workspace.md)。

## 范围与依据

用户确认采用移除前提交 `04a3bab171bc7a843e506971dd0f130b18562593` 的编辑器设计。最初提及的 `af77096` 经核对属于2026-10-21静态修复阶段，已经是重写界面，未作为此次布局来源。

本次恢复原版停靠布局、原生控件、列表与UV交互，并接入当前工程事务和原生预览。当前模型格式仅为 .mfxproj／.mfx／.mfxpack，不恢复被移除的旧格式读写、专用渲染后端或实体动画／模型LOD逻辑。既有资源、未知字段和用户修改保留；文件保护未整体关闭。

## 原始设计与当前接入

| 原始设计 | 当前实现 | 状态与边界 |
|---|---|---|
| 左侧模型层级／UV／模型资源／动画列表，右侧检视／历史，中央场景，底部时间轴与共享资源 | PhotonModelWorkspace、PhotonModelAuthoringPanel、PhotonModelProject | 接入原生View／Tab与当前模型工程；项目打开时安装，关闭时恢复原生面板 |
| 原生层级树、选中检视与向量字段 | PhotonModelWorkspaceFields | 部件／骨骼／形变层级、材质、变换及当前资产属性；旋转以角度输入后写回当前四元数 |
| 原模型动画场景标签共享主场景 | PhotonModelWorkspaceScene、PhotonSceneAutoReleaseAccessor | 借用原生场景、相机及世界；借用期间控制释放，归还与关闭恢复所有权 |
| 原生时间轴外观与传输栏 | PhotonModelWorkspaceTimeline | 复用FXTimelineView和原生时钟，显示当前片段部件／骨骼／形变／透明度关键帧；关键帧拖动一次事务 |
| 原UV贴图、网格和选区 | PhotonModelWorkspaceUV | 原比例、缩放、中键平移、框选、追加选择、全选、精度吸附、面填充；拖动本地预览，结束一次提交，取消或过期回滚 |
| 原命名列表行、拖动柄、加减、选中样式和右键菜单 | PhotonNamedOutline、PhotonNamedLibraryPanel | 复用ArrayConfiguratorGroup及TimelineContext原生样式；新建／复制／排序／改名／引用保护删除接当前草稿事务 |
| 原命名属性四页 | PhotonNamedInspectorPages、PhotonNamedEntryFields | 动画属性／绑定／参数／规则原生TabView；类型控件保留NBT类型和未知字段，颜色控件、标记和高级入口保留 |
| 命名轨道与场景停靠工作区 | PhotonEditorDockedPanel、PhotonNamedTrackWorkspace | 左右／中央／底部停靠，原生TrackEditor及独立TimelineContext；主场景、命名动画、工程测试身份隔离 |
| 原生历史与项目生命周期 | PhotonModelWorkspaceSession、FXEditorMixin | 当前projectId／revision门禁，外部恢复后重新解析；关闭只清理所属面板、事件监听和预览贡献 |

基础模型操作包含平面／方块创建、部件复制／重命名／叶节点删除、内外翻转及材质分配。复制几何独立，重命名同步部件引用，未知原树字段保留。旧版所有高级网格操作（如完整挤出、环切工具链）没有在本轮全部移植；本轮不能描述为旧编辑器全部制作功能恢复。

## 生命周期与事务

- 停靠面板按工作区归属成组管理，重复打开聚焦已有组；未使用的新会话及时丢弃。
- 关闭、切工程及初始化失败均清理本组场景借用、附属面板和事件监听，不停止其他工作区拥有的测试实例。
- 模型修改复用现有编辑API；手势记录起始revision，外部变化后拒绝旧提交。检视区保留本地输入焦点，撤销／重做或外部恢复才重建绑定。
- UV选择与层级共享；拖动中取消、目标消失、revision变化或外部选区改变均取消预览。贴图直接引用既有材质，不创建需要额外释放的动态GPU贴图。
- 命名库仍按当前草稿／应用流程提交，UUID及引用保护沿用现有实现，工程测试轨道不进入发布FX。

## 实际检查

1. 全部代码修改完成后统一通过IDEA处理新增文件和修改行格式，未优化导入或重排无关成员。
2. 19个本次代码／Mixin文件IDEA诊断全部完成、无截断，0错误、0警告；回执为 `backups/editor-design-restore-20261022/static-diagnostics.json`。
3. IDEA构建 `9a6d5ecc-16b3-4260-8002-4bf67315a0a9` 成功，未中止，耗时132728 ms。工具错误／警告计数为null，不能解释为编译零警告。编译包含主代码、test源码和刚接入的bench源码，没有执行JUnit或基准场景。
4. IDEA原有Data配置运行日志 `ij_run__Data_3226310622948242045.log` 为 `BUILD SUCCESSFUL in 28s`；23项生成输出，written=0、removed=0。生成目录含缓存共29文件，运行前后SHA256完全一致；未手改生成文件。
5. Data已经结束，运行参数含dataRunProgramArgs的Java进程查询为空；启动调用未提供sessionId，短生命周期内未捕获PID，不能补造PID／创建时间。未启动本轮游戏、浏览器、SDK桥接或额外常驻服务。
6. 已执行 `git diff --check`，无空白错误。5份相关文档检查完成；新恢复记录与进度文件无问题，原编辑器／系统索引／修改历史共保留61项历史失效链接警告，不扩大范围清理。最终提交前再检查仅本次文件进入暂存。

构建回执、生成目录前后清单和Data日志片段保存于备份目录。Data主日志读取受到IDEA终端输出上限截断，`data.log`为前段、`data-tail.log`保留末段成功和缓存统计；完整原始日志仍在IDEA返回的临时日志路径，不将片段称为完整日志。

本轮未进行游戏GUI、鼠标交互、磁盘往返或视觉验收。静态诊断与编译不构成“与原版视觉完全一样”的证明，也不替代G1全量资源验收。2026-10-21停止时的问题台账保留，本轮未重跑其矩阵。

## 人工审查清单

- 对照04a3bab核对左右／中央／底部面板分区、原生标签、行高、字体、间距、配色与缩放。
- 检查面板停靠、切换、关闭、重复打开、工程切换及布局恢复。
- 命名动画四页字段、列表拖动／菜单、轨道编辑、应用／取消／撤销／重做与工程测试隔离。
- 模型层级／材质／关键帧选中、UV框选／平移／缩放／精度吸附／拖动取消和场景切换。
- 记录需要补齐的旧高级网格操作与视觉差异，分开记录功能问题和外观问题。

## 备份与保护

修改前HEAD为 `2403dea92356a6f34028964ccf25ca3655a1ec25`，保存在 `refs/backups/before-editor-design-restore-20261022/head`；WIP快照为 `089c1da8dc0c1c9425edb00a73de2d9762879fea`，保存在同前缀的 `wip`。未重置工作树或索引。并行BenchMod接入随后单独提交为 `0d1b2f11307547b2820f8daeb1a71bb0e4ff53a7`，本次不重复提交其内容。

`.codex/config.toml`和Minecraft MCP `consts.ts`既有修改不纳入本次提交。原assets、模型资源、实体动画和实体模型LOD路径不修改，不自动推送。

关联：[编辑器入口](photon-editor.md)、[重设计实施进度](photon-redesign-progress.md)、[G1停止记录](photon-resource-acceptance-20261021.md#已按用户要求停止2026-10-21)。
