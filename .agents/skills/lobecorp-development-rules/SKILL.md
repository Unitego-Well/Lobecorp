---
name: lobecorp-development-rules
description: Lobecorp的Java、Definition常量注册、registry.translation翻译、Encounter/Horde及Bench规范入口；只在当前任务实际属于该项目时使用。
---

# Lobecorp 开发规则导航

用于 Lobecorp 的 Java 开发、包职责整理、登记、Encounter/Horde、翻译及 Bench/Mixin 边界工作。这里只定位项目规则正文，不维护第二套规则。

由[项目入口](../../../AGENTS.md)进入本技能；已读且未变化的入口不重复读取。按当前任务选择：

- Java、接口、常量、包分类、登记、翻译或 Bench/Mixin：[Java 与包规范](../../../docs/standards/java-and-packages.md)的对应章节；Java 写法另读 [Java 25 专项](../../../docs/java-25-language-and-comments.md)。
- Encounter 玩法、Horde 公共层、玩法常量或业务翻译：[Encounter 结构规范](../../../docs/standards/encounter-structure.md)。
- 所有位置的翻译键统一为registry.translation中的常量注册，类型键类命名和直接归包遵守Java与包规范；Encounter定义使用world.encounter领域类型和原版／NeoForge注册表，具体项以命名常量登记。成员排列和资源表达式归属分别遵守上述Java与Encounter正文。此处仅定位上述规则，不维护另一份文案或登记清单。
- 系统行为与当前实现：[系统索引](../../../docs/systems/README.md)中匹配的契约，再核对实际源码；目标规范不等于迁移完成。
- 工具、运行或过程文档：按项目入口链接读取对应专项正文。

通用授权与执行仅引用[共享全局工作协议](C:/Users/wang_/.agents/AGENTS.md)。本文件只提供导航。
