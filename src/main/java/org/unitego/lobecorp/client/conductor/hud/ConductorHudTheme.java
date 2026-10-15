package org.unitego.lobecorp.client.conductor.hud;

import java.util.Map;
import java.util.UUID;

/// 指挥家 HUD 的布局、颜色、文本格式和图标规则。
public interface ConductorHudTheme {
	/// 平均生命值显示使用的合成 UUID，不对应真实实体。
	UUID AVERAGE_HEALTH_ID = new UUID(Long.MIN_VALUE, Long.MIN_VALUE);
	/// HUD 与屏幕边缘的间距，单位为 GUI 像素。
	int EDGE_MARGIN = 4;
	/// HUD 区域之间的间距，单位为 GUI 像素。
	int SECTION_GAP = 4;
	/// HUD 区域内边距，单位为 GUI 像素。
	int SECTION_PADDING = 4;
	/// 底部 HUD 占屏幕高度的百分比。
	int HUD_HEIGHT_PERCENT = 30;
	/// 底部 HUD 的功能区域数量。
	int BOTTOM_SECTION_COUNT = 3;
	/// 底部 HUD 的最小高度，单位为 GUI 像素。
	int HUD_MIN_HEIGHT = 100;
	/// 底部 HUD 的最大高度，单位为 GUI 像素。
	int HUD_MAX_HEIGHT = 300;
	/// 成员列表的最大宽度，单位为 GUI 像素。
	int MAX_ROSTER_WIDTH = 152;
	/// 技能列表的最大宽度，单位为 GUI 像素。
	int MAX_SKILL_WIDTH = 172;
	/// 折叠把手宽度，单位为 GUI 像素。
	int HANDLE_WIDTH = 8;
	/// 折叠把手高度，单位为 GUI 像素。
	int HANDLE_HEIGHT = 28;
	/// 折叠把手相对区域高度的顶部偏移百分比。
	int HANDLE_TOP_PERCENT = 40;
	/// 区域标题高度，单位为 GUI 像素。
	int HEADER_HEIGHT = 18;
	/// 搜索框高度，单位为 GUI 像素。
	int SEARCH_HEIGHT = 18;
	/// 成员卡片肖像边长，单位为 GUI 像素。
	int CARD_SIZE = 24;
	/// 相邻成员卡片间距，单位为 GUI 像素。
	int CARD_GAP = 1;
	/// 已选成员栏内边距，单位为 GUI 像素。
	int SELECTED_PADDING = 1;
	/// 队伍下拉菜单最多同时显示的行数。
	int TEAM_DROPDOWN_ROWS = 6;
	/// 技能卡片高度，单位为 GUI 像素。
	int SKILL_SIZE = 16;
	/// 技能图标边长，单位为 GUI 像素。
	int SKILL_ICON_SIZE = 12;
	/// 命令按钮边长，单位为 GUI 像素。
	int BUTTON_SIZE = 18;
	/// 命令按钮间距，单位为 GUI 像素。
	int BUTTON_GAP = 2;
	/// 命令按钮组的总宽度，单位为 GUI 像素。
	int COMMAND_WIDTH = BUTTON_SIZE * 5 + BUTTON_GAP * 4;
	/// 关注成员肖像边长，单位为 GUI 像素。
	int FOCUS_SIZE = 28;
	/// 成员信息区域宽度，单位为 GUI 像素。
	int INFO_WIDTH = 88;
	/// 生命值信息区域宽度，单位为 GUI 像素。
	int INFO_HEALTH_WIDTH = 24;
	/// HUD 文本单行高度，单位为 GUI 像素。
	int TEXT_LINE_HEIGHT = 9;
	/// 搜索图标边长，单位为 GUI 像素。
	int SEARCH_ICON_SIZE = 8;
	/// HUD 边框厚度，单位为 GUI 像素。
	int BORDER = 2;
	/// 按钮立体边缘深度，单位为 GUI 像素。
	int BUTTON_DEPTH = 3;
	/// 面板立体边缘深度，单位为 GUI 像素。
	int PANEL_DEPTH = 4;
	/// 展开面板之间的安全间距，单位为 GUI 像素。
	int PANEL_SAFE_GAP = 16;
	/// 滚动条的横向或纵向厚度，单位为 GUI 像素。
	int SCROLLBAR_WIDTH = 6;
	/// 虚拟列表在可见区域外额外缓存的行数。
	int BUFFER_ROWS = 1;
	/// 显示完整信息所需的最小屏幕宽度，单位为 GUI 像素。
	int FULL_INFO_MIN_WIDTH = 440;
	/// 显示成员信息所需的最小屏幕宽度，单位为 GUI 像素。
	int INFO_MIN_WIDTH = 340;
	/// 搜索文本允许的最大字符数。
	int SEARCH_LIMIT = 64;
	/// 滚轮每步移动的成员列表距离，单位为 GUI 像素。
	int SCROLL_STEP = CARD_SIZE + CARD_GAP;
	/// 按住 Shift 时的滚动距离倍率。
	int SHIFT_SCROLL_MULTIPLIER = 3;
	/// 切换为静态肖像的可见卡片数量阈值。
	int PORTRAIT_LIMIT = 30;
	/// 悬浮提示的最大宽度，单位为 GUI 像素。
	int TOOLTIP_WIDTH = 160;
	/// 显示悬浮提示前的等待时间，单位为毫秒。
	int TOOLTIP_DELAY_MS = 250;
	/// 技能双击判定间隔，单位为毫秒。
	int SKILL_DOUBLE_CLICK_MS = 250;
	/// 技能快捷键可覆盖的技能数量。
	int SKILL_HOTKEY_COUNT = 10;
	/// 应用搜索文本前的防抖等待时间，单位为毫秒。
	int SEARCH_DELAY_MS = 100;
	/// HUD 动画单帧间隔，单位为毫秒。
	int ANIMATION_FRAME_MS = 60;
	/// 选择标记动态效果的阶段间隔，单位为毫秒。
	int SELECTION_PHASE_MS = 500;
	/// 技能就绪闪烁的动画帧数。
	int FLASH_FRAMES = 3;
	/// 悬浮提示相对光标的偏移，单位为 GUI 像素。
	int HOVER_OFFSET = 12;
	/// 悬浮提示内边距，单位为 GUI 像素。
	int HOVER_PADDING = 4;
	/// 悬浮提示行间距，单位为 GUI 像素。
	int HOVER_LINE_GAP = 2;
	/// 状态效果图标边长，单位为 GUI 像素。
	int EFFECT_ICON_SIZE = 18;
	/// 状态效果单元格宽度，单位为 GUI 像素。
	int EFFECT_CELL_WIDTH = 44;
	/// 关注成员信息的区域数量。
	int FOCUS_INFO_SECTIONS = 2;
	/// 关注成员状态效果的列数。
	int FOCUS_EFFECT_COLUMNS = 3;
	/// 状态效果剩余时间条高度，单位为 GUI 像素。
	int EFFECT_BAR_HEIGHT = 3;
	/// 状态效果单元格总高度，单位为 GUI 像素。
	int EFFECT_CELL_HEIGHT = EFFECT_ICON_SIZE + HOVER_LINE_GAP + EFFECT_BAR_HEIGHT + HOVER_PADDING;
	/// 成员生命值条高度，单位为 GUI 像素。
	int HEALTH_HEIGHT = 3;
	/// 队伍成员卡片显示的文本行数。
	int ROSTER_TEXT_LINES = 3;
	/// 队伍成员卡片的完整行高，单位为 GUI 像素。
	int ROSTER_ROW_HEIGHT = Math.max(CARD_SIZE, TEXT_LINE_HEIGHT * ROSTER_TEXT_LINES + SECTION_PADDING * 2) + HEALTH_HEIGHT + BORDER * 2;
	/// 成员列表定期刷新间隔，单位为游戏 tick。
	int ROSTER_REFRESH_TICKS = 10;
	/// 进入低生命值颜色的生命值比例阈值。
	float HEALTH_LOW = 0.33F;
	/// 进入高生命值颜色的生命值比例阈值。
	float HEALTH_HIGH = 0.66F;
	/// 成员肖像的默认缩放倍率。
	float PORTRAIT_SCALE = 0.8F;
	/// 成员肖像在模型坐标中的纵向偏移。
	float PORTRAIT_Y_OFFSET = 0.0625F;
	/// 悬浮成员肖像时的缩放倍率。
	float PORTRAIT_HOVER_SCALE = 1.1F;
	/// 完整旋转一周的角度，单位为度。
	float FULL_ROTATION = 360.0F;
	/// 肖像旋转速度，单位为度每游戏 tick。
	float PORTRAIT_ROTATION_PER_TICK = 1.0F;
	/// 最深背景层的 ARGB 颜色。
	int BG_0 = 0xFF101214;
	/// 基础面板背景的 ARGB 颜色。
	int BG_1 = 0xFF17191C;
	/// 次级控件背景的 ARGB 颜色。
	int BG_2 = 0xFF1F2226;
	/// 高亮背景层的 ARGB 颜色。
	int BG_3 = 0xFF2A2E33;
	/// 外侧边缘的 ARGB 颜色。
	int LINE_OUTER = 0xFF0A0A0A;
	/// 内侧边缘的 ARGB 颜色。
	int LINE_INNER = 0xFF3A3F45;
	/// 立体边缘亮面的 ARGB 颜色。
	int EDGE_LIGHT = 0xFF454B52;
	/// 启用状态的绿色 ARGB 颜色。
	int ORE_GREEN = 0xFF3C8527;
	/// 启用状态高亮的绿色 ARGB 颜色。
	int ORE_GREEN_HI = 0xFF54A83C;
	/// 强调状态的金色 ARGB 颜色。
	int GOLD = 0xFFFCEE4B;
	/// 主题强调的紫色 ARGB 颜色。
	int AMETHYST = 0xFF8B5CF6;
	/// 危险提示的 ARGB 颜色。
	int DANGER = 0xFFFF3B3B;
	/// 信息提示的 ARGB 颜色。
	int INFO = 0xFF3B82F6;
	/// HUD 正文的 ARGB 颜色。
	int TEXT_COLOR = 0xFFFFFFFF;
	/// 次要文字的 ARGB 颜色。
	int MUTED_COLOR = 0xFFAAAAAA;
	/// 禁用文字的 ARGB 颜色。
	int DISABLED_COLOR = 0xFF6E6E6E;
	/// 悬浮提示背景的 ARGB 颜色。
	int TOOLTIP_BACKGROUND = 0xF3101214;
	/// 悬浮提示边框的 ARGB 颜色。
	int TOOLTIP_BORDER = AMETHYST;
	/// 技能冷却遮罩的 ARGB 颜色。
	int SKILL_COOLDOWN_MASK = 0xA6000000;
	/// 禁用控件遮罩的 ARGB 颜色。
	int DISABLED_MASK = 0x99000000;
	/// 高生命值条的绿色 ARGB 颜色。
	int HP_GREEN = 0xFF3FBF3F;
	/// 高生命值条亮面的绿色 ARGB 颜色。
	int HP_GREEN_HI = 0xFF6FDF6F;
	/// 中等生命值条的黄色 ARGB 颜色。
	int HP_YELLOW = 0xFFDDDD33;
	/// 中等生命值条亮面的黄色 ARGB 颜色。
	int HP_YELLOW_HI = 0xFFF2F26A;
	/// 低生命值条的红色 ARGB 颜色。
	int HP_RED = 0xFFCC3333;
	/// 低生命值条亮面的红色 ARGB 颜色。
	int HP_RED_HI = 0xFFE85C5C;
	/// 清除成员搜索按钮的节点标识。
	String ROSTER_CLEAR = "roster-clear";
	/// 清除技能搜索按钮的节点标识。
	String SKILL_CLEAR = "skill-clear";
	/// 技能搜索输入框的节点标识。
	String SKILL_SEARCH = "skill-search";
	/// 技能搜索文本标签的节点标识。
	String SKILL_QUERY = "skill-query";
	/// 技能栏标题的节点标识。
	String SKILL_TITLE = "skill-title";
	/// 关注成员信息区域的节点标识。
	String FOCUS_INFO = "focus-info";
	/// 分散编队选项的节点标识。
	String FORMATION_SCATTERED = "formation-scattered";
	/// 常规编队选项的节点标识。
	String FORMATION_REGULAR = "formation-regular";
	/// 均匀编队选项的节点标识。
	String FORMATION_UNIFORM = "formation-uniform";
	/// 减弱动态效果选项的节点标识。
	String REDUCE_MOTION = "reduce-motion";
	/// 清除操作像素图标的标识。
	String GLYPH_CLEAR = "clear";
	/// 向左操作像素图标的标识。
	String GLYPH_LEFT = "left";
	/// 向右操作像素图标的标识。
	String GLYPH_RIGHT = "right";
	/// 搜索操作像素图标的标识。
	String GLYPH_SEARCH = "search";
	/// 搜索光标闪烁阶段间隔，单位为毫秒。
	int CURSOR_PHASE_MS = 500;
	/// 比例换算为百分比时的倍率。
	int PERCENT = 100;
	/// 文本截断时显示的省略符号。
	String ELLIPSIS = "...";
	/// HUD 小数显示格式，保留一位小数。
	String DECIMAL_FORMAT = "%.1f";
	/// 数量显示格式，在数值前添加乘号。
	String COUNT_FORMAT = "×%s";
	/// 未知数值的显示符号。
	String UNKNOWN_MARK = "?";
	/// 像素图标每个网格单元的边长，单位为 GUI 像素。
	int ICON_PIXEL = 2;
	/// 像素图标每行或每列的网格数量。
	int ICON_GRID = 5;
	/// 命令按钮与固定像素点阵图标的映射。
	Map<String, String> ICONS = Map.ofEntries(
			Map.entry("move", "0010001110111110111000100"),
			Map.entry("attack", "0001100110011001110010100"),
			Map.entry("stop", "1111111111111111111111111"),
			Map.entry("attack-mode", "1000111011101011101110001"),
			Map.entry("behavior-mode", "1111110001100010101000100"),
			Map.entry("manage", "0010011111010101111100100"),
			Map.entry("assign", "0010000100111110010000100"),
			Map.entry("release", "0000000000111110000000000"),
			Map.entry(GLYPH_SEARCH, "0111010001100010111000011"),
			Map.entry(GLYPH_CLEAR, "1000101010001000101010001"),
			Map.entry(GLYPH_LEFT, "0010001000100000100000100"),
			Map.entry(GLYPH_RIGHT, "0010000010000010001000100"));

}
