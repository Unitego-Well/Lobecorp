package org.unitego.lobecorp.client.conductor.hud;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.client.conductor.render.ConductorCamera;
import org.unitego.lobecorp.client.conductor.render.ConductorPortraitCache;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.ability.IConductorAbility;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.util.conductor.ConductorUtil;

import java.util.*;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;
import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.*;

/// 指挥家 HUD 的当帧状态派生与只读绘制。
public class ConductorHudRenderer {
	private final ConductorHudModel model;
	private final ConductorHudLayout layout;
	private final ConductorHudCards cards;
	private final ConductorHudTooltip tooltip;
	private final ConductorHudAnimationState animation = new ConductorHudAnimationState();
	private @Nullable String hoveredId = "";
	private String pressedId = "";
	private long releasedAt;
	private boolean staticPortraits;
	private boolean searchFocused;
	private String searchTarget = ConductorHudLayout.ROSTER_SEARCH;
	private String keyboardFocus = "";
	private long rosterTick = -1;
	private long frameNow;
	private List<Component> focusLines = List.of();
	private List<ConductorHudTooltip.EffectIcon> focusEffects = List.of();
	private final Map<String, Integer> buttonOffsets = new java.util.HashMap<>();
	private int modeHover = -1;

	public ConductorHudRenderer(ConductorHudModel model, ConductorHudLayout layout, ConductorHudCards cards,
	                            ConductorHudTooltip tooltip) {
		this.model = model;
		this.layout = layout;
		this.cards = cards;
		this.tooltip = tooltip;
	}

	public String pressedId() {
		return pressedId;
	}

	public boolean pressReleased() {
		return releasedAt != 0L;
	}


	public void press(String id) {
		pressedId = id;
		releasedAt = 0;
	}

	public void releasePress() {
		releasedAt = System.currentTimeMillis();
	}

	/// 记录被移除卡片的当帧位置；动画到期由下一帧准备阶段处理。
	public void addRemovedCard(UUID member, Button element, Component name) {
		animation.addRemovedCard(member, (int) element.getPositionX(), (int) element.getPositionY(), name,
				model.reducedMotion(), System.currentTimeMillis());
	}

	public boolean searchFocused() {
		return searchFocused;
	}

	public void setSearchFocused(boolean focused) {
		searchFocused = focused;
	}

	public String searchTarget() {
		return searchTarget;
	}

	public void setSearchTarget(String target) {
		searchTarget = target;
	}

	public String keyboardFocus() {
		return keyboardFocus;
	}

	public void setKeyboardFocus(String focus) {
		keyboardFocus = focus;
	}

	/// 在最终布局提取前完成业务派生、布局设定和动画时间采样。
	public void prepare() {
		Minecraft minecraft = Minecraft.getInstance();
		layout.updateLayout();
		long now = System.currentTimeMillis();
		frameNow = now;

		if (now - model.searchChangedAt() >= SEARCH_DELAY_MS) {
			model.setAppliedRosterSearch(model.rosterSearch());
			model.setAppliedSkillSearch(model.skillSearch());
		}

		List<UUID> selected = ConductorControls.selected();

		if (model.focused() == null || !selected.contains(model.focused())) {
			model.setFocused(selected.isEmpty() ? null : selected.getLast());
		}

		ConductorData.Unit focusUnit = model.focused() == null ? null : ConductorClient.unit(model.focused());

		if (focusUnit != null && !Objects.equals(model.focused(), model.previousFocus())) {
			model.selectTeam(focusUnit.team());
		}
		List<String> teams = model.teams(ConductorClient.revision(),
				() -> ConductorClient.snapshot().teams().keySet());
		if (!teams.contains(model.teamSelection())) {
			model.selectTeam(teams.isEmpty() ? "" : teams.getFirst());
		}
		model.hints().put(ConductorHudLayout.TEAM_SELECT, ConductorClient.snapshot().team(model.teamSelection()) == null
				? Component.translatable(ConductorTexts.TEAM)
				: Component.literal(ConductorClient.snapshot().team(model.teamSelection()).name())
				.withColor(ConductorClient.snapshot().team(model.teamSelection()).color()));
		ConductorData.Unit modeUnit = selectedModeUnit();
		model.hints().put(ConductorHudLayout.ATTACK_MODE, Component.translatable(modeUnit != null
				&& modeUnit.attackState() == ConductorData.AttackState.MANUAL
				? ConductorTexts.ATTACK_BASIC : ConductorTexts.ATTACK_AI));
		model.hints().put(ConductorHudLayout.BEHAVIOR_MODE, modeUnit == null ? Component.translatable(ConductorTexts.BEHAVIOR_MODE)
				: Component.translatable(ConductorTexts.BEHAVIOR_VALUE, Component.translatable(ConductorHudLayout.combatBehaviorKey(modeUnit.combatBehavior()))));
		model.hints().put(ConductorHudLayout.ACTIVITY_MODE, modeUnit == null ? Component.translatable(ConductorTexts.ACTIVITY_MODE)
				: Component.translatable(ConductorTexts.ACTIVITY_VALUE, Component.translatable(ConductorHudLayout.behaviorKey(modeUnit.behaviorState()))));

		if (modeUnit == null) {
			model.setModeDropdown("");
		}
		Entity focusEntity = model.focused() == null || minecraft.level == null ? null : minecraft.level.getEntity(model.focused());

		if (focusEntity instanceof Mob mob) {
			ConductorClient.requestAbilitiesIfMissing(mob.getUUID());
		}
		long tick = minecraft.level == null ? 0 : minecraft.level.getGameTime();
		boolean rosterChanged = model.previousRevision() != ConductorClient.revision()
				|| !model.teamSelection().equals(model.previousTeamSelection()) || !model.appliedRosterSearch().equals(model.previousSearch())
				|| tick - rosterTick >= ROSTER_REFRESH_TICKS || rosterTick < 0;

		if (rosterChanged) {
			if (model.previousRevision() != ConductorClient.revision() || !model.teamSelection().equals(model.previousTeamSelection())) {
				model.setTeamMemberCount(ConductorClient.snapshot().units().values().stream()
						.filter(unit -> unit.team().equals(model.teamSelection())).count());
			}

			model.setPreviousRevision(ConductorClient.revision());
			model.setPreviousTeamSelection(model.teamSelection());
			model.setPreviousSearch(model.appliedRosterSearch());
			rosterTick = tick;
			cards.rebuildRoster();
		}

		layout.selectId(ConductorHudLayout.SELECTED_COUNT, Label.class).findFirst().orElseThrow()
				.setText(Component.translatable(ConductorTexts.ROSTER_COUNT, selected.size(), model.teamMemberCount()));
		layout.selectId(ConductorHudLayout.ROSTER_QUERY, Label.class).findFirst().orElseThrow().setText(Component.empty());
		layout.selectId(ConductorHudLayout.SKILL_QUERY, Label.class).findFirst().orElseThrow().setText(Component.empty());
		layout.button(ConductorHudLayout.ROSTER_CLEAR).setDisplay(!model.rosterSearch().isEmpty());
		layout.button(ConductorHudLayout.SKILL_CLEAR).setDisplay(!model.skillSearch().isEmpty());

		if (!selected.equals(model.previousSelection())) {
			model.setPreviousSelection(List.copyOf(selected));
			cards.rebuildSelection();
		}

		List<Identifier> abilities = focusEntity instanceof Mob mob
				? ConductorUtil.abilities(mob).stream().filter(ability ->
						ConductorClient.hasAbility(mob.getUUID(), ability.id()) && matchesSkill(ability))
				.sorted(Comparator.comparing(ability -> ability.display(mob).abilityKind()))
				.map(IConductorAbility::id).toList() : List.of();

		if (!abilities.equals(model.previousAbilities()) || !Objects.equals(model.focused(), model.previousFocus())) {
			model.setPreviousAbilities(abilities);
			model.setPreviousFocus(model.focused());
			cards.rebuildSkills();
		}

		cards.mountCards(ConductorHudLayout.ROSTER_LIST, model.rosterMembers(), cards.rosterIds(), false);
		cards.mountCards(ConductorHudLayout.SELECTED_LIST, selected, cards.selectedIds(), true);
		cards.mountSkills(model.previousAbilities());
		cards.recycleCards(selected);
		sampleAnimations(now);
		prepareButtonOffsets();
		prepareFocus(minecraft);

		if (!keyboardFocus.isEmpty() && !focusableIds().contains(keyboardFocus)) {
			keyboardFocus = "";
		}
	}

