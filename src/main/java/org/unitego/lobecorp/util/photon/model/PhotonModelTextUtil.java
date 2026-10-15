package org.unitego.lobecorp.util.photon.model;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

/// 模型特效发射器与轻量模型编辑器的集中翻译入口。
public class PhotonModelTextUtil {
	/// 模型编辑、动画与发射器属性的翻译前缀。
	public static final String PREFIX = "photon.lobecorp.model.";
	/// 专用模型特效对象的稳定类型标识。
	public static final String TYPE = "lobecorp_model_emitter";

	public static String key(String name) {
		return PREFIX + name;
	}

	public static String register() {
		LangHandler.creates(Lobecorp.NAMESPACE, TYPE, "Model effect", "模型特效");
		LangHandler.creates(Lobecorp.NAMESPACE, "photon.timeline_track.lobecorp_model_animation", "Model animation", "模型动画");
		registerEmitter();
		registerEditor();
		registerEditorControls();
		registerAnimation();
		registerModelResources();
		registerMeshTools();
		return PREFIX;
	}

	private static void registerMeshTools() {
		register(new String[][]{
				{"sphere", "Sphere", "球体"},
				{"arc", "Arc ribbon", "圆弧带"},
				{"cylindrical_patch", "Cylindrical patch", "柱面片"},
				{"spherical_patch", "Spherical patch", "球面片"},
				{"primitive.radius", "Radius", "半径"},
				{"primitive.height", "Height", "高度"},
				{"primitive.angle", "Angle (degrees)", "水平角度（度）"},
				{"primitive.vertical_angle", "Latitude span (degrees)", "纬度跨度（度）"},
				{"primitive.segments", "Circumference segments", "周向分段"},
				{"primitive.rings", "Height / latitude / tube segments", "高度／纬度／管截面分段"},
				{"primitive.invalid", "Invalid primitive dimensions, angles or segment counts.", "基本体尺寸、角度或分段数无效。"},
				{"mesh.tools", "Mesh operations", "网格操作"},
				{"mesh.merge", "Merge vertices", "合并顶点"},
				{"mesh.merge.center", "At center", "合并到中心"},
				{"mesh.merge.first", "At first selected", "合并到首选"},
				{"mesh.merge.last", "At last selected", "合并到末选"},
				{"mesh.merge.distance", "By distance", "按距离合并"},
				{"mesh.dissolve", "Dissolve edges", "消除边"},
				{"mesh.collapse", "Collapse edges", "塌缩边"},
				{"mesh.face", "Create face from ordered vertices", "按顶点顺序生成面"},
				{"mesh.triangulate", "Triangulate faces", "转换为三角面"},
				{"topology.invalid", "Invalid selection or incompatible topology, materials, UVs or vertex weights.", "选择或拓扑无效，或材质、UV、顶点权重不兼容。"},
				{"loop_cut", "Loop cut", "环切"},
				{"loop_cut.uniform", "Uniform cuts by count", "按数量均分"},
				{"loop_cut.count", "Cut count", "切割数量"},
				{"loop_cut.follow", "Follow mouse position", "使用鼠标位置"},
				{"loop_cut.percent", "Edge position (%)", "边上位置（%）"},
				{"loop_cut.preview", "Preview in viewport", "在视窗中预览"},
				{"loop_cut.help", "Hover an edge to preview; click to confirm, or use the cancel action.", "悬停边预览，点击确认，使用取消动作取消。"},
				{"loop_cut.invalid", "Loop cut requires a compatible quad strip in one part; the preview may be stale.", "环切需要同一部件内兼容的四边面带；当前拓扑不支持或预览已过期。"}
		});
	}

