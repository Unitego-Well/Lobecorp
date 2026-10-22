# Minecraft MCP development

- 本目录是独立 Git 仓库，只维护通用 Minecraft MCP、NeoForge 26.1.2 模组和 bridge。消费方业务实现及运行环境留在消费仓库。
- 通过 IDEA 编辑项目文件，保留已有修改和排版；格式仅处理本次修改行，关闭导入优化与成员重排。
- 默认只读 IDEA 诊断，不主动构建、安装依赖、运行测试、同步 Gradle 或启动游戏；运行说明不构成执行授权。
- MCP 请求不得移动操作系统光标或调用 AWT Robot。内部模拟输入使用 ControlModeHelper.runSyntheticInput；嵌套与异常路径必须恢复作用域。真人鼠标捕获不以 MCP 控制模式全局禁用。
- 不共享消费工作区的 build、node_modules、dist、run、存档或端口，不修改其全局配置。
- 变更提交包含源码与维护文档；保留来源、版权和许可证。运行验证、静态诊断与历史证据分别报告。
