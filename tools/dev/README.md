# 通用开发脚本入口

```powershell
python tools/dev/dev.py doctor
python tools/dev/dev.py search LcDebugUtil --limit 5
.\tools\dev\dev.ps1 members org.unitego.lobecorp.util.debug.LcDebugUtil
```

完整命令、参数、批处理与限制只维护在[使用手册](../../docs/tooling/dev-scripts.md)。普通读写使用CLI，不依赖IDEA；符号/引用/调用及重构连接IDEA后端，不使用IDEA控制台。文本修改默认预览，apply要求版本校验；重构apply还需绑定IDEA预览。
