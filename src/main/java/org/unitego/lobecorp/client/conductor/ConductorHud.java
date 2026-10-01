package org.unitego.lobecorp.client.conductor;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.conductor.ability.ConductorAbility;
import org.unitego.lobecorp.conductor.ability.ConductorTargeting;
import org.unitego.lobecorp.conductor.ability.ConductorTargetingResolver;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.LaserSkill;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.util.ConductorUtil;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;

import java.util.*;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;
import static org.unitego.lobecorp.client.conductor.ConductorHudTheme.*;

public class ConductorHud implements ModularHudLayer {
	public static final ConductorHud INSTANCE = new ConductorHud();
	/// HUD 数值范围的最小值与最大值显示格式。
	private static final String RANGE_INTERVAL_FORMAT = "%s–%s";
	private static final String MANAGE = "manage";
	private static final String BOTTOM = "bottom";
	private static final String MOVE = "move";
	private static final String ATTACK = "attack";
	private static final String STOP = "stop";
	private static final String ATTACK_MODE = "attack-mode";
	private static final String BEHAVIOR_MODE = "behavior-mode";
	private static final String ACTIVITY_MODE = "activity-mode";
	private static final String TEAM_SELECT = "team-select";
	private static final String ASSIGN = "assign";
	private static final String RELEASE = "release";
	private static final String ROSTER_SEARCH = "roster-search";
	private static final String ROSTER_QUERY = "roster-query";
	private static final String SKILL_PREFIX = "skill-";
	private static final String SELECTED_COUNT = "selected-count";
	private static final String SKILL_LIST = "skill-list";
	private static final String SKILL_RAIL = "skill-rail";
	private static final String ROSTER_LIST = "roster-list";
	private static final String ROSTER_PANEL = "roster-panel";
	private static final String ROSTER_TOGGLE = "roster-toggle";
	private static final String SELECTED_LIST = "selected-list";
	private static final String SELECTED_PREFIX = "selected-unit-";
	private static final String SKILL_TOGGLE = "skill-toggle";
	private static final String FOCUS_PORTRAIT = "focus-portrait";
	private static final String FOCUS_DETAILS = "focus-details";
	private static final String FOCUS_EFFECTS = "focus-effects";
	private static final String UNIT_PREFIX = "unit-";
	private static final List<ConductorData.CombatBehavior> BEHAVIOR_OPTIONS = List.of(
			ConductorData.CombatBehavior.ACTIVE, ConductorData.CombatBehavior.PASSIVE, ConductorData.CombatBehavior.NEUTRAL);
	private static final List<ConductorData.ControlMode> ACTIVITY_OPTIONS = List.of(
			ConductorData.ControlMode.COMMAND, ConductorData.ControlMode.SOFT, ConductorData.ControlMode.STANDBY);
	private static final List<ConductorData.AttackState> ATTACK_OPTIONS = List.of(
			ConductorData.AttackState.AUTO, ConductorData.AttackState.MANUAL);
	private final Map<String, Button> cardPool = new LinkedHashMap<>();
	private final ArrayDeque<Button> spareCards = new ArrayDeque<>();
	private final Map<String, Integer> lastCooldowns = new HashMap<>();
	private final Map<String, Long> cooldownFlashes = new HashMap<>();
	private final Map<String, Component> hints = new HashMap<>();
	private final ConductorHudPreferences preferences = ConductorHudPreferences.load();
	private final Map<UUID, Float> healthRatios = new HashMap<>();
	private final Map<UUID, Float> previousHealthRatios = new HashMap<>();
	private final Map<UUID, Long> healthChangedAt = new HashMap<>();
	private final Map<LivingEntity, Map<MobEffectInstance, EffectProgress>> effectProgress = new WeakHashMap<>();
	private final Map<String, List<UUID>> mountedMembers = new HashMap<>();
	private final Map<UUID, RemovedCard> removedCards = new HashMap<>();
	private final Map<UUID, Long> removedAt = new HashMap<>();
	private final Map<String, UUID> rosterIds = new HashMap<>();
	private final Map<String, UUID> selectedIds = new HashMap<>();
	private final Map<String, String> skillIds = new HashMap<>();
	private ModularUI modularUI;
	private UI ui;
	private String teamSelection = "";
	private boolean teamDropdown;
	private String modeDropdown = "";
	private int modeCursor;
	private int teamCursor;
	private int teamScroll;
	private String previousTeamSelection = "";
	private String rosterSearch = "";
	private String previousSearch = "";
	private UUID focused;
	private UUID previousFocus;
	private List<UUID> previousSelection;
	private String lastSkillClick = "";
	private long lastSkillClickTime;
	private int selectedColumns = 1;
	private List<Identifier> previousAbilities = List.of();
	private int previousRevision = -1;
	private long teamMemberCount;
	private int teamNamesRevision = -1;
	private List<String> teamNames = List.of();
	private boolean staticPortraits;
	private boolean panelLayoutInitialized;
	private int previousScreenWidth = -1;
	private int previousScreenHeight = -1;
	private int focusSize = FOCUS_SIZE;
	private final int selectedCardSize = CARD_SIZE;
	private int infoWidth = INFO_WIDTH;
	private boolean rosterCollapsed;
	private boolean skillsCollapsed;
	private boolean rosterHidden;
	private boolean skillsHidden;
	private boolean preferSkillsOnNarrow;
	private String skillSearch = "";
	private String appliedRosterSearch = "";
	private String appliedSkillSearch = "";
	private String searchTarget = ROSTER_SEARCH;
	private long searchChangedAt;
	private String keyboardFocus = "";
	private UUID selectionAnchor;
	private List<UUID> rosterMembers = List.of();
	private List<Identifier> mountedSkills = List.of();
	private String hoveredId = "";
	private String tooltipId = "";
	private long tooltipSince;
	private String pressedId = "";
	private long releasedAt;
	private UUID clickedMember;
	private double clickX;
	private double clickY;
	private Object previousLevel;
	private long rosterTick = -1;
	private int rosterColumns = -1;
	private boolean searchFocused;

