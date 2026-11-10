package org.unitego.lobecorp.registry.translation.photon.model;

import org.unitego.lobecorp.registry.translation.TranslationRegistry;

/// 模型特效发射器与轻量模型编辑器的集中翻译入口。
public class PhotonModelTranslations {
	/// 模型编辑、动画与发射器属性的翻译前缀。
	public static final String PREFIX = "photon.lobecorp.model.";
	/// 专用模型特效对象的稳定类型标识。
	public static final String TYPE = "lobecorp_model_emitter";

	public static final String TYPE_TEXT = TranslationRegistry.register(TYPE,
			"Model effect", "模型特效");
	public static final String PHOTON_TIMELINE_TRACK_LOBECORP_MODEL_ANIMATION = TranslationRegistry.register("photon.timeline_track.lobecorp_model_animation",
			"Model animation", "模型动画");

	public static final String SPHERE = TranslationRegistry.register(key("sphere"),
			"Sphere", "球体");
	public static final String ARC = TranslationRegistry.register(key("arc"),
			"Arc ribbon", "圆弧带");
	public static final String CYLINDRICAL_PATCH = TranslationRegistry.register(key("cylindrical_patch"),
			"Cylindrical patch", "柱面片");
	public static final String SPHERICAL_PATCH = TranslationRegistry.register(key("spherical_patch"),
			"Spherical patch", "球面片");
	public static final String PRIMITIVE_RADIUS = TranslationRegistry.register(key("primitive.radius"),
			"Radius", "半径");
	public static final String PRIMITIVE_HEIGHT = TranslationRegistry.register(key("primitive.height"),
			"Height", "高度");
	public static final String PRIMITIVE_ANGLE = TranslationRegistry.register(key("primitive.angle"),
			"Angle (degrees)", "水平角度（度）");
	public static final String PRIMITIVE_VERTICAL_ANGLE = TranslationRegistry.register(key("primitive.vertical_angle"),
			"Latitude span (degrees)", "纬度跨度（度）");
	public static final String PRIMITIVE_SEGMENTS = TranslationRegistry.register(key("primitive.segments"),
			"Circumference segments", "周向分段");
	public static final String PRIMITIVE_RINGS = TranslationRegistry.register(key("primitive.rings"),
			"Height / latitude / tube segments", "高度／纬度／管截面分段");
	public static final String PRIMITIVE_INVALID = TranslationRegistry.register(key("primitive.invalid"),
			"Invalid primitive dimensions, angles or segment counts.", "基本体尺寸、角度或分段数无效。");
	public static final String MESH_TOOLS = TranslationRegistry.register(key("mesh.tools"),
			"Mesh operations", "网格操作");
	public static final String MESH_MERGE = TranslationRegistry.register(key("mesh.merge"),
			"Merge vertices", "合并顶点");
	public static final String MESH_MERGE_CENTER = TranslationRegistry.register(key("mesh.merge.center"),
			"At center", "合并到中心");
	public static final String MESH_MERGE_FIRST = TranslationRegistry.register(key("mesh.merge.first"),
			"At first selected", "合并到首选");
	public static final String MESH_MERGE_LAST = TranslationRegistry.register(key("mesh.merge.last"),
			"At last selected", "合并到末选");
	public static final String MESH_MERGE_DISTANCE = TranslationRegistry.register(key("mesh.merge.distance"),
			"By distance", "按距离合并");
	public static final String MESH_DISSOLVE = TranslationRegistry.register(key("mesh.dissolve"),
			"Dissolve edges", "消除边");
	public static final String MESH_COLLAPSE = TranslationRegistry.register(key("mesh.collapse"),
			"Collapse edges", "塌缩边");
	public static final String MESH_FACE = TranslationRegistry.register(key("mesh.face"),
			"Create face from ordered vertices", "按顶点顺序生成面");
	public static final String MESH_TRIANGULATE = TranslationRegistry.register(key("mesh.triangulate"),
			"Triangulate faces", "转换为三角面");
	public static final String TOPOLOGY_INVALID = TranslationRegistry.register(key("topology.invalid"),
			"Invalid selection or incompatible topology, materials, UVs or vertex weights.", "选择或拓扑无效，或材质、UV、顶点权重不兼容。");
	public static final String LOOP_CUT = TranslationRegistry.register(key("loop_cut"),
			"Loop cut", "环切");
	public static final String LOOP_CUT_UNIFORM = TranslationRegistry.register(key("loop_cut.uniform"),
			"Uniform cuts by count", "按数量均分");
	public static final String LOOP_CUT_COUNT = TranslationRegistry.register(key("loop_cut.count"),
			"Cut count", "切割数量");
	public static final String LOOP_CUT_FOLLOW = TranslationRegistry.register(key("loop_cut.follow"),
			"Follow mouse position", "使用鼠标位置");
	public static final String LOOP_CUT_PERCENT = TranslationRegistry.register(key("loop_cut.percent"),
			"Edge position (%)", "边上位置（%）");
	public static final String LOOP_CUT_PREVIEW = TranslationRegistry.register(key("loop_cut.preview"),
			"Preview in viewport", "在视窗中预览");
	public static final String LOOP_CUT_HELP = TranslationRegistry.register(key("loop_cut.help"),
			"Hover an edge to preview; click to confirm, or use the cancel action.", "悬停边预览，点击确认，使用取消动作取消。");
	public static final String LOOP_CUT_INVALID = TranslationRegistry.register(key("loop_cut.invalid"),
			"Loop cut requires a compatible quad strip in one part; the preview may be stale.", "环切需要同一部件内兼容的四边面带；当前拓扑不支持或预览已过期。");

