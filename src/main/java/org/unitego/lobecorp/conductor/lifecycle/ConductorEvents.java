package org.unitego.lobecorp.conductor.lifecycle;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.entity_skill.IEntitySkill;
import org.unitego.lobecorp.util.EntitySkillUtil;
import org.unitego.lobecorp.event.EntitySkillEvent;
import org.unitego.lobecorp.network.tc.ConductorSnapshotPayload;
import org.unitego.lobecorp.registry.tag.LcEntitySkillTags;

/// 指挥家生命周期与战斗事件的业务处理入口，由 events 包订阅器调用。
public class ConductorEvents {
	private static final TagKey<DamageType> CAN_HURT_ALLIES =
			TagKey.create(Registries.DAMAGE_TYPE, Lobecorp.id("can_hurt_allies"));

	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ConductorSnapshotPayload.send(player, ConductorData.get(player.level().getServer()));
		}
	}

	public static void onTargetChange(LivingChangeTargetEvent event) {
		LivingEntity target = event.getNewAboutToBeSetTarget();
		if (target == null || !(event.getEntity().level() instanceof ServerLevel level)) {
			return;
		}
		ConductorData data = ConductorData.get(level.getServer());
		if (event.getEntity() == target || data.allied(event.getEntity().getUUID(), target.getUUID())
				|| event.getEntity() instanceof Mob mob && !ConductorController.allowsTargetChange(mob, target)) {
			event.setNewAboutToBeSetTarget(null);
		}
	}

	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		Entity source = event.getSource().getEntity();
		if (!(source instanceof LivingEntity attacker)
				|| !(event.getEntity().level() instanceof ServerLevel level)) {
			return;
		}
		ConductorData data = ConductorData.get(level.getServer());
		if (attacker == event.getEntity() && data.unit(attacker.getUUID()) != null) {
			event.setCanceled(true);
			return;
		}
		if (event.getSource().is(CAN_HURT_ALLIES)) {
			return;
		}
		if (data.allied(attacker.getUUID(), event.getEntity().getUUID())) {
			event.setCanceled(true);
		}
	}

	public static void onSkillCast(EntitySkillEvent.Cast event) {
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
		if (!isAttackSkill(event.getSkill())) return;
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
