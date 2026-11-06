package org.unitego.lobecorp.client.conductor.hud;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.client.conductor.ConductorLdlibScreen;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.ability.IConductorTargeting;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;

import java.util.List;
import java.util.UUID;

import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.*;

/// 指挥家 HUD 的输入处理；SELECTED、pending 与相机仍由 [ConductorControls] 唯一拥有。
public class ConductorHudInput {
	private final ConductorHudModel model;
	private final ConductorHudLayout layout;
	private final ConductorHudCards cards;
	private final ConductorHudRenderer renderer;
	private String lastSkillClick = "";
	private long lastSkillClickTime;

	public ConductorHudInput(ConductorHudModel model, ConductorHudLayout layout, ConductorHudCards cards,
	                         ConductorHudRenderer renderer) {
		this.model = model;
		this.layout = layout;
		this.cards = cards;
		this.renderer = renderer;
	}

	public boolean contains(Minecraft minecraft) {
		return containsStatic(layout, minecraft, model, renderer);
	}

	public static boolean containsStatic(ConductorHudLayout layout, Minecraft minecraft) {
		double x = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getWidth();
		double y = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getHeight();
		var hit = layout.hitTest(x, y);

		for (UIElement element = hit == null ? null : hit.getA(); element != null; element = element.getParent()) {
			String id = element.getId();

			if (ConductorHudLayout.MANAGE.equals(id) || ConductorHudLayout.BOTTOM.equals(id)
					|| ConductorHudLayout.ROSTER_PANEL.equals(id) || ConductorHudLayout.ROSTER_TOGGLE.equals(id)
					|| ConductorHudLayout.SKILL_RAIL.equals(id) || ConductorHudLayout.SKILL_TOGGLE.equals(id)
					|| ConductorHudLayout.ROSTER_SEARCH.equals(id)) {
				return true;
			}
		}

		return false;
	}

	private static boolean containsStatic(ConductorHudLayout layout, Minecraft minecraft,
	                                      @Nullable ConductorHudModel model, @Nullable ConductorHudRenderer renderer) {
		if (model != null && (model.teamDropdown() || !model.modeDropdown().isEmpty())) {
			return true;
		}

		if (renderer == null) {
			return containsStatic(layout, minecraft);
		}

		UIElement element = renderer.elementAtCurrentUI(minecraft);

		for (; element != null; element = element.getParent()) {
			String id = element.getId();

			if (ConductorHudLayout.MANAGE.equals(id) || ConductorHudLayout.BOTTOM.equals(id)
					|| ConductorHudLayout.ROSTER_PANEL.equals(id) || ConductorHudLayout.ROSTER_TOGGLE.equals(id)
					|| ConductorHudLayout.SKILL_RAIL.equals(id) || ConductorHudLayout.SKILL_TOGGLE.equals(id)
					|| ConductorHudLayout.ROSTER_SEARCH.equals(id)) {
				return true;
			}
		}

		return false;
	}

	public @Nullable UUID memberAtPointer(Minecraft minecraft) {
		if (model.teamDropdown() || !model.modeDropdown().isEmpty()) {
			return null;
		}

		String id = renderer.interactiveId(renderer.elementAtCurrentUI(minecraft));

		if (ConductorHudLayout.FOCUS_PORTRAIT.equals(id)) {
			return model.focused();
		}

		UUID member = cards.rosterIds().get(id);
		return member == null ? cards.selectedIds().get(id) : member;
	}

