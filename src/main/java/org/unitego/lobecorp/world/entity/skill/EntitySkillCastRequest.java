package org.unitego.lobecorp.world.entity.skill;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// 一次实体技能施放请求。
///
/// 请求只描述本次施放的目标，不保存实体技能运行状态；运行状态由
/// {@link EntitySkillRuntime} 创建后交给技能管理器维护。
public record EntitySkillCastRequest<T extends LivingEntity>(
		IEntitySkill<T> skill,
		@Nullable Entity target,
		@Nullable Vec3 targetPosition
) {
	/// 创建没有实体目标和坐标目标的自身施放请求。
	public static <T extends LivingEntity> EntitySkillCastRequest<T> of(IEntitySkill<T> skill) {
		return new EntitySkillCastRequest<>(skill, null, null);
	}
}
