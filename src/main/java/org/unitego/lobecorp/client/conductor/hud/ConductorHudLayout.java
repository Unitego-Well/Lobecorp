package org.unitego.lobecorp.client.conductor.hud;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.ability.IConductorAbility;
import org.unitego.lobecorp.conductor.data.ConductorData;

import java.util.List;

import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.*;

/// 指挥家 HUD 的 UI 树与布局；自身即根节点，不创建 ModularUI。
public class ConductorHudLayout extends UIElement {
	public static final String MANAGE = "manage";
	public static final String BOTTOM = "bottom";
	public static final String MOVE = "move";
	public static final String ATTACK = "attack";
	public static final String STOP = "stop";
	public static final String ATTACK_MODE = "attack-mode";
	public static final String BEHAVIOR_MODE = "behavior-mode";
	public static final String ACTIVITY_MODE = "activity-mode";
	public static final String TEAM_SELECT = "team-select";
	public static final String ASSIGN = "assign";
	public static final String RELEASE = "release";
	public static final String ROSTER_SEARCH = "roster-search";
	public static final String ROSTER_CLEAR = "roster-clear";
	public static final String ROSTER_QUERY = "roster-query";
	public static final String SKILL_PREFIX = "skill-";
	public static final String SELECTED_COUNT = "selected-count";
	public static final String SKILL_LIST = "skill-list";
	public static final String SKILL_RAIL = "skill-rail";
	public static final String SKILL_SEARCH = "skill-search";
	public static final String SKILL_CLEAR = "skill-clear";
	public static final String SKILL_QUERY = "skill-query";
	public static final String SKILL_TITLE = "skill-title";
	public static final String ROSTER_LIST = "roster-list";
	public static final String ROSTER_PANEL = "roster-panel";
	public static final String ROSTER_TOGGLE = "roster-toggle";
	public static final String SELECTED_LIST = "selected-list";
	public static final String SELECTED_PREFIX = "selected-unit-";
	public static final String SKILL_TOGGLE = "skill-toggle";
	public static final String FOCUS_PORTRAIT = "focus-portrait";
	public static final String FOCUS_DETAILS = "focus-details";
	public static final String FOCUS_EFFECTS = "focus-effects";
	public static final String UNIT_PREFIX = "unit-";
	public static final List<ConductorData.CombatBehavior> BEHAVIOR_OPTIONS = List.of(
			ConductorData.CombatBehavior.ACTIVE, ConductorData.CombatBehavior.PASSIVE, ConductorData.CombatBehavior.NEUTRAL);
	public static final List<ConductorData.ControlMode> ACTIVITY_OPTIONS = List.of(
			ConductorData.ControlMode.COMMAND, ConductorData.ControlMode.SOFT, ConductorData.ControlMode.STANDBY);
	public static final List<ConductorData.AttackState> ATTACK_OPTIONS = List.of(
			ConductorData.AttackState.AUTO, ConductorData.AttackState.MANUAL);

	private final ConductorHudModel model;
	private boolean panelLayoutInitialized;
	private int previousScreenWidth = -1;
	private int previousScreenHeight = -1;
	private boolean rosterHidden;
	private boolean skillsHidden;
	private int focusSize = FOCUS_SIZE;
	private int infoWidth = INFO_WIDTH;

	public ConductorHudLayout(ConductorHudModel model) {
		this.model = model;
		layout(layout -> layout.widthPercent(100).heightPercent(100));
		buildLayout();
		configureIcons();
	}

	public boolean rosterHidden() {
		return rosterHidden;
	}

	public boolean skillsHidden() {
		return skillsHidden;
	}

	public int focusSize() {
		return focusSize;
	}

	public int infoWidth() {
		return infoWidth;
	}

	public UIElement element(String id) {
		return selectId(id, UIElement.class).findFirst().orElseThrow();
	}

	public Button button(String id) {
		return selectId(id, Button.class).findFirst().orElseThrow();
	}

	public ScrollerView scroller(String id) {
		return selectId(id, ScrollerView.class).findFirst().orElseThrow();
	}