	/// 在准备阶段推进不依赖最终几何的动画；可见卡片采样在布局后执行。
	private void sampleAnimations(long now) {
		animation.tickRemovals(now);
		animation.pruneHealth(member -> AVERAGE_HEALTH_ID.equals(member)
				|| ConductorClient.unit(member) != null || ConductorControls.selected().contains(member));
	}

	private void sampleMemberHealth(Minecraft minecraft, String listId, Map<String, UUID> ids, long now) {
		UIElement viewport = layout.scroller(listId).viewPort;

		for (Map.Entry<String, UUID> entry : ids.entrySet()) {
			Button card = cards.card(entry.getKey());

			if (card == null || minecraft.level == null) {
				continue;
			}

			if (card.getPositionX() >= viewport.getPositionX() + viewport.getSizeWidth()
					|| card.getPositionX() + card.getSizeWidth() <= viewport.getPositionX()
					|| card.getPositionY() >= viewport.getPositionY() + viewport.getSizeHeight()
					|| card.getPositionY() + card.getSizeHeight() <= viewport.getPositionY()) {
				continue;
			}

			if (!(minecraft.level.getEntity(entry.getValue()) instanceof LivingEntity living)) {
				continue;
			}

			float ratio = Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F);
			animation.sampleHealth(living.getUUID(), ratio, model.reducedMotion(), now);
		}
	}

	private void sampleSkillCooldowns(Minecraft minecraft, long now) {
		if (layout.skillsHidden() || model.focused() == null || minecraft.level == null
				|| !(minecraft.level.getEntity(model.focused()) instanceof Mob mob)) {
			return;
		}

		UIElement viewport = layout.scroller(ConductorHudLayout.SKILL_LIST).viewPort;
		int top = (int) viewport.getPositionY();
		int bottom = top + (int) viewport.getSizeHeight();

		for (Map.Entry<String, String> entry : cards.skillIds().entrySet()) {
			Button button = layout.selectId(entry.getKey(), Button.class).findFirst().orElse(null);

			if (button == null || button.getPositionY() >= bottom || button.getPositionY() + SKILL_SIZE <= top) {
				continue;
			}

			IConductorAbility ability = ConductorUtil.ability(mob, Identifier.parse(entry.getValue()));

			if (ability != null) {
				int remaining = ConductorClient.abilityCooldownTicks(mob.getUUID(), ability.id());
				animation.sampleCooldown(model.focused() + ability.id().toString(), remaining, now);
			}
		}
	}

	private void prepareFocus(Minecraft minecraft) {
		if (layout.infoWidth() < INFO_WIDTH || minecraft.level == null) {
			focusLines = List.of();
			focusEffects = List.of();
			resizeInfoContents(0, 0, 0);
			return;
		}

		UUID focus = model.focused();
		Entity entity = focus == null ? null : minecraft.level.getEntity(focus);
		List<UUID> selected = ConductorControls.selected();
		List<Component> lines = new ArrayList<>();
		Component name = focus == null ? Component.translatable(ConductorTexts.NO_SELECTION) : memberName(focus);
		lines.add(selected.size() > 1 ? Component.translatable(ConductorTexts.SELECTED, selected.size()) : name);

		if (focus != null) {
			lines.add(memberType(focus));
		}

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
			} else {
				lines.add(Component.translatable(ConductorTexts.UNKNOWN_HEALTH));
			}
		} else if (entity instanceof LivingEntity living) {
			lines.add(Component.translatable(ConductorTexts.HEALTH, (int) Math.ceil(living.getHealth()), (int) Math.ceil(living.getMaxHealth())));
			lines.add(attributes(living));
		} else {
			lines.add(Component.translatable(ConductorTexts.UNKNOWN_HEALTH));
		}

		ConductorData.Unit unit = focus == null ? null : ConductorClient.unit(focus);

		if (unit != null && ConductorClient.snapshot().team(unit.team()) != null) {
			lines.add(Component.translatable(ConductorTexts.BEHAVIOR_VALUE, Component.translatable(ConductorHudLayout.combatBehaviorKey(unit.combatBehavior()))));
			lines.add(Component.translatable(ConductorTexts.ACTIVITY_VALUE, Component.translatable(ConductorHudLayout.behaviorKey(unit.behaviorState()))));
			lines.add(Component.translatable(unit.attackState() == ConductorData.AttackState.MANUAL
					? ConductorTexts.ATTACK_BASIC : ConductorTexts.ATTACK_AI));
		}

		focusLines = List.copyOf(lines);
		focusEffects = entity instanceof LivingEntity living ? List.copyOf(tooltip.prepareEffectIcons(living)) : List.of();
		int textWidth = focusLines.stream().mapToInt(minecraft.font::width).max().orElse(0);
		int columns = Math.min(FOCUS_EFFECT_COLUMNS, Math.max(1, focusEffects.size()));
		int rows = (focusEffects.size() + columns - 1) / columns;
		resizeInfoContents(textWidth, focusEffects.isEmpty() ? 0 : columns * EFFECT_CELL_WIDTH, rows * EFFECT_CELL_HEIGHT);
	}

	private void prepareButtonOffsets() {
		buttonOffsets.clear();
		sampleButtonOffset(pressedId);

		for (String id : cards.skillIds().keySet()) {
			sampleButtonOffset(id);
		}
	}

	private void sampleButtonOffset(String id) {
		if (model.reducedMotion() || !pressedId.equals(id)) {
			buttonOffsets.put(id, 0);
			return;
		}

		if (releasedAt == 0) {
			buttonOffsets.put(id, BUTTON_DEPTH);
			return;
		}

		long frame = (frameNow - releasedAt) / ANIMATION_FRAME_MS;

		if (frame == 0) {
			buttonOffsets.put(id, -1);
			return;
		}

		if (frame >= 2) {
			pressedId = "";
		}
		buttonOffsets.put(id, 0);
	}

	/// 在最终布局后按可见几何采样生命与冷却；draw 只读本次快照。
	private void sampleVisibleAnimations(Minecraft minecraft, long now) {
		if (!layout.rosterHidden()) {
			sampleMemberHealth(minecraft, ConductorHudLayout.ROSTER_LIST, cards.rosterIds(), now);
		}

		sampleMemberHealth(minecraft, ConductorHudLayout.SELECTED_LIST, cards.selectedIds(), now);
		sampleSkillCooldowns(minecraft, now);
	}

	/// 在最终布局后采样悬浮命中与提示目标；绘制阶段只读取该快照。
	public void prepareAfterLayout(Minecraft minecraft) {
		if (!ConductorControls.active() || minecraft.screen != null || minecraft.level == null) {
			return;
		}

		sampleVisibleAnimations(minecraft, frameNow);
		hoveredId = interactiveId(elementAtCurrentUI(minecraft));
		staticPortraits = cards.countVisibleCards(layout.rosterHidden()) > PORTRAIT_LIMIT;
		modeHover = isModeDropdown() ? modeAtPointer(minecraft) : -1;

		if (isTeamDropdown() || isModeDropdown()) {
			tooltip.prepareAtPointer(layout, cards, model, "", frameNow);
		} else {
			tooltip.prepareAtPointer(layout, cards, model, hoveredId, frameNow);
		}
	}

	private void resizeInfoContents(int textWidth, int effectWidth, int effectHeight) {
		resizeInfoContent(layout.scroller(ConductorHudLayout.FOCUS_DETAILS), textWidth, focusLines.size() * TEXT_LINE_HEIGHT);
		resizeInfoContent(layout.scroller(ConductorHudLayout.FOCUS_EFFECTS), effectWidth, effectHeight);
	}

	private boolean matchesSkill(IConductorAbility ability) {
		String query = model.appliedSkillSearch().toLowerCase(Locale.ROOT);
		return query.isBlank() || ConductorHudLayout.abilityName(ability).getString().toLowerCase(Locale.ROOT).contains(query)
				|| ability.id().toString().toLowerCase(Locale.ROOT).contains(query);
	}

	public void draw(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();

		if (!ConductorControls.active() || minecraft.screen != null || minecraft.level == null) {
			return;
		}

		ConductorPortraitCache.beginFrame(minecraft.level, ConductorClient.snapshot(), staticPortraits);
		renderChrome(graphics);

		if (!layout.rosterHidden()) {
			renderMemberCards(graphics, minecraft, ConductorHudLayout.ROSTER_LIST, cards.rosterIds());
		}
		renderMemberCards(graphics, minecraft, ConductorHudLayout.SELECTED_LIST, cards.selectedIds());
		renderFocus(graphics, minecraft);
		renderCommandIcons(graphics);

		if (!layout.skillsHidden()) {
			renderSkills(graphics);
		}
		ConductorPortraitCache.submit(graphics);
		graphics.nextStratum();
		renderPortraitOverlays(graphics, minecraft);

		if (!model.reducedMotion()) {
			for (ConductorHudAnimationState.RemovedCard card : animation.removedCards().values()) {
				frame(graphics, card.x() + SECTION_PADDING, card.y() + SECTION_PADDING,
						CARD_SIZE - SECTION_PADDING * 2, CARD_SIZE - SECTION_PADDING * 2, BG_2, ORE_GREEN, 0);
				textFit(graphics, card.name(), card.x() + SECTION_PADDING + BORDER, card.y() + SECTION_PADDING + BORDER,
						CARD_SIZE - SECTION_PADDING * 2 - BORDER * 2, MUTED_COLOR);
			}
		}

		if (isTeamDropdown()) {
			renderTeamDropdown(graphics);
		} else if (isModeDropdown()) {
			renderModeDropdown(graphics);
		} else {
			tooltip.drawAtPointer(graphics);
		}
	}

	private boolean isTeamDropdown() {
		return model.teamDropdown();
	}

	private boolean isModeDropdown() {
		return !model.modeDropdown().isEmpty();
	}

	private void renderChrome(GuiGraphicsExtractor graphics) {
		for (String id : List.of(ConductorHudLayout.BOTTOM, ConductorHudLayout.ROSTER_PANEL, ConductorHudLayout.SKILL_RAIL)) {
			if (ConductorHudLayout.ROSTER_PANEL.equals(id) && layout.rosterHidden()
					|| ConductorHudLayout.SKILL_RAIL.equals(id) && layout.skillsHidden()) {
				continue;
			}

			frame(graphics, layout.element(id), BG_1, LINE_OUTER, PANEL_DEPTH);
		}

		for (String id : List.of(ConductorHudLayout.ROSTER_SEARCH, ConductorHudLayout.SKILL_SEARCH)) {
			if (ConductorHudLayout.ROSTER_SEARCH.equals(id) && layout.rosterHidden()
					|| ConductorHudLayout.SKILL_SEARCH.equals(id) && layout.skillsHidden()) {
				continue;
			}

			UIElement area = layout.element(id);
			boolean active = searchFocused && searchTarget.equals(id);
			frame(graphics, area, BG_0, active ? ORE_GREEN : LINE_OUTER, 0);
			String query = ConductorHudLayout.ROSTER_SEARCH.equals(id) ? model.rosterSearch() : model.skillSearch();
			Component value = query.isBlank() ? Component.translatable(ConductorHudLayout.ROSTER_SEARCH.equals(id)
					? ConductorTexts.SEARCH : ConductorTexts.SEARCH_SKILLS) : Component.literal(query);
			int textX = (int) area.getPositionX() + SECTION_PADDING + SEARCH_ICON_SIZE;
			int textY = (int) area.getPositionY() + SECTION_PADDING;
			int textWidth = (int) area.getSizeWidth() - SEARCH_ICON_SIZE * 2 - SECTION_PADDING * 2;
			drawGlyph(graphics, GLYPH_SEARCH, (int) area.getPositionX() + SECTION_PADDING, textY + 1, 1, MUTED_COLOR);
			textFit(graphics, value, textX, textY, textWidth, query.isBlank() ? MUTED_COLOR : TEXT_COLOR);

			if (active && frameNow / CURSOR_PHASE_MS % 2 == 0) {
				int cursorX = textX + Math.min(textWidth, Minecraft.getInstance().font.width(query));
				graphics.fill(cursorX, textY, cursorX + 1, textY + TEXT_LINE_HEIGHT, ORE_GREEN_HI);
			}
		}

		if (!layout.rosterHidden()) {
			UIElement area = layout.element(ConductorHudLayout.SELECTED_COUNT);
			textFit(graphics, Component.translatable(ConductorTexts.ROSTER_COUNT, ConductorControls.selected().size(), model.teamMemberCount()),
					(int) area.getPositionX(), (int) area.getPositionY(), (int) area.getSizeWidth(), GOLD);

			if (model.rosterMembers().isEmpty()) {
				emptyMessage(graphics, ConductorHudLayout.ROSTER_LIST,
						model.appliedRosterSearch().isBlank() ? ConductorTexts.EMPTY_TEAM : ConductorTexts.NO_MATCHING_MEMBERS);
			}
		}

		if (!layout.skillsHidden()) {
			UIElement title = layout.element(ConductorHudLayout.SKILL_TITLE);
			textFit(graphics, Component.translatable(ConductorTexts.SKILLS), (int) title.getPositionX(),
					(int) title.getPositionY(), (int) title.getSizeWidth(), TEXT_COLOR);

			if (cards.skillIds().isEmpty()) {
				emptyMessage(graphics, ConductorHudLayout.SKILL_LIST, model.focused() == null ? ConductorTexts.NO_SELECTION
						: Minecraft.getInstance().level == null || Minecraft.getInstance().level.getEntity(model.focused()) == null ? ConductorTexts.REMOTE_SKILLS
						: model.appliedSkillSearch().isBlank() ? ConductorTexts.NO_SKILLS : ConductorTexts.NO_MATCHING_SKILLS);
			}
		}

		for (String id : List.of(ConductorHudLayout.ROSTER_LIST, ConductorHudLayout.SKILL_LIST, ConductorHudLayout.SELECTED_LIST)) {
			if (ConductorHudLayout.ROSTER_LIST.equals(id) && layout.rosterHidden()
					|| ConductorHudLayout.SKILL_LIST.equals(id) && layout.skillsHidden()) {
				continue;
			}

			ScrollerView list = layout.scroller(id);
			int viewport = (int) list.viewPort.getContentHeight();
			int extent = (int) list.viewContainer.getSizeHeight();

			if (extent <= viewport || viewport <= 0) {
				continue;
			}

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
		UIElement area = layout.scroller(listId).viewPort;
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
	                               Map<String, UUID> ids) {
		if (minecraft.level == null) {
			return;
		}

		ScrollerView list = layout.scroller(listId);
		UIElement viewport = list.viewPort;
		boolean roster = ConductorHudLayout.ROSTER_LIST.equals(listId);
		int left = (int) viewport.getPositionX();
		int top = (int) viewport.getPositionY();
		int right = left + (int) viewport.getSizeWidth();
		int bottom = top + (int) viewport.getSizeHeight();
		graphics.enableScissor(left, top, right, bottom);

		for (Map.Entry<String, UUID> entry : ids.entrySet()) {
			Button card = cards.card(entry.getKey());

			if (card == null || card.getPositionX() >= right || card.getPositionX() + card.getSizeWidth() <= left
					|| card.getPositionY() >= bottom || card.getPositionY() + card.getSizeHeight() <= top) {
				continue;
			}

			UUID member = entry.getValue();
			Entity entity = minecraft.level.getEntity(member);
			int x = (int) card.getPositionX();
			int y = (int) card.getPositionY() + (entry.getKey().equals(hoveredId) && !model.reducedMotion() ? -1 : 0);
			int width = (int) card.getSizeWidth();
			int height = (int) card.getSizeHeight();
			int portraitSize = roster ? CARD_SIZE : height;
			boolean selected = ConductorControls.isSelected(member);
			int accent = selected ? selectionColor() : entry.getKey().equals(hoveredId) ? TEXT_COLOR : LINE_OUTER;
			frame(graphics, x, y, width, height, entry.getKey().equals(hoveredId) ? BG_3 : BG_2, accent, 0);

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

			if (keyboardFocus.equals(entry.getKey())) {
				graphics.outline(x - 1, y - 1, width + BORDER, height + BORDER, GOLD);
			}
		}

		graphics.disableScissor();
	}

	private void renderPortraitOverlays(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		if (minecraft.level == null) {
			return;
		}

		for (String listId : List.of(ConductorHudLayout.ROSTER_LIST, ConductorHudLayout.SELECTED_LIST)) {
			if (ConductorHudLayout.ROSTER_LIST.equals(listId) && layout.rosterHidden()) {
				continue;
			}

			UIElement viewport = layout.scroller(listId).viewPort;
			graphics.enableScissor((int) viewport.getPositionX(), (int) viewport.getPositionY(),
					(int) (viewport.getPositionX() + viewport.getSizeWidth()), (int) (viewport.getPositionY() + viewport.getSizeHeight()));
			Map<String, UUID> members = ConductorHudLayout.ROSTER_LIST.equals(listId) ? cards.rosterIds() : cards.selectedIds();

			for (var entry : members.entrySet()) {
				Button card = cards.card(entry.getKey());

				if (card == null) {
					continue;
				}

				int width = (int) card.getSizeWidth();
				int height = (int) card.getSizeHeight();
				int x = (int) card.getPositionX();
				int y = (int) card.getPositionY() + (entry.getKey().equals(hoveredId) && !model.reducedMotion() ? -1 : 0);

				if (minecraft.level.getEntity(entry.getValue()) instanceof LivingEntity living) {
					float ratio = Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F);
					cards.healthBar(graphics, x + BORDER, y + height - HEALTH_HEIGHT - 1, width - BORDER * 2,
							animation.healthRatio(living.getUUID(), ratio),
							animation.healthShown(living.getUUID(), ratio));
				} else {
					graphics.fill(x + BORDER, y + height - HEALTH_HEIGHT - 1, x + width - BORDER, y + height - 1, DISABLED_COLOR);
				}

				ConductorData.Unit unit = ConductorClient.unit(entry.getValue());
				ConductorData.Team team = unit == null ? null : ConductorClient.snapshot().team(unit.team());

				if (team != null) {
					graphics.fill(x + width - BORDER * 2, y + BORDER, x + width - BORDER, y + BORDER * 2, team.color() | 0xFF000000);
				}

				if (ConductorControls.isSelected(entry.getValue())) {
					graphics.fill(x + BORDER, y + BORDER + 1, x + BORDER + 1, y + BORDER + BORDER, GOLD);
					graphics.fill(x + BORDER + 1, y + BORDER + BORDER, x + BORDER + BORDER, y + BORDER + BORDER + 1, GOLD);
					graphics.fill(x + BORDER + BORDER, y + BORDER, x + BORDER + BORDER + 1, y + BORDER + BORDER, GOLD);
				}

				if (staticPortraits) {
					graphics.fill(x + width - BORDER - 1, y + height - BORDER - 1, x + width - BORDER, y + height - BORDER, GOLD);
				}
			}

			graphics.disableScissor();
		}

		List<UUID> selected = ConductorControls.selected();

		if (selected.size() > 1) {
			UIElement portrait = layout.element(ConductorHudLayout.FOCUS_PORTRAIT);
			textFit(graphics, Component.literal(String.format(Locale.ROOT, COUNT_FORMAT, selected.size())),
					(int) portrait.getPositionX() + BORDER, (int) portrait.getPositionY() + layout.focusSize() - TEXT_LINE_HEIGHT,
					layout.focusSize() - BORDER * 2, GOLD);
		}
	}

	private int selectionColor() {
		return !model.reducedMotion() && frameNow / SELECTION_PHASE_MS % 2 != 0 ? ORE_GREEN_HI : ORE_GREEN;
	}

	private void renderFocus(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		if (minecraft.level == null) {
			return;
		}

		UIElement portrait = layout.element(ConductorHudLayout.FOCUS_PORTRAIT);
		frame(graphics, portrait, BG_2, ConductorHudLayout.FOCUS_PORTRAIT.equals(hoveredId) ? GOLD : LINE_OUTER, 0);
		Entity entity = model.focused() == null ? null : minecraft.level.getEntity(model.focused());

		if (entity instanceof LivingEntity living) {
			ConductorPortraitCache.focus(graphics, living, (int) portrait.getPositionX() + BORDER,
					(int) portrait.getPositionY() + BORDER, layout.focusSize() - BORDER * 2, 0,
					ConductorHudLayout.FOCUS_PORTRAIT.equals(hoveredId) && !model.reducedMotion() ? PORTRAIT_HOVER_SCALE : 1);
		} else {
			textFit(graphics, Component.literal(UNKNOWN_MARK),
					(int) portrait.getPositionX() + SECTION_PADDING, (int) portrait.getPositionY() + SECTION_PADDING,
					layout.focusSize(), MUTED_COLOR);
		}

		if (layout.infoWidth() < INFO_WIDTH) {
			return;
		}

		ScrollerView details = layout.scroller(ConductorHudLayout.FOCUS_DETAILS);
		clipInfo(graphics, details);
		int textX = (int) details.viewContainer.getPositionX();
		int textY = (int) details.viewContainer.getPositionY();

		for (int index = 0; index < focusLines.size(); index++) {
			graphics.text(minecraft.font, focusLines.get(index), textX, textY + index * TEXT_LINE_HEIGHT,
					index == 0 ? TEXT_COLOR : MUTED_COLOR, false);
		}

		graphics.disableScissor();
		ScrollerView effects = layout.scroller(ConductorHudLayout.FOCUS_EFFECTS);
		List<ConductorHudTooltip.EffectIcon> icons = focusEffects;
		int columns = Math.min(FOCUS_EFFECT_COLUMNS, Math.max(1, icons.size()));
		clipInfo(graphics, effects);
		int effectX = (int) effects.viewContainer.getPositionX();
		int effectY = (int) effects.viewContainer.getPositionY();

		for (int index = 0; index < icons.size(); index++) {
			int cellX = effectX + index % columns * EFFECT_CELL_WIDTH;
			int cellY = effectY + index / columns * EFFECT_CELL_HEIGHT;

			if (cellX + EFFECT_CELL_WIDTH <= effects.viewPort.getPositionX()
					|| cellX >= effects.viewPort.getPositionX() + effects.viewPort.getSizeWidth()
					|| cellY + EFFECT_CELL_HEIGHT <= effects.viewPort.getPositionY()
					|| cellY >= effects.viewPort.getPositionY() + effects.viewPort.getSizeHeight()) {
				continue;
			}

			tooltip.drawEffectIcon(graphics, icons.get(index), cellX, cellY);
		}

		graphics.disableScissor();
	}

	private void renderCommandIcons(GuiGraphicsExtractor graphics) {
		for (String id : List.of(ConductorHudLayout.MOVE, ConductorHudLayout.ATTACK, ConductorHudLayout.STOP,
				ConductorHudLayout.MANAGE, ConductorHudLayout.ASSIGN, ConductorHudLayout.RELEASE,
				ConductorHudLayout.ATTACK_MODE, ConductorHudLayout.BEHAVIOR_MODE, ConductorHudLayout.ACTIVITY_MODE,
				ConductorHudLayout.TEAM_SELECT, ConductorHudLayout.ROSTER_CLEAR, ConductorHudLayout.SKILL_CLEAR,
				ConductorHudLayout.ROSTER_TOGGLE, ConductorHudLayout.SKILL_TOGGLE,
				FORMATION_SCATTERED, FORMATION_REGULAR, FORMATION_UNIFORM)) {
			if (List.of(ConductorHudLayout.MANAGE, ConductorHudLayout.ASSIGN, ConductorHudLayout.RELEASE,
					ConductorHudLayout.TEAM_SELECT, ConductorHudLayout.ROSTER_CLEAR).contains(id) && layout.rosterHidden()
					|| ConductorHudLayout.SKILL_CLEAR.equals(id) && layout.skillsHidden()
					|| ConductorHudLayout.ROSTER_CLEAR.equals(id) && model.rosterSearch().isEmpty()
					|| ConductorHudLayout.SKILL_CLEAR.equals(id) && model.skillSearch().isEmpty()) {
				continue;
			}

			Button button = layout.button(id);
			boolean hover = id.equals(hoveredId);
			boolean disabled = isDisabled(id);
			int dy = buttonOffset(id);
			int x = (int) button.getPositionX();
			int y = (int) button.getPositionY() + dy;
			int width = (int) button.getSizeWidth();
			int height = (int) button.getSizeHeight();
			int color = disabled ? DISABLED_COLOR : hover ? (ConductorHudLayout.ATTACK.equals(id) || ConductorHudLayout.STOP.equals(id) ? DANGER : ORE_GREEN_HI) : TEXT_COLOR;
			boolean handle = ConductorHudLayout.ROSTER_TOGGLE.equals(id) || ConductorHudLayout.SKILL_TOGGLE.equals(id);
			boolean formation = List.of(FORMATION_SCATTERED, FORMATION_REGULAR, FORMATION_UNIFORM).contains(id);
			boolean selectedMode = formation && model.formationMode() == ConductorHudLayout.formationMode(id);
			frame(graphics, x, y, width, height, dy > 0 ? BG_0 : hover ? BG_3 : BG_2,
					selectedMode ? ORE_GREEN : hover ? color : LINE_OUTER, handle ? 0 : Math.max(1, BUTTON_DEPTH - dy));

			if (formation) {
				textFit(graphics, Component.translatable(ConductorHudLayout.formationKey(id)), x + SECTION_PADDING, y + SECTION_PADDING,
						width - SECTION_PADDING * 2, selectedMode ? ORE_GREEN_HI : color);
			} else if (ConductorHudLayout.TEAM_SELECT.equals(id) || ConductorHudLayout.ATTACK_MODE.equals(id)
					|| ConductorHudLayout.BEHAVIOR_MODE.equals(id) || ConductorHudLayout.ACTIVITY_MODE.equals(id)) {
				textFit(graphics, model.hints().getOrDefault(id, Component.empty()), x + BORDER, y + SECTION_PADDING, width - BORDER * 2, color);
			} else {
				String glyph = id;

				if (ConductorHudLayout.ROSTER_CLEAR.equals(id) || ConductorHudLayout.SKILL_CLEAR.equals(id)) {
					glyph = GLYPH_CLEAR;
				}

				if (ConductorHudLayout.ROSTER_TOGGLE.equals(id)) {
					glyph = layout.rosterHidden() ? GLYPH_RIGHT : GLYPH_LEFT;
				}

				if (ConductorHudLayout.SKILL_TOGGLE.equals(id)) {
					glyph = layout.skillsHidden() ? GLYPH_LEFT : GLYPH_RIGHT;
				}
				int scale = width < BUTTON_SIZE ? 1 : ICON_PIXEL;
				drawGlyph(graphics, glyph, x + (width - ICON_GRID * scale) / 2, y + (height - ICON_GRID * scale) / 2, scale, color);
			}

			if (disabled) {
				graphics.fill(x, y, x + width, y + height, DISABLED_MASK);
			}

			if (keyboardFocus.equals(id)) {
				graphics.outline(x - 1, y - 1, width + BORDER, height + BORDER, GOLD);
			}
		}

		if (searchFocused || ConductorHudLayout.ROSTER_SEARCH.equals(keyboardFocus) || ConductorHudLayout.SKILL_SEARCH.equals(keyboardFocus)) {
			UIElement area = layout.selectId(searchFocused ? searchTarget : keyboardFocus, UIElement.class).findFirst().orElse(null);

			if (area != null) {
				graphics.outline((int) area.getPositionX() - 1, (int) area.getPositionY() - 1,
						(int) area.getSizeWidth() + BORDER, (int) area.getSizeHeight() + BORDER, GOLD);
			}
		}
	}

	private int buttonOffset(String id) {
		return buttonOffsets.getOrDefault(id, 0);
	}

	private void renderSkills(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();

		if (model.focused() == null || minecraft.level == null || !(minecraft.level.getEntity(model.focused()) instanceof Mob mob)) {
			return;
		}

		UIElement viewport = layout.scroller(ConductorHudLayout.SKILL_LIST).viewPort;
		int left = (int) viewport.getPositionX();
		int top = (int) viewport.getPositionY();
		int right = left + (int) viewport.getSizeWidth();
		int bottom = top + (int) viewport.getSizeHeight();
		graphics.enableScissor(left, top, right, bottom);

		for (Map.Entry<String, String> entry : cards.skillIds().entrySet()) {
			Button button = layout.selectId(entry.getKey(), Button.class).findFirst().orElse(null);

			if (button == null || button.getPositionY() >= bottom || button.getPositionY() + SKILL_SIZE <= top) {
				continue;
			}

			IConductorAbility ability = ConductorUtil.ability(mob, Identifier.parse(entry.getValue()));

			if (ability == null) {
				continue;
			}

			int x = (int) button.getPositionX();
			int y = (int) button.getPositionY() + buttonOffset(entry.getKey());
			int width = (int) button.getSizeWidth();
			String cooldownKey = model.focused() + ability.id().toString();
			int remaining = animation.cooldownRemaining(cooldownKey,
					ConductorClient.abilityCooldownTicks(mob.getUUID(), ability.id()));
			boolean unavailable = remaining > 0 || !ability.isAvailable(mob)
					|| ability instanceof EntitySkillConductorAbility entitySkill && !entitySkill.canBeginCast(mob);
			int total = Math.max(ConductorClient.abilityCooldownTotalTicks(mob.getUUID(), ability.id()), ability.totalCooldownTicks(mob));
			frame(graphics, x, y, width, SKILL_SIZE, BG_2,
					ConductorControls.isPendingSkill(entry.getValue(), model.focused()) ? ORE_GREEN_HI
							: entry.getKey().equals(hoveredId) ? ORE_GREEN : LINE_OUTER, 0);
			String name = ConductorHudLayout.abilityName(ability).getString();
			int color = switch (ability.display(mob).abilityKind()) {
				case BASIC -> GOLD;
				case ATTACK -> DANGER;
				case SUPPORT -> INFO;
			};
			graphics.fill(x + BORDER, y + BORDER, x + BORDER + SKILL_ICON_SIZE, y + BORDER + SKILL_ICON_SIZE, BG_0);

			if (!name.isEmpty()) {
				graphics.text(minecraft.font, Component.literal(name.substring(0, name.offsetByCodePoints(0, 1))),
						x + BORDER + 1, y + BORDER + 1, unavailable ? DISABLED_COLOR : color, false);
			}
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

			long flash = (frameNow - animation.cooldownFlash(cooldownKey)) / ANIMATION_FRAME_MS;

			if (!model.reducedMotion() && flash < FLASH_FRAMES) {
				int line = SKILL_SIZE - BORDER - (int) flash * (SKILL_SIZE / FLASH_FRAMES);
				graphics.fill(x + BORDER, y + line, x + width - BORDER, y + line + 1, TEXT_COLOR);

				if (flash == FLASH_FRAMES - 1) {
					graphics.outline(x, y, width, SKILL_SIZE, TEXT_COLOR);
				}
			}

			if (keyboardFocus.equals(entry.getKey())) {
				graphics.outline(x, y, width, SKILL_SIZE, GOLD);
			}
		}

		graphics.disableScissor();
	}

	private void renderTeamDropdown(GuiGraphicsExtractor graphics) {
		UIElement header = layout.element(ConductorHudLayout.TEAM_SELECT);
		int x = (int) header.getPositionX();
		int top = dropdownY();
		List<String> teams = model.teams(ConductorClient.revision(), () -> ConductorClient.snapshot().teams().keySet());
		graphics.nextStratum();

		for (int index = model.teamScroll(); index < Math.min(teams.size(), model.teamScroll() + TEAM_DROPDOWN_ROWS); index++) {
			int y = top + (index - model.teamScroll()) * HEADER_HEIGHT;
			ConductorData.Team team = ConductorClient.snapshot().team(teams.get(index));
			frame(graphics, x, y, MAX_ROSTER_WIDTH, HEADER_HEIGHT, index == model.teamCursor() ? BG_3 : BG_1,
					team.name().equals(model.teamSelection()) ? ORE_GREEN : LINE_OUTER, 0);
			graphics.fill(x + BORDER, y + BORDER, x + BORDER * 2, y + HEADER_HEIGHT - BORDER, team.color() | 0xFF000000);
			textFit(graphics, Component.literal(team.name()), x + BORDER * 3, y + SECTION_PADDING,
					MAX_ROSTER_WIDTH - BORDER * 4, TEXT_COLOR);
		}
	}

	private int dropdownY() {
		UIElement header = layout.element(ConductorHudLayout.TEAM_SELECT);
		int height = Math.min(TEAM_DROPDOWN_ROWS, model.teams(ConductorClient.revision(),
				() -> ConductorClient.snapshot().teams().keySet()).size()) * HEADER_HEIGHT;
		return Math.max(0, Math.min((int) (header.getPositionY() + header.getSizeHeight()),
				Minecraft.getInstance().getWindow().getGuiScaledHeight() - height));
	}

	private void renderModeDropdown(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		ModeBounds bounds = modeBounds(minecraft);
		int hovered = modeHover;
		int current = currentModeIndex();
		graphics.nextStratum();

		for (int index = 0; index < modeCount(); index++) {
			int y = bounds.y() + index * HEADER_HEIGHT;
			frame(graphics, bounds.x(), y, bounds.width(), HEADER_HEIGHT,
					index == hovered || index == model.modeCursor() ? BG_3 : BG_1, index == current ? ORE_GREEN : LINE_OUTER, 0);
			textFit(graphics, modeLabel(index), bounds.x() + SECTION_PADDING, y + SECTION_PADDING,
					bounds.width() - SECTION_PADDING * 2, index == current ? ORE_GREEN_HI : TEXT_COLOR);
		}
	}

	public ModeBounds modeBounds(Minecraft minecraft) {
		UIElement button = layout.button(model.modeDropdown());
		int width = (int) button.getSizeWidth();

		for (int index = 0; index < modeCount(); index++) {
			width = Math.max(width, minecraft.font.width(modeLabel(index)) + SECTION_PADDING * 2);
		}
		width = Math.min(width, minecraft.getWindow().getGuiScaledWidth());
		int height = modeCount() * HEADER_HEIGHT;
		int x = Math.clamp((int) button.getPositionX(), 0, Math.max(0, minecraft.getWindow().getGuiScaledWidth() - width));
		int y = (int) (button.getPositionY() + button.getSizeHeight());

		if (y + height > minecraft.getWindow().getGuiScaledHeight()) {
			y = (int) button.getPositionY() - height;
		}
		y = Math.clamp(y, 0, Math.max(0, minecraft.getWindow().getGuiScaledHeight() - height));
		return new ModeBounds(x, y, width, height);
	}

	public int modeAtPointer(Minecraft minecraft) {
		ModeBounds bounds = modeBounds(minecraft);
		double x = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getWidth();
		double y = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getHeight();
		return x >= bounds.x() && x < bounds.x() + bounds.width() && y >= bounds.y() && y < bounds.y() + bounds.height()
				? (int) ((y - bounds.y()) / HEADER_HEIGHT) : -1;
	}

	public int teamAtPointer(Minecraft minecraft) {
		UIElement header = layout.element(ConductorHudLayout.TEAM_SELECT);
		double x = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getWidth();
		double y = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getHeight();
		int row = (int) ((y - dropdownY()) / HEADER_HEIGHT);
		return x >= header.getPositionX() && x < header.getPositionX() + MAX_ROSTER_WIDTH
				&& y >= dropdownY() && row < TEAM_DROPDOWN_ROWS && row + model.teamScroll() < model.teams(ConductorClient.revision(),
				() -> ConductorClient.snapshot().teams().keySet()).size() ? row + model.teamScroll() : -1;
	}

	public int modeCount() {
		return ConductorHudLayout.ATTACK_MODE.equals(model.modeDropdown()) ? ConductorHudLayout.ATTACK_OPTIONS.size()
				: ConductorHudLayout.ACTIVITY_MODE.equals(model.modeDropdown()) ? ConductorHudLayout.ACTIVITY_OPTIONS.size()
				: ConductorHudLayout.BEHAVIOR_OPTIONS.size();
	}

	public int currentModeIndex() {
		ConductorData.Unit unit = selectedModeUnit();

		if (unit == null) {
			return 0;
		}

		if (ConductorHudLayout.ATTACK_MODE.equals(model.modeDropdown())) {
			return ConductorHudLayout.ATTACK_OPTIONS.indexOf(unit.attackState());
		}

		if (ConductorHudLayout.BEHAVIOR_MODE.equals(model.modeDropdown())) {
			return ConductorHudLayout.BEHAVIOR_OPTIONS.indexOf(unit.combatBehavior());
		}

		for (int index = 0; index < ConductorHudLayout.ACTIVITY_OPTIONS.size(); index++) {
			if (ConductorHudLayout.ACTIVITY_OPTIONS.get(index).behaviorState() == unit.behaviorState()) {
				return index;
			}
		}

		return 0;
	}

	public Component modeLabel(int index) {
		if (ConductorHudLayout.ATTACK_MODE.equals(model.modeDropdown())) {
			return Component.translatable(ConductorHudLayout.ATTACK_OPTIONS.get(index) == ConductorData.AttackState.MANUAL
					? ConductorTexts.ATTACK_BASIC : ConductorTexts.ATTACK_AI);
		}
		return ConductorHudLayout.ACTIVITY_MODE.equals(model.modeDropdown()) ? Component.translatable(ConductorTexts.ACTIVITY_VALUE,
				Component.translatable(ConductorHudLayout.behaviorKey(ConductorHudLayout.ACTIVITY_OPTIONS.get(index).behaviorState())))
				: Component.translatable(ConductorTexts.BEHAVIOR_VALUE,
				Component.translatable(ConductorHudLayout.combatBehaviorKey(ConductorHudLayout.BEHAVIOR_OPTIONS.get(index))));
	}

	public ConductorData.Unit selectedModeUnit() {
		List<UUID> units = ConductorControls.controlledSelection();

		if (units.isEmpty()) {
			return null;
		}

		return ConductorClient.unit(units.contains(model.focused()) ? model.focused() : units.getFirst());
	}

	public boolean isDisabled(String id) {
		if (ConductorHudLayout.ASSIGN.equals(id)) {
			return ConductorControls.selected().isEmpty();
		}

		if (List.of(ConductorHudLayout.MOVE, ConductorHudLayout.ATTACK, ConductorHudLayout.STOP, ConductorHudLayout.RELEASE).contains(id)) {
			return ConductorControls.controlledSelection().isEmpty();
		}

		if (ConductorHudLayout.ATTACK_MODE.equals(id) || ConductorHudLayout.BEHAVIOR_MODE.equals(id)
				|| ConductorHudLayout.ACTIVITY_MODE.equals(id)) {
			return ConductorControls.controlledSelection().isEmpty();
		}

		String skill = cards.skillIds().get(id);
		return skill != null && ConductorControls.skillUnits(model.focused()).stream().noneMatch(member ->
				ConductorClient.hasAbility(member, Identifier.parse(skill)));
	}

	public UIElement elementAtCurrentUI(Minecraft minecraft) {
		double x = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getWidth();
		double y = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getHeight();
		var hit = layout.hitTest(x, y);
		return hit == null ? null : hit.getA();
	}

	public String interactiveId(@Nullable UIElement element) {
		for (; element != null; element = element.getParent()) {
			String id = element.getId();

			if (element instanceof Button || ConductorHudLayout.ROSTER_SEARCH.equals(id) || ConductorHudLayout.SKILL_SEARCH.equals(id)
					|| ConductorHudLayout.FOCUS_PORTRAIT.equals(id)) {
				return id;
			}
		}

		return "";
	}

	public List<String> focusableIds() {
		List<String> ids = new ArrayList<>();

		if (!layout.rosterHidden()) {
			ids.addAll(List.of(ConductorHudLayout.TEAM_SELECT, ConductorHudLayout.ASSIGN, ConductorHudLayout.RELEASE,
					ConductorHudLayout.MANAGE, ConductorHudLayout.ROSTER_SEARCH));

			if (!model.rosterSearch().isEmpty()) {
				ids.add(ConductorHudLayout.ROSTER_CLEAR);
			}
			model.rosterMembers().stream().map(member -> ConductorHudLayout.UNIT_PREFIX + member).forEach(ids::add);
		}

		ids.add(ConductorHudLayout.ROSTER_TOGGLE);

		if (!layout.skillsHidden()) {
			ids.add(ConductorHudLayout.SKILL_SEARCH);

			if (!model.skillSearch().isEmpty()) {
				ids.add(ConductorHudLayout.SKILL_CLEAR);
			}
			model.previousAbilities().stream().map(ability -> ConductorHudLayout.SKILL_PREFIX + ability).forEach(ids::add);
		}

		ids.add(ConductorHudLayout.SKILL_TOGGLE);
		ConductorControls.selected().stream().map(member -> ConductorHudLayout.SELECTED_PREFIX + member).forEach(ids::add);
		ids.addAll(List.of(ConductorHudLayout.MOVE, ConductorHudLayout.ATTACK, ConductorHudLayout.STOP,
				ConductorHudLayout.ATTACK_MODE, ConductorHudLayout.BEHAVIOR_MODE, ConductorHudLayout.ACTIVITY_MODE,
				FORMATION_SCATTERED, FORMATION_REGULAR, FORMATION_UNIFORM));
		return ids;
	}

	public void revealKeyboardFocus() {
		String focus = keyboardFocus;
		String listId;
		int index;
		int count;
		int columns = 1;
		int step = ROSTER_ROW_HEIGHT + CARD_GAP;

		if (focus.startsWith(ConductorHudLayout.UNIT_PREFIX)) {
			listId = ConductorHudLayout.ROSTER_LIST;
			index = model.rosterMembers().indexOf(UUID.fromString(focus.substring(ConductorHudLayout.UNIT_PREFIX.length())));
			count = model.rosterMembers().size();
			columns = Math.max(1, cards.rosterColumns());
		} else if (focus.startsWith(ConductorHudLayout.SELECTED_PREFIX)) {
			listId = ConductorHudLayout.SELECTED_LIST;
			List<UUID> selected = ConductorControls.selected();
			index = selected.indexOf(UUID.fromString(focus.substring(ConductorHudLayout.SELECTED_PREFIX.length())));
			count = selected.size();
			columns = cards.selectedColumns();
			step = CARD_SIZE + CARD_GAP;
		} else if (focus.startsWith(ConductorHudLayout.SKILL_PREFIX) && !ConductorHudLayout.SKILL_SEARCH.equals(focus)
				&& !ConductorHudLayout.SKILL_CLEAR.equals(focus) && !ConductorHudLayout.SKILL_TOGGLE.equals(focus)) {
			listId = ConductorHudLayout.SKILL_LIST;
			index = model.previousAbilities().indexOf(Identifier.parse(focus.substring(ConductorHudLayout.SKILL_PREFIX.length())));
			count = model.previousAbilities().size();
			step = SKILL_SIZE + BUTTON_GAP;
		} else {
			return;
		}

		if (index < 0) {
			return;
		}

		ScrollerView list = layout.scroller(listId);
		int viewport = Math.max(1, (int) list.viewPort.getContentHeight());
		int extent = Math.max(0, ((count + columns - 1) / columns) * step
				- (ConductorHudLayout.SKILL_LIST.equals(listId) ? BUTTON_GAP : CARD_GAP));
		int range = Math.max(0, extent - viewport);
		int position = index / columns * step;
		float current = list.verticalScroller.getNormalizedValue() * range;
		float offset = position < current ? position : position + step > current + viewport ? position + step - viewport : current;
		float normalized = range == 0 ? 0 : Mth.clamp(offset / range, 0, 1);
		list.verticalScroller.setNormalizedValue(normalized, true);

		if (ConductorHudLayout.SKILL_LIST.equals(listId)) {
			cards.setMountedSkills(List.of());
			cards.mountSkills(model.previousAbilities());
		} else {
			cards.removeMountedMembers(listId);
			boolean selectedList = ConductorHudLayout.SELECTED_LIST.equals(listId);
			cards.mountCards(listId, selectedList ? ConductorControls.selected() : model.rosterMembers(),
					selectedList ? cards.selectedIds() : cards.rosterIds(), selectedList);
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

	public static void frame(GuiGraphicsExtractor graphics, UIElement area, int background, int border, int depth) {
		frame(graphics, (int) area.getPositionX(), (int) area.getPositionY(),
				(int) area.getSizeWidth(), (int) area.getSizeHeight(), background, border, depth);
	}

	public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
	                         int background, int border, int depth) {
		if (width <= 0 || height <= 0) {
			return;
		}

		graphics.fill(x, y, x + width, y + height + depth, border);
		graphics.fill(x + BORDER, y + BORDER, x + width - BORDER, y + height - BORDER, background);
		graphics.outline(x + BORDER, y + BORDER, Math.max(1, width - BORDER * 2),
				Math.max(1, height - BORDER * 2), LINE_INNER);
		graphics.fill(x + BORDER, y + BORDER, x + width - BORDER, y + BORDER + 1, EDGE_LIGHT);
	}

	public static void textFit(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		if (width <= 0) {
			return;
		}

		var font = Minecraft.getInstance().font;
		String value = text.getString();

		if (font.width(value) > width) {
			value = font.plainSubstrByWidth(value, Math.max(0, width - font.width(ELLIPSIS))) + ELLIPSIS;
		}

		graphics.text(font, Component.literal(value), x, y, color, false);
	}

	public static void drawGlyph(GuiGraphicsExtractor graphics, String glyph, int x, int y, int scale, int color) {
		String pixels = ICONS.get(glyph);

		if (pixels == null) {
			return;
		}

		for (int pixel = 0; pixel < pixels.length(); pixel++) {
			if (pixels.charAt(pixel) != '1') {
				continue;
			}

			int px = x + pixel % ICON_GRID * scale;
			int py = y + pixel / ICON_GRID * scale;
			graphics.fill(px, py, px + scale, py + scale, color);
		}
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

		if (entity != null) {
			return entity.getDisplayName();
		}

		ConductorData.MemberInfo info = ConductorClient.snapshot().memberInfo(member);
		return Component.literal(info == null || info.name().isBlank() ? member.toString() : info.name());
	}

	public void clearTransient() {
		hoveredId = "";
		pressedId = "";
		releasedAt = 0L;
		staticPortraits = false;
		searchFocused = false;
		searchTarget = ConductorHudLayout.ROSTER_SEARCH;
		keyboardFocus = "";
		rosterTick = -1;
		frameNow = 0L;
		focusLines = List.of();
		focusEffects = List.of();
		buttonOffsets.clear();
		modeHover = -1;
		animation.clear();
	}

	public record ModeBounds(int x, int y, int width, int height) {
	}
}
