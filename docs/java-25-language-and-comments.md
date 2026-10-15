# Java 25 语法与注释规范

本文件是 Lobecorp Java 开发的专项规范，与根 [AGENTS.md](../AGENTS.md) 一起执行。核对日期：2026-10-09。下文“必须”“默认”“不采用”为项目选择；功能版本和语法限制以链接的 Java 25 官方文档为准。

## 基准与适用范围

- 项目以 Java 25 为语言、工具链和字节码基准。当前 `build.gradle.kts` 的 toolchain 为 25，IDEA 的语言级别为 `JDK_25`、字节码目标为 25，Mixin compatibilityLevel 为 `JAVA_25`；不要套用 Java 17／21 的语法能力上限。
- 新编写代码及本次明确修改的代码，必须按下文适用条件使用 Java 25 支持的现代写法。无需为了使用新语法改变设计、引入新抽象或压缩可读性。
- 当前项目未启用预览；默认只使用 Java 25 已正式发布的语言功能和 API。不能通过擅加 `--enable-preview`、孵化模块或修改工具链绕过此边界。
- 已有代码只在当前授权范围内调整。不批量转换历史注释、改修饰符、迁移包或重构数据结构；生成输出和第三方代码保持原样。

Java 25 正式包含模块导入、紧凑源文件／实例 main 与灵活构造器；基本类型模式增强仍是预览。旧版本中已正式发布的现代语法同样属于本项目可用写法。版本依据见 [Java 25 语言功能表](https://docs.oracle.com/en/java/javase/25/language/java-language-changes-summary.html)。

## 注释形式与内容

常规注册声明和从代码结构、类型、调用、参数即可直接看明白的内容，不需要注释；仅复述“登记项”“编解码器”或翻译登记文本的说明应删除。变量名清楚本身不算“非常明了”，不能据此删除业务含义、单位、取值依据、边界、所有权、生命周期、特殊注册时机或具体兼容约束。常量注释也遵守此必要性判断，不机械补充模板说明。

1. 新增或本次修改的类、接口、构造器、方法和字段的 Javadoc 文档注释，必须使用 `///` Markdown 形式。该形式从 JDK 23 正式提供，Java 25 无需预览开关；传统 `/** ... */` 仍合法，未被 Java 废弃，保留未涉及的历史文档及第三方声明。
2. 方法内部的实现说明、维护原因和必要行内说明使用 `//`；确需多行的非文档说明可用 `/* ... */`。不把普通实现说明改成 Javadoc。
3. 说明文字必须为中文；标识符、类型、API 名称、Javadoc 标签、URL、协议、代码片段及 IDE 控制指令保持原文。第三方版权与许可证声明不改写。
4. 文档紧邻声明，放在注解和修饰符之前。首句说明职责或契约，随后只补充必要的单位、边界、所有权、副作用和生命周期；不重复显而易见的实现，不写开发过程流水账。
5. 每行按相同缩进写 `/// 内容`，段落空行写 `///`。同一文档块必须连续；完全空白行会拆开文档，使前一块不再属于该声明。
6. 已决定编写完整 API 文档时，参数／类型参数、非 void 返回值和契约中的异常使用 `@param`、`@return`、`@throws`，置于正文之后；类型参数为 `@param <T>`。覆盖方法可用 `{@inheritDoc}` 继承真实存在的契约，不机械添加空标签。
7. 标识符或短代码使用反引号；程序元素链接使用 `[List]`、`[String#chars()]` 或 `[列表][List]`，类型应正常 import，重载引用写清参数。数组参数链接中的方括号需要转义，如 `[String#copyValueOf(char\[\])]`。外部资料使用普通 Markdown 链接。
8. 多行代码示例使用围栏代码块，每行仍带 `///`；代码块内的注解和 Javadoc 标签是字面示例。需要区域、强调或外部示例时才用 `{@snippet}`，不新增只为文档服务的复杂流程。
9. 已明确授权弃用 API 时，Markdown 的 `@deprecated` 必须配合实际 `@Deprecated` 注解，并说明替代入口；不能只写标签，也不能因注释转换擅自给现有 API 加弃用注解。

上述语法、连续性与链接依据见 [Java 25 Markdown 文档注释指南](https://docs.oracle.com/en/java/javase/25/javadoc/using-markdown-documentation-comments.html)；声明位置和标签依据见 [Java 25 Javadoc 规范](https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html)。

以下为类中成员的写法示例，所用类型正常导入；示例不表示需要新增该业务接口：

~~~java
import java.util.Objects;

/// 校验并返回非空输入。
///
/// 非空检查使用 [Objects#requireNonNull(Object)]，不复制输入。
///
/// @param <T> 输入对象类型
/// @param value 待检查对象
/// @return 原输入对象
/// @throws NullPointerException 输入为 `null`
public static <T> T requireValue(T value) {
    return Objects.requireNonNull(value);
}
~~~

## 常用现代语法的选择

`if`、`else`、普通及增强 `for`、`while`、`do` 的语句体必须使用大括号，单条语句也不例外；`else if` 保持连续分支写法，各条件分支的语句体仍使用大括号。项目 `.editorconfig` 的 Java 强制大括号选项与此规则一致。

以下条件成立且语义等价时，新写或本次修改范围内的代码必须采用对应写法：

| 场景                            | 项目写法与边界                                                                                                                |
|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| 类型判断后强制转换              | 使用 `instanceof Type variable`；保留 null 与后续控制流语义，不重复强转。                                                     |
| 多个分支计算同一结果            | 使用 `switch` 表达式及 `case ... ->`；多语句返回分支使用 `yield`。需要 fall-through 的旧逻辑不能直接换成箭头分支。            |
| 按类型分派                      | 使用类型模式 switch，必要时用 `when` 守卫；核对 null、穷尽性、分支顺序、遮蔽和异常行为。                                      |
| 提取 record 组件                | 使用 record 模式，嵌套解构按实际需要展开；不能改变访问器的调用次数、副作用或异常语义。                                        |
| 回调参数或解构组件确实未使用    | 使用未命名变量／模式 `_`，只在语言允许的位置使用；不把它当成可读取变量，不借此忽略应处理的异常。                              |
| 局部初始化表达式清楚显示类型    | 使用 `var` 避免重复；表达式隐藏类型、需要指定接口／泛型或目标类型影响重载时写显式类型。字段、方法参数和返回类型保持显式类型。 |
| lambda 可安全等价表达为方法引用 | 使用方法引用；保留重载解析、泛型推断、接收者求值、空值异常和构造时机。需要 lambda 时省去冗余块及可推断参数类型。              |
| 需要多行固定文本                | 使用文本块 `"""`；先核对缩进、尾部换行、转义和协议字节。可翻译业务文本仍进入资源，文本块不取消集中管理要求。                  |
| 本方法拥有的可关闭资源          | 使用 try-with-resources；已有 final 或 effectively final 资源可用 `try (resource)`。不关闭 Minecraft／Photon 的共享资源。     |

`var` 自 Java 10、lambda 参数 var 自 11、switch 表达式自 14、文本块自 15、record 与 instanceof 类型模式自 16、record 模式／模式 switch 自 21、未命名变量／模式自 22 正式发布。版本见 [官方功能表](https://docs.oracle.com/en/java/javase/25/language/java-language-changes-summary.html)，细则见 [record 模式](https://docs.oracle.com/en/java/javase/25/language/record-patterns.html)、[模式 switch](https://docs.oracle.com/en/java/javase/25/language/pattern-matching-switch.html) 和 [资源关闭规则](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.3)。

模式 switch 的成员示例：

~~~java
private static boolean isBlankText(Object value) {
    return switch (value) {
        case String text when text.isBlank() -> true;
        case null, default -> false;
    };
}
~~~

## 数据类型与 Java 25 新正式功能

- 新建的简单数据载体在不需要可变字段、继承或框架特殊构造时使用 `record`。record 是浅层不可变，组件中的数组、集合或 Vector3f 不会自动不可变；复制与所有权必须另行定义。既有可变配置、`@Persisted` 类、实体及 Mixin 不因风格要求转换。采用 sealed 层次须有明确封闭类型需求，不能为使用模式 switch 额外封闭已有扩展点。依据见 [Java 25 JLS record 规则](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10)。
- Java 25 灵活构造器（JEP 513）允许在 `super(...)`／`this(...)` 前校验参数和计算构造参数。需要这种前置处理时可直接使用，不另建只为绕过旧语法限制的 helper；必须保留异常与初始化顺序。早期构造阶段不得读取当前实例字段、调用当前实例方法或传出 `this`；语言允许先赋值本类声明的字段，但只在确有初始化需求且检查字段初始化器影响后使用。依据见 [灵活构造器](https://docs.oracle.com/en/java/javase/25/language/flexible-constructor-bodies.html)。
- 模块导入（JEP 511）已正式，`import module java.base;` 合法。本项目默认保持明确的类型 import；仅在模块依赖明确且避免同名歧义时采用模块导入，不批量替换现有导入或新增 module-info.java。依据见 [模块导入](https://docs.oracle.com/en/java/javase/25/language/module-import-declarations.html)。
- 紧凑源文件与实例 main（JEP 512）已正式，但模组源集继续使用明确 package 和命名类型，保留 NeoForge、Mixin 及运行配置需要的入口签名。只有明确需要独立小程序且工具支持时采用相应简写，不能在正式模组代码中省略类或包。依据见 [紧凑源文件与实例 main](https://docs.oracle.com/en/java/javase/25/language/compact-source-files-instance-main-methods.html)。

## 预览、API 与兼容边界

- Java 25 的基本类型模式增强（JEP 507）仍为预览，包括独立基本类型模式及 long／float／double／boolean 的 switch 扩展；不要与已经正式的 record 原有基本类型组件解构混淆。依据见 [语言功能表](https://docs.oracle.com/en/java/javase/25/language/java-language-changes-summary.html)。
- StableValue、StructuredTaskScope、PEM API 仍列于 [Java 25 预览 API 清单](https://docs.oracle.com/en/java/javase/25/docs/api/preview-list.html)；孵化 Vector API 也不属于默认正式 API。使用此类功能前需确认实际需求、编译／运行开关、加载器与交付环境；本规范不授权启用。开关规则见 [预览功能](https://docs.oracle.com/en/java/javase/25/language/preview-language-vm-features.html)。
- String Templates 已撤回，Java 25 不支持旧 `STR."..."` 写法；多行文本使用正式文本块，动态文本使用项目已有格式化与翻译 API。版本记录见 [按版本列出的语言变化](https://docs.oracle.com/en/java/javase/25/language/java-language-changes-release.html)。
- 现代集合工厂、Stream 或线程 API 的可用性不等于可直接替换既有逻辑。必须保留集合可变性、null、异常、遍历顺序、资源关闭及线程所有权；不要为风格把清楚的循环改为 Stream，或把游戏逻辑移到新线程。
- 框架要求的构造器、修饰符、方法签名、注解、Mixin 注入目标及注册生命周期优先保持。改写通过 IDEA 核对类型、引用和语义后才执行，不因本规范扩展当前任务。
- IDEA 格式化仅处理授权的代码行或类，关闭额外导入优化与成员重排；验证及生成输出边界继续遵守根 AGENTS.md。Markdown 示例与文档诊断通过不等于示例编译、Javadoc 生成或运行验收已经执行。