	public boolean click(Minecraft minecraft) {
		if (!model.modeDropdown().isEmpty()) {
			int index = renderer.modeAtPointer(minecraft);

			if (index >= 0) {
				applyMode(index);
			}
			model.setModeDropdown("");
			return true;
		}

		if (model.teamDropdown()) {
			int index = renderer.teamAtPointer(minecraft);

			if (index >= 0) {
				model.selectTeam(model.teams(ConductorClient.revision(), () -> ConductorClient.snapshot().teams().keySet()).get(index));
			}
			model.setTeamDropdown(false);
			return true;
		}

		UIElement clicked = renderer.elementAtCurrentUI(minecraft);

		if (clicked == null) {
			renderer.setSearchFocused(false);
			return false;
		}

		String id = renderer.interactiveId(clicked);

		if (!ConductorHudLayout.ROSTER_SEARCH.equals(id) && !ConductorHudLayout.SKILL_SEARCH.equals(id)) {
			renderer.setSearchFocused(false);
		}
		boolean handled = activate(clicked, minecraft);
		UUID member = cards.selectedIds().get(id);

		if (member == null) {
			member = cards.rosterIds().get(id);
		}
		model.setClickedMember(member != null && ConductorControls.selected().contains(member) ? member : null);
		model.setClickPosition(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
		return handled;
	}

	private boolean activate(UIElement clicked, Minecraft minecraft) {
		for (UIElement element = clicked; element != null; element = element.getParent()) {
			String id = element.getId();

			if (ConductorHudLayout.ROSTER_SEARCH.equals(id) || ConductorHudLayout.SKILL_SEARCH.equals(id)) {
				renderer.setSearchFocused(true);
				renderer.setSearchTarget(id);
				renderer.setKeyboardFocus(id);
				ConductorControls.releaseCameraKeys();
				return true;
			}

			if (ConductorHudLayout.ROSTER_CLEAR.equals(id) || ConductorHudLayout.SKILL_CLEAR.equals(id)) {
				renderer.setSearchTarget(ConductorHudLayout.ROSTER_CLEAR.equals(id)
						? ConductorHudLayout.ROSTER_SEARCH : ConductorHudLayout.SKILL_SEARCH);
				renderer.setKeyboardFocus(renderer.searchTarget());
				setSearchQuery("");
				renderer.setSearchFocused(true);
				ConductorControls.releaseCameraKeys();
				return true;
			}

			if (element instanceof Button && activateButton(id, minecraft)) {
				return true;
			}

			if (ConductorHudLayout.BOTTOM.equals(id) || ConductorHudLayout.ROSTER_PANEL.equals(id)
					|| ConductorHudLayout.SKILL_RAIL.equals(id)) {
				return true;
			}
		}

		return false;
	}

	private boolean activateButton(String id, Minecraft minecraft) {
		renderer.setSearchFocused(false);
		renderer.setKeyboardFocus(id);

		if (renderer.isDisabled(id)) {
			return true;
		}

		renderer.press(id);

		if (activateMemberButton(id, minecraft)) {
			return true;
		}

		if (ConductorHudLayout.MANAGE.equals(id)) {
			ConductorControls.openPanel();
			return true;
		}

		if (ConductorHudLayout.ROSTER_TOGGLE.equals(id)) {
			model.setRosterCollapsed(!layout.rosterHidden());
			model.setPreferSkillsOnNarrow(false);
			model.preferences().rosterCollapsed = model.rosterCollapsed();
			model.preferences().save();
			layout.updateLayout();
			return true;
		}

		if (ConductorHudLayout.SKILL_TOGGLE.equals(id)) {
			model.setSkillsCollapsed(!layout.skillsHidden());
			model.setPreferSkillsOnNarrow(true);
			model.preferences().skillsCollapsed = model.skillsCollapsed();
			model.preferences().save();
			layout.updateLayout();
			return true;
		}

		if (ConductorHudLayout.MOVE.equals(id)) {
			if (hasRemoteSelection(minecraft)) {
				ConductorControls.openPanel(ConductorLdlibScreen.Tab.REMOTE);
			} else {
				ConductorControls.setPending(ConductorCommandPayload.Action.MOVE);
			}
			return true;
		}

		if (ConductorHudLayout.ATTACK.equals(id)) {
			if (hasRemoteSelection(minecraft)) {
				ConductorControls.openPanel(ConductorLdlibScreen.Tab.REMOTE);
			} else {
				ConductorControls.setPending(ConductorCommandPayload.Action.ATTACK);
			}
			return true;
		}

		if (ConductorHudLayout.STOP.equals(id)) {
			send(ConductorCommandPayload.Action.STOP, false, "");
			return true;
		}

		if (ConductorHudLayout.ATTACK_MODE.equals(id)) {
			openModeDropdown(id);
			return true;
		}

		if (List.of(FORMATION_SCATTERED, FORMATION_REGULAR, FORMATION_UNIFORM).contains(id)) {
			model.preferences().formationMode = ConductorHudLayout.formationMode(id);
			model.preferences().save();
			return true;
		}

		if (ConductorHudLayout.BEHAVIOR_MODE.equals(id) || ConductorHudLayout.ACTIVITY_MODE.equals(id)) {
			openModeDropdown(id);
			return true;
		}

		if (ConductorHudLayout.TEAM_SELECT.equals(id)) {
			model.setTeamDropdown(!model.teams(ConductorClient.revision(), () -> ConductorClient.snapshot().teams().keySet()).isEmpty());
			model.setTeamCursor(Math.max(0, model.teams(ConductorClient.revision(),
					() -> ConductorClient.snapshot().teams().keySet()).indexOf(model.teamSelection())));
			model.setTeamScroll(Math.max(0, model.teamCursor() - TEAM_DROPDOWN_ROWS + 1));
			return true;
		}

		if (ConductorHudLayout.ASSIGN.equals(id) || ConductorHudLayout.RELEASE.equals(id)) {
			send(ConductorHudLayout.ASSIGN.equals(id) ? ConductorCommandPayload.Action.ASSIGN
					: ConductorCommandPayload.Action.RELEASE, false, "");
			return true;
		}

		String skill = cards.skillIds().get(id);

		if (skill != null) {
			selectSkill(skill, true);
			return true;
		}

		return false;
	}

	private boolean activateMemberButton(String id, Minecraft minecraft) {
		UUID member = cards.selectedIds().get(id);

		if (member != null) {
			model.setFocused(member);
			return true;
		}

		member = cards.rosterIds().get(id);

		if (member != null) {
			if (minecraft.hasShiftDown()) {
				ConductorControls.selectRange(model.rosterMembers(), model.selectionAnchor(), member);
			} else {
				ConductorControls.selectMember(member, minecraft.hasControlDown());
			}
			model.setSelectionAnchor(member);

			if (ConductorControls.selected().contains(member)) {
				model.setFocused(member);
			}
			return true;
		}

		return false;
	}

	public boolean release() {
		if (renderer.pressedId().isEmpty() || renderer.pressReleased()) {
			return false;
		}

		renderer.releasePress();
		Minecraft minecraft = Minecraft.getInstance();
		UUID clicked = model.clickedMember();

		if (clicked != null && clicked.equals(memberAtPointer(minecraft))
				&& !ConductorControls.isDrag(model.clickX(), model.clickY(), minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos())) {
			ConductorControls.clickedUnit(clicked);
		} else if (clicked != null) {
			ConductorControls.cancelDoubleClick();
		}
		model.setClickedMember(null);
		return true;
	}

	public boolean rightClick(Minecraft minecraft) {
		if (!model.modeDropdown().isEmpty()) {
			model.setModeDropdown("");
			return true;
		}

		if (model.teamDropdown()) {
			model.setTeamDropdown(false);
			return true;
		}

		for (UIElement element = renderer.elementAtCurrentUI(minecraft); element != null; element = element.getParent()) {
			if (cards.skillIds().containsKey(element.getId()) || ConductorHudLayout.SKILL_RAIL.equals(element.getId())) {
				if (ConductorControls.pendingAction() == ConductorCommandPayload.Action.CAST) {
					ConductorControls.setPending(null);
				}

				return true;
			}

			UUID member = cards.selectedIds().get(element.getId());

			if (member != null) {
				renderer.addRemovedCard(member, (Button) element, memberName(member));
				ConductorControls.removeMember(member);
				return true;
			}
		}

		return contains(minecraft);
	}

	public boolean handleSearchKey(KeyEvent event, int action) {
		if (!ConductorControls.active() || Minecraft.getInstance().screen != null) {
			return false;
		}

		if (!model.modeDropdown().isEmpty()) {
			if (action == GLFW.GLFW_RELEASE) {
				return true;
			}

			if (event.isEscape()) {
				model.setModeDropdown("");
			} else if (event.key() == GLFW.GLFW_KEY_ENTER) {
				applyMode(model.modeCursor());
				model.setModeDropdown("");
			} else if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_TAB) {
				int direction = event.key() == GLFW.GLFW_KEY_UP || Minecraft.getInstance().hasShiftDown() ? -1 : 1;
				model.setModeCursor(Math.floorMod(model.modeCursor() + direction, renderer.modeCount()));
			}

			return true;
		}

		if (model.teamDropdown()) {
			if (action == GLFW.GLFW_RELEASE) {
				return true;
			}

			if (event.isEscape()) {
				model.setTeamDropdown(false);
			} else if (event.key() == GLFW.GLFW_KEY_ENTER) {
				List<String> teams = model.teams(ConductorClient.revision(), () -> ConductorClient.snapshot().teams().keySet());

				if (!teams.isEmpty()) {
					model.selectTeam(teams.get(Math.min(model.teamCursor(), teams.size() - 1)));
				}
				model.setTeamDropdown(false);
			} else if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_TAB) {
				int direction = event.key() == GLFW.GLFW_KEY_UP || Minecraft.getInstance().hasShiftDown() ? -1 : 1;
				int size = Math.max(1, model.teams(ConductorClient.revision(),
						() -> ConductorClient.snapshot().teams().keySet()).size());
				model.setTeamCursor(Math.floorMod(model.teamCursor() + direction, size));
				model.setTeamScroll(Math.clamp(model.teamScroll(), Math.max(0, model.teamCursor() - TEAM_DROPDOWN_ROWS + 1), model.teamCursor()));
			}

			return true;
		}