	public static final String RESOURCES_ADD_SLOT = TranslationRegistry.register(key("resources.add_slot"),
			"Add material slot", "添加材质槽");
	public static final String RESOURCES_FROM_LIBRARY = TranslationRegistry.register(key("resources.from_library"),
			"Choose library material", "选入库材质");
	public static final String RESOURCES_IMPORT_PNG = TranslationRegistry.register(key("resources.import_png"),
			"Import PNG reference", "导入 PNG 引用");
	public static final String RESOURCES_REPLACE_LIBRARY = TranslationRegistry.register(key("resources.replace_library"),
			"Replace from library", "从库替换材质");
	public static final String RESOURCES_REPLACE_PNG = TranslationRegistry.register(key("resources.replace_png"),
			"Replace with PNG", "用 PNG 替换");
	public static final String RESOURCES_TEXTURE = TranslationRegistry.register(key("resources.texture"),
			"Texture resource ID", "贴图资源 ID");
	public static final String RESOURCES_APPLY_TEXTURE = TranslationRegistry.register(key("resources.apply_texture"),
			"Apply texture ID", "应用贴图 ID");
	public static final String RESOURCES_TINT = TranslationRegistry.register(key("resources.tint"),
			"Tint", "色调");
	public static final String RESOURCES_TINT_NONE = TranslationRegistry.register(key("resources.tint.none"),
			"Original texture color", "原贴图颜色");
	public static final String RESOURCES_TINT_GRASS = TranslationRegistry.register(key("resources.tint.grass"),
			"Grass color", "草色");
	public static final String RESOURCES_OVERLAY = TranslationRegistry.register(key("resources.overlay"),
			"Transparent overlay", "透明覆盖层");
	public static final String RESOURCES_ASSIGN = TranslationRegistry.register(key("resources.assign"),
			"Assign to selected faces", "分配给选中面");
	public static final String RESOURCES_RENAME = TranslationRegistry.register(key("resources.rename"),
			"Rename material slot", "重命名材质槽");
	public static final String RESOURCES_DELETE_SLOT = TranslationRegistry.register(key("resources.delete_slot"),
			"Delete unused slot", "删除未使用槽");
	public static final String RESOURCES_SLOT_USAGE = TranslationRegistry.register(key("resources.slot_usage"),
			"%s (%s faces)", "%s（%s 个面）");
	public static final String RESOURCES_SLOT_UNUSED = TranslationRegistry.register(key("resources.slot_unused"),
			"%s (unused)", "%s（未使用）");
	public static final String RESOURCES_EMPTY = TranslationRegistry.register(key("resources.empty"),
			"No model material slots", "当前模型没有材质槽");
	public static final String RESOURCES_FALLBACK_FACES = TranslationRegistry.register(key("resources.fallback_faces"),
			"%s faces use the emitter material", "%s 个面沿用发射器材质");
	public static final String RESOURCES_SHARED_NOTE = TranslationRegistry.register(key("resources.shared_note"),
			"Material parameters are saved in the project; shader graphs and textures use shared resource references. Export a model effect pack to include referenced resources.", "材质参数随工程保存，着色器图和贴图使用共享资源引用；导出模型特效包可收集引用资源。");
	public static final String RESOURCES_INVALID_NAME = TranslationRegistry.register(key("resources.invalid_name"),
			"Use a non-empty slot name without control characters, # or backslash.", "槽名不能为空，且不能包含控制字符、# 或反斜杠。");
	public static final String RESOURCES_NAME_EXISTS = TranslationRegistry.register(key("resources.name_exists"),
			"This material slot name already exists.", "材质槽名称已存在。");
	public static final String RESOURCES_INVALID_MATERIAL = TranslationRegistry.register(key("resources.invalid_material"),
			"The material snapshot is invalid or unavailable.", "材质快照无效或不可用。");
	public static final String RESOURCES_NO_FACES = TranslationRegistry.register(key("resources.no_faces"),
			"Select faces before assigning a material.", "请先选择需要分配材质的面。");
	public static final String RESOURCES_IN_USE = TranslationRegistry.register(key("resources.in_use"),
			"This material slot is still used by faces.", "该材质槽仍被面使用。");
	public static final String RESOURCES_MISSING_SLOT = TranslationRegistry.register(key("resources.missing_slot"),
			"Select an existing model material slot.", "请选择现有模型材质槽。");
	public static final String RESOURCES_PNG_ONLY = TranslationRegistry.register(key("resources.png_only"),
			"Choose a PNG image.", "请选择 PNG 图像。");
	public static final String RESOURCES_INVALID_TEXTURE = TranslationRegistry.register(key("resources.invalid_texture"),
			"Use a valid texture resource ID.", "请输入有效的贴图资源 ID。");
	public static final String RESOURCES_EDIT_SLOT = TranslationRegistry.register(key("resources.edit_slot"),
			"Edit model material slot", "编辑模型材质槽");
	public static final String RESOURCES_ADD_GRAPH = TranslationRegistry.register(key("resources.add_graph"),
			"Add shader graph material", "添加着色器图材质");
	public static final String RESOURCES_ADD_CUSTOM = TranslationRegistry.register(key("resources.add_custom"),
			"Add custom shader material", "添加自定义着色器材质");
	public static final String RESOURCES_SHADER_SETTINGS = TranslationRegistry.register(key("resources.shader_settings"),
			"Shader preview and parameters", "着色器预览与参数");
	public static final String RESOURCES_SHADER_SNAPSHOT = TranslationRegistry.register(key("resources.shader_snapshot"),
			"Shader material (project snapshot)", "着色器材质（工程快照）");
	public static final String RESOURCES_EDIT_SHADER = TranslationRegistry.register(key("resources.edit_shader"),
			"Edit model shader parameters", "编辑模型着色器参数");
	public static final String RESOURCES_REPLACE_MATERIAL = TranslationRegistry.register(key("resources.replace_material"),
			"Replace model material", "替换模型材质");

