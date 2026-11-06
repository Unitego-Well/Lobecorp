package org.unitego.lobecorp.client.conductor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.conductor.hud.ConductorHudCards;
import org.unitego.lobecorp.client.conductor.hud.ConductorHudInput;
import org.unitego.lobecorp.client.conductor.hud.ConductorHudLayout;
import org.unitego.lobecorp.client.conductor.hud.ConductorHudModel;
import org.unitego.lobecorp.client.conductor.hud.ConductorHudPreferences;
import org.unitego.lobecorp.client.conductor.hud.ConductorHudRenderer;
import org.unitego.lobecorp.client.conductor.hud.ConductorHudTooltip;
import org.unitego.lobecorp.client.conductor.hud.ConductorHudViewSlot;
import org.unitego.lobecorp.client.conductor.hud.ConductorTexts;
import org.unitego.lobecorp.client.conductor.render.ConductorPortraitCache;
import org.unitego.lobecorp.client.hud.HudLayerManager;
import org.unitego.lobecorp.client.hud.host.HudHost;
import org.unitego.lobecorp.conductor.ability.ConductorTargetingResolver;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.ability.IConductorAbility;
import org.unitego.lobecorp.conductor.ability.IConductorTargeting;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.util.conductor.ConductorUtil;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.LaserSkill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;
import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.*;

/// 指挥家 HUD 的业务门面；持有始终存在的显示模型与当前宿主视图。
public class ConductorHud implements GuiLayer {
	public static final Identifier ID = Lobecorp.id("conductor_hud");
	public static final ConductorHud INSTANCE = new ConductorHud();
	private static final String RANGE_INTERVAL_FORMAT = "%s–%s";
	private final ConductorHudModel model;
	private final ConductorHudTooltip tooltip;
	private final ConductorHudViewSlot<View> view = new ConductorHudViewSlot<>();

	private ConductorHud() {
		model = new ConductorHudModel(ConductorHudPreferences.load());
		tooltip = new ConductorHudTooltip();
	}

	/// 向统一管理器注册宿主工厂与会话清理；重复注册由管理器拒绝。
	public void register() {
		HudLayerManager.INSTANCE.register(ID, this::visible, this::createHost, model::clearTransient);
	}

	private HudHost createHost() {
		var created = new View(model);
		view.set(created);
		return created.host;
	}

	private boolean visible() {
		Minecraft minecraft = Minecraft.getInstance();
		return ConductorControls.active() && minecraft.screen == null && minecraft.level != null;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		HudLayerManager.INSTANCE.render(ID, graphics, deltaTracker);
	}

	public ConductorData.FormationMode formationMode() {
		return model.formationMode();
	}

	public boolean reducedMotion() {
		return model.reducedMotion();
	}

	public void toggleReducedMotion() {
		model.toggleReducedMotion();
	}

	public UUID focusedMember() {
		return model.focused();
	}

	public void setFocusedMember(@Nullable UUID member) {
		model.setFocused(member);
	}

	public String selectedTeam() {
		return model.teamSelection();
	}

	public void selectTeam(String team) {
		model.selectTeam(team);
	}

	/// 重置当前视图的瞬态状态并保留门面引用；不释放宿主。
	public void resetLayout() {
		model.clearTransient();
		ConductorPortraitCache.clear();
		var current = view.get();

		if (current != null) {
			current.resetTransient();
		}
	}

	public boolean handleSearchKey(KeyEvent event, int action) {
		var current = view.get();
		return current != null && current.input.handleSearchKey(event, action);
	}

	public boolean handleSkillKey(KeyEvent event, int action) {
		var current = view.get();
		return current != null && current.input.handleSkillKey(event, action);
	}

	public boolean handleCharTyped(long handle, CharacterEvent event) {
		var current = view.get();
		return current != null && current.input.handleCharTyped(handle, event);
	}

	public boolean scroll(Minecraft minecraft, double scrollDeltaY) {
		var current = view.get();
		return current != null && current.input.scroll(minecraft, scrollDeltaY);
	}

	public UUID memberAtPointer(Minecraft minecraft) {
		var current = view.get();
		return current == null ? null : current.input.memberAtPointer(minecraft);
	}

	public boolean contains(Minecraft minecraft) {
		var current = view.get();
		return current != null && current.input.contains(minecraft);
	}

	public boolean click(Minecraft minecraft) {
		var current = view.get();
		return current != null && current.input.click(minecraft);
	}

	public boolean release() {
		var current = view.get();
		return current != null && current.input.release();
	}

	public boolean rightClick(Minecraft minecraft) {
		var current = view.get();
		return current != null && current.input.rightClick(minecraft);
	}

