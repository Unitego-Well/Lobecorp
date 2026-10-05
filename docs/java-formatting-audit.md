# 2026-10-05 Java 整理与 Photon 修复记录

## 已实施范围

- 开始时 414 个手写 Java 文件，本轮新增 TextFieldMixin、TextAreaMixin 后共 416 个。
- 全部 416 类通过 IDEA 代码格式化；没有导入优化或成员重排。JSON、自动生成输出、第三方与二进制资源没有自动格式化。
- 规则更新涉及 AGENTS.md、.editorconfig、docs/java-architecture.md 和 .codex/standards/java-class-organization.md。
- 包迁移尚未实施，精确清单见 [包整理审批计划](java-package-organization-plan.md)。

## Photon 功能修改

- PhotonEditorTools、ConfiguratorMixin：扩展控件通过原生 ADDED/REMOVED 事件登记、注销，保留构造时一次扫描；tick
  只遍历弱键控件登记表，显示状态改变时才调用 setDisplay。
- 已通过 LDLib2 .39 源码核对嵌套加入、移除及跨编辑器移动的事件时序。功能开关仍每 tick 生效，不降低动画或交互刷新频率。
- TextFieldMixin、TextAreaMixin：在 Photon 系统剪切板开关启用时读取 Minecraft 当前系统剪切板；保留原生文本过滤、选择和撤销流程。
- PhotonObjectClipboardUtil、PhotonResourceClipboardUtil：仅接管匹配类型的 Photon 数据，文本输入目标包含 TextArea。
- PhotonEditorTools 的快捷键同样跳过 TextArea，避免编辑文本时触发工具操作。
- lobecorp.mixins.json 仅注册新增两个客户端 Mixin，保留原排版。
- 噪声预览每帧 NBT 比较仍是后续优化候选，本轮未实施；游戏内帧率、延迟和实际粘贴交互尚未测试。

## IDEA 检查和收尾

| 分组                                 | 类数 | 改前、改后 Java 错误 | 既有警告 |
|--------------------------------------|-----:|---------------------:|---------:|
| 根入口、注册、生成器、网络等         |   86 |                    0 |      134 |
| 核心客户端、指挥家、事件及通用工具   |  122 |                    0 |      234 |
| 实体、技能、动画、命中框及相关 Mixin |  124 |                    0 |      304 |
| Photon（包含新增两类）               |   84 |                    0 |       26 |

未发现新增相关 Java 错误或警告；既有提示保留。通过 IDEA 编译成功；Data 运行配置退出码为 0。IDEA/Git 检查生成资源无新增差异，生成的
en_us.json、zh_cn.json 诊断为 0。

保留工作区已有暂存、未暂存和未跟踪内容；未提交、重置或执行包移动，没有创建额外备份。新增 Mixin 和本轮文档由 IDEA 添加到了索引，后续编辑仍可能保留未暂存差异。

## 尚未实施的方法整理

已确认以下方法在 IDEA 格式化后超过 100 行，尚未拆分：

- network/ts/ConductorCommandPayload.java：apply，160–396，237 行。
- client/conductor/ConductorHud.java：activate，1707–1820，114 行。
- client/conductor/ConductorScreen.java：init，61–185，125 行。
- conductor/control/ConductorController.java：tickControlled，57–207，151 行。
- util/PhotonEditorTextUtil.java：register，16–125，110 行（顺序翻译注册，可按职责分组提取）。

Photon 组对 16 个最长候选文件完成 IDEA 声明检查；未进行全部 84 文件的结构检查。PhotonSubClipboardUtil.paste 为 99 行，PhotonEditorTools.batchPanel 为 73 行。

实体、技能、动画、命中框组的 35 个超过 100 行源文件已通过 IDEA 结构检查，方法和构造均无超过 100
行项。各组逻辑块空行、提前返回和复杂方法拆分尚未进行全项目改写，不能将此次格式化视为这些规则已经全面落实。