	public static final String TYPE_TEXT_TEXT = TranslationRegistry.register(key("type"),
			"Model effect", "模型特效");
	public static final String SETTINGS = TranslationRegistry.register(key("settings"),
			"Model effect settings", "模型特效设置");
	public static final String SOURCE = TranslationRegistry.register(key("source"),
			"Model source", "模型来源");
	public static final String SOURCE_EMBEDDED = TranslationRegistry.register(key("source.embedded"),
			"Embedded model", "内嵌模型");
	public static final String SOURCE_RESOURCE = TranslationRegistry.register(key("source.resource"),
			"Resource", "资源");
	public static final String SOURCE_FILE = TranslationRegistry.register(key("source.file"),
			"Local file", "本地文件");
	public static final String RESOURCE = TranslationRegistry.register(key("resource"),
			"Model resource", "模型资源");
	public static final String FILE = TranslationRegistry.register(key("file"),
			"Model file", "模型文件");
	public static final String FLIP_OBJ_V = TranslationRegistry.register(key("flip_obj_v"),
			"Flip OBJ V", "翻转 OBJ 的 V 坐标");
	public static final String USE_REFERENCE_DIMENSIONS = TranslationRegistry.register(key("use_reference_dimensions"),
			"Use model reference dimensions", "使用模型参考尺寸");
	public static final String RELOAD = TranslationRegistry.register(key("reload"),
			"Reload model", "重新加载模型");
	public static final String EDIT = TranslationRegistry.register(key("edit"),
			"Edit model", "编辑模型");
	public static final String ENABLED = TranslationRegistry.register(key("enabled"),
			"Enabled", "启用");
	public static final String DURATION = TranslationRegistry.register(key("duration"),
			"Duration (ticks)", "持续时间（tick）");
	public static final String LOOPING = TranslationRegistry.register(key("looping"),
			"Loop", "循环");
	public static final String START_DELAY = TranslationRegistry.register(key("start_delay"),
			"Start delay (ticks)", "开始延迟（tick）");
	public static final String CLIP = TranslationRegistry.register(key("clip"),
			"Animation clip", "动画片段");
	public static final String PLAYBACK_SPEED = TranslationRegistry.register(key("playback_speed"),
			"Animation speed", "动画播放倍率");
	public static final String ANIMATION_OFFSET = TranslationRegistry.register(key("animation_offset"),
			"Animation offset (ticks)", "动画偏移（tick）");
	public static final String LENGTH = TranslationRegistry.register(key("length"),
			"Length (model units)", "长度（模型单位）");
	public static final String WIDTH = TranslationRegistry.register(key("width"),
			"Width (model units)", "宽度（模型单位）");
	public static final String ANCHOR = TranslationRegistry.register(key("anchor"),
			"Fixed endpoint", "固定端点");
	public static final String ANCHOR_NONE = TranslationRegistry.register(key("anchor.none"),
			"Model origin", "模型原点");
	public static final String ANCHOR_START = TranslationRegistry.register(key("anchor.start"),
			"Start", "起点");
	public static final String ANCHOR_END = TranslationRegistry.register(key("anchor.end"),
			"End", "终点");
	public static final String OPACITY = TranslationRegistry.register(key("opacity"),
			"Opacity", "透明度");
	public static final String COLOR = TranslationRegistry.register(key("color"),
			"Color", "颜色");
	public static final String RENDERER = TranslationRegistry.register(key("renderer"),
			"Renderer and material", "渲染与材质");
	public static final String DEFAULT_RENDERER = TranslationRegistry.register(key("default_renderer"),
			"Default renderer and material", "默认渲染与材质");
	public static final String OVERRIDE_RENDERER = TranslationRegistry.register(key("override_renderer"),
			"Override model renderer", "覆盖模型渲染设置");
	public static final String INVALID_RENDERER = TranslationRegistry.register(key("invalid_renderer"),
			"Model renderer settings are missing or invalid.", "模型渲染设置缺失或无效。");
	public static final String PARTS = TranslationRegistry.register(key("parts"),
			"Parts", "部件");
	public static final String BONES = TranslationRegistry.register(key("bones"),
			"Bones", "骨骼");
	public static final String MORPHS = TranslationRegistry.register(key("morphs"),
			"Shape keys", "形状键");
	public static final String POSITION = TranslationRegistry.register(key("position"),
			"Position", "位置");
	public static final String ROTATION = TranslationRegistry.register(key("rotation"),
			"Rotation", "旋转");
	public static final String SCALE = TranslationRegistry.register(key("scale"),
			"Scale", "缩放");
	public static final String MORPH_WEIGHT = TranslationRegistry.register(key("morph_weight"),
			"Shape key weight", "形状键权重");
	public static final String LOAD_FAILED = TranslationRegistry.register(key("load_failed"),
			"Unable to load model: %s", "无法加载模型：%s");
	public static final String MODEL_STATUS = TranslationRegistry.register(key("model_status"),
			"Model status", "模型状态");
	public static final String STATUS_OK = TranslationRegistry.register(key("status.ok"),
			"Ready", "正常");
	public static final String STATUS_MISSING = TranslationRegistry.register(key("status.missing"),
			"Model source is missing", "模型来源不存在");
	public static final String STATUS_INVALID = TranslationRegistry.register(key("status.invalid"),
			"Model data is invalid", "模型数据无效");
	public static final String PAUSE = TranslationRegistry.register(key("pause"),
			"Pause animation", "暂停动画");

