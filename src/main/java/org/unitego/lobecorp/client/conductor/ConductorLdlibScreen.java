package org.unitego.lobecorp.client.conductor;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.math.Size;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.unitego.lobecorp.conductor.ability.ConductorAbility;
import org.unitego.lobecorp.conductor.ability.ConductorTargeting;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.conductor.data.ConductorDirectory;
import org.unitego.lobecorp.util.ConductorUtil;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;

import java.util.*;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;
import static org.unitego.lobecorp.client.conductor.ConductorHudTheme.*;

public class ConductorLdlibScreen extends ModularUIScreen {
	private static final int PANEL_WIDTH = 520;
	private static final int PANEL_HEIGHT = 340;
	private static final int SCREEN_MARGIN = 24;
	private static final int COLOR_MASK = 0xFFFFFF;
	private static final int COLOR_LENGTH = 6;
	private static final int ROOT_PADDING = 12;
	private static final int ROOT_GAP = 6;
	private static final int CONTENT_PADDING = 8;
	private static final int CONTENT_GAP = 6;
	private static final int ROW_HEIGHT = 28;
	private static final int ROW_GAP = 6;
	private static final int LABEL_HEIGHT = 18;
	private static final int CONTROL_HEIGHT = 26;
	private static final int LIST_BUTTON_HEIGHT = 26;
	private final UI ui;
	private final Map<String, List<Component>> tooltips = new HashMap<>();
	private String team = "";
	private Tab tab;
	private int revision;
	private UIElement tooltipElement;
	private long tooltipSince;
	private ConductorLdlibScreen(UI ui, Tab tab) {
		super(ModularUI.of(ui), Component.translatable(ConductorTexts.TITLE));
		this.ui = ui;
		modularUI.setDrawTooltips(false);
		this.team = ConductorControls.selectedTeam();
		bind("tab-teams", () -> setTab(Tab.TEAMS));
		bind("tab-units", () -> setTab(Tab.UNITS));
		bind("tab-remote", () -> setTab(Tab.REMOTE));
		revision = ConductorClient.revision();
		bind("create", () -> {
			String name = field("team-name").getValue().trim();
			if (!name.isEmpty()) {
				team = name;
				send(ConductorCommandPayload.Action.CREATE_TEAM, "", null, 0, 0, 0,
						ConductorData.ControlMode.FULL, false, "");
			}
		});
		bind("assign", () -> send(ConductorCommandPayload.Action.ASSIGN, "", null, 0, 0, 0,
				ConductorData.ControlMode.FULL, false, ""));
		bind("release", () -> send(ConductorCommandPayload.Action.RELEASE, "", null, 0, 0, 0,
				ConductorData.ControlMode.FULL, false, ""));
		bind("move", () -> prepare(ConductorCommandPayload.Action.MOVE));
		bind("attack", () -> prepare(ConductorCommandPayload.Action.ATTACK));
		bind("full", () -> setBehavior(ConductorData.CombatBehavior.ACTIVE));
		bind("passive", () -> setBehavior(ConductorData.CombatBehavior.PASSIVE));
		bind("neutral", () -> setBehavior(ConductorData.CombatBehavior.NEUTRAL));
		bind("command", () -> setMode(ConductorData.ControlMode.COMMAND));
		bind("soft", () -> setMode(ConductorData.ControlMode.SOFT));
		bind("attack-mode", this::toggleAttackMode);
		bind("war", () -> hostility(true));
		bind("peace", () -> hostility(false));
		bind("force-load", () -> {
			ConductorData.Team selected = ConductorClient.snapshot().team(team);
			send(ConductorCommandPayload.Action.FORCE_LOAD, "", null, 0, 0, 0,
					ConductorData.ControlMode.FULL, selected == null || !selected.forceLoad(), "");
		});
		bind("apply-color", () -> {
			try {
				int color = Integer.parseInt(field("color-hex").getValue().replace("#", ""), 16);
				if ((color & ~COLOR_MASK) == 0) {
					ConductorClient.setColor(team, color);
				}
			} catch (NumberFormatException ignored) {
			}
		});
		bind("remote-move", this::remoteMove);
		bind("remote-attack", this::remoteAttack);
		bind("remote-cast", this::remoteCast);
		bind(ConductorHudTheme.REDUCE_MOTION, () -> {
			ConductorHud.INSTANCE.toggleReducedMotion();
			updateMotionButton();
		});
		bind("close", this::onClose);
		refresh();
		updateMotionButton();
		setTab(tab);
	}

