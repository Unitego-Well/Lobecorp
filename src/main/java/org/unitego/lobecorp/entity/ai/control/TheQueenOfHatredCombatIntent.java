package org.unitego.lobecorp.entity.ai.control;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredSkill;

/// 憎恶皇后战斗控制器与攻击规划器之间传递的单次决策。
public record TheQueenOfHatredCombatIntent(Type type, @Nullable TheQueenOfHatredSkill skill,
		@Nullable LivingEntity target, @Nullable Vec3 destination, boolean retreat) {
	public static TheQueenOfHatredCombatIntent cast(TheQueenOfHatredSkill skill, @Nullable LivingEntity target) {
		return new TheQueenOfHatredCombatIntent(Type.CAST, skill, target, null, false);
	}

	public static TheQueenOfHatredCombatIntent reposition(TheQueenOfHatredSkill skill, @Nullable LivingEntity target,
			Vec3 destination, boolean retreat) {
		return new TheQueenOfHatredCombatIntent(Type.REPOSITION, skill, target, destination, retreat);
	}

	public enum Type {
		CAST,
		REPOSITION
	}
}