	public @Nullable HoveredSkill hoveredSkill(Minecraft minecraft) {
		var current = view.get();

		if (current == null || !model.modeDropdown().isEmpty()) {
			return null;
		}

		if (!ConductorControls.active() || minecraft.screen != null || minecraft.level == null) {
			return null;
		}

		String hovered = current.renderer.interactiveId(current.renderer.elementAtCurrentUI(minecraft));
		String skill = current.cards.skillIds().get(hovered);

		if (skill == null || model.focused() == null || !(minecraft.level.getEntity(model.focused()) instanceof Mob mob)) {
			return null;
		}

		IConductorTargeting targeting = ConductorTargetingResolver.find(mob, skill);
		return targeting == null || !ConductorClient.hasAbility(mob.getUUID(), Identifier.parse(skill))
				? null : new HoveredSkill(mob, targeting);
	}

	/// 绘制当前待施放技能的提示；供世界层真实外部入口调用。
	public void renderSelectedSkill(GuiGraphicsExtractor graphics) {
		if (!model.modeDropdown().isEmpty()) {
			return;
		}

		UUID unit = ConductorControls.pendingSkillUnit();
		Minecraft minecraft = Minecraft.getInstance();

		if (unit == null || minecraft.level == null || !(minecraft.level.getEntity(unit) instanceof Mob mob)) {
			return;
		}

		Identifier id = Identifier.parse(ConductorControls.pendingSkill());
		IConductorAbility ability = ConductorUtil.ability(mob, id);

		if (ability == null) {
			return;
		}

		int remaining = ConductorControls.skillUnits(unit).stream()
				.filter(member -> ConductorClient.hasAbility(member, id))
				.mapToInt(member -> ConductorClient.abilityCooldownTicks(member, id)).min().orElse(0);
		List<Component> lines = new ArrayList<>(List.of(ConductorHudLayout.abilityName(ability),
				Component.translatable(ConductorTexts.COOLDOWN_REMAINING,
						String.format(Locale.ROOT, DECIMAL_FORMAT, remaining / (float) TICKS_PER_SECOND))));

		if (ability instanceof EntitySkillConductorAbility.Laser) {
			lines.add(Component.translatable(ConductorTexts.RANGE,
					String.format(Locale.ROOT, RANGE_INTERVAL_FORMAT, LaserSkill.MINIMUM_RANGE, LaserSkill.RANGE)));
		}
		appendSkillStatus(lines, mob, ability, remaining);
		tooltip.draw(graphics, lines);
	}

	private static void appendSkillStatus(List<Component> lines, Mob mob, IConductorAbility ability, int remaining) {
		boolean casting = ability instanceof EntitySkillConductorAbility entitySkill && !entitySkill.canBeginCast(mob);
		boolean available = ability.isAvailable(mob);

		if (remaining > 0) {
			lines.add(Component.translatable(ConductorTexts.SKILL_UNAVAILABLE_COOLDOWN).withStyle(ChatFormatting.RED));
		}

		if (casting) {
			lines.add(Component.translatable(ConductorTexts.SKILL_UNAVAILABLE_CASTING).withStyle(ChatFormatting.RED));
		}

		if (!available) {
			lines.add(Component.translatable(ConductorTexts.SKILL_UNAVAILABLE_CONDITION).withStyle(ChatFormatting.RED));
		}

		if (!ConductorControls.isPendingSkill(ability.id().toString(), mob.getUUID())) {
			return;
		}

		ConductorControls.AimPreview aim = ConductorControls.aimPreview(Minecraft.getInstance());

		if (aim == null || aim.caster() != mob) {
			return;
		}

		if (aim.rangeLimited()) {
			lines.add(Component.translatable(ConductorTexts.SKILL_OUT_OF_RANGE).withStyle(ChatFormatting.YELLOW));
		} else if (!aim.valid() && remaining == 0 && available && !casting) {
			lines.add(Component.translatable(ConductorTexts.SKILL_UNAVAILABLE_TARGET).withStyle(ChatFormatting.RED));
		}
	}

	public record HoveredSkill(Mob mob, IConductorTargeting targeting) {
	}

	private static final class View {
		private final ConductorHudLayout layout;
		private final ConductorHudCards cards;
		private final ConductorHudRenderer renderer;
		private final ConductorHudInput input;
		private final HudHost host;

		private View(ConductorHudModel model) {
			layout = new ConductorHudLayout(model);
			cards = new ConductorHudCards(model, layout);
			var tooltip = ConductorHud.INSTANCE.tooltip;
			renderer = new ConductorHudRenderer(model, layout, cards, tooltip);
			input = new ConductorHudInput(model, layout, cards, renderer);
			host = HudHost.special(layout, _ -> renderer.prepare(), _ -> {
			}, frame -> {
				renderer.prepareAfterLayout(Minecraft.getInstance());
				renderer.draw(frame.graphics());
			}, this::clearTransient);
		}

		private void clearTransient() {
			resetTransient();

			ConductorHud.INSTANCE.view.clearIfSame(this);
		}

		private void resetTransient() {
			cards.clearTransient();
			renderer.clearTransient();
			input.clearTransient();
			layout.clearTransient();
			ConductorHud.INSTANCE.tooltip.clearTransient();
			ConductorPortraitCache.clear();
		}
	}
}
