package org.unitego.lobecorp.registry.entity.skill;

import net.minecraft.world.entity.EntityType;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/// 实体技能默认定义，与指挥家的包装能力默认定义相互独立。
public class EntitySkillDefaults {
	/// 延迟读取实体类型和技能注册项，避免注册绑定前访问 DeferredHolder。
	private static final Map<Supplier<? extends EntityType<?>>, Supplier<? extends Collection<IEntitySkill<?>>>> DEFAULTS =
			new LinkedHashMap<>();

	public static void register(Supplier<? extends EntityType<?>> type, Supplier<? extends Collection<IEntitySkill<?>>> skills) {
		DEFAULTS.put(type, skills);
	}

	public static Set<IEntitySkill<?>> forType(EntityType<?> type) {
		Set<IEntitySkill<?>> skills = new LinkedHashSet<>();
		DEFAULTS.forEach((registeredType, defaults) -> {
			if (registeredType.get() == type)
				skills.addAll(defaults.get());
		});
		return Collections.unmodifiableSet(skills);
	}

	public static void init() {
		register(OrdealEntityTypes.SWEEPER, () -> List.of(SweeperSkills.ATTACK.get(), SweeperSkills.LEAP.get(),
				SweeperSkills.SWEEP.get(), SweeperSkills.REASSEMBLE.get()));
		register(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED, () -> List.of(TheQueenOfHatredSkills.REPEL.get(),
				TheQueenOfHatredSkills.SWEEP.get(), TheQueenOfHatredSkills.ATTACK.get(), TheQueenOfHatredSkills.SPIN.get(),
				TheQueenOfHatredSkills.LASER.get(), TheQueenOfHatredSkills.BLINK.get(), TheQueenOfHatredSkills.TELEPORT.get(),
				TheQueenOfHatredSkills.DASH.get(), TheQueenOfHatredSkills.REFRACTION.get(), TheQueenOfHatredSkills.CONVERGENT.get(),
				TheQueenOfHatredSkills.STAR_BEAM.get(), TheQueenOfHatredSkills.DAMAGE_REDUCTION.get(), TheQueenOfHatredSkills.PURIFICATION.get(),
				TheQueenOfHatredSkills.SLOWNESS.get(), TheQueenOfHatredSkills.MARK.get(), TheQueenOfHatredSkills.STARFALL.get(),
				TheQueenOfHatredSkills.PILLAR_OF_LIGHT.get()));
	}
}
