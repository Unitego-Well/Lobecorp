# 统一文档管理（DS 与 GPT 共用）

本文件定义代理运行文档的分类、目录、命名、生命周期与提交边界，适用于 DeepSeek-Harness（DS）与 GPT/Codex 在本工作区的所有会话。用户当前明确指令优先；与[项目根规范](../../AGENTS.md)冲突时按更严格者执行。

## 目录与分工

- 工作区根 `.agent/`：跨项目、跨仓库的报告、交接、计划与记忆。
- `Lobecorp/.agent/`、`minecraft-mcp/.agent/`：仅本仓库内容。
- `Lobecorp/.workspace/lobecorp_photon/.agent/`、`Lobecorp/.workspace/lobecorp_horde/.agent/`：仅对应工作副本内容。
- 现有五个文档中心：工作区根 `.agent/`、`Lobecorp/.agent/`、`Lobecorp/.workspace/lobecorp_photon/.agent/`、`Lobecorp/.workspace/lobecorp_horde/.agent/`、`minecraft-mcp/.agent/`。
- 每个 `.agent/` 固定包含 `reports/`、`handoffs/`、`plans/`、`memory/`、`archive/` 与 `README.md` 索引。
- `.agent/` 以点开头，整体不提交；各仓库根 `.gitignore` 忽略 `/.agent/`。

## 四类文档

| 类型 | 用途 | 必填字段 | 生命周期 |
|---|---|---|---|
| 报告 `reports/` | 任务结果、验证报告、性能与对比、事故复盘 | 日期、任务、范围、结果、证据、未验证或受阻 | 任务收尾后保留；跨月移入 `archive/YYYYMM/` |
| 交接 `handoffs/` | 给下一个会话或代理接续 | 来源会话、目标会话、当前状态、已完成、下一步、关键路径、未决问题 | 接续完成后标记；30 天后归档 |
| 计划 `plans/` | 任务计划、实施方案、待办清单 | 目标、范围、步骤、依赖、验收、状态 | 完成或取消后标记；30 天后归档 |
| 记忆 `memory/` | 长期事实、用户偏好、已确认决策、环境信息 | 主题、结论、来源、日期 | 长期保留；定期去重，过时内容标注或删除 |

## 命名

- 文件名使用 `YYYYMMDD-<主题>.md`；主题用小写英文与连字符，内容用中文。
- 同一主题多份材料放子目录：`<主题>/YYYYMMDD-<说明>.md`。
- 交接文件固定放 `handoffs/`，文件名包含主题与日期。

## 索引与维护

- 每个 `.agent/README.md` 维护索引表：日期、类型、标题、状态、路径。
- 新增、迁移、归档时同步索引；不把 `.agent/` 内容复制到项目 `docs/`。
- 计划完成、报告归档、交接接续后更新状态与位置。
- 历史正文只追加说明：已发出的交接与报告不改写原文，需要更新时在文末追加带日期的说明区块；确属事实记错（路径、结论）可原地订正并注明依据。
- 归档统一放 `archive/YYYYMM/`，不删除仍有证据价值的内容。

## 提交边界

- `.agent/` 整体不提交；`docs/` 只保留需要跨会话共享的项目规则、契约、修改记录与问题清单。
- 已提交的代理记忆或 FAQ 迁入 `.agent/memory/` 并取消 Git 跟踪，旧路径删除。
- 备份、二进制产物和运行输出不进 `.agent/`，也不进 `docs/`：统一放 `<仓库>/build/agent-backups/<YYYYMMDD-主题>/` 下的 `backups|evidence|fingerprints|tools` 子目录，迁移时先出 `migration-manifest-<批次>.tsv`（from／to／sha256／bytes）并逐文件复算比对，清单随批次留档。
- 工作区根与各仓库根 `.agent/` 均不提交；新仓库初始化时同步 `.gitignore` 规则。

## 现有迁移（2026-10-27）

- 工作区根 `agent-backups/photon-r3-20261027/PROGRESS-20261027.md` 迁入 `.agent/handoffs/20261027-photon-r3-progress.md`，旧副本删除。
- `Lobecorp/.cherry-assistant/faq.md` 迁入 `Lobecorp/.agent/memory/faq.md`，取消 Git 跟踪，旧副本删除。
- 备份与基准产物保留原位，不迁入 `.agent/`。

## 与项目文档的关系

- 项目规则、系统契约、修改记录与问题清单仍在 `docs/` 并提交；`.agent/` 不写入 `docs/README.md` 的完整清单，避免链接到未提交内容。
- 会话开工先读所属 `.agent/README.md` 的最新交接；结束前写交接或报告，并更新索引。
