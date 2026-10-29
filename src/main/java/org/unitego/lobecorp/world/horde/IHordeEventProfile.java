package org.unitego.lobecorp.world.horde;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityDimensions;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

public interface IHordeEventProfile {
	Identifier id();

	String faction();

	HordeRules defaults();

	/// 平地稳定奔跑的每逻辑刻位移，不包含接敌加速与碰撞。
	double movementSpeed();

	boolean acceptsTarget(HordeTargetContext context, LivingEntity target);

	Mob createParticipant(ServerLevel level, HordeSpawnContext context);

	EntityDimensions dimensions();

	boolean startsCombat(IEntitySkill<?> skill);

	HordeVisualDefinition visuals();
}