		if (event.isEscape()) {
			renderer.setSearchFocused(false);
			return false;
		}

		if (action == GLFW.GLFW_RELEASE) {
			return renderer.searchFocused() || event.key() == GLFW.GLFW_KEY_TAB;
		}

		if (event.key() == GLFW.GLFW_KEY_TAB) {
			List<String> ids = renderer.focusableIds();

			if (ids.isEmpty()) {
				return true;
			}

			int index = ids.indexOf(renderer.keyboardFocus());
			int direction = Minecraft.getInstance().hasShiftDown() ? -1 : 1;
			renderer.setKeyboardFocus(ids.get(Math.floorMod(index + direction, ids.size())));
			renderer.revealKeyboardFocus();
			renderer.setSearchFocused(ConductorHudLayout.ROSTER_SEARCH.equals(renderer.keyboardFocus())
					|| ConductorHudLayout.SKILL_SEARCH.equals(renderer.keyboardFocus()));

			if (renderer.searchFocused()) {
				renderer.setSearchTarget(renderer.keyboardFocus());
			}
			ConductorControls.releaseCameraKeys();
			return true;
		}

		if (event.key() == GLFW.GLFW_KEY_ENTER) {
			if (renderer.searchFocused()) {
				renderer.setSearchFocused(false);
			} else {
				layout.selectId(renderer.keyboardFocus(), UIElement.class).findFirst().ifPresent(element -> {
					activate(element, Minecraft.getInstance());
					renderer.releasePress();
				});
			}
			return !renderer.keyboardFocus().isEmpty();
		}