	private static void registerModelResources() {
		register(new String[][]{
				{"resources.add_slot", "Add material slot", "添加材质槽"},
				{"resources.from_library", "Choose library material", "选入库材质"},
				{"resources.import_png", "Import PNG reference", "导入 PNG 引用"},
				{"resources.replace_library", "Replace from library", "从库替换材质"},
				{"resources.replace_png", "Replace with PNG", "用 PNG 替换"},
				{"resources.texture", "Texture resource ID", "贴图资源 ID"},
				{"resources.apply_texture", "Apply texture ID", "应用贴图 ID"},
				{"resources.tint", "Tint", "色调"},
				{"resources.tint.none", "Original texture color", "原贴图颜色"},
				{"resources.tint.grass", "Grass color", "草色"},
				{"resources.overlay", "Transparent overlay", "透明覆盖层"},
				{"resources.assign", "Assign to selected faces", "分配给选中面"},
				{"resources.rename", "Rename material slot", "重命名材质槽"},
				{"resources.delete_slot", "Delete unused slot", "删除未使用槽"},
				{"resources.slot_usage", "%s (%s faces)", "%s（%s 个面）"},
				{"resources.slot_unused", "%s (unused)", "%s（未使用）"},
				{"resources.empty", "No model material slots", "当前模型没有材质槽"},
				{"resources.fallback_faces", "%s faces use the emitter material", "%s 个面沿用发射器材质"},
				{"resources.shared_note", "Material parameters are saved in the project; shader graphs and textures use shared resource references. Export a model effect pack to include referenced resources.", "材质参数随工程保存，着色器图和贴图使用共享资源引用；导出模型特效包可收集引用资源。"},
				{"resources.invalid_name", "Use a non-empty slot name without control characters, # or backslash.", "槽名不能为空，且不能包含控制字符、# 或反斜杠。"},
				{"resources.name_exists", "This material slot name already exists.", "材质槽名称已存在。"},
				{"resources.invalid_material", "The material snapshot is invalid or unavailable.", "材质快照无效或不可用。"},
				{"resources.no_faces", "Select faces before assigning a material.", "请先选择需要分配材质的面。"},
				{"resources.in_use", "This material slot is still used by faces.", "该材质槽仍被面使用。"},
				{"resources.missing_slot", "Select an existing model material slot.", "请选择现有模型材质槽。"},
				{"resources.png_only", "Choose a PNG image.", "请选择 PNG 图像。"},
				{"resources.invalid_texture", "Use a valid texture resource ID.", "请输入有效的贴图资源 ID。"},
				{"resources.edit_slot", "Edit model material slot", "编辑模型材质槽"},
				{"resources.add_graph", "Add shader graph material", "添加着色器图材质"},
				{"resources.add_custom", "Add custom shader material", "添加自定义着色器材质"},
				{"resources.shader_settings", "Shader preview and parameters", "着色器预览与参数"},
				{"resources.shader_snapshot", "Shader material (project snapshot)", "着色器材质（工程快照）"},
				{"resources.edit_shader", "Edit model shader parameters", "编辑模型着色器参数"},
				{"resources.replace_material", "Replace model material", "替换模型材质"}
		});
	}

	private static void registerEmitter() {
		register(new String[][]{
				{"type", "Model effect", "模型特效"},
				{"settings", "Model effect settings", "模型特效设置"},
				{"source", "Model source", "模型来源"},
				{"source.embedded", "Embedded model", "内嵌模型"},
				{"source.resource", "Resource", "资源"},
				{"source.file", "Local file", "本地文件"},
				{"resource", "Model resource", "模型资源"},
				{"file", "Model file", "模型文件"},
				{"flip_obj_v", "Flip OBJ V", "翻转 OBJ 的 V 坐标"},
				{"use_reference_dimensions", "Use model reference dimensions", "使用模型参考尺寸"},
				{"reload", "Reload model", "重新加载模型"},
				{"edit", "Edit model", "编辑模型"},
				{"enabled", "Enabled", "启用"},
				{"duration", "Duration (ticks)", "持续时间（tick）"},
				{"looping", "Loop", "循环"},
				{"start_delay", "Start delay (ticks)", "开始延迟（tick）"},
				{"clip", "Animation clip", "动画片段"},
				{"playback_speed", "Animation speed", "动画播放倍率"},
				{"animation_offset", "Animation offset (ticks)", "动画偏移（tick）"},
				{"length", "Length (model units)", "长度（模型单位）"},
				{"width", "Width (model units)", "宽度（模型单位）"},
				{"anchor", "Fixed endpoint", "固定端点"},
				{"anchor.none", "Model origin", "模型原点"},
				{"anchor.start", "Start", "起点"},
				{"anchor.end", "End", "终点"},
				{"opacity", "Opacity", "透明度"},
				{"color", "Color", "颜色"},
				{"renderer", "Renderer and material", "渲染与材质"},
				{"default_renderer", "Default renderer and material", "默认渲染与材质"},
				{"override_renderer", "Override model renderer", "覆盖模型渲染设置"},
				{"invalid_renderer", "Model renderer settings are missing or invalid.", "模型渲染设置缺失或无效。"},
				{"parts", "Parts", "部件"},
				{"bones", "Bones", "骨骼"},
				{"morphs", "Shape keys", "形状键"},
				{"position", "Position", "位置"},
				{"rotation", "Rotation", "旋转"},
				{"scale", "Scale", "缩放"},
				{"morph_weight", "Shape key weight", "形状键权重"},
				{"load_failed", "Unable to load model: %s", "无法加载模型：%s"},
				{"model_status", "Model status", "模型状态"},
				{"status.ok", "Ready", "正常"},
				{"status.missing", "Model source is missing", "模型来源不存在"},
				{"status.invalid", "Model data is invalid", "模型数据无效"},
				{"pause", "Pause animation", "暂停动画"}
		});
	}