	private void configureIcons() {
		configureIcon(MOVE, ConductorTexts.MOVE);
		configureIcon(ATTACK, ConductorTexts.ATTACK);
		configureIcon(STOP, ConductorTexts.STOP);
		configureIcon(MANAGE, ConductorTexts.SETTINGS);
		configureIcon(ASSIGN, ConductorTexts.ASSIGN);
		configureIcon(RELEASE, ConductorTexts.RELEASE);
		configureIcon(ATTACK_MODE, ConductorTexts.ATTACK_MODE);
		configureIcon(BEHAVIOR_MODE, ConductorTexts.BEHAVIOR_MODE);
		configureIcon(ACTIVITY_MODE, ConductorTexts.ACTIVITY_MODE);
		configureIcon(FORMATION_SCATTERED, ConductorTexts.FORMATION_SCATTERED_DESCRIPTION);
		configureIcon(FORMATION_REGULAR, ConductorTexts.FORMATION_REGULAR_DESCRIPTION);
		configureIcon(FORMATION_UNIFORM, ConductorTexts.FORMATION_UNIFORM_DESCRIPTION);
		configureIcon(ROSTER_CLEAR, ConductorTexts.CLEAR_SEARCH);
		configureIcon(SKILL_CLEAR, ConductorTexts.CLEAR_SEARCH);
		configureIcon(ROSTER_TOGGLE, ConductorTexts.TEAM);
		configureIcon(SKILL_TOGGLE, ConductorTexts.SKILLS);
	}

	private void configureIcon(String id, String key) {
		Button button = button(id);
		button.noText();
		model.hints().put(id, Component.translatable(key));
	}

