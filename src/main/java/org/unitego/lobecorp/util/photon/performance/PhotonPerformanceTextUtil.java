package org.unitego.lobecorp.util.photon.performance;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

import java.util.Locale;

public class PhotonPerformanceTextUtil {
	public static final String PREFIX = "photon.lobecorp.performance.";

	public static String key(String name) {
		return PREFIX + name;
	}

	public static String register() {
		String[][] entries = {
				{"execution_context", "Playback: %s", "播放上下文：%s"},
				{"title", "Performance analysis", "性能分析"},
				{"restart_required", "Restart the development client to load the performance instrumentation.", "请重新启动开发客户端，以加载性能采样混入。"},
				{"capacity_short", "Capacity stop: %s", "容量中止：%s"},
				{"capacity_detail", "Capacity stop: %s; frame %s. Partial capture; shorten the next window.", "容量触发：%s；帧 %s。仅保留提前结束前的数据，请缩短下一次捕获范围。"},
				{"quality_breakdown", "Denied node %s / aggregate %s / raw span %s / counter %s requests", "节点拒绝 %s / 聚合拒绝 %s / 原始 span 丢失 %s / 计数拒绝 %s"},
				{"limit.nodes", "Node dictionary", "节点字典"},
				{"limit.threads", "Thread buffers", "线程缓冲"},
				{"limit.aggregates", "Aggregate table", "聚合表"},
				{"limit.counters", "Counter table", "计数表"},
				{"limit.important_marks", "Important milestones", "重要阶段标记"},
				{"limit.depth", "Scope depth", "调用深度"},
				{"gpu", "GPU passes / batches", "GPU 阶段与批次"},
				{"gpu_header", "GPU inclusive sum / max ms | measured / missing | pass / ownership", "GPU 包含合计 / 峰值 ms | 实测 / 缺失 | 阶段与归属"},
				{"sort_gpu", "Sort by GPU inclusive time", "按 GPU 包含耗时排序"},
				{"gpu_linked", "Linked batch", "关联批次"},
				{"gpu_shared", "Shared / unattributed", "共享或未归属"},
				{"resource_header", "Whole capture counters; not selected-window totals", "整会话计数器；不代表选中区间总量"},
				{"operation_header", "Time ms | ownership | operation | detail | values", "时间 ms | 归属 | 操作 | 详情 | 数值"},
				{"window_header", "Known full-call self / max ms | full + partial / open | object / thread", "完整调用已知自身 / 最大 ms | 完整 + 跨界 / 未完 | 对象与线程"},
				{"window_frames", "Frame range", "帧区间"},
				{"window_apply", "Analyze range", "分析区间"},
				{"window_clear", "Whole capture", "整次捕获"},
				{"window_export", "Export range", "导出区间"},
				{"window_invalid", "Choose frames 1 through the last completed interval, with first <= last.", "请选择 1 至末尾帧间隔，起点不大于终点。"},
				{"window_wait_stop", "Range selected. Analysis starts after capture stops.", "已选择区间；采样停止后在后台分析。"},
				{"window_working", "Analyzing frozen data in the background...", "正在后台分析冻结记录……"},
				{"window_summary", "Frames %s-%s; interval P95 %s / P99 %s ms", "帧 %s–%s；间隔 P95 %s / P99 %s ms"},
				{"window_quality", "Retained records only; global dropped %s / overwritten %s; GPU crossing %s", "仅保留记录；整次丢样 %s / 覆盖 %s；GPU 跨界 %s"},
				{"window_overview", "Overview stores no raw CPU calls; range CPU totals unavailable.", "概览未存原始 CPU 调用，不能生成区间 CPU 总量。"},
				{"window_export_unavailable", "Stop capture, wait for analysis/export, then export the range.", "请停止采样并等待分析或导出完成，再导出区间。"},
				{"analysis_queue_full", "Analysis queue is full. Select the range again later.", "分析队列已满，请稍后重新选择区间。"},
				{"chart_legend", "Yellow interval; cyan CPU / violet GPU at bucket interval peak; max %s ms; %s intervals; drag to select", "黄：间隔；青 CPU / 紫 GPU：桶内最长帧；上限 %s ms；%s 间隔；拖动选区"},
				{"row_header", "Self total ms | Max call ms | Calls | Object / stage", "自身合计 ms | 最大调用 ms | 调用数 | 对象与阶段"},
				{"overview", "Overview", "概览"},
				{"objects", "Effects / objects", "特效与对象"},
				{"models", "Models / materials", "模型与材质"},
				{"animations", "Animations / tracks", "动画与轨道"},
				{"operations", "Operations / timeline", "操作与时间轴"},
				{"resources", "Resources / counters", "资源与计数"},
				{"export", "Export / comparison", "导出与对比"},
				{"start_overview", "Capture overview", "开始概览采样"},
				{"start_detail", "Capture objects", "开始逐对象采样"},
				{"start_gpu", "Capture CPU + GPU", "开始 CPU 与 GPU 采样"},
				{"stop", "Stop and export", "停止并导出"},
				{"cancel", "Cancel without exporting", "取消且不导出"},
				{"close", "Close", "关闭"},
				{"seconds", "Duration (seconds)", "采样时长（秒）"},
				{"label", "Capture label", "记录名称"},
				{"search", "Filter name, resource or stage", "筛选名称、资源或阶段"},
				{"previous", "Previous page", "上一页"},
				{"next", "Next page", "下一页"},
				{"all", "All objects", "全部对象"},
				{"focus", "Locate object", "定位对象"},
				{"sort_self", "Sort by self time", "按自身耗时排序"},
				{"sort_max", "Sort by maximum", "按最大调用排序"},
				{"sort_alloc", "Sort by allocation", "按分配量排序"},
				{"empty", "No capture data yet", "尚无捕获数据"},
				{"unavailable", "Unavailable / unmeasured", "不可用或未测量"},
				{"hint", "CPU and GPU are separate. Shared passes are not divided between objects. Call quantiles use retained events.", "CPU 与 GPU 分列；共享 pass 不分摊给对象；调用分位仅来自保留事件。"},
				{"started", "Capturing for %s seconds (%s). Reproduce the operation now.", "已启动 %s 秒的 %s 采样，请复现需要检测的操作。"},
				{"stopped", "Capture stopped; export continues in the background.", "采样已停止，记录正在后台导出。"},
				{"cancelled", "Capture cancelled without exporting.", "采样已取消，未导出。"},
				{"status", "Active: %s; exporting: %s; nodes: %s; frame: %s", "采样：%s；导出中：%s；对象：%s；帧：%s"},
				{"output", "Output: %s", "导出目录：%s"},
				{"error", "Operation failed: %s", "操作未完成：%s"},
				{"busy", "A capture or export is still active. Stop it or wait for export.", "已有采样或导出进行中，请先停止采样或等待导出完成。"},
				{"mode.overview", "overview", "概览"},
				{"mode.detail", "objects", "逐对象"},
				{"mode.gpu", "CPU + GPU", "CPU 与 GPU"},
				{"page", "Page %s / %s; %s rows", "第 %s / %s 页；共 %s 项"},
				{"summary", "Calls %s; self %s ms; max call %s ms; allocation %s; node %s", "调用 %s 次；自身 %s ms；最大调用 %s ms；分配 %s；对象 %s"},
				{"ownership", "Ownership path: %s", "归属路径：%s"},
				{"quality", "Dropped %s; overwritten %s; incomplete %s", "丢样 %s；环形覆盖 %s；未完成 %s"},
				{"export_hint", "CSV, operation log and Perfetto timeline are written locally. Inspect frame and quantile scopes before comparing.", "CSV、操作记录和 Perfetto 时间线仅保存本地；对比前核对帧、调用分位和采样范围。"}
		};
		for (var entry : entries) {
			LangHandler.creates(Lobecorp.NAMESPACE, key(entry[0]), entry[1], entry[2]);
		}
		for (var stage : Stage.values()) {
			LangHandler.creates(Lobecorp.NAMESPACE, key("stage." + stage.name().toLowerCase(Locale.ROOT)),
					stage.name().replace('_', ' '), stageName(stage));
		}
		return PREFIX;
	}

