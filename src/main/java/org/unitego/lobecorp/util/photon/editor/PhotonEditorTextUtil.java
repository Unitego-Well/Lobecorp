package org.unitego.lobecorp.util.photon.editor;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

public class PhotonEditorTextUtil {
	/**
	 * Photon 编辑器扩展的翻译键命名空间。
	 */
	public static final String PREFIX = "photon.lobecorp.editor.";

	public static String key(String name) {
		return PREFIX + name;
	}

	public static String register() {
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_curves", "Curve editing", "曲线编辑");
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_preview", "Preview", "预览");
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_particles", "Particles and rendering", "粒子与渲染");
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_resources", "Resources and clipboard", "资源与剪切板");
		LangHandler.creates(Lobecorp.NAMESPACE, key("settings_tip"), "Select a category to manage extensions. Existing settings and presets are preserved. All new switches default to enabled.", "选择左侧分类管理扩展功能；保留已有设置和预设，新开关默认开启。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.system_clipboard"), "System clipboard", "使用系统剪切板");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.rename_dialog"), "Rename dialogs", "重命名弹窗");
		LangHandler.creates(Lobecorp.NAMESPACE, key("clipboard"), "Clipboard", "剪切板");
		LangHandler.creates(Lobecorp.NAMESPACE, key("copy_system"), "Copy to system clipboard", "复制到系统剪切板");
		LangHandler.creates(Lobecorp.NAMESPACE, key("paste_system"), "Paste from system clipboard", "从系统剪切板粘贴");
		LangHandler.creates(Lobecorp.NAMESPACE, key("clipboard_invalid"), "Paste rejected: invalid data, incompatible type, missing or ambiguous target, locked destination, or overlapping content. No content was pasted.", "无法粘贴：数据无效、类型不符、目标缺失或不唯一、目标锁定，或与现有内容冲突。未写入粘贴内容。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("clipboard_help"), "Ctrl+C/V uses the system clipboard for selected keys, gradient/curve/expression clips and color stops, timeline clips/tracks, hierarchy objects and resource cells. Context menus also offer Copy/Paste from system clipboard; native duplication and copy-to actions remain available. Module Copy/Paste uses the same clipboard. Cross-project tracks require uniquely matching object names/types; a single clip group can paste onto a selected compatible track. Pasting is one undoable operation. Text fields keep their own copy/paste behavior.", "Ctrl+C/V 对选中关键帧、渐变/曲线/表达式子片段及颜色点、时间轴片段/轨道、层级粒子和资源格使用系统剪切板。右键也可复制到或从系统剪切板粘贴，原有直接复制和复制到功能继续可用。模块复制/粘贴共用同一剪切板。跨项目轨道要求对象名称与类型唯一匹配；单组片段可粘贴到选中的兼容轨道。每次粘贴可一次撤销，输入框仍使用自身的文本复制粘贴。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.control"), "Activator track lifecycle options", "激活轨道开始与结束选项");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control"), "Activation behavior", "激活行为");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.start"), "Control start", "开始控制");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.end"), "Control end", "结束控制");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.start_behavior"), "Start behavior", "开始行为");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.end_behavior"), "End behavior", "结束行为");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.restart"), "Restart (original)", "重新启动（原版）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.clear_on_restart"), "Clear existing particles on restart", "重新启动时清理已有粒子");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.continue"), "Continue current state", "继续当前运行状态");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.original"), "Stop updates and hide (original)", "停止更新并隐藏（原版）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.stop_emission"), "Stop particle emission only", "仅停止生成粒子");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.clear"), "Clear particles and hide", "清除现有粒子并隐藏");
		LangHandler.creates(Lobecorp.NAMESPACE, key("control.tip"), "All clips on this activator track share these settings. Default entry restarts and clears existing particles. Restart resets the subtree; clearing existing particles is enabled by default and can be disabled. Disable start/end control to use only the other boundary. Stop emission keeps existing particles updating; infinite-lifetime particles remain until cleared. Multiple activator tracks combine with OR. Control tracks retain their original timing and restart behavior.", "同一激活轨道的片段共用这些设置，默认进入时重新启动并清理已有粒子。重新启动重置子树，默认清理已有粒子，可关闭清理。关闭开始或结束控制可只控制另一端；仅停止生成保留已有粒子并继续更新，无限寿命粒子需要主动清除。多个激活轨道按任意一个激活即显示处理；控制轨道保留原版时间范围与重启行为。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_materials"), "Copy source materials", "复制来源材质列表");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_cycles"), "Copy source cycle settings", "复制来源循环设置");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_source"), "Source emitter", "来源发射器");
		LangHandler.creates(Lobecorp.NAMESPACE, key("details"), "Select keys or tracks before editing. Track selection edits all properties on those tracks. Scaling keeps the earliest selected time fixed. Step presets add native expression clips; remove these clips to return to the original curve. Aligned tangents stay linked while dragging; Break disables the link. Batch editing changes only checked fields; materials and cycles are copied from the displayed source. Module presets are stored in editor settings. Parameters are temporary and restored on close. Isolation keeps the selected branches and their parents. Resource buttons locate the emitter inspector. Escape cancels keyframe and tangent drags.", "编辑前选择关键帧或轨道；选择轨道会编辑轨道内全部属性。时间缩放保持最早选中时间不变。阶梯预设添加原生表达式片段；删除片段即可恢复原曲线。对齐切线后拖动保持联动，打断解除联动。批量编辑只修改勾选字段，材质和循环设置从显示的来源发射器复制。模块预设保存在编辑器设置中。参数仅临时预览，关闭恢复。独显保留选中分支和父级。资源按钮定位发射器检查器。Esc 取消关键帧和切线拖动。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("step"), "Step (expression clips)", "阶梯（表达式片段）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("depth_test"), "Depth test", "深度测试");
		LangHandler.creates(Lobecorp.NAMESPACE, key("depth_write"), "Depth write", "深度写入");
		LangHandler.creates(Lobecorp.NAMESPACE, "lobecorp_photon_shortcuts", "Shortcut settings", "快捷键设置");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.precise"), "Precise keyframe editing", "精确编辑关键帧");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.time_scale"), "Scale selected time", "选中内容时间缩放");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.batch_move"), "Move across tracks", "跨轨道批量移动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.fit"), "Fit curve view", "适配曲线视图");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.tangents"), "Curve presets and tangents", "曲线预设与切线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.range_preview"), "Loop preview range", "局部区间循环预览");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.parameters"), "Temporary preview parameters", "临时预览参数");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.batch_properties"), "Batch edit emitter properties", "批量修改发射器属性");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.presets"), "Module presets and selective copy", "模块预设与选择性复制");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.solo"), "Isolate preview", "独显预览");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.seed"), "Fixed random seed entry", "固定随机种子入口");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.step"), "Step playback", "逐 tick 播放");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.diagnostics"), "Render and resource diagnostics", "渲染与资源诊断");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.shortcuts"), "Editor shortcuts", "编辑器快捷键");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.curve_pan"), "Middle-button curve pan", "曲线中键平移");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.axis_lock"), "Shift drag axis lock", "Shift 拖动锁轴");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.rotation_cycle"), "Rotation cycle controls", "旋转循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.color_cycle"), "Color cycle controls", "颜色循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.size_cycle"), "Size cycle controls", "大小循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.velocity_cycle"), "Velocity cycle controls", "速度循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.force_cycle"), "Force cycle controls", "受力循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.uv_cycle"), "UV cycle controls", "UV 循环控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.time_offset"), "Cycle time offset controls", "循环时间偏移控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("feature.depth_sort"), "View-depth sorting control", "视图深度排序控件");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.fit_selected"), "fit selected", "适配选中");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.fit_all"), "fit all", "显示全部");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.step_back"), "step back", "后退一 tick");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.step_forward"), "step forward", "前进一 tick");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.step_back_large"), "step back large", "大步后退");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.step_forward_large"), "step forward large", "大步前进");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.duplicate"), "duplicate", "复制选中");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.move"), "move", "移动关键帧");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.scale"), "scale", "缩放时间");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.range_in"), "range in", "设置预览起点");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.range_out"), "range out", "设置预览终点");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.range_loop"), "range loop", "循环预览");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.solo"), "solo", "独显选中");
		LangHandler.creates(Lobecorp.NAMESPACE, key("action.cancel"), "cancel", "取消拖动");
		LangHandler.creates(Lobecorp.NAMESPACE, key("documents"), "Documentation", "文档");
		LangHandler.creates(Lobecorp.NAMESPACE, key("settings"), "Settings", "设置");
		LangHandler.creates(Lobecorp.NAMESPACE, key("features"), "Features", "功能开关");
		LangHandler.creates(Lobecorp.NAMESPACE, key("shortcuts"), "Shortcuts", "快捷键");
		LangHandler.creates(Lobecorp.NAMESPACE, key("tools"), "Tools", "工具");
		LangHandler.creates(Lobecorp.NAMESPACE, key("close"), "Close", "关闭");
		LangHandler.creates(Lobecorp.NAMESPACE, key("apply"), "Apply", "应用");
		LangHandler.creates(Lobecorp.NAMESPACE, key("reset"), "Reset", "重置");
		LangHandler.creates(Lobecorp.NAMESPACE, key("time"), "Time (ticks)", "时间（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("value"), "Value", "数值");
		LangHandler.creates(Lobecorp.NAMESPACE, key("time_delta"), "Time offset (ticks)", "时间增量（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("value_delta"), "Value offset", "数值增量");
		LangHandler.creates(Lobecorp.NAMESPACE, key("factor"), "Time scale factor", "时间缩放倍率");
		LangHandler.creates(Lobecorp.NAMESPACE, key("preview_start"), "Preview start (ticks)", "预览起点（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("preview_end"), "Preview end (ticks)", "预览终点（tick）");
		LangHandler.creates(Lobecorp.NAMESPACE, key("preview_loop"), "Loop preview", "循环预览");
		LangHandler.creates(Lobecorp.NAMESPACE, key("linear"), "Linear", "线性");
		LangHandler.creates(Lobecorp.NAMESPACE, key("ease_in"), "Ease in", "缓入");
		LangHandler.creates(Lobecorp.NAMESPACE, key("ease_out"), "Ease out", "缓出");
		LangHandler.creates(Lobecorp.NAMESPACE, key("ease_both"), "Ease in/out", "缓入缓出");
		LangHandler.creates(Lobecorp.NAMESPACE, key("flat"), "Flat tangents", "水平切线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("align"), "Align tangents", "对齐切线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("break"), "Break tangents", "打断切线");
		LangHandler.creates(Lobecorp.NAMESPACE, key("preset_name"), "Preset name", "预设名称");
		LangHandler.creates(Lobecorp.NAMESPACE, key("save_preset"), "Save module preset", "保存模块预设");
		LangHandler.creates(Lobecorp.NAMESPACE, key("load_preset"), "Apply module preset", "应用模块预设");
		LangHandler.creates(Lobecorp.NAMESPACE, key("module"), "Module", "模块");
		LangHandler.creates(Lobecorp.NAMESPACE, key("copy_module"), "Copy module", "复制模块");
		LangHandler.creates(Lobecorp.NAMESPACE, key("paste_module"), "Paste module", "粘贴模块");
		LangHandler.creates(Lobecorp.NAMESPACE, key("no_selection"), "Select editable unlocked keyframes or emitters first.", "请先选择可编辑且未锁定的关键帧或发射器。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("invalid"), "Invalid input, overlapping keys, or unavailable resource.", "输入无效、关键帧重叠或资源不可用。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("parameter_tip"), "Preview only. Reset or close the panel to resume timeline control. Start parameters affect newly spawned particles.", "仅影响预览。重置或关闭面板恢复时间轴控制。起始参数只影响新生成的粒子。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("shortcut_tip"), "Use CTRL/SHIFT/ALT + key; leave empty to disable. Conflicting and reserved bindings are rejected.", "使用 CTRL/SHIFT/ALT + 按键；留空禁用。冲突及保留绑定无法使用。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("help"), "All extensions are controlled by Settings > Lobecorp. Switches affect editor controls only; saved effects keep playing. G moves selected keys; S scales time about the earliest selection. F fits selected curves; Home fits all. I/O set the preview range, L toggles looping. Alt-drag copies keys. Escape cancels a drag. Text fields retain their original keyboard behavior.", "所有扩展由“设置 > Lobecorp”管理。开关只控制编辑器，已保存特效照常播放。G 移动选中关键帧；S 以选中内容最早时间缩放；F 适配选中曲线，Home 显示全部；I/O 设置预览区间，L 切换循环；Alt 拖动复制关键帧；Esc 取消拖动。输入框保留原键盘操作。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("batch_tip"), "Only checked properties are applied to selected particle emitters; all other settings are preserved.", "只将勾选的属性应用到选中的粒子发射器，保留其余设置。");
		LangHandler.creates(Lobecorp.NAMESPACE, key("diagnostic_tip"), "Read-only authored render state and material resources. Intersecting translucent geometry may still require a different rendering technique.", "只读显示配置的渲染状态和材质资源。相交半透明几何仍可能需要其他渲染技术。");
		PhotonEditorTooltipUtil.register();
		return PREFIX;
	}
}
