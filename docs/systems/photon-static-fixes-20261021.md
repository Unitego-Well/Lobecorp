# Photon 静态审查修复与完整轨道接线

日期：2026-10-21。独立工作区：`7903/Lobecorp`；分支：`codex/photon-performance`；基线：`2559a01`。

## 授权与备份

仅修复本轮代码审查问题并补齐轨道兼容。先完成代码和修改行格式，再统一静态检查；本轮不构建、不运行Data、测试或游戏。视觉和界面交互由人工验收。原资源、实体动画和实体模型LOD不修改。

- 修改前基线：`refs/backups/before-photon-fixes-isolated-20261021`。
- 迁移前HEAD、索引和含未跟踪文件的快照分别保留在`refs/backups/photon-migration-head-20261021`、`refs/backups/photon-migration-index-20261021`、`refs/backups/photon-migration-files-20261021`；索引提交`85bf39e`、文件快照`2cf2b48`。
- 基线恢复仅针对逐文件核对的迁移缺失和旧版文件，不恢复原混合任务快照。38个旧源码遗留文件由协调任务另行归档。本轮不自动合并或推送。

## 已编写代码

1. 轨道上下文：撤销/重做和编辑入口检查上下文，片段/轨道操作检查当前草稿归属；历史恢复重新解析对象并清理拖动引用。scrub期间合并历史，完成只入栈一次，取消恢复原树；关闭在finally清理本窗口状态和检视归属。
2. 轨道类型：接通原生activator/control/animation/group/speed/signal/audio/post_process，以及模型动画、子动画和工程测试调用。命名计划采用原生TrackEditor和轨道读取器，先严格检查未知类型和属性，再解析；调用轨道只能位于工程测试容器，发布库继续拒绝。
3. 命名辅助轨道：音频实例由每个句柄独占并在暂停/停止/释放时清理；后期沿用宿主逐帧sink；速度按优先级和播放顺序覆盖，退出只恢复仍属于本混合器的贡献；信号沿用命名周期窗口、事件预算和原生监听入口。实体动画系统没有参与。
4. 文件往返：保存按原生工程格式写出完整meta/data/容器未知字段，先做原生完整容器解码对比，后台磁盘读回再比较完整容器；不再只比较data摘要。定义导出比较完整NBT，包保留其他条目，缺依赖/冲突拒绝发布。
5. MCP文件任务：客户端线程捕获独占数据和原生依赖字节，单后台线程处理文件编码、压缩、ZIP写入、OBJ转换和有界读回，队列上限8。后台不访问活编辑器、世界、GPU或注册表。提交回客户端线程复核工程身份、内容revision、预览runtime、目标路径和包锁；取消/过期结果只清理临时文件，不发布。
6. 声明来源审查：许可路径与参数在客户端捕获，后台完成读取、哈希和递归依赖解析。只读报告仍为runtimeAccepted=false，不冒充独立包验收。
7. 模型状态：普通FX内嵌模型同样省略重复SNBT和资产字节schema，每对象单独返回modelAsset；snapshotIncluded=false必须由调用方检查。revision采用保留NBT类型与键序稳定性的流式哈希，避免把大字节数组展开成SNBT，并复用本次状态快照。
8. 回归源码：新增轨道格式/工程调用隔离/模型与子片段错误输入和工程指纹用例；尚未运行。

## API与线程边界

现有MCP工具名和参数保持。effect_save、effect_export、effect_resource_audit采用后台准备，返回仍等待实际提交结果；客户端线程直接调用这三项会被拒绝，避免阻塞客户端等待自己的回调。超时发生在提交前时拒绝发布；进入提交后仍返回实际提交结果。

原生依赖捕获、工程序列化、原生对象解码与提交仍在客户端线程，未将整个FXRuntime/UI迁移到后台。大工程捕获阶段仍可能耗时；当前修改不等于可取消seek、模型求值工作池、独立GUI呈现或性能优化全部完成，也没有P95/P99收益证据。原生编辑器菜单的保存流程未统一替换为本MCP后台任务。

