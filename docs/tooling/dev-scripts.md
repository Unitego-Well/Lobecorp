# 通用开发脚本

版本：1.0.1；更新：2026-11-07。目的：以短命令和有界结果减少重复上下文、工具往返及命令转义错误。尚无配对usage数据，不宣称Token节省比例。

## 启动与依赖

统一入口为tools/dev/dev.ps1，核心为tools/dev/dev.py。要求Python 3.10+；文件枚举/搜索需要PATH内的rg，diff需要Git。Python只使用标准库，无需pip/npm安装。从任意目录显式传入--root；省略时使用脚本所在仓库根。无全局安装和模型配置变更。

```powershell
python tools/dev/dev.py doctor
python tools/dev/dev.py files '*.java' --limit 10
python tools/dev/dev.py search LcDebugUtil --limit 5
python tools/dev/dev.py members org.unitego.lobecorp.util.debug.LcDebugUtil
.\tools\dev\dev.ps1 read docs/systems/debugging.md
# 任意目录指定完整脚本位置和工程根。
python E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/dev/dev.py doctor --root E:/xm/mc/mod/Unitego-Well/Lobecorp
```

外部cmd直接使用同一个Python入口。无需IDEA控制台；只有语义命令需要已打开正确工程的IDEA MCP；普通文件读写使用CLI。默认端点为项目现有http://127.0.0.1:64342/stream，可用--endpoint或DEV_IDEA_ENDPOINT选择本机端点；不修改MCP配置、不自动启动或重启IDEA。不接受远程/带凭据端点。

## 能力表

| 命令 | 参数args | 后端与边界 |
|---|---|---|
| doctor | ide: true可选 | 本地运行时与工具；选择ide才连接，失败返回错误 |
| capabilities | op、schema可选 | 当前工具目录及适配器限制；schema按需展开，不等于调用成功 |
| files | glob | rg文件列表；遵守忽略规则并排除运行/生成/第三方等目录 |
| read | path、start、count、encoding | 磁盘读取及原字节SHA256，默认100行；不含未保存Document |
| search | q、glob、regex、caseSensitive、maxMatches | rg查询，默认字面量、区分大小写；结果为匹配行 |
| package | name、recursive | 按Java包声明返回文件和主类型候选名；嵌套/多顶层类接members |
| symbols | query、kind、scope、maxResults | IDEA符号查询，默认project |
| class / members | class、includeMembers、includeSource | 全限定类定位/成员；默认不返回源码 |
| symbol | filePath、line、column | IDEA指定位置符号，行列从1开始 |
| refs | class、member、parameters、descriptor、maxResults | 类/成员引用；参数类型或描述符消除重载歧义 |
| callers / callees | class、method、parameters、descriptor、depth、maxResults | 调用方/被调方；默认深度1、最多30节点 |
| hierarchy | class、direction、depth、includeInterfaces、maxResults | 类型继承；direction服从当前IDE定义 |
| implementations | class、maxResults | 实现类查询 |
| overrides / super-methods | class、method、parameters、descriptor | 重写/父方法链 |
| method-read | class、method | 按方法名读取IDE源码结果；可能返回多个重载，不是唯一方法体 |
| problems | filePath、errorsOnly、timeout | 单文件IDE诊断；不构建、不启动运行配置 |
| rename | class、new、member、memberKind、variableName、parameters、descriptor | IDEA原生dry-run，支持范围服从后端；apply需匹配expectedPreviewHash |
| move | class、targetPackage | 类移动预览；有完整文件清单时可apply |
| signature | class、method、parameters、newName、newReturnType、newVisibility、parametersJson | 签名修改预览/apply；parametersJson按真实工具约定填写 |
| extract | filePath、startLine、endLine、newMethodName等 | 提取方法预览/apply |
| write / rewrite | path、text、encoding；rewrite另需expectedHash | CLI新建/整文件重写；默认预览，apply=true才落盘 |
| replace / insert | path、expectedHash、old、text、expectedCount、regex | CLI精确/正则替换或锚点插入；insert仅字面量，before可选；支持预览/apply |
| patch | path、expectedHash、edits数组 | 按顺序在内存执行1..100项old/text/expectedCount替换，全部校验通过才一次写入；不是原始unified-diff解析器 |
| method-replace | path、expectedHash、old、text | 精确替换一次完整旧方法声明；输入方法文本由调用方定位核实，不自动解析/生成Java方法 |
| reads / writes | 暂无 | 未验证局部变量精确引用/读写分类入口，不把refs冒充分类 |
| diff | paths数组 | 指定路径的Git HEAD差异；不含未跟踪文件 |
| links | paths数组 | 简单Markdown相对目标路径检查；不解析锚点、引用式链接和标题 |
| batch | 顶层requests数组 | 一个进程、同一有限IDE会话，串行执行，遇错停止，不自动回滚 |

局部变量/参数重命名服从IDE当前符号选择能力；歧义保持错误，不选第一处。静态调用图不保证覆盖反射、事件路由、Mixin等动态运行路径。

## 请求文件与批处理

复杂参数、多行内容、正则及中文优先使用UTF-8 JSON请求文件或stdin，避免cmd/PowerShell引号混用。脚本不执行请求中的shell代码。

```json
{
  "op": "batch",
  "limit": 20,
  "requests": [
    {"op":"members","args":{"class":"org.unitego.lobecorp.util.debug.LcDebugUtil"},"limit":30},
    {"op":"refs","args":{"class":"org.unitego.lobecorp.util.debug.LcDebugUtil","member":"isEnabled","maxResults":10}},
    {"op":"callers","args":{"class":"org.unitego.lobecorp.util.debug.LcDebugUtil","method":"isEnabled","depth":1,"maxResults":10}}
  ]
}
```

