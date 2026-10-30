# Photon 重复特效性能入口

## 2026-10-30 新基线与元数据优化批次

builder已支持--base-version和--full-version，例如新基线均v6、正式优化后均v8；旧默认v2/v3保持，按显式批次选取，不覆盖旧输出。v7首局仅集成pilot，监测协议不足，不混入正式性能对照；两个关闭缓存控制另留证据。
FULL参数必须显式-Dmcp.port=19877，IDEA同步可能重新生成benchClientRunVmArgs，不再依赖生成文件保留端口。启动观察可加独立name=photon-entry的StartFlightRecording，文件目录须已存在、每局新路径；它不同于Bench03自有JFR。BASE仍不得加入测试启动参数。
完整v6/v8各12局36触发已完成；19目标单测、三JVM局部元数据基准、54个游戏测量后原生检查通过。全局帧时间不支持收益结论，真实每copy分配观测约降7.4%；raw仍有限，不填补unknown。
本轮执行顺序按用户要求：全部代码/必要配置完成前，只IDEA检查本轮新增或修改部分；完成后统一验证，失败修正后再继续。未优化控制结束后生产缓存注册已原位恢复，六份用户配置也恢复原字节。下一局继续时必须重新确认实际图形/光影状态、新世界、新报告路径，不能把当前参数最后一局直接当新局。


## 2026-10-30 当前用法与验收

已完成四组12局36触发；有效BASE为v2六局，有效FULL为v4普通三局及激光01、v5激光02/03。每次继续运行必须使用新的worldId和resultDirectory；当前参数文件保留最后一局配置，不可直接当作新局。以下初始配置示例属于入口创建时记录，以本节为当前状态。

三个诊断属性分别为lobecorp.photon.trace.maxNodes、lobecorp.photon.trace.maxAggregatesPerThread、lobecorp.photon.trace.maxCountersPerThread。默认均4096；节点与counter范围4096..16384、聚合范围4096..65536。FULL最近使用16384/65536/16384，保留容量和quality保护，raw环仍有限。用户Iris/options/Spark已恢复测试前配置，未来测试需按当前授权重新准备并回读实际shader状态。

离线组装示例（所有游戏结束后、输出目录必须不存在；HEAD须经IDEA后台Git核对）：
```powershell
python -B tools/photon/performance/build_repeat_manifest.py --matrix-ended --full-version v4 --full-retry-version v5 --git-head <verified-head> --output-dir <new-output-directory>
```
显式retry覆盖对应格，失败retry不会回退为成功。audit按JVM重复-D最后值及DURATION主窗口判断，level.dat按可变世界快照分类，静态资源严格复算；缺失进程环境保持unknown。analyze的matrix_complete只确认执行/帧数据完整；configuration_comparable=false时不得声称严格A/B或优化收益。最新真实证据为build/agent-backups/20261030-repeat-matrix-final/，原始失败与初版审计结果保留。



## FULL03

IDEA 永久配置 `Photon Repeat Full` 位于 `.idea/runConfigurations/Photon_Repeat_Full.xml`，复用现有 `Photon Bench Debug` 的 main class、`lobecorp_photon.photonBenchClient` 模块、main + bench 的 `fml.modFolders`、程序参数文件与工作目录。VM 顺序为生成的 `@build/moddev/benchClientRunVmArgs.txt`、原 modFolders、最后 `@tools/photon/performance/config/full-repeat-vmargs.txt`；后面的同名属性覆盖前面的值。旧配置与 run-control 参数文件不改。

已有构建产物准备完成后，通过 IDEA **Debug** 启动此配置。核心要求实际 JVM 含 JDWP；由 IDEA Debug 注入，不手写 JDWP，也不要用普通 Run 冒充 FULL。配置保留原 Make 步骤，未来启动时可能触发编译。本次仅静态配置与文档核对，没有启动、构建或连接游戏。

参数选择精确 03 场景，普通原生 `photon:first_effect2`，CAST、提交探针、preserveWindow、JFR 均显式开启。FULL 还要求 Sodium、Spark、MCP 模组，且实际没有活动光影包；`PhotonBenchDiagnostics` 要求唯一 RUNNING 的 `modbench:lobecorp.photon-03-repeat-performance` JFR。preserveWindow 必须在 Mixin 初始化前生效。核心自动使用 GPU 模式的 ManagedCapture，无须增加另一 GPU 开关；没有 shader 或 packed 开关。

Run 的当前 options 为 VSync=true、renderDistance=16、simulationDistance=12、maxFps=260、graphicsPreset=fancy。追加文件对齐前三项，FPS 沿用生成基础值 260；图形预设等其余差异仍须由实际快照记录。preserveWindow 只阻止窗口尺寸/全屏切换，不阻止 Bench 应用 VSync、FPS 与视距。requireWindowFocus=false 不代表窗口已经最大化或处于焦点。

## 世界与结果路径

通过 IDEA 读取 BenchMod 0.1.5-beta 依赖 `ClientWorldController.effectiveWorldId`：normal 使用原 worldId，flat/void 追加 `-flat`/`-void`。生成基础参数当前是 flat，因此本配置请求 `photon_repeat_full_pilot_n1`，实际显示名与存档目录名均为 **photon_repeat_full_pilot_n1-flat**。完整存档路径为 `build/modBench/runs/default/client/saves/photon_repeat_full_pilot_n1-flat`。已存在时会打开原世界；每次正式独立局应换新 worldId，不能重复使用 pilot 世界冒充新局。

