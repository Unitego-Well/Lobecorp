package org.unitego.lobecorp.entity.entity_skill.effect;

import net.minecraft.server.level.ServerLevel;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 服务端 Level 中技能效果的创建、推进和移除入口。
public final class EntitySkillEffectManager {
	private static final TypedDataKey<Set<EntitySkillEffect>> BOUND_SKILL_EFFECTS = TypedDataKey.create();

	private EntitySkillEffectManager() {
	}

	public static <T extends EntitySkillEffect> T add(T effect) {
		if (!effect.level().getData(LcAttachmentTypes.ENTITY_SKILL_EFFECT_LEVEL_DATA).add(effect)) {
			throw new IllegalStateException("skill effect is already managed");
		}
		effect.markAdded();
		EntitySkillRuntime<?> runtime = effect.boundSkillRuntime();
		if (runtime != null) {
			Set<EntitySkillEffect> effects = runtime.getData(BOUND_SKILL_EFFECTS);
			if (effects == null) {
				effects = new HashSet<>();
				runtime.setData(BOUND_SKILL_EFFECTS, effects);
			}
			effects.add(effect);
		}
		return effect;
	}

	public static boolean remove(EntitySkillEffect effect) {
		boolean removed = effect.level().getData(LcAttachmentTypes.ENTITY_SKILL_EFFECT_LEVEL_DATA).remove(effect);
		effect.markRemoved();
		EntitySkillRuntime<?> runtime = effect.boundSkillRuntime();
		if (runtime != null) {
			Set<EntitySkillEffect> effects = runtime.getData(BOUND_SKILL_EFFECTS);
			if (effects != null) {
				effects.remove(effect);
				if (effects.isEmpty()) {
					runtime.removeData(BOUND_SKILL_EFFECTS);
				}
			}
		}
		return removed;
	}

	public static void removeForSkillRuntime(EntitySkillRuntime<?> runtime) {
		Set<EntitySkillEffect> effects = runtime.removeData(BOUND_SKILL_EFFECTS);
		if (effects == null) {
			return;
		}
		for (EntitySkillEffect effect : Set.copyOf(effects)) {
			remove(effect);
		}
	}

	public static void tick(ServerLevel level) {
		EntitySkillEffectLevelData data = level.getData(LcAttachmentTypes.ENTITY_SKILL_EFFECT_LEVEL_DATA);
		for (EntitySkillEffect effect : List.copyOf(data.effects())) {
			if (effect.isRemoved()) {
				remove(effect);
				continue;
			}
			if (effect.isExpired()) {
				remove(effect);
				continue;
			}
			if (effect.hasUnavailableBoundSource()) {
				remove(effect);
				continue;
			}
			effect.advanceTick();
			if (effect.isRemoved()) {
				remove(effect);
			}
		}
	}
}
