package org.unitego.lobecorp.client.conductor.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
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
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.client.conductor.render.ConductorCamera;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.ability.IConductorAbility;
import org.unitego.lobecorp.conductor.ability.IConductorTargeting;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;
import org.unitego.lobecorp.registry.translation.ConductorTranslationKeys;
import org.unitego.lobecorp.util.conductor.ConductorUtil;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.LaserSkill;

import java.util.*;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;
import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.*;

/// 指挥家 HUD 的悬浮提示生成与绘制。
public class ConductorHudTooltip {
	private static final String RANGE_INTERVAL_FORMAT = "%s–%s";
	private final Map<LivingEntity, Map<MobEffectInstance, EffectProgress>> effectProgress = new java.util.WeakHashMap<>();
	private final ConductorHudTooltipState state = new ConductorHudTooltipState();
	private List<Component> preparedLines = List.of();
	private List<EffectIcon> preparedEffects = List.of();

	private static void appendSkillStatus(List<Component> lines, Mob mob, IConductorAbility ability, int remaining) {
		boolean casting = ability instanceof EntitySkillConductorAbility entitySkill && !entitySkill.canBeginCast(mob);
		boolean available = ability.isAvailable(mob);

		if (remaining > 0) {
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_COOLDOWN).withStyle(ChatFormatting.RED));
		}

		if (casting) {
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_CASTING).withStyle(ChatFormatting.RED));
		}

		if (!available) {
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_CONDITION).withStyle(ChatFormatting.RED));
		}

		if (!ConductorControls.isPendingSkill(ability.id().toString(), mob.getUUID())) {
			return;
		}

		ConductorControls.AimPreview aim = ConductorControls.aimPreview(Minecraft.getInstance());

		if (aim == null || aim.caster() != mob) {
			return;
		}

		if (aim.rangeLimited()) {
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_OUT_OF_RANGE).withStyle(ChatFormatting.YELLOW));
		} else if (!aim.valid() && remaining == 0 && available && !casting) {
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SKILL_UNAVAILABLE_TARGET).withStyle(ChatFormatting.RED));
		}
	}

	/// 共享的纯文本提示绘制入口；不依赖 HUD 宿主状态。
	public static void drawTooltip(GuiGraphicsExtractor graphics, List<Component> lines) {
		drawLines(graphics, lines, List.of());
	}

	private static void drawLines(GuiGraphicsExtractor graphics, List<Component> lines, List<EffectIcon> effects) {
		Minecraft minecraft = Minecraft.getInstance();
		int maxWidth = Math.max(1, Math.min(TOOLTIP_WIDTH, graphics.guiWidth() - HOVER_PADDING * 2));
		List<FormattedCharSequence> wrapped = new ArrayList<>();

		for (Component line : lines) {
			wrapped.addAll(minecraft.font.split(line, maxWidth - HOVER_PADDING * 2));
		}
		int lineHeight = minecraft.font.lineHeight + HOVER_LINE_GAP;
		int columns = Math.max(1, (maxWidth - HOVER_PADDING * 2) / EFFECT_CELL_WIDTH);
		int maxEffectRows = Math.max(0, (graphics.guiHeight() - HOVER_PADDING * 4 - lineHeight) / EFFECT_CELL_HEIGHT);
		int effectCount = Math.min(effects.size(), maxEffectRows * columns);
		int effectRows = (effectCount + columns - 1) / columns;
		int effectsHeight = effectRows * EFFECT_CELL_HEIGHT;
		int maxLines = Math.max(1, (graphics.guiHeight() - HOVER_PADDING * 4 - effectsHeight) / lineHeight);

		if (wrapped.size() > maxLines) {
			wrapped = new ArrayList<>(wrapped.subList(0, maxLines));
		}
		int width = Math.min(maxWidth, wrapped.stream().mapToInt(minecraft.font::width).max().orElse(0) + HOVER_PADDING * 2);

		if (effectCount > 0) {
			width = maxWidth;
		}
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

	public static void drawEffectIcon(GuiGraphicsExtractor graphics, EffectIcon icon, int x, int y) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(icon.effect().getEffect()),
				x, y, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE);
		drawTextFit(graphics, Component.literal(Integer.toString(icon.effect().getAmplifier() + 1)),
				x + EFFECT_ICON_SIZE + HOVER_LINE_GAP, y + (EFFECT_ICON_SIZE - Minecraft.getInstance().font.lineHeight) / 2,
				EFFECT_CELL_WIDTH - EFFECT_ICON_SIZE - HOVER_LINE_GAP - HOVER_PADDING, TEXT_COLOR);
		int barY = y + EFFECT_ICON_SIZE + HOVER_LINE_GAP;
		int barWidth = EFFECT_CELL_WIDTH - HOVER_PADDING;
		graphics.fill(x, barY, x + barWidth, barY + EFFECT_BAR_HEIGHT, LINE_OUTER);
		graphics.fill(x, barY, x + Math.round(barWidth * icon.remaining()), barY + EFFECT_BAR_HEIGHT, ORE_GREEN);
	}

	private static void drawTextFit(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
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

	private static Component memberDistance(UUID member) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(member);
		return Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DISTANCE, entity == null ? UNKNOWN_MARK
				: String.format(Locale.ROOT, DECIMAL_FORMAT, entity.position().distanceTo(ConductorCamera.center())));
	}

	private static Component memberDimension(UUID member) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(member);
		ConductorData.Unit unit = ConductorClient.unit(member);
		return Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DIMENSION, entity != null ? entity.level().dimension().identifier().toString()
				: unit == null ? UNKNOWN_MARK : unit.dimension());
	}

	private static Component memberType(UUID member) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(member);
		ConductorData.MemberInfo info = ConductorClient.snapshot().memberInfo(member);
		return Component.literal(entity != null ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
				: info == null ? UNKNOWN_MARK : info.type());
	}

	private static Component attributes(LivingEntity living) {
		String attack = living.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
				? String.format(Locale.ROOT, DECIMAL_FORMAT, living.getAttributeValue(Attributes.ATTACK_DAMAGE)) : UNKNOWN_MARK;
		return Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ATTRIBUTES, attack, living.getArmorValue());
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

	/// 清除悬浮计时；宿主释放时调用。
	public void clearTransient() {
		effectProgress.clear();
		state.clear();
		preparedLines = List.of();
		preparedEffects = List.of();
	}

	/// 在最终布局后采样悬浮目标并生成当帧提示快照；绘制阶段只消费快照。
	public void prepareAtPointer(ConductorHudLayout layout, ConductorHudCards cards, ConductorHudModel model,
	                             @Nullable String hoveredId, long now) {
		preparedLines = List.of();
		preparedEffects = List.of();
		Minecraft minecraft = Minecraft.getInstance();

		if (ConductorControls.pendingAction() == ConductorCommandPayload.Action.CAST
				&& !ConductorHudInput.containsStatic(layout, minecraft)) {
			return;
		}

		if (minecraft.level == null) {
			return;
		}

		String id = hoveredId == null ? "" : hoveredId;
		UUID member = cards.rosterIds().get(id);

		if (member == null) {
			member = cards.selectedIds().get(id);
		}

		if (ConductorHudLayout.FOCUS_PORTRAIT.equals(id)) {
			member = model.focused();
		}
		LivingEntity worldEntity = id.isEmpty() ? ConductorControls.hoveredLivingEntity(minecraft) : null;
		String key = worldEntity == null ? id : worldEntity.getUUID().toString();

		state.prepareTarget(key, now);

		if (!state.visible(now)) {
			return;
		}

		String skill = cards.skillIds().get(id);

		if (skill != null && model.focused() != null && minecraft.level.getEntity(model.focused()) instanceof Mob mob) {
			IConductorAbility ability = ConductorUtil.ability(mob, Identifier.parse(skill));

			if (ability != null) {
				preparedLines = skillLines(mob, ability);
			}
			return;
		}

		if (member == null && worldEntity != null) {
			member = worldEntity.getUUID();
		}
		List<Component> lines = new ArrayList<>();
		List<EffectIcon> effects = List.of();

		if (member != null) {
			effects = appendMemberTooltip(minecraft.level, id, member, lines, cards);
		} else if (model.hints().containsKey(id)) {
			lines.add(model.hints().get(id));

			if (isDisabled(id, cards, model)) {
				lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_NO_SELECTION));
			}
		} else if (ConductorHudLayout.ROSTER_SEARCH.equals(id) || ConductorHudLayout.SKILL_SEARCH.equals(id)) {
			lines.add(Component.translatable(ConductorHudLayout.ROSTER_SEARCH.equals(id) ? ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SEARCH : ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_SEARCH_SKILLS));
		}

		if (ConductorHudLayout.FOCUS_PORTRAIT.equals(id) && ConductorControls.selected().size() > 1) {
			List<LivingEntity> known = ConductorControls.selected().stream().map(minecraft.level::getEntity)
					.filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList();

			if (!known.isEmpty()) {
				double average = known.stream().mapToDouble(entity -> Mth.clamp(
						entity.getHealth() / Math.max(1, entity.getMaxHealth()), 0, 1)).average().orElse(0);
				lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_AVERAGE_HEALTH,
						Math.round(average * PERCENT), known.size(), ConductorControls.selected().size()));
			}
		}

		preparedLines = List.copyOf(lines);
		preparedEffects = List.copyOf(effects);
	}

	public void drawAtPointer(GuiGraphicsExtractor graphics) {
		if (!preparedLines.isEmpty()) {
			drawLines(graphics, preparedLines, preparedEffects);
		}
	}

	private boolean isDisabled(String id, ConductorHudCards cards, ConductorHudModel model) {
		if (ConductorHudLayout.ASSIGN.equals(id)) {
			return ConductorControls.selected().isEmpty();
		}

		if (List.of(ConductorHudLayout.MOVE, ConductorHudLayout.ATTACK, ConductorHudLayout.STOP,
				ConductorHudLayout.RELEASE).contains(id)) {
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

	/// 生成成员属性、队伍及命令状态提示，并返回需要绘制的效果图标。
	private List<EffectIcon> appendMemberTooltip(ClientLevel level, String id, UUID member, List<Component> lines,
	                                             ConductorHudCards cards) {
		List<EffectIcon> effects = List.of();
		lines.add(memberName(member));
		lines.add(memberType(member));

		if (cards.rosterIds().containsKey(id)) {
			lines.add(memberDistance(member));
			lines.add(memberDimension(member));
		}

		Entity entity = level.getEntity(member);

		if (entity instanceof LivingEntity living) {
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_HEALTH, (int) Math.ceil(living.getHealth()), (int) Math.ceil(living.getMaxHealth())));
			lines.add(attributes(living));
			effects = sampleEffectIcons(living);
		} else {
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_UNKNOWN_HEALTH));
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_UNKNOWN_ATTRIBUTES));
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_REMOTE_FOCUS));
		}

		ConductorData.Unit unit = ConductorClient.unit(member);
		ConductorData.Team team = unit == null ? null : ConductorClient.snapshot().team(unit.team());
		lines.add(team == null ? Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_NO_TEAM) : Component.literal(team.name()).withColor(team.color()));

		if (unit != null && team != null) {
			lines.add(Component.translatable(ConductorHudLayout.orderKey(unit.order())));
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_BEHAVIOR_VALUE, Component.translatable(ConductorHudLayout.combatBehaviorKey(unit.combatBehavior()))));
			lines.add(Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ACTIVITY_VALUE, Component.translatable(ConductorHudLayout.behaviorKey(unit.behaviorState()))));
			lines.add(Component.translatable(unit.attackState() == ConductorData.AttackState.MANUAL
					? ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ATTACK_BASIC : ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_ATTACK_AI));
			String result = switch (unit.moveResult()) {
				case NONE -> null;
				case ARRIVED -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_MOVE_ARRIVED;
				case UNREACHABLE -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_MOVE_UNREACHABLE;
				case CANCELED -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_MOVE_CANCELED;
			};

			if (result != null) {
				lines.add(Component.translatable(result));
			}
		}

		return effects;
	}

	/// 在准备阶段推进效果进度并返回当帧图标快照。
	public List<EffectIcon> prepareEffectIcons(LivingEntity living) {
		return List.copyOf(sampleEffectIcons(living));
	}

	private List<EffectIcon> sampleEffectIcons(LivingEntity living) {
		Map<MobEffectInstance, EffectProgress> durations = effectProgress.computeIfAbsent(living, _ -> new IdentityHashMap<>());
		durations.keySet().removeIf(effect -> living.getEffect(effect.getEffect()) != effect);
		List<EffectIcon> icons = new ArrayList<>();

		for (MobEffectInstance effect : living.getActiveEffects()) {
			EffectProgress progress = durations.computeIfAbsent(effect, _ -> new EffectProgress());
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

	private List<Component> skillLines(Mob mob, IConductorAbility ability) {
		IConductorAbility.Display display = ability.display(mob);
		Component damage;

		if (display.damageKind() == IConductorAbility.DamageKind.FIXED) {
			damage = Component.literal(Double.toString(display.damage()));
		} else if (display.damageKind() == IConductorAbility.DamageKind.DYNAMIC) {
			double basicDamage = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
			damage = basicDamage > 0.0D ? Component.literal(Double.toString(basicDamage))
					: Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DAMAGE_DYNAMIC);
		} else {
			damage = Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DAMAGE_NONE);
		}

		String typeKey = switch (display.abilityKind()) {
			case BASIC -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TYPE_BASIC;
			case ATTACK -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TYPE_ATTACK;
			case SUPPORT -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TYPE_SUPPORT;
		};
		String targetKey = switch (ability.targetKind()) {
			case SELF -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET_SELF;
			case ENTITY -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET_ENTITY;
			case POSITION -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET_POSITION;
			case EITHER -> ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET_EITHER;
		};
		double range = ability.displayRange(mob);
		Component rangeText = ability instanceof EntitySkillConductorAbility.Laser
				? Component.literal(String.format(Locale.ROOT, RANGE_INTERVAL_FORMAT, LaserSkill.MINIMUM_RANGE, range))
				: range > 0.0D ? Component.literal(Double.toString(range)) : Component.translatable(
				ability.targetKind() == IConductorTargeting.TargetKind.SELF
						? ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_RANGE_SELF : ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_RANGE_UNSPECIFIED);
		int total = Math.max(ConductorClient.abilityCooldownTotalTicks(mob.getUUID(), ability.id()),
				ability.totalCooldownTicks(mob));
		int remaining = ConductorClient.abilityCooldownTicks(mob.getUUID(), ability.id());
		List<Component> lines = List.of(
				Component.translatableWithFallback(ability instanceof EntitySkillConductorAbility
								? ConductorTranslationKeys.skillName(ability.id()) : ConductorTranslationKeys.abilityName(ability.id()),
						ability.id().getPath()),
				Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_DAMAGE, damage),
				Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TYPE, Component.translatable(typeKey)),
				Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_TARGET, Component.translatable(targetKey)),
				Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_RANGE, rangeText),
				Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_COOLDOWN_TOTAL,
						String.format(Locale.ROOT, DECIMAL_FORMAT, total / (float) TICKS_PER_SECOND)),
				remaining == 0 ? Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_READY) : Component.translatable(ConductorTranslationKeys.SCREEN_LOBECORP_CONDUCTOR_COOLDOWN_REMAINING,
						String.format(Locale.ROOT, DECIMAL_FORMAT, remaining / (float) TICKS_PER_SECOND)));
		List<Component> details = new ArrayList<>(lines);
		appendSkillStatus(details, mob, ability, remaining);
		Component description = ConductorTranslationKeys.abilityDescription(ability.id());

		if (!description.getString().isEmpty()) {
			details.add(description);
		}
		return List.copyOf(details);
	}

	public void draw(GuiGraphicsExtractor graphics, List<Component> lines) {
		drawLines(graphics, lines, List.of());
	}

	public record EffectIcon(MobEffectInstance effect, float remaining) {
	}

	private static class EffectProgress {
		private int previous;
		private int total;
		private int amplifier;
	}
}