	public static final String EDITOR = TranslationRegistry.register(key("editor"),
			"Model effect editor", "模型特效编辑器");
	public static final String PROJECT_TYPE = TranslationRegistry.register(key("project.type"),
			"Model effect project", "模型特效项目");
	public static final String DEFINITION_TYPE = TranslationRegistry.register(key("definition.type"),
			"Model definition", "模型定义");
	public static final String OBJ_TYPE = TranslationRegistry.register(key("obj.type"),
			"OBJ model", "OBJ 模型");
	public static final String PROJECT_TITLE = TranslationRegistry.register(key("project.title"),
			"[Model effect project] - %s", "[模型特效项目] - %s");
	public static final String PROJECT_UNNAMED = TranslationRegistry.register(key("project.unnamed"),
			"Unnamed model effect", "未命名模型特效");
	public static final String PROJECT_MISSING_ASSET = TranslationRegistry.register(key("project.missing_asset"),
			"Model project or definition is missing valid model data", "模型工程或定义缺少有效的模型数据");
	public static final String PROJECT_EDITOR_REQUIRED = TranslationRegistry.register(key("project.editor_required"),
			"Open this project in the model effect project editor", "请在模型特效项目编辑器中打开该工程");
	public static final String EDITOR_HIERARCHY = TranslationRegistry.register(key("editor.hierarchy"),
			"Model hierarchy", "模型层级");
	public static final String EDITOR_RESOURCES = TranslationRegistry.register(key("editor.resources"),
			"Model resources", "模型资源");
	public static final String EDITOR_MODEL_DATA = TranslationRegistry.register(key("editor.model_data"),
			"Model data", "模型数据");
	public static final String EDITOR_PANELS = TranslationRegistry.register(key("editor.panels"),
			"Panels", "面板");
	public static final String EDITOR_HELP = TranslationRegistry.register(key("editor.help"),
			"Select a part in Model hierarchy to edit its properties; double-click it to open UV. Inspector and right-click menus provide per-part shading, double-sided rendering and an Inside out action. Use Animation for keyframes and Model resources for materials and textures. Change shortcuts in Settings > Keymap. Right-click panel tabs to maximize or float them. Save model effect projects as .mfxproj. Open OBJ or .mfx definitions to create a project; export OBJ from File, or .mfx definitions and .mfxpack packs from File > Export.", "模型层级单击选择部件并刷新检视，双击打开 UV。检视和右键可设置部件阴影、双面渲染，并执行一次负尺寸翻面；UV、位置和大小保持不变。动画编辑关键帧，模型资源管理材质和贴图。快捷键在设置 → Keymap 中修改。右键面板标签可最大化或浮动。模型特效工程保存为 .mfxproj；打开 OBJ 或 .mfx 定义创建工程。文件菜单可导出 OBJ，文件 → 导出可导出 .mfx 定义或 .mfxpack 包。");
	public static final String PROJECT_SOURCE_UNAVAILABLE = TranslationRegistry.register(key("project.source_unavailable"),
			"The source effect project was closed or changed. Reopen the model project from the target emitter.", "来源特效项目已关闭或切换，请重新从目标发射器打开模型工程。");
	public static final String MODEL = TranslationRegistry.register(key("model"),
			"Model", "模型");
	public static final String UV = TranslationRegistry.register(key("uv"),
			"UV", "UV");
	public static final String ANIMATION = TranslationRegistry.register(key("animation"),
			"Animation", "动画");
	public static final String NAME = TranslationRegistry.register(key("name"),
			"Name", "名称");
	public static final String NEW = TranslationRegistry.register(key("new"),
			"New model", "新建模型");
	public static final String OPEN = TranslationRegistry.register(key("open"),
			"Open project", "打开工程");
	public static final String SAVE = TranslationRegistry.register(key("save"),
			"Save project", "保存工程");
	public static final String IMPORT_OBJ = TranslationRegistry.register(key("import_obj"),
			"Import OBJ", "导入 OBJ");
	public static final String EXPORT_OBJ = TranslationRegistry.register(key("export_obj"),
			"Export OBJ", "导出 OBJ");
	public static final String EXPORT_DATA = TranslationRegistry.register(key("export_data"),
			"Export model data", "导出模型数据");
	public static final String EXPORT = TranslationRegistry.register(key("export"),
			"Export model effect", "导出模型特效");
	public static final String APPLY = TranslationRegistry.register(key("apply"),
			"Apply to emitter", "应用到发射器");
	public static final String CANCEL = TranslationRegistry.register(key("cancel"),
			"Cancel", "取消");
	public static final String UNDO = TranslationRegistry.register(key("undo"),
			"Undo", "撤销");
	public static final String REDO = TranslationRegistry.register(key("redo"),
			"Redo", "重做");
	public static final String VERTEX = TranslationRegistry.register(key("vertex"),
			"Vertex", "顶点");
	public static final String FACE = TranslationRegistry.register(key("face"),
			"Face", "面");
	public static final String PART = TranslationRegistry.register(key("part"),
			"Part", "部件");
	public static final String PRIMITIVE = TranslationRegistry.register(key("primitive"),
			"Primitive", "基本体");
	public static final String PLANE = TranslationRegistry.register(key("plane"),
			"Plane", "平面");
	public static final String CUBE = TranslationRegistry.register(key("cube"),
			"Cube", "立方体");
	public static final String CYLINDER = TranslationRegistry.register(key("cylinder"),
			"Cylinder", "圆柱");
	public static final String CONE = TranslationRegistry.register(key("cone"),
			"Cone", "圆锥");
	public static final String RING = TranslationRegistry.register(key("ring"),
			"Ring", "圆环");
	public static final String DELETE = TranslationRegistry.register(key("delete"),
			"Delete", "删除");
	public static final String DUPLICATE = TranslationRegistry.register(key("duplicate"),
			"Duplicate", "复制");
	public static final String EXTRUDE = TranslationRegistry.register(key("extrude"),
			"Extrude face", "挤出面");
	public static final String DISTANCE = TranslationRegistry.register(key("distance"),
			"Distance", "距离");
	public static final String PIVOT = TranslationRegistry.register(key("pivot"),
			"Pivot", "轴心");
	public static final String STRETCH = TranslationRegistry.register(key("stretch"),
			"Local stretch", "局部拉伸");
	public static final String STRETCH_START = TranslationRegistry.register(key("stretch_start"),
			"Stretch start", "拉伸起点");
	public static final String STRETCH_END = TranslationRegistry.register(key("stretch_end"),
			"Stretch end", "拉伸终点");
	public static final String PARENT = TranslationRegistry.register(key("parent"),
			"Parent", "父级");
	public static final String NONE = TranslationRegistry.register(key("none"),
			"None", "无");
	public static final String UV_PLANAR = TranslationRegistry.register(key("uv_planar"),
			"Planar projection", "平面投影");
	public static final String ERROR = TranslationRegistry.register(key("error"),
			"Operation failed: %s", "操作失败：%s");
	public static final String SAVED = TranslationRegistry.register(key("saved"),
			"Saved: %s", "已保存：%s");
	public static final String TOPOLOGY_LOCKED = TranslationRegistry.register(key("topology_locked"),
			"Topology is locked while shape keys exist", "存在形状键时拓扑已锁定");
	public static final String SELECT_ALL = TranslationRegistry.register(key("select_all"),
			"Select all", "全选");
	public static final String CLEAR_SELECTION = TranslationRegistry.register(key("clear_selection"),
			"Clear selection", "清空选择");