	private static void registerEditor() {
		register(new String[][]{
				{"editor", "Model effect editor", "模型特效编辑器"},
				{"project.type", "Model effect project", "模型特效项目"},
				{"definition.type", "Model definition", "模型定义"},
				{"obj.type", "OBJ model", "OBJ 模型"},
				{"project.title", "[Model effect project] - %s", "[模型特效项目] - %s"},
				{"project.unnamed", "Unnamed model effect", "未命名模型特效"},
				{"project.missing_asset", "Model project or definition is missing valid model data", "模型工程或定义缺少有效的模型数据"},
				{"project.editor_required", "Open this project in the model effect project editor", "请在模型特效项目编辑器中打开该工程"},
				{"editor.hierarchy", "Model hierarchy", "模型层级"},
				{"editor.resources", "Model resources", "模型资源"},
				{"editor.model_data", "Model data", "模型数据"},
				{"editor.panels", "Panels", "面板"},
				{"editor.help", "Select a part in Model hierarchy to edit its properties; double-click it to open UV. Inspector and right-click menus provide per-part shading, double-sided rendering and an Inside out action. Use Animation for keyframes and Model resources for materials and textures. Change shortcuts in Settings > Keymap. Right-click panel tabs to maximize or float them. Save model effect projects as .mfxproj. Open OBJ or .mfx definitions to create a project; export OBJ from File, or .mfx definitions and .mfxpack packs from File > Export.", "模型层级单击选择部件并刷新检视，双击打开 UV。检视和右键可设置部件阴影、双面渲染，并执行一次负尺寸翻面；UV、位置和大小保持不变。动画编辑关键帧，模型资源管理材质和贴图。快捷键在设置 → Keymap 中修改。右键面板标签可最大化或浮动。模型特效工程保存为 .mfxproj；打开 OBJ 或 .mfx 定义创建工程。文件菜单可导出 OBJ，文件 → 导出可导出 .mfx 定义或 .mfxpack 包。"},
				{"project.source_unavailable", "The source effect project was closed or changed. Reopen the model project from the target emitter.", "来源特效项目已关闭或切换，请重新从目标发射器打开模型工程。"},
				{"model", "Model", "模型"},
				{"uv", "UV", "UV"},
				{"animation", "Animation", "动画"},
				{"name", "Name", "名称"},
				{"new", "New model", "新建模型"},
				{"open", "Open project", "打开工程"},
				{"save", "Save project", "保存工程"},
				{"import_obj", "Import OBJ", "导入 OBJ"},
				{"export_obj", "Export OBJ", "导出 OBJ"},
				{"export_data", "Export model data", "导出模型数据"},
				{"export", "Export model effect", "导出模型特效"},
				{"apply", "Apply to emitter", "应用到发射器"},
				{"cancel", "Cancel", "取消"},
				{"undo", "Undo", "撤销"},
				{"redo", "Redo", "重做"},
				{"vertex", "Vertex", "顶点"},
				{"face", "Face", "面"},
				{"part", "Part", "部件"},
				{"primitive", "Primitive", "基本体"},
				{"plane", "Plane", "平面"},
				{"cube", "Cube", "立方体"},
				{"cylinder", "Cylinder", "圆柱"},
				{"cone", "Cone", "圆锥"},
				{"ring", "Ring", "圆环"},
				{"delete", "Delete", "删除"},
				{"duplicate", "Duplicate", "复制"},
				{"extrude", "Extrude face", "挤出面"},
				{"distance", "Distance", "距离"},
				{"pivot", "Pivot", "轴心"},
				{"stretch", "Local stretch", "局部拉伸"},
				{"stretch_start", "Stretch start", "拉伸起点"},
				{"stretch_end", "Stretch end", "拉伸终点"},
				{"parent", "Parent", "父级"},
				{"none", "None", "无"},
				{"uv_planar", "Planar projection", "平面投影"},
				{"error", "Operation failed: %s", "操作失败：%s"},
				{"saved", "Saved: %s", "已保存：%s"},
				{"topology_locked", "Topology is locked while shape keys exist", "存在形状键时拓扑已锁定"},
				{"select_all", "Select all", "全选"},
				{"clear_selection", "Clear selection", "清空选择"}
		});
	}