## 检查与验收状态

本轮代码与修改行格式已完成。IDEA统一批分析覆盖20个Java文件及Mixin JSON，均为analyzed且输出未截断：首轮0错误/13警告；修正本次空值边界、冗余赋值和批量添加提示后为0错误/8警告；文件往返比较改为Objects.equals并完成该文件复查，无问题，合计保留6项存量提示。保留项是命名Prepared资源所有权提示、未使用listener/rejectEffect接口，以及ProjectUtil既有的编辑器空值/恒真条件/静态Mixin桥接提示，未扩大清理。批分析不含全部编辑器弱提示，不能替代编译。

四份文档均完成IDEA分析；changes.md历史段落存在旧文档/截图断链，30项输出上限发生截断，未作为全文无警告；本次新增段落另按行范围检查。其余三份文档无诊断问题，历史断链不在本轮清理范围。

Git差异确认assets/resources/src/generated、实体动画、GeckoLib/模型LOD及VisualQualityConfig没有本轮变化；git diff --check无输出。新增6项JUnit用例仅完成源码和静态分析，未执行。未执行构建、Data、自动测试、游戏或视觉验收；G1未通过。文件保护仍按字段和类型保留，未知版本、错误格式、外部模型来源和未验收旧生效渲染字段不整体放行。

后续运行验收必须覆盖完整轨道的添加/复制/历史/磁盘往返，音频暂停与周期边界，信号重复和跳转，速度多实例叠加与恢复，后期关闭清理，文件取消/过期竞争与未知容器字段，以及真实包独立加载。视觉单独交人工。

## 用户授权构建后的集中检查

用户随后明确允许在独立工作区构建和运行游戏；此授权覆盖本节，前述“不构建”记录保留为当时边界。Client Photon/Data Photon/Server Photon工作目录、modFolders和moddev参数均指向7903；客户端MCP端口19877，服务端25572。已检查参数文件中没有旧主目录或90be的build/run引用，但未启动客户端，不能宣称游戏监听或桥接连接已通过。

- 首次IDEA构建54ffdee6-c219-4664-8364-0ae51be7cda7失败：26错误。2项由本轮prepareExport返回Job后漏接PhotonModelAuthoringPanel调用造成，另24项为QueenBeamEffects依赖的实体动画契约缺失。此前静态批分析仅覆盖修改文件，未覆盖这个未修改的GUI调用方，因此无错误结果不能等同于完整编译成功。
- 修正GUI导出为exportFromEditor异步入口，共用有界文件准备/主线程提交，不阻塞GUI，也不要求人工GUI操作进入远程MCP接管模式。IDEA引用检查确认prepareExport的两个调用方、prepareSave的单一调用方；两份修改文件静态分析无错误，保留命名Prepared既有所有权提示。新增代码及修改行完成格式处理。
- 第二次IDEA构建5fdf17b5-9bf6-4810-a995-5f696ecd15d1失败：24错误，全部落在QueenBeamEffects。缺失TheQueenOfHatredAnimationDefinitions、TheQueenOfHatredBeamAnimation（含Phase与阶段参数）和LcAnimationDefinition.effectId()；原af77096备份有这些动画任务增量，2559a01基线没有。没有恢复混合快照、复制/改写实体动画，亦未删除特效调用或改成空实现绕过。
- 首轮工具回执摘要保留为backups/photon-build-followup-20261021/compile-round1-summary.json（原始构建缓存已失效，不能补取完整原始日志），第二轮完整日志为同目录compile-round2.log；跟进修改前备份refs/backups/before-photon-build-followup-20261021指向ed3857e。
- 当前不能启动含新代码的游戏或运行依赖主源码编译的测试；没有用旧产物冒充验收，没有执行Data。需要实体动画任务提供上述契约及必要关联改动的可集成版本后再继续，原资源与实体模型LOD保持不动。G1仍未通过。