	private static String stageName(Stage stage) {
		return switch (stage) {
			case RENDER -> "绘制帧";
			case EXTRACT -> "提取";
			case FX_CREATE -> "创建运行态";
			case FX_COPY -> "复制";
			case FX_EMIT -> "发射";
			case FX_RESET -> "重置";
			case OBJECT_TICK -> "对象模拟";
			case OBJECT_FRAME -> "对象逐帧更新";
			case OBJECT_EXTRACT -> "对象提取";
			case GEOMETRY -> "网格烘焙";
			case MODEL_PREPARE -> "模型准备";
			case MODEL_SAMPLE -> "模型采样";
			case MODEL_POSE -> "模型姿态";
			case MODEL_PART -> "模型部件";
			case MATERIAL -> "材质";
			case UPLOAD -> "上传";
			case DRAW -> "提交与绘制";
			case BLOOM -> "泛光";
			case ANIMATION -> "动画求值";
			case ANIMATION_FRAME -> "动画逐帧";
			case TRACK -> "轨道求值";
			case CURVE -> "曲线";
			case SEEK -> "时间定位";
			case REPLAY -> "模拟重放";
			case UI_INPUT -> "界面输入";
			case UI_LAYOUT -> "布局";
			case UI_RENDER -> "界面绘制";
			case MANUAL_INPUT -> "手动输入";
			case COMMAND_SEND -> "指令发送";
			case COMMAND_HANDLE -> "指令处理";
			case SKILL -> "技能执行";
			case CLIENT_PHASE -> "客户端阶段";
			case RESOURCE_LOAD -> "资源加载";
			case PREWARM -> "准备";
			case CLEANUP -> "清理";
			case EXPORT -> "导出";
		};
	}
}