		if (!renderer.searchFocused()) {
			return false;
		}

		String query = currentSearch();

		if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
			setSearchQuery(query.substring(0, query.offsetByCodePoints(query.length(), -1)));
		} else if (event.key() == GLFW.GLFW_KEY_DELETE) {
			setSearchQuery("");
		}
		return true;
	}

	private String currentSearch() {
		return ConductorHudLayout.SKILL_SEARCH.equals(renderer.searchTarget()) ? model.skillSearch() : model.rosterSearch();
	}

	private void setSearchQuery(String query) {
		if (ConductorHudLayout.SKILL_SEARCH.equals(renderer.searchTarget())) {
			model.setSkillSearch(query);
		} else {
			model.setRosterSearch(query);
		}
		model.setSearchChangedAt(query.isEmpty() ? 0 : System.currentTimeMillis());
	}

	public boolean handleCharTyped(long handle, CharacterEvent event) {
		Minecraft minecraft = Minecraft.getInstance();

		if (!renderer.searchFocused() || handle != minecraft.getWindow().handle() || minecraft.screen != null
				|| !event.isAllowedChatCharacter()) {
			return false;
		}

		String query = currentSearch();

		if (query.codePointCount(0, query.length()) >= SEARCH_LIMIT) {
			return true;
		}

		setSearchQuery(query + new String(Character.toChars(event.codepoint())));
		return true;
	}

	public boolean scroll(Minecraft minecraft, double scrollDeltaY) {
		if (!model.modeDropdown().isEmpty()) {
			if (scrollDeltaY != 0) {
				model.setModeCursor(Math.floorMod(model.modeCursor() + (scrollDeltaY > 0 ? -1 : 1), renderer.modeCount()));
			}
			return true;
		}

		if (model.teamDropdown()) {
			int size = model.teams(ConductorClient.revision(), () -> ConductorClient.snapshot().teams().keySet()).size();
			model.setTeamScroll(Math.clamp(model.teamScroll() + (scrollDeltaY > 0.0 ? -1 : 1), 0,
					Math.max(0, size - TEAM_DROPDOWN_ROWS)));
			return true;
		}

		if (scrollDeltaY == 0.0D) {
			return false;
		}

		for (UIElement element = renderer.elementAtCurrentUI(minecraft); element != null; element = element.getParent()) {
			if (!(element instanceof ScrollerView scroller)) {
				continue;
			}

			float direction = (scrollDeltaY > 0.0D ? -1.0F : 1.0F)
					* (minecraft.hasShiftDown() ? SHIFT_SCROLL_MULTIPLIER : 1);

			switch (element.getId()) {
				case ConductorHudLayout.FOCUS_DETAILS, ConductorHudLayout.FOCUS_EFFECTS -> {
					boolean horizontal = minecraft.hasShiftDown();
					float extent = horizontal ? scroller.getContainerWidth() - scroller.viewPort.getContentWidth()
							: scroller.getContainerHeight() - scroller.viewPort.getContentHeight();

					if (extent > 0) {
						var axis = horizontal ? scroller.horizontalScroller : scroller.verticalScroller;
						float step = (scrollDeltaY > 0 ? -SCROLL_STEP : SCROLL_STEP) / extent;
						axis.setNormalizedValue(net.minecraft.util.Mth.clamp(axis.getNormalizedValue() + step, 0, 1), true);
					}

					return true;
				}
				case ConductorHudLayout.ROSTER_LIST, ConductorHudLayout.SKILL_LIST,
				     ConductorHudLayout.SELECTED_LIST -> {
					scrollList(scroller, direction);
					return true;
				}
				default -> {
				}
			}
		}

		return contains(minecraft);
	}

	private static void scrollList(ScrollerView list, float direction) {
		float extent = list.viewContainer.getSizeHeight() - list.viewPort.getContentHeight();

		if (extent <= 0) {
			return;
		}

		float step = direction * SCROLL_STEP / extent;
		list.verticalScroller.setNormalizedValue(net.minecraft.util.Mth.clamp(list.verticalScroller.getNormalizedValue() + step, 0, 1), true);
	}

	public boolean handleSkillKey(KeyEvent event, int action) {
		if (renderer.searchFocused() || model.teamDropdown() || !model.modeDropdown().isEmpty()) {
			return false;
		}

		int key = event.key();
		int index = key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 ? key - GLFW.GLFW_KEY_1
				: key == GLFW.GLFW_KEY_0 ? SKILL_HOTKEY_COUNT - 1
				: key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9 ? key - GLFW.GLFW_KEY_KP_1
				: key == GLFW.GLFW_KEY_KP_0 ? SKILL_HOTKEY_COUNT - 1 : -1;
		boolean previous = key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT;
		boolean next = key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD;

		if (index < 0 && !previous && !next) {
			return false;
		}

		if (action != GLFW.GLFW_PRESS || model.previousAbilities().isEmpty()) {
			return true;
		}

		if (previous || next) {
			int current = -1;

			for (int i = 0; i < model.previousAbilities().size(); i++) {
				if (ConductorControls.isPendingSkill(model.previousAbilities().get(i).toString(), model.focused())) {
					current = i;
				}
			}
			index = current < 0 ? (previous ? model.previousAbilities().size() - 1 : 0)
					: Math.floorMod(current + (previous ? -1 : 1), model.previousAbilities().size());
		}

		if (index < model.previousAbilities().size()) {
			selectSkill(model.previousAbilities().get(index).toString(), false);
		}
		return true;
	}

	public void selectSkill(String skill, boolean mouse) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = model.focused() == null || minecraft.level == null ? null : minecraft.level.getEntity(model.focused());

		if (!(entity instanceof Mob mob)) {
			return;
		}

		IConductorTargeting targeting = org.unitego.lobecorp.conductor.ability.ConductorTargetingResolver.find(mob, skill);

		if (targeting == null) {
			return;
		}

		ConductorControls.setPendingSkill(skill, model.focused());
		long now = System.currentTimeMillis();

		if (mouse && targeting.directional() && skill.equals(lastSkillClick)
				&& now - lastSkillClickTime < SKILL_DOUBLE_CLICK_MS) {
			ConductorControls.castFacing();
			lastSkillClick = "";
		} else {
			lastSkillClick = mouse ? skill : "";

			if (mouse && targeting.targetKind() == IConductorTargeting.TargetKind.SELF
					&& (!(targeting instanceof EntitySkillConductorAbility entitySkill) || entitySkill.canBeginCast(mob))) {
				sendFocused(ConductorCommandPayload.Action.CAST, false, skill);
			}
		}

		lastSkillClickTime = now;
	}

	private void applyMode(int index) {
		ConductorData.Unit unit = renderer.selectedModeUnit();

		if (unit == null || index < 0 || index >= renderer.modeCount()) {
			return;
		}

		if (ConductorHudLayout.ATTACK_MODE.equals(model.modeDropdown())) {
			send(ConductorCommandPayload.Action.SET_ATTACK_MODE,
					ConductorHudLayout.ATTACK_OPTIONS.get(index) == ConductorData.AttackState.MANUAL, "");
		} else if (ConductorHudLayout.BEHAVIOR_MODE.equals(model.modeDropdown())) {
			ConductorClient.setCombatBehavior(unit.team(), ConductorControls.controlledSelection(), ConductorHudLayout.BEHAVIOR_OPTIONS.get(index));
		} else {
			ConductorClient.send(ConductorCommandPayload.Action.SET_MODE, unit.team(), "",
					ConductorControls.controlledSelection(), null, 0.0D, 0.0D, 0.0D,
					ConductorHudLayout.ACTIVITY_OPTIONS.get(index), false, "");
		}
	}

	private void openModeDropdown(String id) {
		model.setModeDropdown(id);
		model.setTeamDropdown(false);
		ConductorControls.releaseCameraKeys();
		model.setModeCursor(renderer.currentModeIndex());
	}

	private static boolean hasRemoteSelection(Minecraft minecraft) {
		if (minecraft.level == null) {
			return false;
		}

		for (UUID member : ConductorControls.selected()) {
			if (ConductorClient.unit(member) != null && minecraft.level.getEntity(member) == null) {
				return true;
			}
		}

		return false;
	}

	private void send(ConductorCommandPayload.Action action, boolean flag, String skill) {
		String team = action == ConductorCommandPayload.Action.ASSIGN ? model.teamSelection() : ConductorControls.selectedTeam();

		if (team.isBlank()) {
			team = model.teamSelection();
		}

		if (!team.isBlank() && !ConductorControls.selected().isEmpty()) {
			ConductorClient.send(action, team, "", action == ConductorCommandPayload.Action.ASSIGN
							? ConductorControls.selected() : ConductorControls.controlledSelection(), null,
					0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, flag, skill);
		}
	}

	private void sendFocused(ConductorCommandPayload.Action action, boolean flag, String skill) {
		List<UUID> units = action == ConductorCommandPayload.Action.CAST
				? ConductorControls.skillUnits(model.focused()) : model.focused() == null ? List.of() : List.of(model.focused());
		ConductorData.Unit unit = units.isEmpty() ? null : ConductorClient.unit(units.getFirst());

		if (unit != null) {
			ConductorClient.send(action, unit.team(), "", units, null,
					0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, flag, skill);
		}
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
		lastSkillClick = "";
		lastSkillClickTime = 0L;
	}
}