	private void buildLayout() {
		UIElement roster = panel(ROSTER_PANEL);
		roster.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
				.left(EDGE_MARGIN).top(EDGE_MARGIN).bottom(HUD_MIN_HEIGHT + SECTION_GAP)
				.width(MAX_ROSTER_WIDTH).paddingAll(SECTION_PADDING)
				.flexDirection(FlexDirection.COLUMN).gapAll(SECTION_GAP));
		var heading = new UIElement();
		heading.layout(layout -> layout.widthPercent(100).height(HEADER_HEIGHT)
				.flexDirection(FlexDirection.ROW).gapAll(BUTTON_GAP));
		Button team = button(TEAM_SELECT, ConductorTexts.TEAM, 0, HEADER_HEIGHT);
		team.layout(layout -> layout.flexGrow(1).minWidth(0));
		heading.addChildren(team, button(ASSIGN, ConductorTexts.ASSIGN, BUTTON_SIZE, HEADER_HEIGHT),
				button(RELEASE, ConductorTexts.RELEASE, BUTTON_SIZE, HEADER_HEIGHT),
				button(MANAGE, ConductorTexts.SETTINGS, BUTTON_SIZE, HEADER_HEIGHT));
		ScrollerView rosterList = flatScroller(ROSTER_LIST);
		rosterList.layout(layout -> layout.height(0).minHeight(0).flexGrow(1));
		roster.addChildren(heading, label(SELECTED_COUNT),
				searchRow(ROSTER_SEARCH, ROSTER_QUERY, ROSTER_CLEAR), rosterList);
		UIElement rail = panel(SKILL_RAIL);
		rail.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
				.right(EDGE_MARGIN).top(EDGE_MARGIN).bottom(HUD_MIN_HEIGHT + SECTION_GAP)
				.width(MAX_SKILL_WIDTH).paddingAll(SECTION_PADDING)
				.flexDirection(FlexDirection.COLUMN).gapAll(SECTION_GAP));
		Label skillTitle = label(SKILL_TITLE);
		skillTitle.setText(Component.translatable(ConductorTexts.SKILLS));
		ScrollerView skills = flatScroller(SKILL_LIST);
		skills.layout(layout -> layout.height(0).minHeight(0).flexGrow(1));
		rail.addChildren(skillTitle, searchRow(SKILL_SEARCH, SKILL_QUERY, SKILL_CLEAR), skills);
		UIElement bottom = panel(BOTTOM);
		bottom.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
				.left(EDGE_MARGIN).right(EDGE_MARGIN).bottom(0).height(HUD_MIN_HEIGHT)
				.paddingAll(SECTION_PADDING).flexDirection(FlexDirection.ROW).gapAll(SECTION_GAP));
		var current = new UIElement();
		current.layout(layout -> layout.width(0).minWidth(0).flexGrow(1)
				.flexDirection(FlexDirection.ROW).gapAll(SECTION_GAP));
		UIElement portrait = panel(FOCUS_PORTRAIT);
		portrait.layout(layout -> layout.width(FOCUS_SIZE).height(FOCUS_SIZE).flexShrink(0));
		var info = new UIElement();
		info.setId(FOCUS_INFO);
		info.layout(layout -> layout.width(INFO_WIDTH).height(FOCUS_SIZE).flexShrink(0).flexDirection(FlexDirection.COLUMN));

		for (String id : List.of(FOCUS_DETAILS, FOCUS_EFFECTS)) {
			ScrollerView section = flatScroller(id);
			section.layout(layout -> layout.heightPercent((float) PERCENT / FOCUS_INFO_SECTIONS).minHeight(0).flexShrink(0));
			section.getScrollerViewStyle().mode(ScrollerMode.BOTH);
			section.viewContainer.layout(layout -> layout.flexShrink(0));
			section.horizontalScroller.layout(layout -> layout.height(SCROLLBAR_WIDTH));
			info.addChild(section);
		}

		ScrollerView selected = flatScroller(SELECTED_LIST);
		selected.layout(layout -> layout.width(0).minWidth(0).minHeight(0).heightPercent(100).flexGrow(1));
		selected.viewPort.layout(layout -> layout.paddingAll(SELECTED_PADDING));
		var commands = new UIElement();
		commands.layout(layout -> layout.width(0).minWidth(0).flexGrow(1).heightPercent(100)
				.flexDirection(FlexDirection.COLUMN).gapAll(BUTTON_GAP));
		var commandButtons = new UIElement();
		commandButtons.layout(layout -> layout.widthPercent(100).minWidth(0).height(FOCUS_SIZE)
				.flexDirection(FlexDirection.ROW).gapAll(BUTTON_GAP).flexShrink(0).paddingTop(BUTTON_GAP));
		commandButtons.addChildren(button(MOVE, ConductorTexts.MOVE, BUTTON_SIZE, BUTTON_SIZE),
				button(ATTACK, ConductorTexts.ATTACK, BUTTON_SIZE, BUTTON_SIZE),
				button(STOP, ConductorTexts.STOP, BUTTON_SIZE, BUTTON_SIZE));

		for (String id : List.of(ATTACK_MODE, BEHAVIOR_MODE, ACTIVITY_MODE)) {
			Button selector = button(id, ATTACK_MODE.equals(id) ? ConductorTexts.ATTACK_MODE
					: ACTIVITY_MODE.equals(id) ? ConductorTexts.ACTIVITY_MODE : ConductorTexts.BEHAVIOR_MODE, 0, BUTTON_SIZE);
			selector.layout(layout -> layout.width(0).minWidth(0).flexGrow(1));
			commandButtons.addChild(selector);
		}

		commands.addChild(commandButtons);

		for (String id : List.of(FORMATION_SCATTERED, FORMATION_REGULAR, FORMATION_UNIFORM)) {
			Button option = button(id, formationKey(id), 0, BUTTON_SIZE);
			option.layout(layout -> layout.widthPercent(100).flexShrink(0));
			commands.addChild(option);
		}

		current.addChildren(portrait, info);
		bottom.addChildren(current, selected, commands);
		Button left = button(ROSTER_TOGGLE, ConductorTexts.TEAM, HANDLE_WIDTH, HANDLE_HEIGHT);
		left.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(MAX_ROSTER_WIDTH + EDGE_MARGIN)
				.topPercent(HANDLE_TOP_PERCENT));
		Button right = button(SKILL_TOGGLE, ConductorTexts.SKILLS, HANDLE_WIDTH, HANDLE_HEIGHT);
		right.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).right(MAX_SKILL_WIDTH + EDGE_MARGIN)
				.topPercent(HANDLE_TOP_PERCENT));
		addChildren(bottom, roster, rail, left, right);
	}

	public static ConductorData.FormationMode formationMode(String id) {
		return switch (id) {
			case FORMATION_SCATTERED -> ConductorData.FormationMode.SCATTERED;
			case FORMATION_UNIFORM -> ConductorData.FormationMode.UNIFORM;
			default -> ConductorData.FormationMode.FORMATION;
		};
	}

	public static String formationKey(String id) {
		return switch (id) {
			case FORMATION_SCATTERED -> ConductorTexts.FORMATION_SCATTERED;
			case FORMATION_UNIFORM -> ConductorTexts.FORMATION_UNIFORM;
			default -> ConductorTexts.FORMATION_REGULAR;
		};
	}

	private UIElement searchRow(String id, String queryId, String clearId) {
		UIElement row = panel(id);
		row.layout(layout -> layout.widthPercent(100).height(SEARCH_HEIGHT).flexShrink(0)
				.flexDirection(FlexDirection.ROW).paddingAll(BORDER).gapAll(BUTTON_GAP));
		Label query = label(queryId);
		query.layout(layout -> layout.width(0).minWidth(0).flexGrow(1));
		row.addChildren(query, button(clearId, ConductorTexts.CLEAR_SEARCH, SEARCH_ICON_SIZE, SEARCH_ICON_SIZE));
		return row;
	}

	private static ScrollerView flatScroller(String id) {
		var scroller = new ScrollerView();
		scroller.setId(id);
		scroller.layout(layout -> layout.widthPercent(100).heightPercent(100));
		scroller.viewPort.layout(layout -> layout.paddingAll(0).minHeight(0).minWidth(0));
		scroller.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
		scroller.getScrollerViewStyle().adaptiveWidth(false).adaptiveHeight(false)
				.verticalScrollDisplay(ScrollDisplay.AUTO)
				.horizontalScrollDisplay(ScrollDisplay.AUTO);
		scroller.verticalScroller.headButton.setDisplay(false);
		scroller.verticalScroller.tailButton.setDisplay(false);
		scroller.horizontalScroller.headButton.setDisplay(false);
		scroller.horizontalScroller.tailButton.setDisplay(false);
		scroller.viewContainer.layout(layout -> layout.gapAll(0));
		scroller.verticalScroller.layout(layout -> layout.width(SCROLLBAR_WIDTH));
		scroller.horizontalScroller.layout(layout -> layout.height(BORDER));
		return scroller;
	}

	private static UIElement panel(String id) {
		var element = new UIElement();
		element.setId(id);
		element.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
		return element;
	}

	private static Label label(String id) {
		var label = new Label();
		label.setId(id);
		label.layout(layout -> layout.height(TEXT_LINE_HEIGHT).paddingAll(0).flexShrink(0));
		return label;
	}

	private Button button(String id, String key, int width, int height) {
		var button = new Button();
		button.setId(id);
		button.noText();
		model.hints().put(id, Component.translatable(key));
		button.layout(layout -> layout.width(width).height(height).paddingAll(0).flexShrink(0));
		button.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY)
				.hoverTexture(IGuiTexture.EMPTY)
				.pressedTexture(IGuiTexture.EMPTY));
		return button;
	}

	public static Button portraitButton(String id, int width, int height) {
		var button = new Button();
		button.setId(id);
		button.noText();
		button.layout(layout -> {
			layout.height(height).paddingAll(0).flexShrink(0);

			if (width > 0) {
				layout.width(width);
			}
		});
		button.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY)
				.hoverTexture(IGuiTexture.EMPTY)
				.pressedTexture(IGuiTexture.EMPTY));
		return button;
	}

	public static Component abilityName(IConductorAbility ability) {
		return Component.translatableWithFallback(ability instanceof EntitySkillConductorAbility
				? ConductorTexts.skillName(ability.id()) : ConductorTexts.abilityName(ability.id()), ability.id().getPath());
	}

	public static String combatBehaviorKey(ConductorData.CombatBehavior behavior) {
		return switch (behavior) {
			case ACTIVE -> ConductorTexts.BEHAVIOR_ACTIVE;
			case PASSIVE -> ConductorTexts.BEHAVIOR_PASSIVE;
			case NEUTRAL -> ConductorTexts.BEHAVIOR_NEUTRAL;
		};
	}

	public static String behaviorKey(ConductorData.BehaviorState state) {
		return switch (state) {
			case PATROL -> ConductorTexts.BEHAVIOR_PATROL;
			case IDLE -> ConductorTexts.BEHAVIOR_IDLE;
			case GUARD -> ConductorTexts.BEHAVIOR_GUARD;
			case STANDBY -> ConductorTexts.BEHAVIOR_STANDBY;
		};
	}

	public static String orderKey(ConductorData.OrderType order) {
		return switch (order) {
			case NONE -> ConductorTexts.ORDER_NONE;
			case MOVE -> ConductorTexts.ORDER_MOVE;
			case ATTACK -> ConductorTexts.ORDER_ATTACK;
			case ATTACK_POINT -> ConductorTexts.ORDER_ATTACK_POINT;
			case RETURN -> ConductorTexts.ORDER_RETURN;
			case CLEANUP -> ConductorTexts.ORDER_CLEANUP;
			case REASSEMBLE -> ConductorTexts.ORDER_REASSEMBLE;
		};
	}

	/// 按当前窗口尺寸与折叠状态更新面板槽位；只读模型状态。
	public void updateLayout() {
		Minecraft minecraft = Minecraft.getInstance();
		int width = minecraft.getWindow().getGuiScaledWidth();
		int height = minecraft.getWindow().getGuiScaledHeight();
		boolean wasRosterHidden = rosterHidden;
		boolean wasSkillsHidden = skillsHidden;
		boolean narrow = width < MAX_ROSTER_WIDTH + MAX_SKILL_WIDTH + EDGE_MARGIN * 2 + HANDLE_WIDTH * 2 + PANEL_SAFE_GAP;
		rosterHidden = model.rosterCollapsed() || width < MAX_ROSTER_WIDTH + EDGE_MARGIN * 2 + HANDLE_WIDTH * 2 + PANEL_SAFE_GAP;
		skillsHidden = model.skillsCollapsed() || width < MAX_SKILL_WIDTH + EDGE_MARGIN * 2 + HANDLE_WIDTH * 2 + PANEL_SAFE_GAP;

		if (narrow && !rosterHidden && !skillsHidden) {
			if (model.preferSkillsOnNarrow()) {
				rosterHidden = true;
			} else {
				skillsHidden = true;
			}
		}

		if (!panelLayoutInitialized || wasRosterHidden != rosterHidden) {
			layoutPanel(ROSTER_PANEL, ROSTER_TOGGLE, MAX_ROSTER_WIDTH, rosterHidden, true);
		}

		if (!panelLayoutInitialized || wasSkillsHidden != skillsHidden) {
			layoutPanel(SKILL_RAIL, SKILL_TOGGLE, MAX_SKILL_WIDTH, skillsHidden, false);
		}

		panelLayoutInitialized = true;

		if (width != previousScreenWidth || height != previousScreenHeight) {
			int bottomHeight = Math.clamp((long) height * HUD_HEIGHT_PERCENT / PERCENT, HUD_MIN_HEIGHT, HUD_MAX_HEIGHT);
			int sectionWidth = Math.max(1, (width - EDGE_MARGIN * 2 - SECTION_PADDING * 2
					- SECTION_GAP * (BOTTOM_SECTION_COUNT - 1)) / BOTTOM_SECTION_COUNT);
			focusSize = Math.min(bottomHeight - SECTION_PADDING * 2, sectionWidth);
			infoWidth = Math.max(0, sectionWidth - focusSize - SECTION_GAP);
			element(FOCUS_PORTRAIT).layout(layout -> layout.width(focusSize).height(focusSize));
			element(FOCUS_INFO).layout(layout -> layout.width(infoWidth).height(focusSize));
			scroller(SELECTED_LIST).layout(layout -> layout.height(bottomHeight - SECTION_PADDING * 2));
			element(BOTTOM).layout(layout -> layout.height(bottomHeight));
			element(ROSTER_PANEL).layout(layout -> layout.bottom(bottomHeight + SECTION_GAP));
			element(SKILL_RAIL).layout(layout -> layout.bottom(bottomHeight + SECTION_GAP));
			previousScreenWidth = width;
			previousScreenHeight = height;
		}

		element(FOCUS_INFO).setDisplay(infoWidth >= INFO_WIDTH);
	}

	private void layoutPanel(String panelId, String toggleId, int width, boolean hidden, boolean left) {
		UIElement panel = element(panelId);
		int offset = hidden ? width : 0;
		panel.setDisplay(!hidden);
		panel.layout(layout -> {
			if (left) {
				layout.left(EDGE_MARGIN - offset);
			} else {
				layout.right(EDGE_MARGIN - offset);
			}
		});
		button(toggleId).layout(layout -> {
			if (left) {
				layout.left(EDGE_MARGIN + width - offset);
			} else {
				layout.right(EDGE_MARGIN + width - offset);
			}
		});
	}

	/// 清除布局缓存的屏幕尺寸与面板状态，用于会话重建。
	public void clearTransient() {
		panelLayoutInitialized = false;
		previousScreenWidth = -1;
		previousScreenHeight = -1;
		// model.clearTransient 会清空全部提示；静态按钮提示只在这里重建，动态选择提示仍由渲染阶段刷新。
		configureIcons();
	}
}
