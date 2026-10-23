# Photon 高级网格、光束与 G1 未完成工作记录

记录日期：2026-10-23。本文保存写到一半的实现和恢复入口，不代表旧版功能已全部对齐、卡顿已消除或 G1 已验收。

## 任务与边界

- 对照旧备份 `04a3bab171bc7a843e506971dd0f130b18562593`，补齐高级网格操作和剩余编辑器功能；先备份，全部代码完成后统一检查。
- 改善憎恶女皇激光前几次释放卡顿、跟随轻微顿挫及激光／星束结束后的过渡；继续推进 G1。
- 用户选择 A 后允许调整技能过渡的代码状态。保留原始资源，不修改实体动画资源和模型 LOD，不推送。
- 禁止操控电脑；项目编辑仅使用 IDEA MCP/API，视觉验收交人工。
- 本文只记录当前业务 WIP。代码仍保留原有暂存、未暂存状态，不作为本次文档提交的内容。

## 工作区与备份

当前真实路径：`E:/xm/mc/mod/Unitego-Well/Lobecorp/.workspace/lobecorp_photon`；兼容 junction：`C:/Users/wang_/.codex/worktrees/7903/Lobecorp`。IDEA 工具仍通过兼容路径路由，不得删除该链接或清理此工作区。

分支：`codex/photon-performance`。记录时 HEAD：`ca4fd9203bfd18c491044f9abc369923f06fc960`。

| 备份引用 | 对象 | 用途 |
| --- | --- | --- |
| refs/backups/before-editor-mesh-g1-20261022/head | bdc23609e5ee82a86beb57178077deed098cd6ed | 本轮开发前 HEAD |
| refs/backups/before-editor-mesh-g1-20261022/wip | 95b26dbf0cb967b341f35c3e4e4969e29b25d570 | 开发前既有 WIP |
| refs/backups/editor-mesh-g1-resume-20261023/head | ca4fd9203bfd18c491044f9abc369923f06fc960 | 迁移后续做 HEAD |
| refs/backups/editor-mesh-g1-resume-20261023/wip | 170f9964f164fb140777d5e8b409839a0c5d2706 | 迁移后当前业务 WIP |

备份已核对存在；恢复时先读取引用并比较当前差异，不能直接 reset、覆盖用户文件或消耗既有暂存。迁移备份见 [工作区布局](workspace-layout.md)。Minecraft MCP 已外部制品化，只消费 `lib/minecraft-mcp-0.3.0.jar` 和桥接制品，端口 19877；不得恢复 modules 源码子项目或在此安装 npm／重建 MCP，见 [制品规范](minecraft-mcp-artifacts.md)。

## 已写入但仍待完整验收的实现

### 高级网格与编辑器

- 新增 `PhotonModelEditMesh`，将当前 JSON 模型转换为私有编辑候选；保留原始字段、面来源、权重和 morph 元数据，删除／重排后保留未改动顶点的原始精度。
- 以旧备份算法适配当前格式：`PhotonModelEditUtil`、`PhotonModelHierarchyUtil`、`PhotonModelLoopCutUtil`、`PhotonModelPrimitiveUtil`、`PhotonModelTopologyUtil`、`PhotonModelPolygonUtil`。覆盖挤出、复制、删除、变换、UV 投影、合并、塌陷、溶解、建面、三角化、环切，以及柱、锥、环、球、弧和曲面片。未恢复历史文件格式。
- `PhotonModelWorkspaceMesh` 接入部件／顶点／边／面选择、追加／全选／框选、投影与线框预览、原生 gizmo、枢轴补偿、候选一次提交、环切悬停和参数化几何操作。新增投影访问 Mixin。
- `PhotonModelWorkspaceResources` 接入材质库、PNG 引用导入、原生材质／shader 配置副本和显式应用，保留未知字段；清理自身持有的对话框与监听。
- Workspace、Fields、Session、Timeline、UV 与 EditUtil 已接入默认字段延迟写入、材质使用检查、部件子树删除／无损提升、骨骼／morph 与权重编辑、片段复制／改名／删除／排序、关键帧写入和 UV 数值变换。稳定 ID 保持选择，时间轴修订使旧拖拽失效。
- `PhotonModelPoseSampler` 恢复骨骼层级、逆绑定、归一化线性蒙皮和法线变换，按 morph → 尺寸变形 → 蒙皮 → 部件变换采样；拒绝奇异绑定，保留无蒙皮快路径。Bindings／Output 增加骨骼动态 TRS。

这些代码尚不能据此声明所有编辑器行为与 04a3bab 完全一致；完整功能清单对照和人工视觉审查仍未完成。

### 激光跟随、首次开销与技能过渡

