package org.unitego.lobecorp.registry.translation.photon.performance;

import org.unitego.lobecorp.registry.translation.TranslationRegistry;

public class PhotonPerformanceTranslations {
	public static final String PREFIX = "photon.lobecorp.performance.";

	public static final String EXECUTION_CONTEXT = TranslationRegistry.register(key("execution_context"),
			"Playback: %s", "播放上下文：%s");
	public static final String TITLE = TranslationRegistry.register(key("title"),
			"Performance analysis", "性能分析");
	public static final String RESTART_REQUIRED = TranslationRegistry.register(key("restart_required"),
			"Restart the development client to load the performance instrumentation.", "请重新启动开发客户端，以加载性能采样混入。");
	public static final String CAPACITY_SHORT = TranslationRegistry.register(key("capacity_short"),
			"Capacity stop: %s", "容量中止：%s");
	public static final String CAPACITY_DETAIL = TranslationRegistry.register(key("capacity_detail"),
			"Capacity stop: %s; frame %s. Partial capture; shorten the next window.", "容量触发：%s；帧 %s。仅保留提前结束前的数据，请缩短下一次捕获范围。");
	public static final String QUALITY_BREAKDOWN = TranslationRegistry.register(key("quality_breakdown"),
			"Denied node %s / aggregate %s / raw span %s / counter %s requests", "节点拒绝 %s / 聚合拒绝 %s / 原始 span 丢失 %s / 计数拒绝 %s");
	public static final String LIMIT_NODES = TranslationRegistry.register(key("limit.nodes"),
			"Node dictionary", "节点字典");
	public static final String LIMIT_THREADS = TranslationRegistry.register(key("limit.threads"),
			"Thread buffers", "线程缓冲");
	public static final String LIMIT_AGGREGATES = TranslationRegistry.register(key("limit.aggregates"),
			"Aggregate table", "聚合表");
	public static final String LIMIT_COUNTERS = TranslationRegistry.register(key("limit.counters"),
			"Counter table", "计数表");
	public static final String LIMIT_IMPORTANT_MARKS = TranslationRegistry.register(key("limit.important_marks"),
			"Important milestones", "重要阶段标记");
	public static final String LIMIT_DEPTH = TranslationRegistry.register(key("limit.depth"),
			"Scope depth", "调用深度");
	public static final String GPU = TranslationRegistry.register(key("gpu"),
			"GPU passes / batches", "GPU 阶段与批次");
	public static final String GPU_HEADER = TranslationRegistry.register(key("gpu_header"),
			"GPU inclusive sum / max ms | measured / missing | pass / ownership", "GPU 包含合计 / 峰值 ms | 实测 / 缺失 | 阶段与归属");
	public static final String SORT_GPU = TranslationRegistry.register(key("sort_gpu"),
			"Sort by GPU inclusive time", "按 GPU 包含耗时排序");
	public static final String GPU_LINKED = TranslationRegistry.register(key("gpu_linked"),
			"Linked batch", "关联批次");
	public static final String GPU_SHARED = TranslationRegistry.register(key("gpu_shared"),
			"Shared / unattributed", "共享或未归属");
	public static final String RESOURCE_HEADER = TranslationRegistry.register(key("resource_header"),
			"Whole capture counters; not selected-window totals", "整会话计数器；不代表选中区间总量");
	public static final String OPERATION_HEADER = TranslationRegistry.register(key("operation_header"),
			"Time ms | ownership | operation | detail | values", "时间 ms | 归属 | 操作 | 详情 | 数值");
	public static final String WINDOW_HEADER = TranslationRegistry.register(key("window_header"),
			"Known full-call self / max ms | full + partial / open | object / thread", "完整调用已知自身 / 最大 ms | 完整 + 跨界 / 未完 | 对象与线程");
	public static final String WINDOW_FRAMES = TranslationRegistry.register(key("window_frames"),
			"Frame range", "帧区间");
	public static final String WINDOW_APPLY = TranslationRegistry.register(key("window_apply"),
			"Analyze range", "分析区间");
	public static final String WINDOW_CLEAR = TranslationRegistry.register(key("window_clear"),
			"Whole capture", "整次捕获");
	public static final String WINDOW_EXPORT = TranslationRegistry.register(key("window_export"),
			"Export range", "导出区间");
	public static final String WINDOW_INVALID = TranslationRegistry.register(key("window_invalid"),
			"Choose frames 1 through the last completed interval, with first <= last.", "请选择 1 至末尾帧间隔，起点不大于终点。");
	public static final String WINDOW_WAIT_STOP = TranslationRegistry.register(key("window_wait_stop"),
			"Range selected. Analysis starts after capture stops.", "已选择区间；采样停止后在后台分析。");
	public static final String WINDOW_WORKING = TranslationRegistry.register(key("window_working"),
			"Analyzing frozen data in the background...", "正在后台分析冻结记录……");
	public static final String WINDOW_SUMMARY = TranslationRegistry.register(key("window_summary"),
			"Frames %s-%s; interval P95 %s / P99 %s ms", "帧 %s–%s；间隔 P95 %s / P99 %s ms");
	public static final String WINDOW_QUALITY = TranslationRegistry.register(key("window_quality"),
			"Retained records only; global dropped %s / overwritten %s; GPU crossing %s", "仅保留记录；整次丢样 %s / 覆盖 %s；GPU 跨界 %s");
	public static final String WINDOW_OVERVIEW = TranslationRegistry.register(key("window_overview"),
			"Overview stores no raw CPU calls; range CPU totals unavailable.", "概览未存原始 CPU 调用，不能生成区间 CPU 总量。");
	public static final String WINDOW_EXPORT_UNAVAILABLE = TranslationRegistry.register(key("window_export_unavailable"),
			"Stop capture, wait for analysis/export, then export the range.", "请停止采样并等待分析或导出完成，再导出区间。");
	public static final String ANALYSIS_QUEUE_FULL = TranslationRegistry.register(key("analysis_queue_full"),
			"Analysis queue is full. Select the range again later.", "分析队列已满，请稍后重新选择区间。");
	public static final String CHART_LEGEND = TranslationRegistry.register(key("chart_legend"),
			"Yellow interval; cyan CPU / violet GPU at bucket interval peak; max %s ms; %s intervals; drag to select", "黄：间隔；青 CPU / 紫 GPU：桶内最长帧；上限 %s ms；%s 间隔；拖动选区");
	public static final String ROW_HEADER = TranslationRegistry.register(key("row_header"),
			"Self total ms | Max call ms | Calls | Object / stage", "自身合计 ms | 最大调用 ms | 调用数 | 对象与阶段");
	public static final String OVERVIEW = TranslationRegistry.register(key("overview"),
			"Overview", "概览");
	public static final String OBJECTS = TranslationRegistry.register(key("objects"),
			"Effects / objects", "特效与对象");
	public static final String MODELS = TranslationRegistry.register(key("models"),
			"Models / materials", "模型与材质");
	public static final String ANIMATIONS = TranslationRegistry.register(key("animations"),
			"Animations / tracks", "动画与轨道");
	public static final String OPERATIONS = TranslationRegistry.register(key("operations"),
			"Operations / timeline", "操作与时间轴");
	public static final String RESOURCES = TranslationRegistry.register(key("resources"),
			"Resources / counters", "资源与计数");
	public static final String EXPORT = TranslationRegistry.register(key("export"),
			"Export / comparison", "导出与对比");
	public static final String START_OVERVIEW = TranslationRegistry.register(key("start_overview"),
			"Capture overview", "开始概览采样");
	public static final String START_DETAIL = TranslationRegistry.register(key("start_detail"),
			"Capture objects", "开始逐对象采样");
	public static final String START_GPU = TranslationRegistry.register(key("start_gpu"),
			"Capture CPU + GPU", "开始 CPU 与 GPU 采样");
	public static final String STOP = TranslationRegistry.register(key("stop"),
			"Stop and export", "停止并导出");
	public static final String CANCEL = TranslationRegistry.register(key("cancel"),
			"Cancel without exporting", "取消且不导出");
	public static final String CLOSE = TranslationRegistry.register(key("close"),
			"Close", "关闭");
	public static final String SECONDS = TranslationRegistry.register(key("seconds"),
			"Duration (seconds)", "采样时长（秒）");
	public static final String LABEL = TranslationRegistry.register(key("label"),
			"Capture label", "记录名称");
	public static final String SEARCH = TranslationRegistry.register(key("search"),
			"Filter name, resource or stage", "筛选名称、资源或阶段");
	public static final String PREVIOUS = TranslationRegistry.register(key("previous"),
			"Previous page", "上一页");
	public static final String NEXT = TranslationRegistry.register(key("next"),
			"Next page", "下一页");
	public static final String ALL = TranslationRegistry.register(key("all"),
			"All objects", "全部对象");
	public static final String FOCUS = TranslationRegistry.register(key("focus"),
			"Locate object", "定位对象");
	public static final String SORT_SELF = TranslationRegistry.register(key("sort_self"),
			"Sort by self time", "按自身耗时排序");
	public static final String SORT_MAX = TranslationRegistry.register(key("sort_max"),
			"Sort by maximum", "按最大调用排序");
	public static final String SORT_ALLOC = TranslationRegistry.register(key("sort_alloc"),
			"Sort by allocation", "按分配量排序");
	public static final String EMPTY = TranslationRegistry.register(key("empty"),
			"No capture data yet", "尚无捕获数据");
	public static final String UNAVAILABLE = TranslationRegistry.register(key("unavailable"),
			"Unavailable / unmeasured", "不可用或未测量");
	public static final String HINT = TranslationRegistry.register(key("hint"),
			"CPU and GPU are separate. Shared passes are not divided between objects. Call quantiles use retained events.", "CPU 与 GPU 分列；共享 pass 不分摊给对象；调用分位仅来自保留事件。");
	public static final String STARTED = TranslationRegistry.register(key("started"),
			"Capturing for %s seconds (%s). Reproduce the operation now.", "已启动 %s 秒的 %s 采样，请复现需要检测的操作。");
	public static final String STOPPED = TranslationRegistry.register(key("stopped"),
			"Capture stopped; export continues in the background.", "采样已停止，记录正在后台导出。");
	public static final String CANCELLED = TranslationRegistry.register(key("cancelled"),
			"Capture cancelled without exporting.", "采样已取消，未导出。");
	public static final String STATUS = TranslationRegistry.register(key("status"),
			"Active: %s; exporting: %s; nodes: %s; frame: %s", "采样：%s；导出中：%s；对象：%s；帧：%s");
	public static final String OUTPUT = TranslationRegistry.register(key("output"),
			"Output: %s", "导出目录：%s");
	public static final String ERROR = TranslationRegistry.register(key("error"),
			"Operation failed: %s", "操作未完成：%s");
	public static final String BUSY = TranslationRegistry.register(key("busy"),
			"A capture or export is still active. Stop it or wait for export.", "已有采样或导出进行中，请先停止采样或等待导出完成。");
	public static final String MODE_OVERVIEW = TranslationRegistry.register(key("mode.overview"),
			"overview", "概览");
	public static final String MODE_DETAIL = TranslationRegistry.register(key("mode.detail"),
			"objects", "逐对象");
	public static final String MODE_GPU = TranslationRegistry.register(key("mode.gpu"),
			"CPU + GPU", "CPU 与 GPU");
	public static final String PAGE = TranslationRegistry.register(key("page"),
			"Page %s / %s; %s rows", "第 %s / %s 页；共 %s 项");
	public static final String SUMMARY = TranslationRegistry.register(key("summary"),
			"Calls %s; self %s ms; max call %s ms; allocation %s; node %s", "调用 %s 次；自身 %s ms；最大调用 %s ms；分配 %s；对象 %s");
	public static final String OWNERSHIP = TranslationRegistry.register(key("ownership"),
			"Ownership path: %s", "归属路径：%s");
	public static final String QUALITY = TranslationRegistry.register(key("quality"),
			"Dropped %s; overwritten %s; incomplete %s", "丢样 %s；环形覆盖 %s；未完成 %s");
	public static final String EXPORT_HINT = TranslationRegistry.register(key("export_hint"),
			"CSV, operation log and Perfetto timeline are written locally. Inspect frame and quantile scopes before comparing.", "CSV、操作记录和 Perfetto 时间线仅保存本地；对比前核对帧、调用分位和采样范围。");