	public static final String EDGE = TranslationRegistry.register(key("edge"),
			"Edge", "边");
	public static final String AXIS_X = TranslationRegistry.register(key("axis_x"),
			"X", "X");
	public static final String AXIS_Y = TranslationRegistry.register(key("axis_y"),
			"Y", "Y");
	public static final String AXIS_Z = TranslationRegistry.register(key("axis_z"),
			"Z", "Z");
	public static final String FORWARD_AXIS = TranslationRegistry.register(key("forward_axis"),
			"Model forward axis", "模型前向轴");
	public static final String POSE = TranslationRegistry.register(key("pose"),
			"Animation pose", "动画姿态");
	public static final String ROTATION_DEGREES = TranslationRegistry.register(key("rotation_degrees"),
			"Rotation (degrees)", "旋转（度）");
	public static final String EDITOR_EDIT_PIVOT = TranslationRegistry.register(key("editor.edit_pivot"),
			"Edit pivot in viewport", "在视窗中编辑轴心");
	public static final String EDITOR_MOVE_PIVOT = TranslationRegistry.register(key("editor.move_pivot"),
			"Move part pivot", "移动部件轴心");
	public static final String EDITOR_PIVOT_HELP = TranslationRegistry.register(key("editor.pivot_help"),
			"Move the pivot with the viewport's translation handles while keeping the model in place. Rotation and scaling use this pivot; existing animations also use the new pivot.", "使用视窗的移动手柄调整轴心，模型保持原位。旋转和缩放围绕此轴心，已有动画也会使用新轴心。");
	public static final String START = TranslationRegistry.register(key("start"),
			"Start", "起点");
	public static final String END = TranslationRegistry.register(key("end"),
			"End", "终点");
	public static final String UV_TRANSLATE_U = TranslationRegistry.register(key("uv_translate_u"),
			"U translation (pixels)", "U 位移（像素）");
	public static final String UV_TRANSLATE_V = TranslationRegistry.register(key("uv_translate_v"),
			"V translation (pixels)", "V 位移（像素）");
	public static final String UV_SCALE_U = TranslationRegistry.register(key("uv_scale_u"),
			"U scale", "U 缩放");
	public static final String UV_SCALE_V = TranslationRegistry.register(key("uv_scale_v"),
			"V scale", "V 缩放");
	public static final String UV_ROTATION = TranslationRegistry.register(key("uv_rotation"),
			"UV rotation (degrees)", "UV 旋转（度）");
	public static final String UV_TEXTURE = TranslationRegistry.register(key("uv_texture"),
			"UV preview texture", "UV 预览纹理");
	public static final String UV_GRID = TranslationRegistry.register(key("uv_grid"),
			"Pixel grid", "像素网格");
	public static final String EDITOR_SHADING = TranslationRegistry.register(key("editor.shading"),
			"Model directional shading", "模型阴影");
	public static final String EDITOR_DOUBLE_SIDED = TranslationRegistry.register(key("editor.double_sided"),
			"Double-sided rendering", "双面渲染");
	public static final String MESH_INSIDE_OUT = TranslationRegistry.register(key("mesh.inside_out"),
			"Inside out", "负尺寸");
	public static final String EDITOR_INVALID_GEOMETRY = TranslationRegistry.register(key("editor.invalid_geometry"),
			"Invalid geometry; the last valid edit is retained.", "几何数据无效，已保留上一次有效修改。");
	public static final String EDITOR_RENAME_PART = TranslationRegistry.register(key("editor.rename_part"),
			"Rename part", "重命名部件");
	public static final String EDITOR_DELETE_SUBTREE = TranslationRegistry.register(key("editor.delete_subtree"),
			"Delete node and children", "删除节点及子级");
	public static final String EDITOR_DELETE_PROMOTE = TranslationRegistry.register(key("editor.delete_promote"),
			"Delete node and promote children", "删除节点并提升子级");
	public static final String EDITOR_HIERARCHY_INSPECT = TranslationRegistry.register(key("editor.hierarchy.inspect"),
			"Edit", "修改");
	public static final String EDITOR_HIERARCHY_ADD = TranslationRegistry.register(key("editor.hierarchy.add"),
			"Add", "添加");
	public static final String EDITOR_HIERARCHY_LEGACY_PART = TranslationRegistry.register(key("editor.hierarchy.legacy_part"),
			"Part %s", "部件 %s");
	public static final String EDITOR_HIERARCHY_MISSING_PART = TranslationRegistry.register(key("editor.hierarchy.missing_part"),
			"This part no longer exists.", "该部件不存在。");
	public static final String EDITOR_HIERARCHY_ANIMATED_PARENT = TranslationRegistry.register(key("editor.hierarchy.animated_parent"),
			"Related parts contain animation; children cannot be promoted losslessly.", "相关部件含动画，无法无损提升子级。");
	public static final String EDITOR_HIERARCHY_NON_TRS = TranslationRegistry.register(key("editor.hierarchy.non_trs"),
			"The transform contains shear or degenerate scaling; children cannot be promoted losslessly.", "变换含剪切或退化缩放，无法无损提升子级。");
	public static final String TIMELINE_ANIMATIONS = TranslationRegistry.register(key("timeline.animations"),
			"Animations", "动画列表");
	public static final String TIMELINE_TITLE = TranslationRegistry.register(key("timeline.title"),
			"Timeline", "时间轴");
	public static final String TIMELINE_TRACKS = TranslationRegistry.register(key("timeline.tracks"),
			"Tracks", "轨道");
	public static final String TIMELINE_NEW_NAME = TranslationRegistry.register(key("timeline.new_name"),
			"Animation %s", "动画 %s");
	public static final String TIMELINE_RENAME = TranslationRegistry.register(key("timeline.rename"),
			"Rename animation", "重命名动画");
	public static final String TIMELINE_REORDER = TranslationRegistry.register(key("timeline.reorder"),
			"Reorder animations", "调整动画顺序");
	public static final String EDITOR_SINGULAR_PART_TRANSFORM = TranslationRegistry.register(key("editor.singular_part_transform"),
			"Part transform is not invertible; geometry cannot be edited in the viewport.", "部件变换不可逆，无法在视窗中编辑几何。");
	public static final String TIMELINE_MOVE_KEY = TranslationRegistry.register(key("timeline.move_key"),
			"Move keyframe", "移动关键帧");
	public static final String TIMELINE_EMPTY = TranslationRegistry.register(key("timeline.empty"),
			"Create or select an animation on the left", "在左侧新建或选择动画");
	public static final String TIMELINE_PART_TRACK = TranslationRegistry.register(key("timeline.part_track"),
			"Part %s · TRS", "部件 %s · TRS");
	public static final String TIMELINE_BONE_TRACK = TranslationRegistry.register(key("timeline.bone_track"),
			"Bone %s · TRS", "骨骼 %s · TRS");
	public static final String TIMELINE_MORPH_TRACK = TranslationRegistry.register(key("timeline.morph_track"),
			"Shape %s · Weight", "形状 %s · 权重");
	public static final String TIMELINE_OPACITY_TRACK = TranslationRegistry.register(key("timeline.opacity_track"),
			"Model opacity", "整体透明度");
	public static final String TIMELINE_KEY_TIME = TranslationRegistry.register(key("timeline.key_time"),
			"Key time (ticks)", "关键帧时间（tick）");
	public static final String TIMELINE_KEY_VALUE = TranslationRegistry.register(key("timeline.key_value"),
			"Key value", "关键帧值");
	public static final String EDITOR_ADD_BONE = TranslationRegistry.register(key("editor.add_bone"),
			"Add bone", "添加骨骼");
	public static final String EDITOR_ADD_CLIP = TranslationRegistry.register(key("editor.add_clip"),
			"Add clip", "添加片段");
	public static final String EDITOR_ADD_MORPH = TranslationRegistry.register(key("editor.add_morph"),
			"Add shape key", "添加形状键");
	public static final String EDITOR_ASSIGN_WEIGHT = TranslationRegistry.register(key("editor.assign_weight"),
			"Assign weight to selected vertices", "设置选中顶点权重");
	public static final String EDITOR_BOX_SELECT = TranslationRegistry.register(key("editor.box_select"),
			"Box selection", "框选");
	public static final String EDITOR_DELETE_BONE = TranslationRegistry.register(key("editor.delete_bone"),
			"Delete bone", "删除骨骼");
	public static final String EDITOR_DELETE_CLIP = TranslationRegistry.register(key("editor.delete_clip"),
			"Delete clip", "删除片段");
	public static final String EDITOR_DELETE_KEY = TranslationRegistry.register(key("editor.delete_key"),
			"Delete keyframe", "删除关键帧");
	public static final String EDITOR_DELETE_MORPH = TranslationRegistry.register(key("editor.delete_morph"),
			"Delete shape key", "删除形状键");
	public static final String EDITOR_KEY_AT = TranslationRegistry.register(key("editor.key_at"),
			"Key at %s ticks", "关键帧：%s tick");
	public static final String EDITOR_LOAD = TranslationRegistry.register(key("editor.load"),
			"Load model", "载入模型");
	public static final String EDITOR_MORPH_DELTA = TranslationRegistry.register(key("editor.morph_delta"),
			"Shape key vertex delta", "形状键顶点偏移");
	public static final String EDITOR_OVERWRITE = TranslationRegistry.register(key("editor.overwrite"),
			"Overwrite existing file?", "覆盖现有文件？");
	public static final String EDITOR_RECORD_KEY = TranslationRegistry.register(key("editor.record_key"),
			"Record keyframe", "记录关键帧");
	public static final String EDITOR_SELECTION_STATUS = TranslationRegistry.register(key("editor.selection_status"),
			"Model: %s vertices, %s faces; selected: %s vertices", "模型：%s 个顶点、%s 个面；已选：%s 个顶点");
	public static final String EDITOR_MODEL_INFORMATION = TranslationRegistry.register(key("editor.model_information"),
			"Model information", "模型信息");
	public static final String EDITOR_SELECTED_COUNT = TranslationRegistry.register(key("editor.selected_count"),
			"Selected elements", "已选元素");
	public static final String EDITOR_TRANSFORM = TranslationRegistry.register(key("editor.transform"),
			"Transform selection", "变换选中部分");
	public static final String EDITOR_UV_DRAG = TranslationRegistry.register(key("editor.uv_drag"),
			"Drag UV", "拖动 UV");
	public static final String EDITOR_UV_HELP = TranslationRegistry.register(key("editor.uv_help"),
			"Drag corners; drag empty space or use the box-select action, Shift adds to selection. Shift/Ctrl/Shift+Ctrl snap by 1/0.5/0.1 pixels within the texture.", "拖动角点；空白处拖动或使用框选动作框选，Shift 追加选择。Shift / Ctrl / 同时按下按 1 / 0.5 / 0.1 像素吸附，限制在纹理内。");
	public static final String EDITOR_UV_PROJECT_AXIS = TranslationRegistry.register(key("editor.uv_project_axis"),
			"Planar projection (%s axis)", "平面投影（%s 轴）");
	public static final String EDITOR_UV_TRANSFORM = TranslationRegistry.register(key("editor.uv_transform"),
			"Transform selected UV", "变换选中 UV");