	private static void registerEditorControls() {
		register(new String[][]{
				{"edge", "Edge", "边"},
				{"axis_x", "X", "X"},
				{"axis_y", "Y", "Y"},
				{"axis_z", "Z", "Z"},
				{"forward_axis", "Model forward axis", "模型前向轴"},
				{"pose", "Animation pose", "动画姿态"},
				{"rotation_degrees", "Rotation (degrees)", "旋转（度）"},
				{"editor.edit_pivot", "Edit pivot in viewport", "在视窗中编辑轴心"},
				{"editor.move_pivot", "Move part pivot", "移动部件轴心"},
				{"editor.pivot_help", "Move the pivot with the viewport's translation handles while keeping the model in place. Rotation and scaling use this pivot; existing animations also use the new pivot.", "使用视窗的移动手柄调整轴心，模型保持原位。旋转和缩放围绕此轴心，已有动画也会使用新轴心。"},
				{"start", "Start", "起点"},
				{"end", "End", "终点"},
				{"uv_translate_u", "U translation (pixels)", "U 位移（像素）"},
				{"uv_translate_v", "V translation (pixels)", "V 位移（像素）"},
				{"uv_scale_u", "U scale", "U 缩放"},
				{"uv_scale_v", "V scale", "V 缩放"},
				{"uv_rotation", "UV rotation (degrees)", "UV 旋转（度）"},
				{"uv_texture", "UV preview texture", "UV 预览纹理"},
				{"uv_grid", "Pixel grid", "像素网格"},
				{"editor.shading", "Model directional shading", "模型阴影"},
				{"editor.double_sided", "Double-sided rendering", "双面渲染"},
				{"mesh.inside_out", "Inside out", "负尺寸"},
				{"editor.invalid_geometry", "Invalid geometry; the last valid edit is retained.", "几何数据无效，已保留上一次有效修改。"},
				{"editor.rename_part", "Rename part", "重命名部件"},
				{"editor.delete_subtree", "Delete node and children", "删除节点及子级"},
				{"editor.delete_promote", "Delete node and promote children", "删除节点并提升子级"},
				{"editor.hierarchy.inspect", "Edit", "修改"},
				{"editor.hierarchy.add", "Add", "添加"},
				{"editor.hierarchy.legacy_part", "Part %s", "部件 %s"},
				{"editor.hierarchy.missing_part", "This part no longer exists.", "该部件不存在。"},
				{"editor.hierarchy.animated_parent", "Related parts contain animation; children cannot be promoted losslessly.", "相关部件含动画，无法无损提升子级。"},
				{"editor.hierarchy.non_trs", "The transform contains shear or degenerate scaling; children cannot be promoted losslessly.", "变换含剪切或退化缩放，无法无损提升子级。"},
				{"timeline.animations", "Animations", "动画列表"},
				{"timeline.title", "Timeline", "时间轴"},
				{"timeline.tracks", "Tracks", "轨道"},
				{"timeline.new_name", "Animation %s", "动画 %s"},
				{"timeline.rename", "Rename animation", "重命名动画"},
				{"timeline.reorder", "Reorder animations", "调整动画顺序"},
				{"editor.singular_part_transform", "Part transform is not invertible; geometry cannot be edited in the viewport.", "部件变换不可逆，无法在视窗中编辑几何。"},
				{"timeline.move_key", "Move keyframe", "移动关键帧"},
				{"timeline.empty", "Create or select an animation on the left", "在左侧新建或选择动画"},
				{"timeline.part_track", "Part %s · TRS", "部件 %s · TRS"},
				{"timeline.bone_track", "Bone %s · TRS", "骨骼 %s · TRS"},
				{"timeline.morph_track", "Shape %s · Weight", "形状 %s · 权重"},
				{"timeline.opacity_track", "Model opacity", "整体透明度"},
				{"timeline.key_time", "Key time (ticks)", "关键帧时间（tick）"},
				{"timeline.key_value", "Key value", "关键帧值"},
				{"editor.add_bone", "Add bone", "添加骨骼"},
				{"editor.add_clip", "Add clip", "添加片段"},
				{"editor.add_morph", "Add shape key", "添加形状键"},
				{"editor.assign_weight", "Assign weight to selected vertices", "设置选中顶点权重"},
				{"editor.box_select", "Box selection", "框选"},
				{"editor.delete_bone", "Delete bone", "删除骨骼"},
				{"editor.delete_clip", "Delete clip", "删除片段"},
				{"editor.delete_key", "Delete keyframe", "删除关键帧"},
				{"editor.delete_morph", "Delete shape key", "删除形状键"},
				{"editor.key_at", "Key at %s ticks", "关键帧：%s tick"},
				{"editor.load", "Load model", "载入模型"},
				{"editor.morph_delta", "Shape key vertex delta", "形状键顶点偏移"},
				{"editor.overwrite", "Overwrite existing file?", "覆盖现有文件？"},
				{"editor.record_key", "Record keyframe", "记录关键帧"},
				{"editor.selection_status", "Model: %s vertices, %s faces; selected: %s vertices", "模型：%s 个顶点、%s 个面；已选：%s 个顶点"},
				{"editor.model_information", "Model information", "模型信息"},
				{"editor.selected_count", "Selected elements", "已选元素"},
				{"editor.transform", "Transform selection", "变换选中部分"},
				{"editor.uv_drag", "Drag UV", "拖动 UV"},
				{"editor.uv_help", "Drag corners; drag empty space or use the box-select action, Shift adds to selection. Shift/Ctrl/Shift+Ctrl snap by 1/0.5/0.1 pixels within the texture.", "拖动角点；空白处拖动或使用框选动作框选，Shift 追加选择。Shift / Ctrl / 同时按下按 1 / 0.5 / 0.1 像素吸附，限制在纹理内。"},
				{"editor.uv_project_axis", "Planar projection (%s axis)", "平面投影（%s 轴）"},
				{"editor.uv_transform", "Transform selected UV", "变换选中 UV"}
		});
	}