	private static UIElement createLayout() {
		UIElement root = new UIElement();
		root.layout(layout -> layout.widthPercent(100).heightPercent(100));
		UIElement roster = panel(ROSTER_PANEL);
		roster.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
				.left(EDGE_MARGIN).top(EDGE_MARGIN).bottom(HUD_MIN_HEIGHT + SECTION_GAP)
				.width(MAX_ROSTER_WIDTH).paddingAll(SECTION_PADDING)
				.flexDirection(FlexDirection.COLUMN).gapAll(SECTION_GAP));
		UIElement heading = new UIElement();
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
		UIElement current = new UIElement();
		current.layout(layout -> layout.width(0).minWidth(0).flexGrow(1)
				.flexDirection(FlexDirection.ROW).gapAll(SECTION_GAP));
		UIElement portrait = panel(FOCUS_PORTRAIT);
		portrait.layout(layout -> layout.width(FOCUS_SIZE).height(FOCUS_SIZE).flexShrink(0));
		UIElement info = new UIElement();
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
		UIElement commands = new UIElement();
		commands.layout(layout -> layout.width(0).minWidth(0).flexGrow(1).heightPercent(100)
				.flexDirection(FlexDirection.COLUMN).gapAll(BUTTON_GAP));
		UIElement commandButtons = new UIElement();
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
		root.addChildren(bottom, roster, rail, left, right);
		return root;
	}

	private static ConductorData.FormationMode formationMode(String id) {
		return switch (id) {
			case FORMATION_SCATTERED -> ConductorData.FormationMode.SCATTERED;
			case FORMATION_UNIFORM -> ConductorData.FormationMode.UNIFORM;
			default -> ConductorData.FormationMode.FORMATION;
		};
	}

	private static String formationKey(String id) {
		return switch (id) {
			case FORMATION_SCATTERED -> ConductorTexts.FORMATION_SCATTERED;
			case FORMATION_UNIFORM -> ConductorTexts.FORMATION_UNIFORM;
			default -> ConductorTexts.FORMATION_REGULAR;
		};
	}

	private static UIElement searchRow(String id, String queryId, String clearId) {
		UIElement row = panel(id);
		row.layout(layout -> layout.widthPercent(100).height(SEARCH_HEIGHT).flexShrink(0)
				.flexDirection(FlexDirection.ROW).paddingAll(BORDER).gapAll(BUTTON_GAP));
		Label query = label(queryId);
		query.layout(layout -> layout.width(0).minWidth(0).flexGrow(1));
		row.addChildren(query, button(clearId, ConductorTexts.CLEAR_SEARCH, SEARCH_ICON_SIZE, SEARCH_ICON_SIZE));
		return row;
	}

	private static ScrollerView flatScroller(String id) {
		ScrollerView scroller = new ScrollerView();
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
		UIElement element = new UIElement();
		element.setId(id);
		element.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
		return element;
	}

	private static Label label(String id) {
		Label label = new Label();
		label.setId(id);
		label.layout(layout -> layout.height(TEXT_LINE_HEIGHT).paddingAll(0).flexShrink(0));
		return label;
	}

	private static Button button(String id, String key, int width, int height) {
		Button button = new Button();
		button.setId(id);
		button.noText();
		INSTANCE.hints.put(id, Component.translatable(key));
		button.layout(layout -> layout.width(width).height(height).paddingAll(0).flexShrink(0));
		button.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY)
				.hoverTexture(IGuiTexture.EMPTY)
				.pressedTexture(IGuiTexture.EMPTY));
		return button;
	}

	private static Component abilityName(ConductorAbility ability) {
		return Component.translatableWithFallback(ability instanceof EntitySkillConductorAbility
				? ConductorTexts.skillName(ability.id()) : ConductorTexts.abilityName(ability.id()), ability.id().getPath());
	}

	private static Button portraitButton(String id, int width, int height) {
		Button button = new Button();
		button.setId(id);
		button.noText();
		button.layout(layout -> {
			layout.height(height).paddingAll(0).flexShrink(0);
			if (width > 0) layout.width(width);
		});
		button.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY)
				.hoverTexture(IGuiTexture.EMPTY)
				.pressedTexture(IGuiTexture.EMPTY));
		return button;
	}

	private static String combatBehaviorKey(ConductorData.CombatBehavior behavior) {
		return switch (behavior) {
			case ACTIVE -> ConductorTexts.BEHAVIOR_ACTIVE;
			case PASSIVE -> ConductorTexts.BEHAVIOR_PASSIVE;
			case NEUTRAL -> ConductorTexts.BEHAVIOR_NEUTRAL;
		};
	}

	private static String behaviorKey(ConductorData.BehaviorState state) {
		return switch (state) {
			case PATROL -> ConductorTexts.BEHAVIOR_PATROL;
			case IDLE -> ConductorTexts.BEHAVIOR_IDLE;
			case GUARD -> ConductorTexts.BEHAVIOR_GUARD;
			case STANDBY -> ConductorTexts.BEHAVIOR_STANDBY;
		};
	}

	private static String orderKey(ConductorData.OrderType order) {
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

	private static void frame(GuiGraphicsExtractor graphics, UIElement area, int background, int border, int depth) {
		frame(graphics, (int) area.getPositionX(), (int) area.getPositionY(),
				(int) area.getSizeWidth(), (int) area.getSizeHeight(), background, border, depth);
	}

	private static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
	                          int background, int border, int depth) {
		if (width <= 0 || height <= 0) return;
		graphics.fill(x, y, x + width, y + height + depth, border);
		graphics.fill(x + BORDER, y + BORDER, x + width - BORDER, y + height - BORDER, background);
		graphics.outline(x + BORDER, y + BORDER, Math.max(1, width - BORDER * 2),
				Math.max(1, height - BORDER * 2), LINE_INNER);
		graphics.fill(x + BORDER, y + BORDER, x + width - BORDER, y + BORDER + 1, EDGE_LIGHT);
	}

	private static void textFit(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		if (width <= 0) return;
		var font = Minecraft.getInstance().font;
		String value = text.getString();
		if (font.width(value) > width) {
			value = font.plainSubstrByWidth(value, Math.max(0, width - font.width(ELLIPSIS))) + ELLIPSIS;
		}
		graphics.text(font, Component.literal(value), x, y, color, false);
	}

	private static void textFitUnscaled(GuiGraphicsExtractor graphics, float scale, Component text, int x, int y, int width, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().scale(1 / scale, 1 / scale);
		textFit(graphics, text, Math.round(x * scale), Math.round(y * scale), Math.round(width * scale), color);
		graphics.pose().popMatrix();
	}

	private static Component memberDistance(UUID member) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(member);
		return Component.translatable(ConductorTexts.DISTANCE, entity == null ? UNKNOWN_MARK
				: String.format(Locale.ROOT, DECIMAL_FORMAT, entity.position().distanceTo(ConductorCamera.center())));
	}

	private static Component memberDimension(UUID member) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(member);
		ConductorData.Unit unit = ConductorClient.unit(member);
		return Component.translatable(ConductorTexts.DIMENSION, entity != null ? entity.level().dimension().identifier().toString()
				: unit == null ? UNKNOWN_MARK : unit.dimension());
	}

	private static void renderHealthBar(GuiGraphicsExtractor graphics, int x, int y, int width, LivingEntity living) {
		float ratio = Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F);
		INSTANCE.healthBar(graphics, x, y, width, living.getUUID(), ratio);
	}

	private static void drawGlyph(GuiGraphicsExtractor graphics, String glyph, int x, int y, int scale, int color) {
		String pixels = ICONS.get(glyph);
		if (pixels == null) return;
		for (int pixel = 0; pixel < pixels.length(); pixel++) {
			if (pixels.charAt(pixel) != '1') continue;
			int px = x + pixel % ICON_GRID * scale;
			int py = y + pixel / ICON_GRID * scale;
			graphics.fill(px, py, px + scale, py + scale, color);
		}
	}

	private static void resizeInfoContent(ScrollerView scroller, int width, int height) {
		int contentWidth = Math.max(1, width);
		int contentHeight = Math.max(1, height);
		if (scroller.viewContainer.getSizeWidth() != contentWidth || scroller.viewContainer.getSizeHeight() != contentHeight) {
			scroller.viewContainer.layout(layout -> layout.width(contentWidth).height(contentHeight));
		}
	}

	private static void clipInfo(GuiGraphicsExtractor graphics, ScrollerView scroller) {
		UIElement viewport = scroller.viewPort;
		graphics.enableScissor((int) viewport.getPositionX(), (int) viewport.getPositionY(),
				(int) (viewport.getPositionX() + viewport.getSizeWidth()), (int) (viewport.getPositionY() + viewport.getSizeHeight()));
	}

	private static Component memberType(UUID member) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(member);
		ConductorData.MemberInfo info = ConductorClient.snapshot().memberInfo(member);
		return Component.literal(entity != null ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
				: info == null ? UNKNOWN_MARK : info.type());
	}

	private static Component attributes(LivingEntity living) {
		String attack = living.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
				? String.format(Locale.ROOT, DECIMAL_FORMAT, living.getAttributeValue(Attributes.ATTACK_DAMAGE)) : UNKNOWN_MARK;
		return Component.translatable(ConductorTexts.ATTRIBUTES, attack, living.getArmorValue());
	}

	private static Component memberName(UUID member) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(member);
		if (entity != null) return entity.getDisplayName();
		ConductorData.MemberInfo info = ConductorClient.snapshot().memberInfo(member);
		return Component.literal(info == null || info.name().isBlank() ? member.toString() : info.name());
	}

	private static void renderSkillDetails(GuiGraphicsExtractor graphics, Mob mob, ConductorAbility ability) {
		ConductorAbility.Display display = ability.display(mob);
		Component damage;
		if (display.damageKind() == ConductorAbility.DamageKind.FIXED) {
			damage = Component.literal(Double.toString(display.damage()));
		} else if (display.damageKind() == ConductorAbility.DamageKind.DYNAMIC) {
			double basicDamage = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
			damage = basicDamage > 0.0D ? Component.literal(Double.toString(basicDamage))
					: Component.translatable(ConductorTexts.DAMAGE_DYNAMIC);
		} else {
			damage = Component.translatable(ConductorTexts.DAMAGE_NONE);
		}
		String typeKey = switch (display.abilityKind()) {
			case BASIC -> ConductorTexts.TYPE_BASIC;
			case ATTACK -> ConductorTexts.TYPE_ATTACK;
			case SUPPORT -> ConductorTexts.TYPE_SUPPORT;
		};
		String targetKey = switch (ability.targetKind()) {
			case SELF -> ConductorTexts.TARGET_SELF;
			case ENTITY -> ConductorTexts.TARGET_ENTITY;
			case POSITION -> ConductorTexts.TARGET_POSITION;
			case EITHER -> ConductorTexts.TARGET_EITHER;
		};
		double range = ability.displayRange(mob);
		Component rangeText = ability instanceof EntitySkillConductorAbility.Laser
				? Component.literal(String.format(Locale.ROOT, RANGE_INTERVAL_FORMAT, LaserSkill.MINIMUM_RANGE, range))
				: range > 0.0D ? Component.literal(Double.toString(range)) : Component.translatable(
						ability.targetKind() == ConductorTargeting.TargetKind.SELF
								? ConductorTexts.RANGE_SELF : ConductorTexts.RANGE_UNSPECIFIED);
		int total = Math.max(ConductorClient.abilityCooldownTotalTicks(mob.getUUID(), ability.id()),
				ability.totalCooldownTicks(mob));
		int remaining = ConductorClient.abilityCooldownTicks(mob.getUUID(), ability.id());
		List<Component> lines = List.of(
				Component.translatableWithFallback(ability instanceof EntitySkillConductorAbility
								? ConductorTexts.skillName(ability.id()) : ConductorTexts.abilityName(ability.id()),
						ability.id().getPath()),
				Component.translatable(ConductorTexts.DAMAGE, damage),
				Component.translatable(ConductorTexts.TYPE, Component.translatable(typeKey)),
				Component.translatable(ConductorTexts.TARGET, Component.translatable(targetKey)),
				Component.translatable(ConductorTexts.RANGE, rangeText),
				Component.translatable(ConductorTexts.COOLDOWN_TOTAL,
						String.format(Locale.ROOT, DECIMAL_FORMAT, total / (float) TICKS_PER_SECOND)),
				remaining == 0 ? Component.translatable(ConductorTexts.READY) : Component.translatable(ConductorTexts.COOLDOWN_REMAINING,
						String.format(Locale.ROOT, DECIMAL_FORMAT, remaining / (float) TICKS_PER_SECOND)));
		List<Component> details = new ArrayList<>(lines);
		appendSkillStatus(details, mob, ability, remaining);
		Component description = ConductorTexts.abilityDescription(ability.id());
		if (!description.getString().isEmpty()) details.add(description);
		drawTooltip(graphics, details);
	}

	private static void appendSkillStatus(List<Component> lines, Mob mob, ConductorAbility ability, int remaining) {
		boolean casting = ability instanceof EntitySkillConductorAbility entitySkill && !entitySkill.canBeginCast(mob);
		boolean available = ability.isAvailable(mob);
		if (remaining > 0) lines.add(Component.translatable(ConductorTexts.SKILL_UNAVAILABLE_COOLDOWN).withStyle(ChatFormatting.RED));
		if (casting) lines.add(Component.translatable(ConductorTexts.SKILL_UNAVAILABLE_CASTING).withStyle(ChatFormatting.RED));
		if (!available) lines.add(Component.translatable(ConductorTexts.SKILL_UNAVAILABLE_CONDITION).withStyle(ChatFormatting.RED));
		if (!ConductorControls.isPendingSkill(ability.id().toString(), mob.getUUID())) return;
		ConductorControls.AimPreview aim = ConductorControls.aimPreview(Minecraft.getInstance());
		if (aim == null || aim.caster() != mob) return;
		if (aim.rangeLimited()) {
			lines.add(Component.translatable(ConductorTexts.SKILL_OUT_OF_RANGE).withStyle(ChatFormatting.YELLOW));
		} else if (!aim.valid() && remaining == 0 && available && !casting) {
			lines.add(Component.translatable(ConductorTexts.SKILL_UNAVAILABLE_TARGET).withStyle(ChatFormatting.RED));
		}
	}

	private static void renderHover(GuiGraphicsExtractor graphics) {
		INSTANCE.renderTooltipAtPointer(graphics);
	}

	protected static void drawTooltip(GuiGraphicsExtractor graphics, List<Component> lines) {
		drawTooltip(graphics, lines, List.of());
	}

	private static void drawTooltip(GuiGraphicsExtractor graphics, List<Component> lines, List<EffectIcon> effects) {
		Minecraft minecraft = Minecraft.getInstance();
		int maxWidth = Math.max(1, Math.min(TOOLTIP_WIDTH, graphics.guiWidth() - HOVER_PADDING * 2));
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) wrapped.addAll(minecraft.font.split(line, maxWidth - HOVER_PADDING * 2));
		int lineHeight = minecraft.font.lineHeight + HOVER_LINE_GAP;
		int columns = Math.max(1, (maxWidth - HOVER_PADDING * 2) / EFFECT_CELL_WIDTH);
		int maxEffectRows = Math.max(0, (graphics.guiHeight() - HOVER_PADDING * 4 - lineHeight) / EFFECT_CELL_HEIGHT);
		int effectCount = Math.min(effects.size(), maxEffectRows * columns);
		int effectRows = (effectCount + columns - 1) / columns;
		int effectsHeight = effectRows * EFFECT_CELL_HEIGHT;
		int maxLines = Math.max(1, (graphics.guiHeight() - HOVER_PADDING * 4 - effectsHeight) / lineHeight);
		if (wrapped.size() > maxLines) wrapped = new ArrayList<>(wrapped.subList(0, maxLines));
		int width = Math.min(maxWidth, wrapped.stream().mapToInt(minecraft.font::width).max().orElse(0) + HOVER_PADDING * 2);
		if (effectCount > 0) width = maxWidth;
		int height = wrapped.size() * lineHeight + HOVER_PADDING * 2 + effectsHeight;
		int mx = (int) (minecraft.mouseHandler.xpos() * graphics.guiWidth() / minecraft.getWindow().getWidth());
		int my = (int) (minecraft.mouseHandler.ypos() * graphics.guiHeight() / minecraft.getWindow().getHeight());
		int x = mx + HOVER_OFFSET + width > graphics.guiWidth() ? mx - HOVER_OFFSET - width : mx + HOVER_OFFSET;
		int y = my + HOVER_OFFSET + height > graphics.guiHeight() ? my - HOVER_OFFSET - height : my + HOVER_OFFSET;
		x = Math.max(0, Math.min(x, graphics.guiWidth() - width));
		y = Math.max(0, Math.min(y, graphics.guiHeight() - height));
		graphics.nextStratum();
		graphics.fill(x, y, x + width, y + height, TOOLTIP_BACKGROUND);
		graphics.outline(x, y, width, height, TOOLTIP_BORDER);
		for (int index = 0; index < wrapped.size(); index++) {
			graphics.text(minecraft.font, wrapped.get(index), x + HOVER_PADDING,
					y + HOVER_PADDING + effectsHeight + index * lineHeight, TEXT_COLOR, false);
		}
		graphics.enableScissor(x + BORDER, y + BORDER, x + width - BORDER, y + height - BORDER);
		for (int index = 0; index < effectCount; index++) {
			EffectIcon icon = effects.get(index);
			int effectX = x + HOVER_PADDING + index % columns * EFFECT_CELL_WIDTH;
			int effectY = y + HOVER_PADDING + index / columns * EFFECT_CELL_HEIGHT;
			drawEffectIcon(graphics, icon, effectX, effectY);
		}
		graphics.disableScissor();
	}

	private static void drawEffectIcon(GuiGraphicsExtractor graphics, EffectIcon icon, int x, int y) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(icon.effect().getEffect()),
				x, y, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE);
		textFit(graphics, Component.literal(Integer.toString(icon.effect().getAmplifier() + 1)),
				x + EFFECT_ICON_SIZE + HOVER_LINE_GAP, y + (EFFECT_ICON_SIZE - Minecraft.getInstance().font.lineHeight) / 2,
				EFFECT_CELL_WIDTH - EFFECT_ICON_SIZE - HOVER_LINE_GAP - HOVER_PADDING, TEXT_COLOR);
		int barY = y + EFFECT_ICON_SIZE + HOVER_LINE_GAP;
		int barWidth = EFFECT_CELL_WIDTH - HOVER_PADDING;
		graphics.fill(x, barY, x + barWidth, barY + EFFECT_BAR_HEIGHT, LINE_OUTER);
		graphics.fill(x, barY, x + Math.round(barWidth * icon.remaining()), barY + EFFECT_BAR_HEIGHT, ORE_GREEN);
	}

	private static boolean hasRemoteSelection(Minecraft minecraft) {
		if (minecraft.level == null) return false;
		for (UUID member : ConductorControls.selected()) {
			if (ConductorClient.unit(member) != null && minecraft.level.getEntity(member) == null) return true;
		}
		return false;
	}

	private static void scrollList(ScrollerView list, float direction) {
		float extent = list.viewContainer.getSizeHeight() - list.viewPort.getContentHeight();
		if (extent <= 0) return;
		float step = direction * SCROLL_STEP / extent;
		list.verticalScroller.setNormalizedValue(Mth.clamp(list.verticalScroller.getNormalizedValue() + step, 0, 1), true);
	}

	public void selectTeam(String team) {
		teamSelection = team;
	}

	public UUID focusedMember() {
		return focused;
	}

	public void resetLayout() {
		teamDropdown = false;
		modeDropdown = "";
		clickedMember = null;
		rosterCollapsed = preferences.rosterCollapsed;
		skillsCollapsed = preferences.skillsCollapsed;
		skillSearch = "";
		appliedRosterSearch = "";
		appliedSkillSearch = "";
		keyboardFocus = "";
		selectionAnchor = null;
		focused = null;
		rosterTick = -1;
		cardPool.clear();
		spareCards.clear();
		mountedSkills = List.of();
		mountedMembers.clear();
		lastCooldowns.clear();
		cooldownFlashes.clear();
		removedAt.clear();
		removedCards.clear();
		healthRatios.clear();
		previousHealthRatios.clear();
		healthChangedAt.clear();
		effectProgress.clear();
		ConductorPortraitCache.clear();
		searchFocused = false;
		preferSkillsOnNarrow = false;
		rosterSearch = "";
		previousSearch = "";
		previousRevision = -1;
		panelLayoutInitialized = false;
		teamNamesRevision = -1;
		previousSelection = null;
		previousFocus = null;
		previousScreenWidth = -1;
		previousScreenHeight = -1;
	}

	@Override
	public ModularUI getModularUI() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ConductorControls.active() || minecraft.screen != null) {
			return null;
		}
		if (modularUI == null) {
			ui = UI.of(createLayout(), size -> size);
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
			modularUI = ModularUI.of(ui);
		}
		update();
		return modularUI;
	}

	private void configureIcon(String id, String key) {
		Button button = ui.selectId(id, Button.class).findFirst().orElseThrow();
		button.noText();
		hints.put(id, Component.translatable(key));
	}

	public ConductorData.FormationMode formationMode() {
		return preferences.formationMode;
	}

	private void update() {
		Minecraft minecraft = Minecraft.getInstance();
		if (previousLevel != minecraft.level) {
			resetLayout();
			previousLevel = minecraft.level;
		}
		updateLayout();
		long now = System.currentTimeMillis();
		if (now - searchChangedAt >= SEARCH_DELAY_MS) {
			appliedRosterSearch = rosterSearch;
			appliedSkillSearch = skillSearch;
		}
		List<UUID> selected = ConductorControls.selected();
		if (focused == null || !selected.contains(focused)) {
			focused = selected.isEmpty() ? null : selected.getLast();
		}
		ConductorData.Unit focusUnit = focused == null ? null : ConductorClient.unit(focused);
		if (focusUnit != null && !Objects.equals(focused, previousFocus)) teamSelection = focusUnit.team();
		List<String> teams = teams();
		if (!teams.contains(teamSelection)) teamSelection = teams.isEmpty() ? "" : teams.getFirst();
		ConductorData.Team team = ConductorClient.snapshot().team(teamSelection);
		hints.put(TEAM_SELECT, team == null ? Component.translatable(ConductorTexts.TEAM)
				: Component.literal(team.name()).withColor(team.color()));
		ConductorData.Unit modeUnit = selectedModeUnit();
		hints.put(ATTACK_MODE, Component.translatable(modeUnit != null
				&& modeUnit.attackState() == ConductorData.AttackState.MANUAL
				? ConductorTexts.ATTACK_BASIC : ConductorTexts.ATTACK_AI));
		hints.put(BEHAVIOR_MODE, modeUnit == null ? Component.translatable(ConductorTexts.BEHAVIOR_MODE)
				: Component.translatable(ConductorTexts.BEHAVIOR_VALUE, Component.translatable(combatBehaviorKey(modeUnit.combatBehavior()))));
		hints.put(ACTIVITY_MODE, modeUnit == null ? Component.translatable(ConductorTexts.ACTIVITY_MODE)
				: Component.translatable(ConductorTexts.ACTIVITY_VALUE, Component.translatable(behaviorKey(modeUnit.behaviorState()))));
		if (modeUnit == null) modeDropdown = "";
		Entity focusEntity = focused == null || minecraft.level == null ? null : minecraft.level.getEntity(focused);
		if (focusEntity instanceof Mob mob) ConductorClient.requestAbilitiesIfMissing(mob.getUUID());
		long tick = minecraft.level == null ? 0 : minecraft.level.getGameTime();
		boolean rosterChanged = previousRevision != ConductorClient.revision()
				|| !teamSelection.equals(previousTeamSelection) || !appliedRosterSearch.equals(previousSearch)
				|| tick - rosterTick >= ROSTER_REFRESH_TICKS || rosterTick < 0;
		if (rosterChanged) {
			if (previousRevision != ConductorClient.revision() || !teamSelection.equals(previousTeamSelection)) {
				teamMemberCount = team == null ? 0 : ConductorClient.snapshot().units().values().stream()
						.filter(unit -> unit.team().equals(teamSelection)).count();
			}
			previousRevision = ConductorClient.revision();
			previousTeamSelection = teamSelection;
			previousSearch = appliedRosterSearch;
			rosterTick = tick;
			rebuildRoster();
		}
		ui.selectId(SELECTED_COUNT, Label.class).findFirst().orElseThrow().setText(
				Component.translatable(ConductorTexts.ROSTER_COUNT, selected.size(),
						teamMemberCount));
		ui.selectId(ROSTER_QUERY, Label.class).findFirst().orElseThrow().setText(Component.empty());
		ui.selectId(SKILL_QUERY, Label.class).findFirst().orElseThrow().setText(Component.empty());
		ui.selectId(ROSTER_CLEAR, Button.class).findFirst().orElseThrow().setDisplay(!rosterSearch.isEmpty());
		ui.selectId(SKILL_CLEAR, Button.class).findFirst().orElseThrow().setDisplay(!skillSearch.isEmpty());
		if (!selected.equals(previousSelection)) {
			previousSelection = List.copyOf(selected);
			rebuildSelection();
		}
		List<Identifier> abilities = focusEntity instanceof Mob mob
				? ConductorUtil.abilities(mob).stream().filter(ability ->
						ConductorClient.hasAbility(mob.getUUID(), ability.id()) && matchesSkill(ability))
				.sorted(Comparator.comparing(ability -> ability.display(mob).abilityKind()))
				.map(ConductorAbility::id).toList() : List.of();
		if (!abilities.equals(previousAbilities) || !Objects.equals(focused, previousFocus)) {
			previousAbilities = abilities;
			previousFocus = focused;
			rebuildSkills();
		}
		mountCards(ROSTER_LIST, rosterMembers, rosterIds, false);
		mountCards(SELECTED_LIST, selected, selectedIds, true);
		mountSkills();
		var iterator = cardPool.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (!rosterIds.containsKey(entry.getKey()) && !selectedIds.containsKey(entry.getKey()) && !skillIds.containsKey(entry.getKey())) {
				if (spareCards.size() < rosterIds.size() + selectedIds.size() + skillIds.size())
					spareCards.add(entry.getValue());
				iterator.remove();
			}
		}
		healthRatios.keySet().removeIf(id -> !AVERAGE_HEALTH_ID.equals(id) && ConductorClient.unit(id) == null && !selected.contains(id));
		previousHealthRatios.keySet().retainAll(healthRatios.keySet());
		healthChangedAt.keySet().retainAll(healthRatios.keySet());
		if (!keyboardFocus.isEmpty() && !focusableIds().contains(keyboardFocus)) keyboardFocus = "";
	}

	private boolean matchesSkill(ConductorAbility ability) {
		String query = appliedSkillSearch.toLowerCase(Locale.ROOT);
		return query.isBlank() || abilityName(ability).getString().toLowerCase(Locale.ROOT).contains(query)
				|| ability.id().toString().toLowerCase(Locale.ROOT).contains(query);
	}

	private void updateLayout() {
		Minecraft minecraft = Minecraft.getInstance();
		int width = minecraft.getWindow().getGuiScaledWidth();
		int height = minecraft.getWindow().getGuiScaledHeight();
		boolean wasRosterHidden = rosterHidden;
		boolean wasSkillsHidden = skillsHidden;
		boolean narrow = width < MAX_ROSTER_WIDTH + MAX_SKILL_WIDTH + EDGE_MARGIN * 2 + HANDLE_WIDTH * 2 + PANEL_SAFE_GAP;
		rosterHidden = rosterCollapsed || width < MAX_ROSTER_WIDTH + EDGE_MARGIN * 2 + HANDLE_WIDTH * 2 + PANEL_SAFE_GAP;
		skillsHidden = skillsCollapsed || width < MAX_SKILL_WIDTH + EDGE_MARGIN * 2 + HANDLE_WIDTH * 2 + PANEL_SAFE_GAP;
		if (narrow && !rosterHidden && !skillsHidden) {
			if (preferSkillsOnNarrow) rosterHidden = true;
			else skillsHidden = true;
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
			ui.selectId(FOCUS_PORTRAIT, UIElement.class).findFirst().orElseThrow()
					.layout(layout -> layout.width(focusSize).height(focusSize));
			ui.selectId(FOCUS_INFO, UIElement.class).findFirst().orElseThrow()
					.layout(layout -> layout.width(infoWidth).height(focusSize));
			ui.selectId(SELECTED_LIST, ScrollerView.class).findFirst().orElseThrow()
					.layout(layout -> layout.height(bottomHeight - SECTION_PADDING * 2));
			ui.selectId(BOTTOM, UIElement.class).findFirst().orElseThrow().layout(layout -> layout.height(bottomHeight));
			ui.selectId(ROSTER_PANEL, UIElement.class).findFirst().orElseThrow()
					.layout(layout -> layout.bottom(bottomHeight + SECTION_GAP));
			ui.selectId(SKILL_RAIL, UIElement.class).findFirst().orElseThrow()
					.layout(layout -> layout.bottom(bottomHeight + SECTION_GAP));
			mountedMembers.clear();
			mountedSkills = List.of();
			previousScreenWidth = width;
			previousScreenHeight = height;
		}
		ui.selectId(FOCUS_INFO, UIElement.class).findFirst().orElseThrow()
				.setDisplay(infoWidth >= INFO_WIDTH);
	}

	private void layoutPanel(String panelId, String toggleId, int width, boolean hidden, boolean left) {
		UIElement panel = ui.selectId(panelId, UIElement.class).findFirst().orElseThrow();
		int offset = hidden ? width : 0;
		panel.setDisplay(!hidden);
		panel.layout(layout -> {
			if (left) layout.left(EDGE_MARGIN - offset);
			else layout.right(EDGE_MARGIN - offset);
		});
		ui.selectId(toggleId, Button.class).findFirst().orElseThrow().layout(layout -> {
			if (left) layout.left(EDGE_MARGIN + width - offset);
			else layout.right(EDGE_MARGIN + width - offset);
		});
	}

	private void rebuildRoster() {
		List<UUID> members = ConductorClient.snapshot().units().entrySet().stream()
				.filter(entry -> entry.getValue().team().equals(teamSelection))
				.map(entry -> {
					try {
						return UUID.fromString(entry.getKey());
					} catch (IllegalArgumentException ignored) {
						return null;
					}
				})
				.filter(Objects::nonNull).filter(this::matchesSearch)
				.sorted(Comparator.comparing(UUID::toString)).toList();
		if (!members.equals(rosterMembers)) {
			rosterMembers = members;
			mountedMembers.remove(ROSTER_LIST);
		}
	}

	private void mountCards(String listId, List<UUID> members, Map<String, UUID> ids, boolean horizontal) {
		ScrollerView list = ui.selectId(listId, ScrollerView.class).findFirst().orElseThrow();
		int cardHeight = horizontal ? selectedCardSize : ROSTER_ROW_HEIGHT;
		int cardWidth = horizontal ? selectedCardSize : Math.max(1, (int) list.viewPort.getContentWidth());
		int step = cardHeight + CARD_GAP;
		int columns = horizontal ? Math.max(1, ((int) list.viewPort.getContentWidth() + CARD_GAP) / step) : 1;
		int count = (members.size() + columns - 1) / columns;
		int extent = Math.max(0, count * step - CARD_GAP);
		int viewport = Math.max(0, (int) list.viewPort.getContentHeight());
		float normalized = list.verticalScroller.getNormalizedValue();
		int first = Math.max(0, (int) (normalized * Math.max(0, extent - viewport)) / step - BUFFER_ROWS);
		int last = Math.min(count, first + (viewport + step - 1) / step + BUFFER_ROWS * 2);
		List<UUID> visible = members.subList(Math.min(members.size(), first * columns), Math.min(members.size(), last * columns));
		if (visible.equals(mountedMembers.get(listId)) && columns == (horizontal ? selectedColumns : rosterColumns))
			return;
		if (horizontal) selectedColumns = columns;
		else rosterColumns = columns;
		mountedMembers.put(listId, List.copyOf(visible));
		list.clearAllScrollViewChildren();
		ids.clear();
		list.getScrollerViewStyle().mode(ScrollerMode.VERTICAL)
				.horizontalScrollDisplay(ScrollDisplay.NEVER).verticalScrollDisplay(ScrollDisplay.AUTO);
		UIElement spacer = new UIElement();
		spacer.setAllowHitTest(false);
		spacer.layout(layout -> {
			layout.widthPercent(100).height(extent);
		});
		list.addScrollViewChild(spacer);
		for (int index = first * columns; index < Math.min(members.size(), last * columns); index++) {
			UUID member = members.get(index);
			String id = (horizontal ? SELECTED_PREFIX : UNIT_PREFIX) + member;
			Button card = cardPool.computeIfAbsent(id, key -> acquireCard(key, cardWidth, cardHeight));
			int x = horizontal ? index % columns * step : 0;
			int y = index / columns * step;
			card.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(cardWidth).height(cardHeight));
			list.addScrollViewChild(card);
			ids.put(id, member);
		}
	}

	private boolean matchesSearch(UUID member) {
		if (appliedRosterSearch.isBlank()) return true;
		ConductorData.MemberInfo info = ConductorClient.snapshot().memberInfo(member);
		ConductorData.Unit unit = ConductorClient.unit(member);
		String query = appliedRosterSearch.toLowerCase(Locale.ROOT);
		String name = info == null ? "" : info.name();
		String type = info == null ? "" : info.type();
		String dimension = unit == null ? "" : unit.dimension();
		return member.toString().toLowerCase(Locale.ROOT).contains(query)
				|| name.toLowerCase(Locale.ROOT).contains(query)
				|| type.toLowerCase(Locale.ROOT).contains(query)
				|| dimension.toLowerCase(Locale.ROOT).contains(query);
	}

	private void rebuildSelection() {
		mountedMembers.remove(SELECTED_LIST);
	}

	private void rebuildSkills() {
		mountedSkills = List.of();
		skillIds.clear();
		ui.selectId(SKILL_LIST, ScrollerView.class).findFirst().orElseThrow().clearAllScrollViewChildren();
	}

	private Button acquireCard(String id, int width, int height) {
		Button button = spareCards.pollFirst();
		if (button == null) return portraitButton(id, width, height);
		button.setId(id);
		button.layout(layout -> layout.width(width).height(height));
		return button;
	}

	private void mountSkills() {
		ScrollerView list = ui.selectId(SKILL_LIST, ScrollerView.class).findFirst().orElseThrow();
		int step = SKILL_SIZE + BUTTON_GAP;
		int viewport = Math.max(0, (int) list.viewPort.getContentHeight());
		int extent = Math.max(0, previousAbilities.size() * step - BUTTON_GAP);
		int first = Math.max(0, (int) (list.verticalScroller.getNormalizedValue() * Math.max(0, extent - viewport)) / step - BUFFER_ROWS);
		int last = Math.min(previousAbilities.size(), first + (viewport + step - 1) / step + BUFFER_ROWS * 2);
		first = Math.min(first, previousAbilities.size());
		List<Identifier> visible = previousAbilities.subList(first, Math.max(first, last));
		if (visible.equals(mountedSkills) && (!visible.isEmpty() || previousAbilities.isEmpty())) return;
		mountedSkills = List.copyOf(visible);
		skillIds.clear();
		list.clearAllScrollViewChildren();
		list.getScrollerViewStyle().mode(ScrollerMode.VERTICAL).horizontalScrollDisplay(ScrollDisplay.NEVER);
		UIElement spacer = new UIElement();
		spacer.setAllowHitTest(false);
		spacer.layout(layout -> layout.widthPercent(100).height(extent));
		list.addScrollViewChild(spacer);
		for (int index = first; index < last; index++) {
			Identifier ability = previousAbilities.get(index);
			String id = SKILL_PREFIX + ability;
			Button card = cardPool.computeIfAbsent(id, key -> acquireCard(key, 0, SKILL_SIZE));
			int y = index * step;
			card.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).top(y).widthPercent(100).height(SKILL_SIZE));
			list.addScrollViewChild(card);
			skillIds.put(id, ability.toString());
		}
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ModularHudLayer.super.render(graphics, deltaTracker);
		Minecraft minecraft = Minecraft.getInstance();
		if (ui == null || !ConductorControls.active() || minecraft.screen != null || minecraft.level == null) return;
		UIElement hovered = elementAtCurrentUI(minecraft);
		String nextHover = interactiveId(hovered);
		if (!nextHover.equals(hoveredId)) {
			hoveredId = nextHover;
		}
		staticPortraits = countVisibleCards() > PORTRAIT_LIMIT;
		ConductorPortraitCache.beginFrame(minecraft.level, ConductorClient.snapshot(), staticPortraits);
		renderChrome(graphics);
		if (!rosterHidden) renderMemberCards(graphics, minecraft, ROSTER_LIST, rosterIds);
		renderMemberCards(graphics, minecraft, SELECTED_LIST, selectedIds);
		renderFocus(graphics, minecraft);
		renderCommandIcons(graphics);
		if (!skillsHidden) renderSkills(graphics);
		ConductorPortraitCache.submit(graphics);
		graphics.nextStratum();
		renderPortraitOverlays(graphics, minecraft);
		removedAt.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() >= ANIMATION_FRAME_MS);
		removedCards.keySet().retainAll(removedAt.keySet());
		if (!preferences.reduceMotion) {
			for (RemovedCard card : removedCards.values()) {
				frame(graphics, card.x() + SECTION_PADDING, card.y() + SECTION_PADDING,
						selectedCardSize - SECTION_PADDING * 2, selectedCardSize - SECTION_PADDING * 2, BG_2, ORE_GREEN, 0);
				textFit(graphics, card.name(), card.x() + SECTION_PADDING + BORDER, card.y() + SECTION_PADDING + BORDER,
						selectedCardSize - SECTION_PADDING * 2 - BORDER * 2, MUTED_COLOR);
			}
		}
		if (teamDropdown) renderTeamDropdown(graphics);
		else if (!modeDropdown.isEmpty()) renderModeDropdown(graphics);
		else renderHover(graphics);
	}

	private int countVisibleCards() {
		Set<UUID> visible = new HashSet<>();
		for (String listId : List.of(ROSTER_LIST, SELECTED_LIST)) {
			if (ROSTER_LIST.equals(listId) && rosterHidden) continue;
			UIElement viewport = ui.selectId(listId, ScrollerView.class).findFirst().orElseThrow().viewPort;
			Map<String, UUID> ids = ROSTER_LIST.equals(listId) ? rosterIds : selectedIds;
			for (var entry : ids.entrySet()) {
				Button card = cardPool.get(entry.getKey());
				if (card != null && card.getPositionX() < viewport.getPositionX() + viewport.getSizeWidth()
						&& card.getPositionX() + card.getSizeWidth() > viewport.getPositionX()
						&& card.getPositionY() < viewport.getPositionY() + viewport.getSizeHeight()
						&& card.getPositionY() + card.getSizeHeight() > viewport.getPositionY())
					visible.add(entry.getValue());
			}
		}
		return visible.size();
	}

	private String interactiveId(UIElement element) {
		for (; element != null; element = element.getParent()) {
			String id = element.getId();
			if (element instanceof Button || ROSTER_SEARCH.equals(id) || SKILL_SEARCH.equals(id)
					|| FOCUS_PORTRAIT.equals(id)) return id;
		}
		return "";
	}

	private void renderChrome(GuiGraphicsExtractor graphics) {
		for (String id : List.of(BOTTOM, ROSTER_PANEL, SKILL_RAIL)) {
			if (ROSTER_PANEL.equals(id) && rosterHidden || SKILL_RAIL.equals(id) && skillsHidden) continue;
			UIElement area = ui.selectId(id, UIElement.class).findFirst().orElseThrow();
			frame(graphics, area, BG_1, LINE_OUTER, PANEL_DEPTH);
		}
		for (String id : List.of(ROSTER_SEARCH, SKILL_SEARCH)) {
			if (ROSTER_SEARCH.equals(id) && rosterHidden || SKILL_SEARCH.equals(id) && skillsHidden) continue;
			UIElement area = ui.selectId(id, UIElement.class).findFirst().orElseThrow();
			boolean active = searchFocused && searchTarget.equals(id);
			frame(graphics, area, BG_0, active ? ORE_GREEN : LINE_OUTER, 0);
			String query = ROSTER_SEARCH.equals(id) ? rosterSearch : skillSearch;
			Component value = query.isBlank() ? Component.translatable(ROSTER_SEARCH.equals(id)
					? ConductorTexts.SEARCH : ConductorTexts.SEARCH_SKILLS) : Component.literal(query);
			int textX = (int) area.getPositionX() + SECTION_PADDING + SEARCH_ICON_SIZE;
			int textY = (int) area.getPositionY() + SECTION_PADDING;
			int textWidth = (int) area.getSizeWidth() - SEARCH_ICON_SIZE * 2 - SECTION_PADDING * 2;
			drawGlyph(graphics, GLYPH_SEARCH, (int) area.getPositionX() + SECTION_PADDING, textY + 1, 1, MUTED_COLOR);
			textFit(graphics, value, textX, textY, textWidth, query.isBlank() ? MUTED_COLOR : TEXT_COLOR);
			if (active && System.currentTimeMillis() / CURSOR_PHASE_MS % 2 == 0) {
				int cursorX = textX + Math.min(textWidth, Minecraft.getInstance().font.width(query));
				graphics.fill(cursorX, textY, cursorX + 1, textY + TEXT_LINE_HEIGHT, ORE_GREEN_HI);
			}
		}
		if (!rosterHidden) {
			UIElement area = ui.selectId(SELECTED_COUNT, UIElement.class).findFirst().orElseThrow();
			textFit(graphics, Component.translatable(ConductorTexts.ROSTER_COUNT, ConductorControls.selected().size(), teamMemberCount),
					(int) area.getPositionX(), (int) area.getPositionY(), (int) area.getSizeWidth(), GOLD);
			if (rosterMembers.isEmpty()) emptyMessage(graphics, ROSTER_LIST,
					appliedRosterSearch.isBlank() ? ConductorTexts.EMPTY_TEAM : ConductorTexts.NO_MATCHING_MEMBERS);
		}
		if (!skillsHidden) {
			UIElement title = ui.selectId(SKILL_TITLE, UIElement.class).findFirst().orElseThrow();
			textFit(graphics, Component.translatable(ConductorTexts.SKILLS), (int) title.getPositionX(),
					(int) title.getPositionY(), (int) title.getSizeWidth(), TEXT_COLOR);
			if (skillIds.isEmpty()) emptyMessage(graphics, SKILL_LIST, focused == null ? ConductorTexts.NO_SELECTION
					: Minecraft.getInstance().level == null || Minecraft.getInstance().level.getEntity(focused) == null ? ConductorTexts.REMOTE_SKILLS
					: appliedSkillSearch.isBlank() ? ConductorTexts.NO_SKILLS : ConductorTexts.NO_MATCHING_SKILLS);
		}
		for (String id : List.of(ROSTER_LIST, SKILL_LIST, SELECTED_LIST)) {
			if (ROSTER_LIST.equals(id) && rosterHidden || SKILL_LIST.equals(id) && skillsHidden) continue;
			ScrollerView list = ui.selectId(id, ScrollerView.class).findFirst().orElseThrow();
			int viewport = (int) list.viewPort.getContentHeight();
			int extent = (int) list.viewContainer.getSizeHeight();
			if (extent <= viewport || viewport <= 0) continue;
			int x = (int) list.viewPort.getPositionX();
			int y = (int) list.viewPort.getPositionY();
			float progress = list.verticalScroller.getNormalizedValue();
			int thumb = Math.max(SCROLLBAR_WIDTH, viewport * viewport / extent);
			int offset = (int) ((viewport - thumb) * progress);
			x += (int) list.viewPort.getSizeWidth();
			graphics.fill(x, y, x + SCROLLBAR_WIDTH, y + viewport, BG_0);
			graphics.fill(x + 1, y + offset, x + SCROLLBAR_WIDTH - 1, y + offset + thumb, LINE_INNER);
		}
	}

	private void emptyMessage(GuiGraphicsExtractor graphics, String listId, String key) {
		UIElement area = ui.selectId(listId, ScrollerView.class).findFirst().orElseThrow().viewPort;
		var font = Minecraft.getInstance().font;
		int width = Math.max(1, (int) area.getSizeWidth() - SECTION_PADDING * 2);
		var lines = font.split(Component.translatable(key), width);
		int x = (int) area.getPositionX() + SECTION_PADDING;
		int y = (int) area.getPositionY() + Math.max(SECTION_PADDING, ((int) area.getSizeHeight() - lines.size() * TEXT_LINE_HEIGHT) / 2);
		for (var line : lines) {
			graphics.text(font, line, x, y, MUTED_COLOR, false);
			y += TEXT_LINE_HEIGHT;
		}
	}

	private void renderMemberCards(GuiGraphicsExtractor graphics, Minecraft minecraft, String listId,
	                               Map<String, UUID> cards) {
		if (minecraft.level == null) return;
		ScrollerView list = ui.selectId(listId, ScrollerView.class).findFirst().orElseThrow();
		UIElement viewport = list.viewPort;
		boolean roster = ROSTER_LIST.equals(listId);
		int left = (int) viewport.getPositionX();
		int top = (int) viewport.getPositionY();
		int right = left + (int) viewport.getSizeWidth();
		int bottom = top + (int) viewport.getSizeHeight();
		graphics.enableScissor(left, top, right, bottom);
		for (Map.Entry<String, UUID> entry : cards.entrySet()) {
			Button card = cardPool.get(entry.getKey());
			if (card == null || card.getPositionX() >= right || card.getPositionX() + card.getSizeWidth() <= left
					|| card.getPositionY() >= bottom || card.getPositionY() + card.getSizeHeight() <= top) continue;
			UUID member = entry.getValue();
			Entity entity = minecraft.level.getEntity(member);
			int x = (int) card.getPositionX();
			int y = (int) card.getPositionY() + (hoveredId.equals(entry.getKey()) && !preferences.reduceMotion ? -1 : 0);
			int width = (int) card.getSizeWidth();
			int height = (int) card.getSizeHeight();
			int portraitSize = roster ? CARD_SIZE : height;
			boolean selected = ConductorControls.isSelected(member);
			int accent = selected ? selectionColor() : hoveredId.equals(entry.getKey()) ? TEXT_COLOR : LINE_OUTER;
			frame(graphics, x, y, width, height, hoveredId.equals(entry.getKey()) ? BG_3 : BG_2, accent, 0);
			if (entity instanceof LivingEntity living) {
				ConductorPortraitCache.card(graphics, member, living, x + BORDER, y + BORDER,
						portraitSize - BORDER * 2, portraitSize - BORDER * 2);
			} else {
				ConductorData.MemberInfo info = ConductorClient.snapshot().memberInfo(member);
				String value = info == null || info.name().isBlank() ? UNKNOWN_MARK
						: info.name().substring(0, info.name().offsetByCodePoints(0, 1));
				textFit(graphics, Component.literal(value), x + SECTION_PADDING, y + SECTION_PADDING, portraitSize - SECTION_PADDING * 2, MUTED_COLOR);
				graphics.fill(x + width - BORDER - 1, y + height - BORDER - 1, x + width - BORDER, y + height - BORDER, GOLD);
			}
			if (roster) {
				int textX = x + portraitSize + SECTION_GAP;
				int textY = y + SECTION_PADDING;
				int textWidth = width - portraitSize - SECTION_GAP - BORDER;
				textFit(graphics, memberName(member), textX, textY, textWidth, TEXT_COLOR);
				textY += TEXT_LINE_HEIGHT;
				textFit(graphics, memberDistance(member), textX, textY, textWidth, MUTED_COLOR);
				textY += TEXT_LINE_HEIGHT;
				textFit(graphics, memberDimension(member), textX, textY, textWidth, MUTED_COLOR);
			}
			if (keyboardFocus.equals(entry.getKey()))
				graphics.outline(x - 1, y - 1, width + BORDER, height + BORDER, GOLD);
		}
		graphics.disableScissor();
	}

	private void renderPortraitOverlays(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		if (minecraft.level == null) return;
		for (String listId : List.of(ROSTER_LIST, SELECTED_LIST)) {
			if (ROSTER_LIST.equals(listId) && rosterHidden) continue;
			UIElement viewport = ui.selectId(listId, ScrollerView.class).findFirst().orElseThrow().viewPort;
			graphics.enableScissor((int) viewport.getPositionX(), (int) viewport.getPositionY(),
					(int) (viewport.getPositionX() + viewport.getSizeWidth()), (int) (viewport.getPositionY() + viewport.getSizeHeight()));
			Map<String, UUID> members = ROSTER_LIST.equals(listId) ? rosterIds : selectedIds;
			for (var entry : members.entrySet()) {
				Button card = cardPool.get(entry.getKey());
				if (card == null) continue;
				int width = (int) card.getSizeWidth();
				int height = (int) card.getSizeHeight();
				int x = (int) card.getPositionX();
				int y = (int) card.getPositionY() + (hoveredId.equals(entry.getKey()) && !preferences.reduceMotion ? -1 : 0);
				if (minecraft.level.getEntity(entry.getValue()) instanceof LivingEntity living) {
					renderHealthBar(graphics, x + BORDER, y + height - HEALTH_HEIGHT - 1, width - BORDER * 2, living);
				} else {
					graphics.fill(x + BORDER, y + height - HEALTH_HEIGHT - 1, x + width - BORDER, y + height - 1, DISABLED_COLOR);
				}
				ConductorData.Unit unit = ConductorClient.unit(entry.getValue());
				ConductorData.Team team = unit == null ? null : ConductorClient.snapshot().team(unit.team());
				if (team != null)
					graphics.fill(x + width - BORDER * 2, y + BORDER, x + width - BORDER, y + BORDER * 2, team.color() | 0xFF000000);
				if (ConductorControls.isSelected(entry.getValue())) {
					graphics.fill(x + BORDER, y + BORDER + 1, x + BORDER + 1, y + BORDER + BORDER, GOLD);
					graphics.fill(x + BORDER + 1, y + BORDER + BORDER, x + BORDER + BORDER, y + BORDER + BORDER + 1, GOLD);
					graphics.fill(x + BORDER + BORDER, y + BORDER, x + BORDER + BORDER + 1, y + BORDER + BORDER, GOLD);
				}
				if (staticPortraits) graphics.fill(x + width - BORDER - 1,
						y + height - BORDER - 1, x + width - BORDER, y + height - BORDER, GOLD);
			}
			graphics.disableScissor();
		}
		List<UUID> selected = ConductorControls.selected();
		if (selected.size() > 1) {
			UIElement portrait = ui.selectId(FOCUS_PORTRAIT, UIElement.class).findFirst().orElseThrow();
			textFit(graphics, Component.literal(String.format(Locale.ROOT, COUNT_FORMAT, selected.size())),
					(int) portrait.getPositionX() + BORDER, (int) portrait.getPositionY() + focusSize - TEXT_LINE_HEIGHT,
					focusSize - BORDER * 2, GOLD);
		}
	}

	private int selectionColor() {
		return !preferences.reduceMotion && System.currentTimeMillis() / SELECTION_PHASE_MS % 2 != 0 ? ORE_GREEN_HI : ORE_GREEN;
	}

	private void healthBar(GuiGraphicsExtractor graphics, int x, int y, int width, UUID member, float ratio) {
		long now = System.currentTimeMillis();
		Float previous = healthRatios.put(member, ratio);
		if (previous != null && previous != ratio) {
			previousHealthRatios.put(member, previous);
			healthChangedAt.put(member, now);
		}
		float shown = ratio;
		if (!preferences.reduceMotion && now - healthChangedAt.getOrDefault(member, 0L) < ANIMATION_FRAME_MS) {
			shown = (previousHealthRatios.getOrDefault(member, ratio) + ratio) / 2;
		}
		int color = ratio > HEALTH_HIGH ? HP_GREEN : ratio >= HEALTH_LOW ? HP_YELLOW : HP_RED;
		int light = ratio > HEALTH_HIGH ? HP_GREEN_HI : ratio >= HEALTH_LOW ? HP_YELLOW_HI : HP_RED_HI;
		graphics.fill(x, y, x + width, y + HEALTH_HEIGHT, LINE_OUTER);
		int fill = Math.round(width * shown);
		if (fill > 0) {
			graphics.fill(x, y, x + fill, y + HEALTH_HEIGHT, color);
			graphics.fill(x, y, x + fill, y + 1, light);
		}
	}

	private void renderCommandIcons(GuiGraphicsExtractor graphics) {
		for (String id : List.of(MOVE, ATTACK, STOP, MANAGE, ASSIGN, RELEASE, ATTACK_MODE, BEHAVIOR_MODE, ACTIVITY_MODE,
				TEAM_SELECT, ROSTER_CLEAR, SKILL_CLEAR, ROSTER_TOGGLE, SKILL_TOGGLE,
				FORMATION_SCATTERED, FORMATION_REGULAR, FORMATION_UNIFORM)) {
			if (List.of(MANAGE, ASSIGN, RELEASE, TEAM_SELECT, ROSTER_CLEAR).contains(id) && rosterHidden
					|| SKILL_CLEAR.equals(id) && skillsHidden
					|| ROSTER_CLEAR.equals(id) && rosterSearch.isEmpty()
					|| SKILL_CLEAR.equals(id) && skillSearch.isEmpty()) continue;
			Button button = ui.selectId(id, Button.class).findFirst().orElseThrow();
			boolean hover = hoveredId.equals(id);
			boolean disabled = disabled(id);
			int dy = buttonOffset(id);
			int x = (int) button.getPositionX();
			int y = (int) button.getPositionY() + dy;
			int width = (int) button.getSizeWidth();
			int height = (int) button.getSizeHeight();
			int color = disabled ? DISABLED_COLOR : hover ? (ATTACK.equals(id) || STOP.equals(id) ? DANGER : ORE_GREEN_HI) : TEXT_COLOR;
			boolean handle = ROSTER_TOGGLE.equals(id) || SKILL_TOGGLE.equals(id);
			boolean formation = List.of(FORMATION_SCATTERED, FORMATION_REGULAR, FORMATION_UNIFORM).contains(id);
			boolean selectedMode = formation && preferences.formationMode == formationMode(id);
			frame(graphics, x, y, width, height, dy > 0 ? BG_0 : hover ? BG_3 : BG_2,
					selectedMode ? ORE_GREEN : hover ? color : LINE_OUTER, handle ? 0 : Math.max(1, BUTTON_DEPTH - dy));
			if (formation) {
				textFit(graphics, Component.translatable(formationKey(id)), x + SECTION_PADDING, y + SECTION_PADDING,
						width - SECTION_PADDING * 2, selectedMode ? ORE_GREEN_HI : color);
			} else if (TEAM_SELECT.equals(id) || ATTACK_MODE.equals(id) || BEHAVIOR_MODE.equals(id) || ACTIVITY_MODE.equals(id)) {
				textFit(graphics, hints.getOrDefault(id, Component.empty()), x + BORDER, y + SECTION_PADDING, width - BORDER * 2, color);
			} else {
				String glyph = id;
				if (ROSTER_CLEAR.equals(id) || SKILL_CLEAR.equals(id)) glyph = GLYPH_CLEAR;
				if (ROSTER_TOGGLE.equals(id)) glyph = rosterHidden ? GLYPH_RIGHT : GLYPH_LEFT;
				if (SKILL_TOGGLE.equals(id)) glyph = skillsHidden ? GLYPH_LEFT : GLYPH_RIGHT;
				int scale = width < BUTTON_SIZE ? 1 : ICON_PIXEL;
				drawGlyph(graphics, glyph, x + (width - ICON_GRID * scale) / 2, y + (height - ICON_GRID * scale) / 2, scale, color);
			}
			if (disabled) graphics.fill(x, y, x + width, y + height, DISABLED_MASK);
			if (keyboardFocus.equals(id)) graphics.outline(x - 1, y - 1, width + BORDER, height + BORDER, GOLD);
		}
		if (searchFocused || ROSTER_SEARCH.equals(keyboardFocus) || SKILL_SEARCH.equals(keyboardFocus)) {
			UIElement area = ui.selectId(searchFocused ? searchTarget : keyboardFocus, UIElement.class).findFirst().orElse(null);
			if (area != null) graphics.outline((int) area.getPositionX() - 1, (int) area.getPositionY() - 1,
					(int) area.getSizeWidth() + BORDER, (int) area.getSizeHeight() + BORDER, GOLD);
		}
	}

	private int buttonOffset(String id) {
		if (preferences.reduceMotion || !pressedId.equals(id)) return 0;
		if (releasedAt == 0) return BUTTON_DEPTH;
		long frame = (System.currentTimeMillis() - releasedAt) / ANIMATION_FRAME_MS;
		if (frame == 0) return -1;
		if (frame >= 2) pressedId = "";
		return 0;
	}

	private void renderFocus(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		if (minecraft.level == null) return;
		UIElement portrait = ui.selectId(FOCUS_PORTRAIT, UIElement.class).findFirst().orElseThrow();
		frame(graphics, portrait, BG_2, hoveredId.equals(FOCUS_PORTRAIT) ? GOLD : LINE_OUTER, 0);
		Entity entity = focused == null ? null : minecraft.level.getEntity(focused);
		if (entity instanceof LivingEntity living) {
			ConductorPortraitCache.focus(graphics, living, (int) portrait.getPositionX() + BORDER,
					(int) portrait.getPositionY() + BORDER, focusSize - BORDER * 2,
					0,
					hoveredId.equals(FOCUS_PORTRAIT) && !preferences.reduceMotion ? PORTRAIT_HOVER_SCALE : 1);
		} else textFit(graphics, Component.literal(UNKNOWN_MARK),
				(int) portrait.getPositionX() + SECTION_PADDING, (int) portrait.getPositionY() + SECTION_PADDING, focusSize, MUTED_COLOR);
		if (infoWidth < INFO_WIDTH) return;
		List<UUID> selected = ConductorControls.selected();
		List<Component> lines = new ArrayList<>();
		Component name = focused == null ? Component.translatable(ConductorTexts.NO_SELECTION) : memberName(focused);
		lines.add(selected.size() > 1 ? Component.translatable(ConductorTexts.SELECTED, selected.size()) : name);
		if (focused != null) lines.add(memberType(focused));
		if (selected.size() > 1) {
			float sum = 0;
			int known = 0;
			for (UUID member : selected) {
				if (minecraft.level.getEntity(member) instanceof LivingEntity living) {
					sum += Mth.clamp(living.getHealth() / Math.max(1, living.getMaxHealth()), 0, 1);
					known++;
				}
			}
			if (known > 0) {
				lines.add(Component.translatable(ConductorTexts.HEALTH_PERCENT, Math.round(sum / known * PERCENT)));
				lines.add(Component.translatable(ConductorTexts.KNOWN_HEALTH, known, selected.size()));
			} else lines.add(Component.translatable(ConductorTexts.UNKNOWN_HEALTH));
		} else if (entity instanceof LivingEntity living) {
			lines.add(Component.translatable(ConductorTexts.HEALTH, (int) Math.ceil(living.getHealth()), (int) Math.ceil(living.getMaxHealth())));
			lines.add(attributes(living));
		} else lines.add(Component.translatable(ConductorTexts.UNKNOWN_HEALTH));
		ConductorData.Unit unit = focused == null ? null : ConductorClient.unit(focused);
		if (unit != null && ConductorClient.snapshot().team(unit.team()) != null) {
			lines.add(Component.translatable(ConductorTexts.BEHAVIOR_VALUE, Component.translatable(combatBehaviorKey(unit.combatBehavior()))));
			lines.add(Component.translatable(ConductorTexts.ACTIVITY_VALUE, Component.translatable(behaviorKey(unit.behaviorState()))));
			lines.add(Component.translatable(unit.attackState() == ConductorData.AttackState.MANUAL
					? ConductorTexts.ATTACK_BASIC : ConductorTexts.ATTACK_AI));
		}
		ScrollerView details = ui.selectId(FOCUS_DETAILS, ScrollerView.class).findFirst().orElseThrow();
		int textWidth = lines.stream().mapToInt(minecraft.font::width).max().orElse(0);
		resizeInfoContent(details, textWidth, lines.size() * TEXT_LINE_HEIGHT);
		clipInfo(graphics, details);
		int textX = (int) details.viewContainer.getPositionX();
		int textY = (int) details.viewContainer.getPositionY();
		for (int index = 0; index < lines.size(); index++) {
			graphics.text(minecraft.font, lines.get(index), textX, textY + index * TEXT_LINE_HEIGHT,
					index == 0 ? TEXT_COLOR : MUTED_COLOR, false);
		}
		graphics.disableScissor();
		ScrollerView effects = ui.selectId(FOCUS_EFFECTS, ScrollerView.class).findFirst().orElseThrow();
		List<EffectIcon> icons = entity instanceof LivingEntity living ? effectIcons(living) : List.of();
		int columns = Math.min(FOCUS_EFFECT_COLUMNS, Math.max(1, icons.size()));
		int rows = (icons.size() + columns - 1) / columns;
		resizeInfoContent(effects, icons.isEmpty() ? 0 : columns * EFFECT_CELL_WIDTH, rows * EFFECT_CELL_HEIGHT);
		clipInfo(graphics, effects);
		int effectX = (int) effects.viewContainer.getPositionX();
		int effectY = (int) effects.viewContainer.getPositionY();
		for (int index = 0; index < icons.size(); index++) {
			int cellX = effectX + index % columns * EFFECT_CELL_WIDTH;
			int cellY = effectY + index / columns * EFFECT_CELL_HEIGHT;
			if (cellX + EFFECT_CELL_WIDTH <= effects.viewPort.getPositionX()
					|| cellX >= effects.viewPort.getPositionX() + effects.viewPort.getSizeWidth()
					|| cellY + EFFECT_CELL_HEIGHT <= effects.viewPort.getPositionY()
					|| cellY >= effects.viewPort.getPositionY() + effects.viewPort.getSizeHeight()) continue;
			drawEffectIcon(graphics, icons.get(index), cellX, cellY);
		}
		graphics.disableScissor();
	}

	private void selectSkill(String skill, boolean mouse) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = focused == null || minecraft.level == null ? null : minecraft.level.getEntity(focused);
		if (!(entity instanceof Mob mob)) return;
		ConductorTargeting targeting = ConductorTargetingResolver.find(mob, skill);
		if (targeting == null) return;
		ConductorControls.setPendingSkill(skill, focused);
		long now = System.currentTimeMillis();
		if (mouse && targeting.directional() && skill.equals(lastSkillClick)
				&& now - lastSkillClickTime < SKILL_DOUBLE_CLICK_MS) {
			ConductorControls.castFacing();
			lastSkillClick = "";
		} else {
			lastSkillClick = mouse ? skill : "";
			if (mouse && targeting.targetKind() == ConductorTargeting.TargetKind.SELF
					&& (!(targeting instanceof EntitySkillConductorAbility entitySkill) || entitySkill.canBeginCast(mob)))
				sendFocused(ConductorCommandPayload.Action.CAST, false, skill);
		}
		lastSkillClickTime = now;
	}

	public boolean handleSkillKey(KeyEvent event, int action) {
		if (searchFocused || teamDropdown || !modeDropdown.isEmpty() || ui == null) return false;
		int key = event.key();
		int index = key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 ? key - GLFW.GLFW_KEY_1
				: key == GLFW.GLFW_KEY_0 ? SKILL_HOTKEY_COUNT - 1
				: key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9 ? key - GLFW.GLFW_KEY_KP_1
				: key == GLFW.GLFW_KEY_KP_0 ? SKILL_HOTKEY_COUNT - 1 : -1;
		boolean previous = key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT;
		boolean next = key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD;
		if (index < 0 && !previous && !next) return false;
		if (action != GLFW.GLFW_PRESS || previousAbilities.isEmpty()) return true;
		if (previous || next) {
			int current = -1;
			for (int i = 0; i < previousAbilities.size(); i++)
				if (ConductorControls.isPendingSkill(previousAbilities.get(i).toString(), focused)) current = i;
			index = current < 0 ? (previous ? previousAbilities.size() - 1 : 0)
					: Math.floorMod(current + (previous ? -1 : 1), previousAbilities.size());
		}
		if (index < previousAbilities.size()) selectSkill(previousAbilities.get(index).toString(), false);
		return true;
	}

	public void renderSelectedSkill(GuiGraphicsExtractor graphics) {
		if (!modeDropdown.isEmpty()) return;
		UUID unit = ConductorControls.pendingSkillUnit();
		Minecraft minecraft = Minecraft.getInstance();
		if (unit == null || minecraft.level == null || !(minecraft.level.getEntity(unit) instanceof Mob mob)) return;
		Identifier id = Identifier.parse(ConductorControls.pendingSkill());
		ConductorAbility ability = ConductorUtil.ability(mob, id);
		if (ability == null) return;
		int remaining = ConductorControls.skillUnits(unit).stream()
				.filter(member -> ConductorClient.hasAbility(member, id))
				.mapToInt(member -> ConductorClient.abilityCooldownTicks(member, id)).min().orElse(0);
		List<Component> lines = new ArrayList<>(List.of(abilityName(ability), Component.translatable(ConductorTexts.COOLDOWN_REMAINING,
				String.format(Locale.ROOT, DECIMAL_FORMAT, remaining / (float) TICKS_PER_SECOND))));
		if (ability instanceof EntitySkillConductorAbility.Laser) lines.add(Component.translatable(ConductorTexts.RANGE,
				String.format(Locale.ROOT, RANGE_INTERVAL_FORMAT, LaserSkill.MINIMUM_RANGE, LaserSkill.RANGE)));
		appendSkillStatus(lines, mob, ability, remaining);
		drawTooltip(graphics, lines);
	}

	private void renderSkills(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (focused == null || minecraft.level == null || !(minecraft.level.getEntity(focused) instanceof Mob mob))
			return;
		UIElement viewport = ui.selectId(SKILL_LIST, ScrollerView.class).findFirst().orElseThrow().viewPort;
		int left = (int) viewport.getPositionX();
		int top = (int) viewport.getPositionY();
		int right = left + (int) viewport.getSizeWidth();
		int bottom = top + (int) viewport.getSizeHeight();
		graphics.enableScissor(left, top, right, bottom);
		for (Map.Entry<String, String> entry : skillIds.entrySet()) {
			Button button = ui.selectId(entry.getKey(), Button.class).findFirst().orElse(null);
			if (button == null || button.getPositionY() >= bottom || button.getPositionY() + SKILL_SIZE <= top)
				continue;
			ConductorAbility ability = ConductorUtil.ability(mob, Identifier.parse(entry.getValue()));
			if (ability == null) continue;
			int x = (int) button.getPositionX();
			int y = (int) button.getPositionY() + buttonOffset(entry.getKey());
			int width = (int) button.getSizeWidth();
			int remaining = ConductorClient.abilityCooldownTicks(mob.getUUID(), ability.id());
			boolean unavailable = remaining > 0 || !ability.isAvailable(mob)
					|| ability instanceof EntitySkillConductorAbility entitySkill && !entitySkill.canBeginCast(mob);
			int total = Math.max(ConductorClient.abilityCooldownTotalTicks(mob.getUUID(), ability.id()), ability.totalCooldownTicks(mob));
			String cooldownKey = focused + ability.id().toString();
			Integer previous = lastCooldowns.put(cooldownKey, remaining);
			if (previous != null && previous > 0 && remaining == 0)
				cooldownFlashes.put(cooldownKey, System.currentTimeMillis());
			frame(graphics, x, y, width, SKILL_SIZE, BG_2,
					ConductorControls.isPendingSkill(entry.getValue(), focused) ? ORE_GREEN_HI
							: hoveredId.equals(entry.getKey()) ? ORE_GREEN : LINE_OUTER, 0);
			String name = abilityName(ability).getString();
			int color = switch (ability.display(mob).abilityKind()) {
				case BASIC -> GOLD;
				case ATTACK -> DANGER;
				case SUPPORT -> INFO;
			};
			graphics.fill(x + BORDER, y + BORDER, x + BORDER + SKILL_ICON_SIZE, y + BORDER + SKILL_ICON_SIZE, BG_0);
			if (!name.isEmpty())
				graphics.text(minecraft.font, Component.literal(name.substring(0, name.offsetByCodePoints(0, 1))),
						x + BORDER + 1, y + BORDER + 1, unavailable ? DISABLED_COLOR : color, false);
			String time = remaining > 0 ? String.format(Locale.ROOT, DECIMAL_FORMAT, remaining / (float) TICKS_PER_SECOND) : "";
			int numberWidth = minecraft.font.width(time);
			textFit(graphics, Component.literal(name), x + SKILL_ICON_SIZE + SECTION_PADDING,
					y + SECTION_PADDING, width - SKILL_ICON_SIZE - SECTION_PADDING * 3 - numberWidth,
					unavailable ? DISABLED_COLOR : TEXT_COLOR);
			if (remaining > 0 && total > 0) {
				int mask = Math.min(SKILL_SIZE, (int) Math.ceil(SKILL_SIZE * (double) remaining / total));
				graphics.fill(x, y + SKILL_SIZE - mask, x + width, y + SKILL_SIZE, SKILL_COOLDOWN_MASK);
				graphics.text(minecraft.font, Component.literal(time), x + width - numberWidth - SECTION_PADDING,
						y + SECTION_PADDING, GOLD, false);
			}
			long flash = (System.currentTimeMillis() - cooldownFlashes.getOrDefault(cooldownKey, 0L)) / ANIMATION_FRAME_MS;
			if (!preferences.reduceMotion && flash < FLASH_FRAMES) {
				int line = SKILL_SIZE - BORDER - (int) flash * (SKILL_SIZE / FLASH_FRAMES);
				graphics.fill(x + BORDER, y + line, x + width - BORDER, y + line + 1, TEXT_COLOR);
				if (flash == FLASH_FRAMES - 1) graphics.outline(x, y, width, SKILL_SIZE, TEXT_COLOR);
			}
			if (keyboardFocus.equals(entry.getKey())) graphics.outline(x, y, width, SKILL_SIZE, GOLD);
		}
		graphics.disableScissor();
	}

	private void renderTooltipAtPointer(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (ConductorControls.pendingAction() == ConductorCommandPayload.Action.CAST && !contains(minecraft)) return;
		if (minecraft.level == null) return;
		String id = interactiveId(elementAtCurrentUI(minecraft));
		UUID member = rosterIds.get(id);
		if (member == null) member = selectedIds.get(id);
		if (FOCUS_PORTRAIT.equals(id)) member = focused;
		LivingEntity worldEntity = id.isEmpty() && !isHudElement(elementAtCurrentUI(minecraft)) ? ConductorControls.hoveredLivingEntity(minecraft) : null;
		String key = worldEntity == null ? id : worldEntity.getUUID().toString();
		if (!key.equals(tooltipId)) {
			tooltipId = key;
			tooltipSince = System.currentTimeMillis();
		}
		if (key.isEmpty() || System.currentTimeMillis() - tooltipSince < TOOLTIP_DELAY_MS) return;
		String skill = skillIds.get(id);
		if (skill != null && focused != null && minecraft.level.getEntity(focused) instanceof Mob mob) {
			ConductorAbility ability = ConductorUtil.ability(mob, Identifier.parse(skill));
			if (ability != null) renderSkillDetails(graphics, mob, ability);
			return;
		}
		if (member == null && worldEntity != null) member = worldEntity.getUUID();
		List<Component> lines = new ArrayList<>();
		List<EffectIcon> effects = List.of();
		if (member != null) {
			lines.add(memberName(member));
			lines.add(memberType(member));
			if (rosterIds.containsKey(id)) {
				lines.add(memberDistance(member));
				lines.add(memberDimension(member));
			}
			Entity entity = minecraft.level.getEntity(member);
			if (entity instanceof LivingEntity living) {
				lines.add(Component.translatable(ConductorTexts.HEALTH, (int) Math.ceil(living.getHealth()), (int) Math.ceil(living.getMaxHealth())));
				lines.add(attributes(living));
				effects = effectIcons(living);
			} else {
				lines.add(Component.translatable(ConductorTexts.UNKNOWN_HEALTH));
				lines.add(Component.translatable(ConductorTexts.UNKNOWN_ATTRIBUTES));
				lines.add(Component.translatable(ConductorTexts.REMOTE_FOCUS));
			}

			ConductorData.Unit unit = ConductorClient.unit(member);
			ConductorData.Team team = unit == null ? null : ConductorClient.snapshot().team(unit.team());
			lines.add(team == null ? Component.translatable(ConductorTexts.NO_TEAM) : Component.literal(team.name()).withColor(team.color()));
			if (unit != null && team != null) {
				lines.add(Component.translatable(orderKey(unit.order())));
				lines.add(Component.translatable(ConductorTexts.BEHAVIOR_VALUE, Component.translatable(combatBehaviorKey(unit.combatBehavior()))));
				lines.add(Component.translatable(ConductorTexts.ACTIVITY_VALUE, Component.translatable(behaviorKey(unit.behaviorState()))));
				lines.add(Component.translatable(unit.attackState() == ConductorData.AttackState.MANUAL
						? ConductorTexts.ATTACK_BASIC : ConductorTexts.ATTACK_AI));
				String result = switch (unit.moveResult()) {
					case NONE -> null;
					case ARRIVED -> ConductorTexts.MOVE_ARRIVED;
					case UNREACHABLE -> ConductorTexts.MOVE_UNREACHABLE;
					case CANCELED -> ConductorTexts.MOVE_CANCELED;
				};
				if (result != null) lines.add(Component.translatable(result));
			}
		} else if (hints.containsKey(id)) {
			lines.add(hints.get(id));
			if (disabled(id)) lines.add(Component.translatable(ConductorTexts.NO_SELECTION));
		} else if (ROSTER_SEARCH.equals(id) || SKILL_SEARCH.equals(id)) {
			lines.add(Component.translatable(ROSTER_SEARCH.equals(id) ? ConductorTexts.SEARCH : ConductorTexts.SEARCH_SKILLS));
		}
		if (FOCUS_PORTRAIT.equals(id) && ConductorControls.selected().size() > 1) {
			List<LivingEntity> known = ConductorControls.selected().stream().map(minecraft.level::getEntity)
					.filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList();
			if (!known.isEmpty()) {
				double average = known.stream().mapToDouble(entity -> Mth.clamp(
						entity.getHealth() / Math.max(1, entity.getMaxHealth()), 0, 1)).average().orElse(0);
				lines.add(Component.translatable(ConductorTexts.AVERAGE_HEALTH,
						Math.round(average * PERCENT), known.size(), ConductorControls.selected().size()));
			}
		}
		if (!lines.isEmpty()) drawTooltip(graphics, lines, effects);
	}

	private List<EffectIcon> effectIcons(LivingEntity living) {
		Map<MobEffectInstance, EffectProgress> durations = effectProgress.computeIfAbsent(living, ignored -> new IdentityHashMap<>());
		durations.keySet().removeIf(effect -> living.getEffect(effect.getEffect()) != effect);
		List<EffectIcon> icons = new ArrayList<>();
		for (MobEffectInstance effect : living.getActiveEffects()) {
			EffectProgress progress = durations.computeIfAbsent(effect, ignored -> new EffectProgress());
			int remaining = Math.max(0, effect.getDuration());
			if (progress.total == 0 || remaining > progress.previous || progress.amplifier != effect.getAmplifier()) {
				progress.total = Math.max(1, remaining);
			}
			progress.previous = remaining;
			progress.amplifier = effect.getAmplifier();
			icons.add(new EffectIcon(effect, effect.isInfiniteDuration() ? 1 : Mth.clamp((float) remaining / progress.total, 0, 1)));
		}
		return icons;
	}

	public void setFocusedMember(UUID member) {
		focused = member;
	}

	public String selectedTeam() {
		return teamSelection;
	}

	public UUID memberAtPointer(Minecraft minecraft) {
		if (getModularUI() == null || teamDropdown || !modeDropdown.isEmpty()) return null;
		String id = interactiveId(elementAtCurrentUI(minecraft));
		if (FOCUS_PORTRAIT.equals(id)) return focused;
		UUID member = rosterIds.get(id);
		return member == null ? selectedIds.get(id) : member;
	}

	public boolean click(Minecraft minecraft) {
		if (!modeDropdown.isEmpty()) {
			int index = modeAtPointer(minecraft);
			if (index >= 0) applyMode(index);
			modeDropdown = "";
			return true;
		}
		if (teamDropdown) {
			int index = teamAtPointer(minecraft);
			if (index >= 0) teamSelection = teams().get(index);
			teamDropdown = false;
			return true;
		}
		UIElement clicked = elementAt(minecraft);
		if (clicked == null) {
			searchFocused = false;
			return false;
		}
		String id = interactiveId(clicked);
		if (!ROSTER_SEARCH.equals(id) && !SKILL_SEARCH.equals(id)) searchFocused = false;
		boolean handled = activate(clicked, minecraft);
		UUID member = selectedIds.get(id);
		if (member == null) member = rosterIds.get(id);
		clickedMember = member != null && ConductorControls.selected().contains(member) ? member : null;
		clickX = minecraft.mouseHandler.xpos();
		clickY = minecraft.mouseHandler.ypos();
		return handled;
	}

	private boolean activate(UIElement clicked, Minecraft minecraft) {
		for (UIElement element = clicked; element != null; element = element.getParent()) {
			String id = element.getId();
			if (ROSTER_SEARCH.equals(id) || SKILL_SEARCH.equals(id)) {
				searchFocused = true;
				searchTarget = id;
				keyboardFocus = id;
				ConductorControls.releaseCameraKeys();
				return true;
			}
			if (ROSTER_CLEAR.equals(id) || SKILL_CLEAR.equals(id)) {
				searchTarget = ROSTER_CLEAR.equals(id) ? ROSTER_SEARCH : SKILL_SEARCH;
				keyboardFocus = searchTarget;
				setSearchQuery("");
				searchFocused = true;
				ConductorControls.releaseCameraKeys();
				return true;
			}
			if (element instanceof Button) {
				searchFocused = false;
				keyboardFocus = id;
				if (disabled(id)) return true;
				pressedId = id;
				releasedAt = 0;
				UUID member = selectedIds.get(id);
				if (member != null) {
					setFocusedMember(member);
					return true;
				}
				member = rosterIds.get(id);
				if (member != null) {
					if (minecraft.hasShiftDown()) ConductorControls.selectRange(rosterMembers, selectionAnchor, member);
					else ConductorControls.selectMember(member, minecraft.hasControlDown());
					selectionAnchor = member;
					if (ConductorControls.selected().contains(member)) setFocusedMember(member);
					return true;
				}
				if (MANAGE.equals(id)) {
					ConductorControls.openPanel();
					return true;
				}
				if (ROSTER_TOGGLE.equals(id)) {
					rosterCollapsed = !rosterHidden;
					preferSkillsOnNarrow = false;
					preferences.rosterCollapsed = rosterCollapsed;
					preferences.save();
					updateLayout();
					return true;
				}
				if (SKILL_TOGGLE.equals(id)) {
					skillsCollapsed = !skillsHidden;
					preferSkillsOnNarrow = true;
					preferences.skillsCollapsed = skillsCollapsed;
					preferences.save();
					updateLayout();
					return true;
				}
				if (MOVE.equals(id)) {
					if (hasRemoteSelection(minecraft)) ConductorControls.openPanel(ConductorLdlibScreen.Tab.REMOTE);
					else ConductorControls.setPending(ConductorCommandPayload.Action.MOVE);
					return true;
				}
				if (ATTACK.equals(id)) {
					if (hasRemoteSelection(minecraft)) ConductorControls.openPanel(ConductorLdlibScreen.Tab.REMOTE);
					else ConductorControls.setPending(ConductorCommandPayload.Action.ATTACK);
					return true;
				}
				if (STOP.equals(id)) {
					send(ConductorCommandPayload.Action.STOP, false, "");
					return true;
				}
				if (ATTACK_MODE.equals(id)) {
					openModeDropdown(id);
					return true;
				}
				if (List.of(FORMATION_SCATTERED, FORMATION_REGULAR, FORMATION_UNIFORM).contains(id)) {
					preferences.formationMode = formationMode(id);
					preferences.save();
					return true;
				}
				if (BEHAVIOR_MODE.equals(id) || ACTIVITY_MODE.equals(id)) {
					openModeDropdown(id);
					return true;
				}
				if (TEAM_SELECT.equals(id)) {
					teamDropdown = !teams().isEmpty();
					teamCursor = Math.max(0, teams().indexOf(teamSelection));
					teamScroll = Math.max(0, teamCursor - TEAM_DROPDOWN_ROWS + 1);
					return true;
				}
				if (ASSIGN.equals(id) || RELEASE.equals(id)) {
					send(ASSIGN.equals(id) ? ConductorCommandPayload.Action.ASSIGN
							: ConductorCommandPayload.Action.RELEASE, false, "");
					return true;
				}
				String skill = skillIds.get(id);
				if (skill != null) {
					selectSkill(skill, true);
					return true;
				}
			}
			if (BOTTOM.equals(id) || ROSTER_PANEL.equals(id) || SKILL_RAIL.equals(id)) return true;
		}
		return false;
	}

	public boolean handleSearchKey(KeyEvent event, int action) {
		if (!ConductorControls.active() || Minecraft.getInstance().screen != null) return false;
		if (!modeDropdown.isEmpty()) {
			if (action == GLFW.GLFW_RELEASE) return true;
			if (event.isEscape()) modeDropdown = "";
			else if (event.key() == GLFW.GLFW_KEY_ENTER) {
				applyMode(modeCursor);
				modeDropdown = "";
			} else if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_TAB) {
				int direction = event.key() == GLFW.GLFW_KEY_UP || Minecraft.getInstance().hasShiftDown() ? -1 : 1;
				modeCursor = Math.floorMod(modeCursor + direction, modeCount());
			}
			return true;
		}
		if (teamDropdown) {
			if (action == GLFW.GLFW_RELEASE) return true;
			if (event.isEscape()) teamDropdown = false;
			else if (event.key() == GLFW.GLFW_KEY_ENTER) {
				if (!teams().isEmpty()) teamSelection = teams().get(Math.min(teamCursor, teams().size() - 1));
				teamDropdown = false;
			} else if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_TAB) {
				int direction = event.key() == GLFW.GLFW_KEY_UP || Minecraft.getInstance().hasShiftDown() ? -1 : 1;
				teamCursor = Math.floorMod(teamCursor + direction, Math.max(1, teams().size()));
				teamScroll = Math.clamp(teamScroll, Math.max(0, teamCursor - TEAM_DROPDOWN_ROWS + 1), teamCursor);
			}
			return true;
		}
		if (event.isEscape()) {
			searchFocused = false;
			return false;
		}
		if (action == GLFW.GLFW_RELEASE) return searchFocused || event.key() == GLFW.GLFW_KEY_TAB;
		if (event.key() == GLFW.GLFW_KEY_TAB) {
			List<String> ids = focusableIds();
			if (ids.isEmpty()) return true;
			int index = ids.indexOf(keyboardFocus);
			int direction = Minecraft.getInstance().hasShiftDown() ? -1 : 1;
			keyboardFocus = ids.get(Math.floorMod(index + direction, ids.size()));
			revealKeyboardFocus();
			searchFocused = ROSTER_SEARCH.equals(keyboardFocus) || SKILL_SEARCH.equals(keyboardFocus);
			if (searchFocused) searchTarget = keyboardFocus;
			ConductorControls.releaseCameraKeys();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER) {
			if (searchFocused) searchFocused = false;
			else ui.selectId(keyboardFocus, UIElement.class).findFirst().ifPresent(element -> {
				activate(element, Minecraft.getInstance());
				releasedAt = System.currentTimeMillis();
			});
			return !keyboardFocus.isEmpty();
		}
		if (!searchFocused) return false;
		String query = currentSearch();
		if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
			setSearchQuery(query.substring(0, query.offsetByCodePoints(query.length(), -1)));
		} else if (event.key() == GLFW.GLFW_KEY_DELETE) setSearchQuery("");
		return true;
	}

	private String currentSearch() {
		return SKILL_SEARCH.equals(searchTarget) ? skillSearch : rosterSearch;
	}

	private List<String> focusableIds() {
		List<String> ids = new ArrayList<>();
		if (!rosterHidden) {
			ids.addAll(List.of(TEAM_SELECT, ASSIGN, RELEASE, MANAGE, ROSTER_SEARCH));
			if (!rosterSearch.isEmpty()) ids.add(ROSTER_CLEAR);
			rosterMembers.stream().map(member -> UNIT_PREFIX + member).forEach(ids::add);
		}
		ids.add(ROSTER_TOGGLE);
		if (!skillsHidden) {
			ids.add(SKILL_SEARCH);
			if (!skillSearch.isEmpty()) ids.add(SKILL_CLEAR);
			previousAbilities.stream().map(ability -> SKILL_PREFIX + ability).forEach(ids::add);
		}
		ids.add(SKILL_TOGGLE);
		ConductorControls.selected().stream().map(member -> SELECTED_PREFIX + member).forEach(ids::add);
		ids.addAll(List.of(MOVE, ATTACK, STOP, ATTACK_MODE, BEHAVIOR_MODE, ACTIVITY_MODE,
				FORMATION_SCATTERED, FORMATION_REGULAR, FORMATION_UNIFORM));
		return ids;
	}

	private void revealKeyboardFocus() {
		String listId;
		int index;
		int count;
		int columns = 1;
		int step = ROSTER_ROW_HEIGHT + CARD_GAP;
		if (keyboardFocus.startsWith(UNIT_PREFIX)) {
			listId = ROSTER_LIST;
			index = rosterMembers.indexOf(UUID.fromString(keyboardFocus.substring(UNIT_PREFIX.length())));
			count = rosterMembers.size();
			columns = Math.max(1, rosterColumns);
		} else if (keyboardFocus.startsWith(SELECTED_PREFIX)) {
			listId = SELECTED_LIST;
			List<UUID> selected = ConductorControls.selected();
			index = selected.indexOf(UUID.fromString(keyboardFocus.substring(SELECTED_PREFIX.length())));
			count = selected.size();
			columns = selectedColumns;
			step = selectedCardSize + CARD_GAP;
		} else if (keyboardFocus.startsWith(SKILL_PREFIX) && !SKILL_SEARCH.equals(keyboardFocus)
				&& !SKILL_CLEAR.equals(keyboardFocus) && !SKILL_TOGGLE.equals(keyboardFocus)) {
			listId = SKILL_LIST;
			index = previousAbilities.indexOf(Identifier.parse(keyboardFocus.substring(SKILL_PREFIX.length())));
			count = previousAbilities.size();
			step = SKILL_SIZE + BUTTON_GAP;
		} else return;
		if (index < 0) return;
		ScrollerView list = ui.selectId(listId, ScrollerView.class).findFirst().orElseThrow();
		int viewport = Math.max(1, (int) list.viewPort.getContentHeight());
		int extent = Math.max(0, ((count + columns - 1) / columns) * step
				- (SKILL_LIST.equals(listId) ? BUTTON_GAP : CARD_GAP));
		int range = Math.max(0, extent - viewport);
		int position = index / columns * step;
		float current = list.verticalScroller.getNormalizedValue() * range;
		float offset = position < current ? position : position + step > current + viewport ? position + step - viewport : current;
		float normalized = range == 0 ? 0 : Mth.clamp(offset / range, 0, 1);
		list.verticalScroller.setNormalizedValue(normalized, true);
		if (SKILL_LIST.equals(listId)) {
			mountedSkills = List.of();
			mountSkills();
		} else {
			mountedMembers.remove(listId);
			boolean selectedList = SELECTED_LIST.equals(listId);
			mountCards(listId, selectedList ? ConductorControls.selected() : rosterMembers, selectedList ? selectedIds : rosterIds, selectedList);
		}
	}

	private boolean disabled(String id) {
		if (ASSIGN.equals(id)) return ConductorControls.selected().isEmpty();
		if (List.of(MOVE, ATTACK, STOP, RELEASE).contains(id)) return ConductorControls.controlledSelection().isEmpty();
		if (ATTACK_MODE.equals(id) || BEHAVIOR_MODE.equals(id) || ACTIVITY_MODE.equals(id))
			return ConductorControls.controlledSelection().isEmpty();
		String skill = skillIds.get(id);
		return skill != null && ConductorControls.skillUnits(focused).stream().noneMatch(member ->
				ConductorClient.hasAbility(member, Identifier.parse(skill)));
	}

	public boolean release() {
		if (pressedId.isEmpty() || releasedAt != 0) return false;
		releasedAt = System.currentTimeMillis();
		Minecraft minecraft = Minecraft.getInstance();
		if (clickedMember != null && clickedMember.equals(memberAtPointer(minecraft))
				&& !ConductorControls.isDrag(clickX, clickY, minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos())) {
			ConductorControls.clickedUnit(clickedMember);
		} else if (clickedMember != null) ConductorControls.cancelDoubleClick();
		clickedMember = null;
		return true;
	}

	public boolean rightClick(Minecraft minecraft) {
		if (!modeDropdown.isEmpty()) {
			modeDropdown = "";
			return true;
		}
		if (teamDropdown) {
			teamDropdown = false;
			return true;
		}
		for (UIElement element = elementAt(minecraft); element != null; element = element.getParent()) {
			if (skillIds.containsKey(element.getId()) || SKILL_RAIL.equals(element.getId())) {
				if (ConductorControls.pendingAction() == ConductorCommandPayload.Action.CAST) {
					ConductorControls.setPending(null);
				}
				return true;
			}
			UUID member = selectedIds.get(element.getId());
			if (member != null) {
				if (!preferences.reduceMotion) {
					removedCards.put(member, new RemovedCard((int) element.getPositionX(), (int) element.getPositionY(), memberName(member)));
					removedAt.put(member, System.currentTimeMillis());
				}
				ConductorControls.removeMember(member);
				return true;
			}
		}
		return contains(minecraft);
	}

	public boolean reducedMotion() {
		return preferences.reduceMotion;
	}

	public void toggleReducedMotion() {
		preferences.reduceMotion = !preferences.reduceMotion;
		preferences.save();
	}

	public boolean handleCharTyped(long handle, CharacterEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!searchFocused || handle != minecraft.getWindow().handle() || minecraft.screen != null
				|| !event.isAllowedChatCharacter()) return false;
		appendSearchCharacter(event.codepoint());
		return true;
	}

	private void appendSearchCharacter(int codepoint) {
		String query = currentSearch();
		if (query.codePointCount(0, query.length()) >= SEARCH_LIMIT) return;
		setSearchQuery(query + new String(Character.toChars(codepoint)));
	}

	private void setSearchQuery(String query) {
		if (SKILL_SEARCH.equals(searchTarget)) skillSearch = query;
		else rosterSearch = query;
		searchChangedAt = query.isEmpty() ? 0 : System.currentTimeMillis();
	}

	public boolean scroll(Minecraft minecraft, double scrollDeltaY) {
		if (!modeDropdown.isEmpty()) {
			if (scrollDeltaY != 0) modeCursor = Math.floorMod(modeCursor + (scrollDeltaY > 0 ? -1 : 1), modeCount());
			return true;
		}
		if (teamDropdown) {
			teamScroll = Math.clamp(teamScroll + (scrollDeltaY > 0.0 ? -1 : 1), 0, Math.max(0, teams().size() - TEAM_DROPDOWN_ROWS));
			return true;
		}
		if (scrollDeltaY == 0.0D) return false;
		for (UIElement element = elementAt(minecraft); element != null; element = element.getParent()) {
			if (!(element instanceof ScrollerView scroller)) continue;
			float direction = (scrollDeltaY > 0.0D ? -1.0F : 1.0F)
					* (minecraft.hasShiftDown() ? SHIFT_SCROLL_MULTIPLIER : 1);
			switch (element.getId()) {
				case FOCUS_DETAILS, FOCUS_EFFECTS -> {
					boolean horizontal = minecraft.hasShiftDown();
					float extent = horizontal ? scroller.getContainerWidth() - scroller.viewPort.getContentWidth()
							: scroller.getContainerHeight() - scroller.viewPort.getContentHeight();
					if (extent > 0) {
						var axis = horizontal ? scroller.horizontalScroller : scroller.verticalScroller;
						float step = (scrollDeltaY > 0 ? -SCROLL_STEP : SCROLL_STEP) / extent;
						axis.setNormalizedValue(Mth.clamp(axis.getNormalizedValue() + step, 0, 1), true);
					}
					return true;
				}
				case ROSTER_LIST, SKILL_LIST, SELECTED_LIST -> {
					scrollList(scroller, direction);
					return true;
				}
				default -> {
				}
			}
		}
		return contains(minecraft);
	}

	private ConductorData.Unit selectedModeUnit() {
		List<UUID> units = ConductorControls.controlledSelection();
		if (units.isEmpty()) return null;
		return ConductorClient.unit(units.contains(focused) ? focused : units.getFirst());
	}

	private void send(ConductorCommandPayload.Action action, boolean flag, String skill) {
		String team = action == ConductorCommandPayload.Action.ASSIGN ? teamSelection : ConductorControls.selectedTeam();
		if (team.isBlank()) team = teamSelection;
		if (!team.isBlank() && !ConductorControls.selected().isEmpty()) {
			ConductorClient.send(action, team, "", action == ConductorCommandPayload.Action.ASSIGN
							? ConductorControls.selected() : ConductorControls.controlledSelection(), null,
					0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, flag, skill);
		}
	}

	private void sendFocused(ConductorCommandPayload.Action action, boolean flag, String skill) {
		List<UUID> units = action == ConductorCommandPayload.Action.CAST
				? ConductorControls.skillUnits(focused) : focused == null ? List.of() : List.of(focused);
		ConductorData.Unit unit = units.isEmpty() ? null : ConductorClient.unit(units.getFirst());
		if (unit != null) {
			ConductorClient.send(action, unit.team(), "", units, null,
					0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, flag, skill);
		}
	}

	public boolean contains(Minecraft minecraft) {
		return teamDropdown || !modeDropdown.isEmpty() || isHudElement(elementAt(minecraft));
	}

	private void openModeDropdown(String id) {
		modeDropdown = id;
		teamDropdown = false;
		ConductorControls.releaseCameraKeys();
		modeCursor = currentModeIndex();
	}

	private int modeCount() {
		return ATTACK_MODE.equals(modeDropdown) ? ATTACK_OPTIONS.size()
				: ACTIVITY_MODE.equals(modeDropdown) ? ACTIVITY_OPTIONS.size() : BEHAVIOR_OPTIONS.size();
	}

	private int currentModeIndex() {
		ConductorData.Unit unit = selectedModeUnit();
		if (unit == null) return 0;
		if (ATTACK_MODE.equals(modeDropdown)) return ATTACK_OPTIONS.indexOf(unit.attackState());
		if (BEHAVIOR_MODE.equals(modeDropdown)) return BEHAVIOR_OPTIONS.indexOf(unit.combatBehavior());
		for (int index = 0; index < ACTIVITY_OPTIONS.size(); index++) {
			if (ACTIVITY_OPTIONS.get(index).behaviorState() == unit.behaviorState()) return index;
		}
		return 0;
	}

	private Component modeLabel(int index) {
		if (ATTACK_MODE.equals(modeDropdown))
			return Component.translatable(ATTACK_OPTIONS.get(index) == ConductorData.AttackState.MANUAL
					? ConductorTexts.ATTACK_BASIC : ConductorTexts.ATTACK_AI);
		return ACTIVITY_MODE.equals(modeDropdown) ? Component.translatable(ConductorTexts.ACTIVITY_VALUE,
				Component.translatable(behaviorKey(ACTIVITY_OPTIONS.get(index).behaviorState())))
				: Component.translatable(ConductorTexts.BEHAVIOR_VALUE, Component.translatable(combatBehaviorKey(BEHAVIOR_OPTIONS.get(index))));
	}

	private void applyMode(int index) {
		ConductorData.Unit unit = selectedModeUnit();
		if (unit == null || index < 0 || index >= modeCount()) return;
		if (ATTACK_MODE.equals(modeDropdown)) {
			send(ConductorCommandPayload.Action.SET_ATTACK_MODE, ATTACK_OPTIONS.get(index) == ConductorData.AttackState.MANUAL, "");
		} else if (BEHAVIOR_MODE.equals(modeDropdown)) {
			ConductorClient.setCombatBehavior(unit.team(), ConductorControls.controlledSelection(), BEHAVIOR_OPTIONS.get(index));
		} else {
			ConductorClient.send(ConductorCommandPayload.Action.SET_MODE, unit.team(), "",
					ConductorControls.controlledSelection(), null, 0.0D, 0.0D, 0.0D, ACTIVITY_OPTIONS.get(index), false, "");
		}
	}

	private ModeBounds modeBounds(Minecraft minecraft) {
		UIElement button = ui.selectId(modeDropdown, Button.class).findFirst().orElseThrow();
		int width = (int) button.getSizeWidth();
		for (int index = 0; index < modeCount(); index++)
			width = Math.max(width, minecraft.font.width(modeLabel(index)) + SECTION_PADDING * 2);
		width = Math.min(width, minecraft.getWindow().getGuiScaledWidth());
		int height = modeCount() * HEADER_HEIGHT;
		int x = Math.clamp((int) button.getPositionX(), 0, Math.max(0, minecraft.getWindow().getGuiScaledWidth() - width));
		int y = (int) (button.getPositionY() + button.getSizeHeight());
		if (y + height > minecraft.getWindow().getGuiScaledHeight()) y = (int) button.getPositionY() - height;
		y = Math.clamp(y, 0, Math.max(0, minecraft.getWindow().getGuiScaledHeight() - height));
		return new ModeBounds(x, y, width, height);
	}

	private int modeAtPointer(Minecraft minecraft) {
		ModeBounds bounds = modeBounds(minecraft);
		double x = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getWidth();
		double y = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getHeight();
		return x >= bounds.x() && x < bounds.x() + bounds.width() && y >= bounds.y() && y < bounds.y() + bounds.height()
				? (int) ((y - bounds.y()) / HEADER_HEIGHT) : -1;
	}

	private void renderModeDropdown(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		ModeBounds bounds = modeBounds(minecraft);
		int hovered = modeAtPointer(minecraft);
		int current = currentModeIndex();
		graphics.nextStratum();
		for (int index = 0; index < modeCount(); index++) {
			int y = bounds.y() + index * HEADER_HEIGHT;
			frame(graphics, bounds.x(), y, bounds.width(), HEADER_HEIGHT,
					index == hovered || index == modeCursor ? BG_3 : BG_1, index == current ? ORE_GREEN : LINE_OUTER, 0);
			textFit(graphics, modeLabel(index), bounds.x() + SECTION_PADDING, y + SECTION_PADDING,
					bounds.width() - SECTION_PADDING * 2, index == current ? ORE_GREEN_HI : TEXT_COLOR);
		}
	}

	private List<String> teams() {
		if (teamNamesRevision != ConductorClient.revision()) {
			teamNames = ConductorClient.snapshot().teams().keySet().stream().sorted().toList();
			teamNamesRevision = ConductorClient.revision();
		}
		return teamNames;
	}

	private int dropdownY() {
		UIElement header = ui.selectId(TEAM_SELECT, UIElement.class).findFirst().orElseThrow();
		int height = Math.min(TEAM_DROPDOWN_ROWS, teams().size()) * HEADER_HEIGHT;
		return Math.max(0, Math.min((int) (header.getPositionY() + header.getSizeHeight()),
				Minecraft.getInstance().getWindow().getGuiScaledHeight() - height));
	}

	private int teamAtPointer(Minecraft minecraft) {
		UIElement header = ui.selectId(TEAM_SELECT, UIElement.class).findFirst().orElseThrow();
		double x = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getWidth();
		double y = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getHeight();
		int row = (int) ((y - dropdownY()) / HEADER_HEIGHT);
		return x >= header.getPositionX() && x < header.getPositionX() + MAX_ROSTER_WIDTH
				&& y >= dropdownY() && row < TEAM_DROPDOWN_ROWS && row + teamScroll < teams().size() ? row + teamScroll : -1;
	}

	private void renderTeamDropdown(GuiGraphicsExtractor graphics) {
		UIElement header = ui.selectId(TEAM_SELECT, UIElement.class).findFirst().orElseThrow();
		int x = (int) header.getPositionX();
		int top = dropdownY();
		List<String> teams = teams();
		teamScroll = Math.min(teamScroll, Math.max(0, teams.size() - TEAM_DROPDOWN_ROWS));
		graphics.nextStratum();
		for (int index = teamScroll; index < Math.min(teams.size(), teamScroll + TEAM_DROPDOWN_ROWS); index++) {
			int y = top + (index - teamScroll) * HEADER_HEIGHT;
			ConductorData.Team team = ConductorClient.snapshot().team(teams.get(index));
			frame(graphics, x, y, MAX_ROSTER_WIDTH, HEADER_HEIGHT, index == teamCursor ? BG_3 : BG_1,
					team.name().equals(teamSelection) ? ORE_GREEN : LINE_OUTER, 0);
			graphics.fill(x + BORDER, y + BORDER, x + BORDER * 2, y + HEADER_HEIGHT - BORDER, team.color() | 0xFF000000);
			textFit(graphics, Component.literal(team.name()), x + BORDER * 3, y + SECTION_PADDING,
					MAX_ROSTER_WIDTH - BORDER * 4, TEXT_COLOR);
		}
	}

	private boolean isHudElement(UIElement clicked) {
		for (UIElement element = clicked; element != null; element = element.getParent()) {
			String id = element.getId();
			if (MANAGE.equals(id) || BOTTOM.equals(id) || ROSTER_PANEL.equals(id) || ROSTER_TOGGLE.equals(id)
					|| SKILL_RAIL.equals(id) || SKILL_TOGGLE.equals(id) || ROSTER_SEARCH.equals(id)) return true;
		}
		return false;
	}

	public @Nullable HoveredSkill hoveredSkill(Minecraft minecraft) {
		if (!modeDropdown.isEmpty()) return null;
		if (!ConductorControls.active() || minecraft.screen != null || minecraft.level == null || ui == null)
			return null;
		for (UIElement element = elementAtCurrentUI(minecraft); element != null; element = element.getParent()) {
			String skill = skillIds.get(element.getId());
			if (skill == null || focused == null || !(minecraft.level.getEntity(focused) instanceof Mob mob)) continue;
			ConductorTargeting targeting = ConductorTargetingResolver.find(mob, skill);
			return targeting == null || !ConductorClient.hasAbility(mob.getUUID(), Identifier.parse(skill))
					? null : new HoveredSkill(mob, targeting);
		}
		return null;
	}

	private UIElement elementAt(Minecraft minecraft) {
		if (getModularUI() == null) return null;
		return elementAtCurrentUI(minecraft);
	}

	private UIElement elementAtCurrentUI(Minecraft minecraft) {
		double x = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
				/ minecraft.getWindow().getWidth();
		double y = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
				/ minecraft.getWindow().getHeight();
		var hit = ui.rootElement.hitTest(x, y);
		return hit == null ? null : hit.getA();
	}

	public record HoveredSkill(Mob mob, ConductorTargeting targeting) {
	}

	private record EffectIcon(MobEffectInstance effect, float remaining) {
	}

	private static class EffectProgress {
		private int previous;
		private int total;
		private int amplifier;
	}

	private record RemovedCard(int x, int y, Component name) {
	}

	private record ModeBounds(int x, int y, int width, int height) {
	}
}