	public static ConductorLdlibScreen create() {
		return create(Tab.TEAMS);
	}

	public static ConductorLdlibScreen create(Tab tab) {
		return new ConductorLdlibScreen(UI.of(createLayout(), size -> Size.of(
				Math.max(1, Math.min(PANEL_WIDTH, size.width - SCREEN_MARGIN)),
				Math.max(1, Math.min(PANEL_HEIGHT, size.height - SCREEN_MARGIN)))), tab);
	}

	private static IGuiTexture surface(int background, int border) {
		return IGuiTexture.group(new ColorRectTexture(background), new ColorBorderTexture(BORDER + SELECTED_PADDING, EDGE_LIGHT),
				new ColorBorderTexture(BORDER, border));
	}

	private static UIElement createLayout() {
		UIElement root = new UIElement();
		root.layout(layout -> layout.widthPercent(100).heightPercent(100).paddingAll(ROOT_PADDING)
				.flexDirection(FlexDirection.COLUMN).gapAll(ROOT_GAP));
		root.style(style -> style.backgroundTexture(surface(BG_1, LINE_OUTER)));

		UIElement heading = row(label("", ConductorTexts.TITLE), label("selected-count", ConductorTexts.SELECTED));
		heading.layout(layout -> layout.height(24));
		root.addChild(heading);
		root.addChild(row(button("tab-teams", ConductorTexts.TEAM_MANAGEMENT),
				button("tab-units", ConductorTexts.CONTROLS),
				button("tab-remote", ConductorTexts.REMOTE_ORDERS)));

		ScrollerView teams = page("page-teams");
		teams.addScrollViewChild(row(newField("team-name"), button("create", ConductorTexts.CREATE)));
		teams.addScrollViewChild(button(ConductorHudTheme.REDUCE_MOTION, ConductorTexts.REDUCE_MOTION_OFF));
		teams.addScrollViewChild(label("", ConductorTexts.TEAM_MANAGEMENT));
		teams.addScrollViewChild(list("team-list"));
		teams.addScrollViewChild(label("", ConductorTexts.COLOR));
		teams.addScrollViewChild(row(newField("color-hex"), button("apply-color", ConductorTexts.APPLY_COLOR)));
		teams.addScrollViewChild(label("", ConductorTexts.ENEMY_TEAM));
		teams.addScrollViewChild(newField("enemy-team"));
		teams.addScrollViewChild(row(button("war", ConductorTexts.WAR), button("peace", ConductorTexts.PEACE)));
		teams.addScrollViewChild(button("force-load", ConductorTexts.LOAD_OFF));
		teams.addScrollViewChild(row(button("assign", ConductorTexts.ASSIGN),
				button("release", ConductorTexts.RELEASE)));

		ScrollerView units = page("page-units");
		units.addScrollViewChild(label("", ConductorTexts.BEHAVIOR_MODE));
		units.addScrollViewChild(row(button("full", ConductorTexts.BEHAVIOR_ACTIVE),
				button("passive", ConductorTexts.BEHAVIOR_PASSIVE), button("neutral", ConductorTexts.BEHAVIOR_NEUTRAL)));
		units.addScrollViewChild(label("", ConductorTexts.ACTIVITY_MODE));
		units.addScrollViewChild(row(button("command", ConductorTexts.COMMAND), button("soft", ConductorTexts.SOFT)));
		units.addScrollViewChild(button("attack-mode", ConductorTexts.ATTACK_MODE));
		units.addScrollViewChild(row(button("move", ConductorTexts.MOVE), button("attack", ConductorTexts.ATTACK)));
		units.addScrollViewChild(label("", ConductorTexts.SKILLS));
		units.addScrollViewChild(list("skill-list"));

		ScrollerView remote = page("page-remote");
		remote.addScrollViewChild(label("", ConductorTexts.REMOTE_ORDERS));
		remote.addScrollViewChild(row(newField("x"), newField("y"), newField("z")));
		remote.addScrollViewChild(button("remote-move", ConductorTexts.REMOTE_MOVE));
		remote.addScrollViewChild(label("", ConductorTexts.TARGET_UUID));
		remote.addScrollViewChild(newField("target-uuid"));
		remote.addScrollViewChild(button("remote-attack", ConductorTexts.REMOTE_ATTACK));
		remote.addScrollViewChild(label("", ConductorTexts.SKILL_ID));
		remote.addScrollViewChild(newField("skill-id"));
		remote.addScrollViewChild(button("remote-cast", ConductorTexts.REMOTE_CAST));

		Button close = button("close", ConductorTexts.CLOSE);
		close.layout(layout -> layout.height(CONTROL_HEIGHT));
		root.addChildren(teams, units, remote, close);
		return root;
	}