	public static final String STAGE_RENDER = TranslationRegistry.register(key("stage.render"),
			"RENDER", "绘制帧");
	public static final String STAGE_EXTRACT = TranslationRegistry.register(key("stage.extract"),
			"EXTRACT", "提取");
	public static final String STAGE_FX_CREATE = TranslationRegistry.register(key("stage.fx_create"),
			"FX CREATE", "创建运行态");
	public static final String STAGE_FX_COPY = TranslationRegistry.register(key("stage.fx_copy"),
			"FX COPY", "复制");
	public static final String STAGE_FX_EMIT = TranslationRegistry.register(key("stage.fx_emit"),
			"FX EMIT", "发射");
	public static final String STAGE_FX_RESET = TranslationRegistry.register(key("stage.fx_reset"),
			"FX RESET", "重置");
	public static final String STAGE_OBJECT_TICK = TranslationRegistry.register(key("stage.object_tick"),
			"OBJECT TICK", "对象模拟");
	public static final String STAGE_OBJECT_FRAME = TranslationRegistry.register(key("stage.object_frame"),
			"OBJECT FRAME", "对象逐帧更新");
	public static final String STAGE_OBJECT_EXTRACT = TranslationRegistry.register(key("stage.object_extract"),
			"OBJECT EXTRACT", "对象提取");
	public static final String STAGE_GEOMETRY = TranslationRegistry.register(key("stage.geometry"),
			"GEOMETRY", "网格烘焙");
	public static final String STAGE_MODEL_PREPARE = TranslationRegistry.register(key("stage.model_prepare"),
			"MODEL PREPARE", "模型准备");
	public static final String STAGE_MODEL_SAMPLE = TranslationRegistry.register(key("stage.model_sample"),
			"MODEL SAMPLE", "模型采样");
	public static final String STAGE_MODEL_POSE = TranslationRegistry.register(key("stage.model_pose"),
			"MODEL POSE", "模型姿态");
	public static final String STAGE_MODEL_PART = TranslationRegistry.register(key("stage.model_part"),
			"MODEL PART", "模型部件");
	public static final String STAGE_MATERIAL = TranslationRegistry.register(key("stage.material"),
			"MATERIAL", "材质");
	public static final String STAGE_UPLOAD = TranslationRegistry.register(key("stage.upload"),
			"UPLOAD", "上传");
	public static final String STAGE_DRAW = TranslationRegistry.register(key("stage.draw"),
			"DRAW", "提交与绘制");
	public static final String STAGE_BLOOM = TranslationRegistry.register(key("stage.bloom"),
			"BLOOM", "泛光");
	public static final String STAGE_ANIMATION = TranslationRegistry.register(key("stage.animation"),
			"ANIMATION", "动画求值");
	public static final String STAGE_ANIMATION_FRAME = TranslationRegistry.register(key("stage.animation_frame"),
			"ANIMATION FRAME", "动画逐帧");
	public static final String STAGE_TRACK = TranslationRegistry.register(key("stage.track"),
			"TRACK", "轨道求值");
	public static final String STAGE_CURVE = TranslationRegistry.register(key("stage.curve"),
			"CURVE", "曲线");
	public static final String STAGE_SEEK = TranslationRegistry.register(key("stage.seek"),
			"SEEK", "时间定位");
	public static final String STAGE_REPLAY = TranslationRegistry.register(key("stage.replay"),
			"REPLAY", "模拟重放");
	public static final String STAGE_UI_INPUT = TranslationRegistry.register(key("stage.ui_input"),
			"UI INPUT", "界面输入");
	public static final String STAGE_UI_LAYOUT = TranslationRegistry.register(key("stage.ui_layout"),
			"UI LAYOUT", "布局");
	public static final String STAGE_UI_RENDER = TranslationRegistry.register(key("stage.ui_render"),
			"UI RENDER", "界面绘制");
	public static final String STAGE_MANUAL_INPUT = TranslationRegistry.register(key("stage.manual_input"),
			"MANUAL INPUT", "手动输入");
	public static final String STAGE_COMMAND_SEND = TranslationRegistry.register(key("stage.command_send"),
			"COMMAND SEND", "指令发送");
	public static final String STAGE_COMMAND_HANDLE = TranslationRegistry.register(key("stage.command_handle"),
			"COMMAND HANDLE", "指令处理");
	public static final String STAGE_SKILL = TranslationRegistry.register(key("stage.skill"),
			"SKILL", "技能执行");
	public static final String STAGE_CLIENT_PHASE = TranslationRegistry.register(key("stage.client_phase"),
			"CLIENT PHASE", "客户端阶段");
	public static final String STAGE_RESOURCE_LOAD = TranslationRegistry.register(key("stage.resource_load"),
			"RESOURCE LOAD", "资源加载");
	public static final String STAGE_PREWARM = TranslationRegistry.register(key("stage.prewarm"),
			"PREWARM", "准备");
	public static final String STAGE_CLEANUP = TranslationRegistry.register(key("stage.cleanup"),
			"CLEANUP", "清理");
	public static final String STAGE_EXPORT = TranslationRegistry.register(key("stage.export"),
			"EXPORT", "导出");

	public static String key(String name) {
		return PREFIX + name;
	}
}