	private static void registerAnimation() {
		register(new String[][]{
				{"timeline.model_animation", "Model animation", "模型动画"},
				{"timeline.model_target", "Model emitter", "模型发射器"},
				{"timeline.completion", "After clip", "片段结束后"},
				{"timeline.completion.hold", "Hold last sample", "保持末帧"},
				{"timeline.completion.restore", "Restore default animation", "恢复默认动画"},
				{"default_playback", "Default playback", "默认播放"},
				{"default_clip", "Default animation clip", "默认动画片段"},
				{"clip_none", "No animation", "不播放动画"},
				{"invalid_default_clip", "The default animation clip is missing. Select it again.", "默认动画片段已不存在，请重新选择。"},
				{"invalid_playback", "Invalid default playback settings.", "默认播放设置无效。"},
				{"clips", "Animation clips", "动画片段"},
				{"time", "Time (ticks)", "时间（tick）"},
				{"play", "Play", "播放"},
				{"stop", "Stop", "停止"},
				{"remove_keyframe", "Remove keyframe", "删除关键帧"},
				{"bone", "Bone", "骨骼"},
				{"add_bone", "Add bone", "添加骨骼"},
				{"bind", "Bind selected vertices", "绑定选中顶点"},
				{"weight", "Weight", "权重"},
				{"add_morph", "Add shape key", "添加形状键"},
				{"edit_morph", "Edit shape key", "编辑形状键"},
				{"base_shape", "Base shape", "基础形状"},
				{"part_key", "Keyframe part transform", "记录部件变换关键帧"},
				{"bone_key", "Keyframe bone transform", "记录骨骼变换关键帧"},
				{"morph_key", "Keyframe shape key weight", "记录形状键权重关键帧"},
				{"animation_loop", "Loop animation", "循环动画"},
				{"bind_pose", "Bind pose", "绑定姿态"},
		});
	}

	private static void register(String[][] entries) {
		for (var entry : entries) {
			LangHandler.creates(Lobecorp.NAMESPACE, key(entry[0]), entry[1], entry[2]);
		}
	}
}