	private static ScrollerView page(String id) {
		ScrollerView page = new ScrollerView();
		page.setId(id);
		page.layout(layout -> layout.widthPercent(100).height(0).flexGrow(1).flexShrink(1));
		page.style(style -> style.backgroundTexture(surface(BG_2, LINE_INNER)));
		page.getScrollerViewStyle().mode(ScrollerMode.VERTICAL)
				.adaptiveWidth(false).adaptiveHeight(false)
				.verticalScrollDisplay(ScrollDisplay.AUTO)
				.horizontalScrollDisplay(ScrollDisplay.NEVER);
		page.viewPort.layout(layout -> layout.paddingAll(CONTENT_PADDING));
		page.viewPort.style(style -> style.backgroundTexture(surface(BG_1, LINE_OUTER)));
		page.verticalScroller.headButton.setDisplay(false);
		page.verticalScroller.tailButton.setDisplay(false);
		page.verticalScroller.scrollBar.buttonStyle(style -> style
				.baseTexture(surface(BG_2, LINE_OUTER))
				.hoverTexture(surface(BG_3, ORE_GREEN_HI))
				.pressedTexture(surface(BG_0, ORE_GREEN)));
		page.viewContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN).gapAll(CONTENT_GAP));
		return page;
	}

	private static UIElement list(String id) {
		UIElement list = new UIElement();
		list.setId(id);
		list.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(CONTENT_GAP));
		list.style(style -> style.backgroundTexture(surface(BG_1, LINE_OUTER)));
		return list;
	}

	private static UIElement row(UIElement... children) {
		UIElement row = new UIElement();
		row.layout(layout -> layout.widthPercent(100).height(ROW_HEIGHT)
				.flexDirection(FlexDirection.ROW).gapAll(ROW_GAP));
		for (UIElement child : children) {
			child.layout(layout -> layout.flexGrow(1));
			row.addChild(child);
		}
		return row;
	}

	private static Label label(String id, String key) {
		Label label = new Label();
		label.setId(id);
		label.setText(Component.translatable(key));
		label.layout(layout -> layout.height(LABEL_HEIGHT).paddingAll(2));
		return label;
	}

	private static TextField newField(String id) {
		TextField field = new TextField();
		field.setId(id);
		field.setAnyString();
		field.layout(layout -> layout.height(CONTROL_HEIGHT).paddingAll(3));
		field.style(style -> style.backgroundTexture(surface(BG_1, LINE_OUTER)));
		field.textFieldStyle(style -> style.textColor(TEXT_COLOR).cursorColor(GOLD)
				.errorColor(DANGER));
		String placeholder = switch (id) {
			case "team-name" -> ConductorTexts.TEAM;
			case "color-hex" -> ConductorTexts.COLOR;
			case "enemy-team" -> ConductorTexts.ENEMY_TEAM;
			case "x" -> ConductorTexts.X;
			case "y" -> ConductorTexts.Y;
			case "z" -> ConductorTexts.Z;
			case "target-uuid" -> ConductorTexts.TARGET_UUID;
			case "skill-id" -> ConductorTexts.SKILL_ID;
			default -> null;
		};
		if (placeholder != null) {
			field.textFieldStyle(style -> style.placeholder(Component.translatable(placeholder)));
		}
		return field;
	}

	private static Button button(String id, String key) {
		Button button = new Button();
		button.setId(id);
		button.setText(Component.translatable(key));
		button.layout(layout -> layout.height(CONTROL_HEIGHT).paddingAll(0).flexShrink(0));
		button.buttonStyle(style -> style.baseTexture(surface(BG_2, LINE_OUTER))
				.hoverTexture(surface(BG_3, ORE_GREEN_HI))
				.pressedTexture(surface(BG_0, ORE_GREEN)));
		return button;
	}

	private void updateMotionButton() {
		ui.selectId(ConductorHudTheme.REDUCE_MOTION, Button.class).findFirst().orElseThrow().setText(
				Component.translatable(ConductorHud.INSTANCE.reducedMotion()
						? ConductorTexts.REDUCE_MOTION_ON : ConductorTexts.REDUCE_MOTION_OFF));
	}

	private void setTab(Tab nextTab) {
		tab = nextTab;
		ui.selectId("page-teams", UIElement.class).findFirst().orElseThrow().setDisplay(nextTab == Tab.TEAMS);
		ui.selectId("page-units", UIElement.class).findFirst().orElseThrow().setDisplay(nextTab == Tab.UNITS);
		ui.selectId("page-remote", UIElement.class).findFirst().orElseThrow().setDisplay(nextTab == Tab.REMOTE);
		selectTabButton("tab-teams", nextTab == Tab.TEAMS);
		selectTabButton("tab-units", nextTab == Tab.UNITS);
		selectTabButton("tab-remote", nextTab == Tab.REMOTE);
	}

	private void selectTabButton(String id, boolean selected) {
		ui.selectId(id, Button.class).findFirst().orElseThrow().buttonStyle(style -> style
				.baseTexture(surface(selected ? BG_3 : BG_2, selected ? ORE_GREEN : LINE_OUTER))
				.hoverTexture(surface(BG_3, ORE_GREEN_HI))
				.pressedTexture(surface(BG_0, ORE_GREEN)));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		UIElement hovered = modularUI.hitTestAtScreen(mouseX, mouseY);
		while (hovered != null && !tooltips.containsKey(hovered.getId())) hovered = hovered.getParent();
		if (hovered != tooltipElement) {
			tooltipElement = hovered;
			tooltipSince = System.currentTimeMillis();
		}
		if (hovered != null && System.currentTimeMillis() - tooltipSince >= TOOLTIP_DELAY_MS)
			ConductorHud.drawTooltip(graphics, tooltips.get(hovered.getId()));
	}

	private void bind(String id, Runnable action) {
		ui.selectId(id, Button.class).findFirst().orElseThrow().setOnClick(event -> action.run());
	}

	private TextField field(String id) {
		return ui.selectId(id, TextField.class).findFirst().orElseThrow();
	}

	private void send(ConductorCommandPayload.Action action, String otherTeam, UUID target,
	                  double x, double y, double z, ConductorData.ControlMode mode, boolean flag, String skill) {
		ConductorClient.send(action, team, otherTeam, ConductorControls.selected(), target,
				x, y, z, mode, flag, skill);
	}

	private void prepare(ConductorCommandPayload.Action action) {
		ConductorControls.setPending(action);
		onClose();
	}

	private void setBehavior(ConductorData.CombatBehavior behavior) {
		ConductorClient.setCombatBehavior(team, ConductorControls.selected(), behavior);
	}

	private void setMode(ConductorData.ControlMode mode) {
		send(ConductorCommandPayload.Action.SET_MODE, "", null, 0, 0, 0, mode, false, "");
	}

	private void toggleAttackMode() {
		ConductorData.Unit first = firstSelectedUnit();
		if (first != null) {
			ConductorClient.send(ConductorCommandPayload.Action.SET_ATTACK_MODE, first.team(), "",
					ConductorControls.selected(), null, 0.0D, 0.0D, 0.0D,
					ConductorData.ControlMode.FULL, first.attackState() == ConductorData.AttackState.AUTO, "");
		}
	}

	private ConductorData.Unit firstSelectedUnit() {
		for (UUID uuid : ConductorControls.selected()) {
			ConductorData.Unit unit = ConductorClient.unit(uuid);
			if (unit != null) return unit;
		}
		return null;
	}

	private void hostility(boolean hostile) {
		send(ConductorCommandPayload.Action.HOSTILITY, field("enemy-team").getValue().trim(), null,
				0, 0, 0, ConductorData.ControlMode.FULL, hostile, "");
	}

	private void remoteMove() {
		try {
			double x = Double.parseDouble(field("x").getValue());
			double y = Double.parseDouble(field("y").getValue());
			double z = Double.parseDouble(field("z").getValue());
			send(ConductorCommandPayload.Action.MOVE, "", null, x, y, z,
					ConductorData.ControlMode.FULL, false, "");
			onClose();
		} catch (NumberFormatException ignored) {
		}
	}

	private UUID target() {
		String value = field("target-uuid").getValue().trim();
		return value.isEmpty() ? null : UUID.fromString(value);
	}

	private void remoteAttack() {
		try {
			UUID target = target();
			if (target != null) {
				send(ConductorCommandPayload.Action.ATTACK, "", target, 0, 0, 0,
						ConductorData.ControlMode.FULL, false, "");
				onClose();
			}
		} catch (IllegalArgumentException ignored) {
		}
	}

	private void remoteCast() {
		String skill = field("skill-id").getValue().trim();
		if (skill.isEmpty()) return;
		try {
			send(ConductorCommandPayload.Action.CAST, "", target(), 0, 0, 0,
					ConductorData.ControlMode.FULL, false, skill);
			onClose();
		} catch (IllegalArgumentException ignored) {
		}
	}

	private void refresh() {
		ConductorDirectory snapshot = ConductorClient.snapshot();
		if (team.isBlank() && !snapshot.teams().isEmpty()) {
			team = snapshot.teams().keySet().stream().sorted().findFirst().orElse("");
		}
		UIElement teams = ui.selectId("team-list", UIElement.class).findFirst().orElseThrow();
		teams.clearAllChildren();
		snapshot.teams().keySet().stream().sorted().forEach(name -> {
			ConductorData.Team data = snapshot.team(name);
			Button button = button("team-" + name, name);
			button.setText(Component.literal(name).withColor(data.color()));
			button.layout(layout -> layout.widthPercent(100).height(LIST_BUTTON_HEIGHT));
			button.setOnClick(event -> {
				team = name;
				ConductorControls.selectTeam(name);
				ConductorHud.INSTANCE.selectTeam(name);
				refreshSkills();
				updateTeam();
			});
			teams.addChild(button);
		});
		refreshSkills();
		updateTeam();
	}

	private void refreshSkills() {
		UIElement skills = ui.selectId("skill-list", UIElement.class).findFirst().orElseThrow();
		skills.clearAllChildren();
		tooltips.clear();
		for (ConductorAbility skill : selectedMobSkills()) {
			UUID focus = ConductorHud.INSTANCE.focusedMember();
			if (focus == null || !ConductorControls.selected().contains(focus)) {
				focus = ConductorControls.selected().isEmpty() ? null : ConductorControls.selected().getFirst();
			}
			Mob focusMob = focus == null || Minecraft.getInstance().level == null ? null
					: Minecraft.getInstance().level.getEntity(focus) instanceof Mob mob ? mob : null;
			int total = focus == null ? 0 : ConductorClient.abilityCooldownTotalTicks(focus, skill.id());
			if (focusMob != null) {
				total = Math.max(total, skill.totalCooldownTicks(focusMob));
			}
			Component title = Component.translatableWithFallback(
					skill instanceof EntitySkillConductorAbility
							? ConductorTexts.skillName(skill.id()) : ConductorTexts.abilityName(skill.id()),
					skill.id().getPath());
			Component text = total > 0 ? Component.translatable(ConductorTexts.SKILL_COOLDOWN, title,
					(total + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND) : title;
			Button button = button("skill-" + skill.id(), ConductorTexts.SKILLS);
			button.setText(text);
			Component description = ConductorTexts.abilityDescription(skill.id());
			tooltips.put(button.getId(), description.getString().isEmpty() ? List.of(title) : List.of(title, description));
			button.layout(layout -> layout.widthPercent(100).height(LIST_BUTTON_HEIGHT));
			button.setOnClick(event -> {
				UUID selected = ConductorHud.INSTANCE.focusedMember();
				if (selected == null || !ConductorControls.selected().contains(selected)) {
					selected = ConductorControls.selected().isEmpty() ? null : ConductorControls.selected().getFirst();
				}
				if (selected == null) return;
				if (skill.targetKind() != ConductorTargeting.TargetKind.SELF) {
					ConductorControls.setPendingSkill(skill.id().toString(), selected);
				} else {
					ConductorData.Unit unit = ConductorClient.unit(selected);
					if (unit != null) {
						ConductorClient.send(ConductorCommandPayload.Action.CAST, unit.team(), "",
								ConductorControls.skillUnits(selected), null, 0.0D, 0.0D, 0.0D,
								ConductorData.ControlMode.FULL, false, skill.id().toString());
					}
				}
				onClose();
			});
			skills.addChild(button);
		}
	}

	private void updateTeam() {
		ConductorData.Team selected = ConductorClient.snapshot().team(team);
		if (tab != null) setTab(tab);
		if (selected != null) {
			field("color-hex").setText(String.format("%0" + COLOR_LENGTH + "X", selected.color()), false);
		}
		ui.selectId("force-load", Button.class).findFirst().orElseThrow().setText(
				Component.translatable(selected != null && selected.forceLoad()
						? ConductorTexts.LOAD_ON : ConductorTexts.LOAD_OFF));
		ui.selectId("selected-count", Label.class).findFirst().orElseThrow().setText(
				Component.translatable(ConductorTexts.SELECTED, ConductorControls.selected().size()));
		ConductorData.Unit first = firstSelectedUnit();
		ui.selectId("attack-mode", Button.class).findFirst().orElseThrow().setText(Component.translatable(
				first == null ? ConductorTexts.ATTACK_MODE
						: first.attackState() == ConductorData.AttackState.MANUAL
						? ConductorTexts.ATTACK_BASIC : ConductorTexts.ATTACK_AI));
	}

	private List<ConductorAbility> selectedMobSkills() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || ConductorControls.selected().isEmpty()) return List.of();
		UUID focus = ConductorHud.INSTANCE.focusedMember();
		if (focus == null || !ConductorControls.selected().contains(focus)) {
			focus = ConductorControls.selected().getFirst();
		}
		Entity entity = minecraft.level.getEntity(focus);
		if (!(entity instanceof Mob mob)) return List.of();
		ConductorClient.requestAbilitiesIfMissing(mob.getUUID());
		return ConductorUtil.abilities(mob).stream()
				.filter(ability -> ConductorClient.hasAbility(mob.getUUID(), ability.id()))
				.sorted(Comparator.comparing(skill -> skill.id().toString())).toList();
	}

	@Override
	public void tick() {
		super.tick();
		if (revision != ConductorClient.revision()) {
			revision = ConductorClient.revision();
			refresh();
		}
	}

	public enum Tab {TEAMS, UNITS, REMOTE}
}