```powershell
python tools/dev/dev.py --request request.json --format json
Get-Content -Raw -Encoding UTF8 request.json | python tools/dev/dev.py --request -
```

简短查询支持第二个位置参数：files对应glob、read对应path、search对应q、symbols对应query、package对应name、class/members对应class。其他操作使用请求文件；--args仅用于能够正确传递的小JSON。batch内每个写子请求独立指定apply，顶层apply不下传。

## 输出、分页与退出码

统一返回ok、operation、backend、items、total、truncated、continuation、warnings；失败附error.code/message/details。退出0表示当前请求成功，2表示失败；CLI参数解析错误也为2。batch任一子项失败时顶层ok=false，即使该项在分页外。

- 默认--format summary：紧凑JSON，超过10000字符时明确output_truncated；此时items是输出预览，使用json/full恢复数据结构。
- --format json：不按字符裁剪，仍遵守条数分页；适合程序消费。
- --format full：缩进JSON，默认最多500条，不是无限输出。--limit 1..500与--offset N控制响应切片。
- IDEA文本结果items单位为响应行，total仅表示后端已返回行数；后端maxResults截断独立存在，可能写在正文，不是全项目精确总数。
- continuation给下一次offset，调用时保留同一请求并更新offset。重新请求会重新查询，不保证跨修改快照一致；batch复用连接，不持久缓存。
- read行范围在单个item内，用start/count续读；range_truncated标明后续行。search达到maxMatches时warnings注明总量只是已收集值。
- 默认超时30秒，--timeout范围(0,300]，分别用于每次子进程/HTTP操作，不是整批全局时限。

## 修改保护与当前限制

写路径限定真实root，拒绝路径穿越与指向root外的链接；默认排除.git、.workspace、.local、.idea、.gradle、build、run、node_modules、lib、vendor、src/generated。脚本不提交、推送、切分支或运行游戏。权限仍来自实际请求，apply不创造授权。

2026-11-07用户明确“只有需要idea的才用”：普通文本操作使用CLI，语义操作使用IDEA。write创建不存在的文件，rewrite显式重写已有文件，replace/insert/patch/method-replace做精确文本编辑。默认仅预览，apply=true或--apply才写入。已有文件必须提供当前原字节expectedHash；不会无条件覆盖。

已有文件apply还需args.documentState="no-unsaved"，表示调用方已核实没有未保存的编辑器版本。这是输入前置事实，不是脚本自动验证结论，也不要求逐次向用户重复确认。无法核实时使用IDE Document编辑或保持预览，不把磁盘hash当作Document版本。新建不需要该断言。语义重构apply使用顶层documentState，覆盖所有受影响文件。

UTF-8默认保留已有BOM，其他编码可显式指定encoding；保留原统一LF/CRLF/CR，替换文本跟随原分隔符。混合换行文件拒绝，避免全文件归一化；未修改正文保持字节格式。匹配次数必须与expectedCount一致，空锚点拒绝。patch按顺序执行edits，可依赖前项结果；任一失败均不写目标文件。正则replace沿用Python re语法。

apply写入前在.local/dev-script-writes/<唯一ID>/保存原字节及计划；输出receipt路径。临时文件与目标同目录，新文件以独占创建写入而不覆盖并发创建（不依赖Windows硬链接权限，失败时可能保留未完成新文件），已有文件在再次hash检查后os.replace；写后核对原字节。失败不覆盖后续用户编辑以自动回滚。检查与替换间不是操作系统级compare-and-swap，外部并发写入必须先协调；不宣称多文件原子事务。

重构首先由IDEA dry-run返回previewHash、受影响磁盘文件与applyAvailable。apply请求保留相同args，并传顶层expectedPreviewHash及documentState="no-unsaved"；脚本重新预览，比较预览、参数与报告文件的hash，再以ignoreConflicts=false执行原生重构。清单缺失/截断/格式未识别时拒绝apply。hash并非IDEA原子事务，不保证覆盖反射/字符串等未报告引用；原生预览冲突信息必须审阅。当前没有实际执行重构验收，不将接口实现当作运行成功。

精确局部变量读写引用分类与自动生成override仍无经验证后端；方法定位可用method-read/symbol，方法文本修改用method-replace或patch，不用正则括号配平假装PSI重写。

```json
{"op":"write","args":{"path":"docs/example.md","text":"# 示例\n"},"apply":false}
```

通用规则引用[项目规范](../../AGENTS.md)、[工作流程](../standards/workflow.md)，不另建授权制度。过程计划与证据位于忽略的.agent和.local，不随Git发布。

## 故障与证据边界

错误码：CAPABILITY_MISSING为工具缺失/适配器限制；IDE_UNAVAILABLE为连接或协议读取失败；IDE_HTTP、IDE_RPC、IDE_TOOL_FAILED分别为HTTP、JSON-RPC和工具错误；INVALID_TOOL_ARGS列出与当前schema不符的字段；PATH_OUTSIDE_ROOT、PATH_EXCLUDED为范围限制；VERSION_CONFLICT、MATCH_CONFLICT为磁盘/预览版本与匹配次数不符；DOCUMENT_STATE_REQUIRED要求核实无未保存内容；MIXED_LINE_ENDINGS拒绝混合换行。

不通过自动重启IDEA、修改过滤配置、静默跨根目录或文本替换语义符号绕过错误。连接在正常/错误/超时路径关闭，不常驻服务。HTTP支持JSON与有限SSE；无限事件流或交互elicitation不在适配范围。

工具目录在线、只读查询成功、静态检查通过、新建/重构执行成功分别记录。未执行的重构、未保存冲突、多文件部分失败或跨终端行为验收不标通过；正式结果见修改记录与任务报告。