	public static final String TIMELINE_MODEL_ANIMATION = TranslationRegistry.register(key("timeline.model_animation"),
			"Model animation", "模型动画");
	public static final String TIMELINE_MODEL_TARGET = TranslationRegistry.register(key("timeline.model_target"),
			"Model emitter", "模型发射器");
	public static final String TIMELINE_COMPLETION = TranslationRegistry.register(key("timeline.completion"),
			"After clip", "片段结束后");
	public static final String TIMELINE_COMPLETION_HOLD = TranslationRegistry.register(key("timeline.completion.hold"),
			"Hold last sample", "保持末帧");
	public static final String TIMELINE_COMPLETION_RESTORE = TranslationRegistry.register(key("timeline.completion.restore"),
			"Restore default animation", "恢复默认动画");
	public static final String DEFAULT_PLAYBACK = TranslationRegistry.register(key("default_playback"),
			"Default playback", "默认播放");
	public static final String DEFAULT_CLIP = TranslationRegistry.register(key("default_clip"),
			"Default animation clip", "默认动画片段");
	public static final String CLIP_NONE = TranslationRegistry.register(key("clip_none"),
			"No animation", "不播放动画");
	public static final String INVALID_DEFAULT_CLIP = TranslationRegistry.register(key("invalid_default_clip"),
			"The default animation clip is missing. Select it again.", "默认动画片段已不存在，请重新选择。");
	public static final String INVALID_PLAYBACK = TranslationRegistry.register(key("invalid_playback"),
			"Invalid default playback settings.", "默认播放设置无效。");
	public static final String CLIPS = TranslationRegistry.register(key("clips"),
			"Animation clips", "动画片段");
	public static final String TIME = TranslationRegistry.register(key("time"),
			"Time (ticks)", "时间（tick）");
	public static final String PLAY = TranslationRegistry.register(key("play"),
			"Play", "播放");
	public static final String STOP = TranslationRegistry.register(key("stop"),
			"Stop", "停止");
	public static final String REMOVE_KEYFRAME = TranslationRegistry.register(key("remove_keyframe"),
			"Remove keyframe", "删除关键帧");
	public static final String BONE = TranslationRegistry.register(key("bone"),
			"Bone", "骨骼");
	public static final String ADD_BONE = TranslationRegistry.register(key("add_bone"),
			"Add bone", "添加骨骼");
	public static final String BIND = TranslationRegistry.register(key("bind"),
			"Bind selected vertices", "绑定选中顶点");
	public static final String WEIGHT = TranslationRegistry.register(key("weight"),
			"Weight", "权重");
	public static final String ADD_MORPH = TranslationRegistry.register(key("add_morph"),
			"Add shape key", "添加形状键");
	public static final String EDIT_MORPH = TranslationRegistry.register(key("edit_morph"),
			"Edit shape key", "编辑形状键");
	public static final String BASE_SHAPE = TranslationRegistry.register(key("base_shape"),
			"Base shape", "基础形状");
	public static final String PART_KEY = TranslationRegistry.register(key("part_key"),
			"Keyframe part transform", "记录部件变换关键帧");
	public static final String BONE_KEY = TranslationRegistry.register(key("bone_key"),
			"Keyframe bone transform", "记录骨骼变换关键帧");
	public static final String MORPH_KEY = TranslationRegistry.register(key("morph_key"),
			"Keyframe shape key weight", "记录形状键权重关键帧");
	public static final String ANIMATION_LOOP = TranslationRegistry.register(key("animation_loop"),
			"Loop animation", "循环动画");
	public static final String BIND_POSE = TranslationRegistry.register(key("bind_pose"),
			"Bind pose", "绑定姿态");

	public static String key(String name) {
		return PREFIX + name;
	}
}