- `QueenBeamEffects` 改用每帧回调更新插值后的眼睛／法杖起点、朝向和一次射线裁剪；蓄力阶段不做射线，恢复阶段冻结端点，阶段生命周期仍按 tick 管理。
- `PhotonNamedRuntime` 暂存输入，`PhotonNamedMixer` 跳过参数驱动绑定中最终被丢弃的曲线采样／应用／恢复，减少实际工作量。
- `PhotonChildSpawner` 在蓄力时最多准备一个独占、尚未 emit 的后续 FX，检查定义身份、来源和深度后复用；关闭时销毁。运行时可先准备而不播放粒子／声音／时间轴。
- `PhotonSpawnInstance` 在 emit 后安装跟随回调，使预备与普通实例顺序一致；帧采样前更新变换，避免播放速率重复相乘。
- `TheQueenOfHatredBeamAnimation` 通过现有同步快照写入阶段开始 game tick；`LcAnimationBoneAdapter` 保持基础控制器推进，以平滑权重混合蓄力／恢复及插值俯仰。过渡计时使用阶段开始 tick，避免动作片段末端钳制后停止推进。快照查询避免每帧分配全部快照列表。
- 未改变伤害、持续时间、冷却；未修改实体动画文件和模型 LOD。

尚无游戏帧时间、P99、冷／热启动对比或人工观感证据，不能宣称卡顿、跟随或过渡已修复。

### G1 编写态／预览态隔离

新增 `PhotonAuthoringPose`、`PhotonAuthoringSnapshotUtil`，并修改属性目标、NamedMixer／Runtime、Timeline 状态访问、TimelinePlayerMixin、FXProjectLosslessMixin 和自动编辑入口。

预览贡献记录写入前／应用后分量，只在序列化副本中替换仍由预览持有的分量，保留用户外部修改；编辑提交复查项目身份和完整 before 快照。此路径与历史 F11 修订漂移、F12 undo_full_data 问题有关，但尚未重跑原生复现，不能记为解决或 G1 通过。

## 实际执行过的检查

2026-10-22 已执行：分组 IDEA 诊断，40 个代码／配置文件无错误，保留既有警告；IDEA 构建返回成功。Data 配置日志为 `C:/Users/wang_/AppData/Local/JetBrains/IntelliJIdea2026.2/tmp/ij_run__Data_17237209521181167582.log`，显示 BUILD SUCCESSFUL，23 个输出、写入 0、删除 0，未产生生成资源差异。

2026-10-23 只读取当前已有测试 XML 报告，结果与前次一致：

| 检查 | 实际结果 | 限制 |
| --- | --- | --- |
| PhotonModelMeshEditingTest | 10 / 10 通过 | 算法测试，不是原生编辑器验收 |
| PhotonModelPoseSamplerTest | 9 通过、1 失败、0 错误 | samplesExistingModelOccurrencesWithoutChangingOriginals 找不到所需原始 fxpacks 目录，保留断言 |
| PhotonResourceReaderTest | 27 / 27 通过 | 包括不安全依赖引用等负例 |
| PhotonModelStagedResourcesTest | 1 / 1 通过 | 496 项 SHA 核对，5 项历史状态源不可用，234 个模型 occurrence 按 0／5／100 CPU 采样 |

暂存资源采样是独立补充，不能替代失败的原始资源测试、原生编辑器保存重开或独立包验收。没有复制原资源回 source，也未削弱缺失资源断言。

此前 `git diff --check HEAD` 无空白错误，受保护资源／fxpack／实体动画路径差异为空。没有启动游戏，没有执行视觉或性能验收。

迁移协调期间的整项目构建中编译与 assemble 已完成，但整体 test 失败，另涉及资源基线 fixture 和 reserveBuildIdentity 配置缓存问题。该结果不能记作整项目构建成功，也不扩大本任务去修复无关问题。

## 格式与未解决项

- 已通过 IDEA 处理新增代码和修改范围格式，未做全项目整理、导入优化或成员重排。
- 项目要求 CRLF。IDEA 原生补丁使部分修改源码落为 LF；尝试 Document API 仅恢复换行时返回 Wrong line separators，磁盘仍为 LF。尚未恢复，不得声称格式全部完成；没有借助命令行改文件或 UI 绕过。
- 一次整份新网格文件格式请求被自动审查拒绝，随后采用明确小行范围获准执行；不代表存在新的整文件格式授权。
- 高级网格／剩余编辑器与旧备份的完整对齐、真实编辑器交互和视觉一致性仍待人工审查。
- 激光前几次开销、连续跟随、星束／激光退出过渡仍待真实游戏测量和人工判断。
- G1 仍需 F11／F12 原生复现、保存重开、撤销／重做、独立包完整 roundtrip，以及 5 项源不可用状态的处理。
- 原始资源 fixture 缺失导致的 1 项测试失败保留；不以新测试通过覆盖该失败。
- 本次额外文档不继续业务实现，不启动游戏或主动重跑构建、测试、Data。

## 后续恢复顺序

1. 核对真实工作区、分支、HEAD、上述备份与当前暂存／未暂存差异；保留 .codex/config.toml 和其他已有修改。
2. 阅读本记录以及 [编辑器入口](photon-editor.md)、[设计恢复记录](photon-editor-design-restore-20261022.md)、[资源验收记录](photon-resource-acceptance-20261021.md)；只读相关范围。
3. 在 IDEA 中继续未完成实现和修改行格式，先解决 CRLF 工具问题，不覆盖用户缓冲区或原始资源。
4. 全部授权代码完成后统一读 IDEA 诊断，再按已获授权范围执行验证；准确区分代码检查、构建、测试与视觉验收。
5. 人工进行视觉与性能验收；在有证据之前保留以上未解决标记。整理本任务内容后再提交业务修改，不推送。
