package org.unitego.lobecorp.conductor.lifecycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.unitego.lobecorp.util.entity.combat.CombatUtil;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.event.EntitySkillEvent;
import org.unitego.lobecorp.network.tc.ConductorSnapshotPayload;
import org.unitego.lobecorp.registry.tag.LcEntitySkillTags;

/// 指挥家生命周期与战斗事件的业务处理入口，由 events 包订阅器调用。
public class ConductorEvents {
	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ConductorSnapshotPayload.send(player, ConductorData.get(player.level().getServer()));
		}
	}

	public static void onTargetChange(LivingChangeTargetEvent event) {
		LivingEntity target = event.getNewAboutToBeSetTarget();
		if (target == null || !(event.getEntity().level() instanceof ServerLevel)) {
			return;
		}

		if (event.getEntity() instanceof Mob mob && !ConductorController.allowsTargetChange(mob, target)) {
			event.setNewAboutToBeSetTarget(null);
		}
	}

	public static void onSkillCast(EntitySkillEvent.Cast event) {
		if (!CombatUtil.acceptsControl(event.getEntity())) {
			return;
		}
		if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
				|| ConductorController.isExplicitSkillCast(mob, event.getSkill())) {
			return;
		}

		ConductorData.Unit unit = ConductorData.get(level.getServer()).unit(mob.getUUID());
		if (unit != null && ConductorController.hasPendingSkillCast(mob)) {
			event.setCanceled(true);
			return;
		}

		if (unit != null && unit.order() == ConductorData.OrderType.NONE
				&& unit.combatBehavior() == ConductorData.CombatBehavior.PASSIVE) {
			event.setCanceled(true);
			return;
		}

		if (!isAttackSkill(event.getSkill())) {
			return;
		}
		if (unit != null && unit.order() == ConductorData.OrderType.NONE
				&& unit.combatBehavior() == ConductorData.CombatBehavior.NEUTRAL) {
			LivingEntity target = mob.getTarget();
			if (target == null && mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
				target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			}

			if (target == null || !ConductorController.allowsTargetChange(mob, target)) {
				event.setCanceled(true);
				return;
			}
		}

		if (unit != null && unit.attackState() == ConductorData.AttackState.MANUAL
				&& !event.getSkill().isBasicAttack()
				&& EntitySkillUtil.getSkills(mob).stream().anyMatch(skill ->
				skill.isBasicAttack() && isAttackSkill(skill))) {
			event.setCanceled(true);
		}
	}

	private static boolean isAttackSkill(IEntitySkill<?> skill) {
		return skill.is(LcEntitySkillTags.DAMAGE) || skill.is(LcEntitySkillTags.MELEE)
				|| skill.is(LcEntitySkillTags.PURE_MELEE) || skill.is(LcEntitySkillTags.RANGED)
				|| skill.is(LcEntitySkillTags.PROJECTILE);
	}
}