`RuntimeConfiguration.fromSystemProperties` 用 `Path.of(required("modBench.resultDirectory"))` 读取结果目录，无非空默认值。本追加文件有意保留生成基础参数当前的绝对目录 `build/modBench/raw-results/default/client`，不把它改成工作目录相对路径。正式独立批次可在追加文件最后使用绝对 `-DmodBench.resultDirectory=E:/.../build/modBench/raw-results/<unique-batch>/client`，由主任务指定真实批次名称。核心自有输出另外位于 Bench gameDir 的 `performance/photon_repeat_full_<uuid>/`，Bench adapter 归档到 artifacts 并记录映射。每局新 JVM、世界与独立报告，最终以运行时 PID、VM 参数和输出文件为证据。

当前静态 Iris 文件：Run 为 `enableShaders=true`、`shaderPack=` 空，因此没有选中的光影包，预期运行态 shader=false；Bench 为 `enableShaders=true` 且选中 `ComplementaryReimagined_r5.9.3.zip`。不能把 Run 的 shader=false 推广为 Bench 默认状态。若 Bench 实际加载该包，FULL 核心会拒绝 `repeat_requires_standard_sodium_no_shader`。本入口不覆盖光影设置；实际状态由主任务审计，本次未改 options/Iris。

## 原生 Node / MCP SDK 与桥接

当前环境的 MCP SDK 可由原生 Node 从原始仓库 `E:/xm/mc/mod/Unitego-Well/Lobecorp/tools/lobecorp-normify/node_modules` 导入；node_repl 的传递导入会在 `zod/v4` 失败，不能据此判断 SDK 或游戏扩展不存在。使用已有共享依赖，不安装、不改全局模块或环境配置。

标准 bridge CLI 的工具列表没有 photon_repeat 扩展，但 `lib/minecraft-mcp/bridge/dist/index.js` 导出 `ModClient`，其 `sendCommand(method, params)` POST 的正文为 `{cmd: method, ...params}`，可以发送扩展名称。该 index 的外部 SDK/zod 导入需要进程内临时 `node:module.registerHooks`，按已有 `run_repeat_base.mjs` 的实现，仅在 bridge 父模块导入 SDK/zod 时用原始 normify 的 `createRequire` 解析，再交回 nextResolve。主任务已在内存中实施此方式；本次仅核对源码，不重新连接或发命令。

只读查询的调用结构如下，放在上述 hook 注册和动态导入之后：

```js
const { ModClient } = await import(bridgeIndexFileUrl);
const mod = new ModClient();
try {
  const identity = await mod.discover(19877, 19877);
  if (!identity || identity.port !== 19877) throw Error("wrong_instance");
  const result = await mod.sendCommand("photon_repeat_status", {});
  if (result.ok === false) throw Error(JSON.stringify(result));
  console.log(JSON.stringify(result));
} finally {
  mod.disconnect();
}
```

扩展源码在 `LcPhotonMcpCommands`：status 接受空对象或 `{label}`；start 为 `{kind:"laser"|"native",label}`，且始终走 BASE；prepare 为空对象；cancel/close 为 `{label}`。写命令要求 MCP control mode，label 匹配 `[a-z0-9_]{1,80}`。FULL 由 Bench 自动启动，不调用 repeat_start。成功结果为 `{ok:true,status:...}`；超时可返回 `operation_may_be_running`，先查状态再决定后续操作。status/prepare 返回 runtimeStatus，start/cancel/close 返回 trial status。

已有 BASE 驱动的真实参数顺序是 `node tools/photon/performance/run_repeat_base.mjs <laser|native> <label> <world>`。它会进入控制模式、创建测试世界并运行，**不是只读查询**。world 必须匹配 `photon_repeat_[a-z0-9_]+` 且长度不超过 30；不要对当前运行中的 pilot 重复执行。

## 离线审计与分析

两套工具使用 `schema="photon-repeat-matrix-v1"`，`runs` 每项包含 id、kind(laser/native)、mode(full/run)、iteration(1..3)、game_pid、frames、prewarm_frames、trial_report、metadata、console、trace_directory、jfr、spark、triggers。路径相对 manifest 所在目录或使用绝对路径。triggers 含 index、requested_ns、observed_ns、started，可有 ack_ns、first_active_ns；observed_ns 的派发范围须标明，不能当作实际首帧。详见 `.agent/plans/20261029-repeat-analysis-manifest.md`。

```powershell
python -B tools/photon/performance/audit_repeat_inputs.py <manifest.json>
python -B tools/photon/performance/analyze_repeat_frames.py --manifest <manifest.json> --output-dir <new-output-directory>
```

audit JSON 写 stdout，退出码 0=pass、1=fail、2=unknown/incomplete/有警告；metadata 需同 PID runtimeStatus，试验 BASE 对应 manifest 的 run。分析生成 analysis.json 与 report.md，已有文件不会覆盖；缺失/负数探针保持未知，不填零。分位数 nearest rank，慢帧严格大于阈值；跨触发间隔和活动重叠须保留，不能视作纯单实例冷暖 A/B。空 runs 不代表完成 12 局。

主任务已报告这两套工具合计 50 个测试通过，本次没有重跑。配置静态正确不等于 FULL 启动、12 局/36 次矩阵、视觉或性能验收。
