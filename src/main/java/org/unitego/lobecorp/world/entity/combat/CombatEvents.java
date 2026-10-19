package org.unitego.lobecorp.world.entity.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.util.entity.combat.CombatUtil;

/// 由现有唯一事件入口分发，目标与伤害使用同一战斗关系规则。
// 世界由事件实体持有，处理入口不负责关闭它。
@SuppressWarnings("resource")
public class CombatEvents {
	private static final TagKey<DamageType> CAN_HURT_ALLIES =
			TagKey.create(Registries.DAMAGE_TYPE, Lobecorp.id("can_hurt_allies"));

	public static void onTargetChange(LivingChangeTargetEvent event) {
		LivingEntity target = event.getNewAboutToBeSetTarget();
		if (!event.getEntity().level().isClientSide() && target != null
				&& (event.getEntity() == target || (CombatUtil.participates(event.getEntity()) || CombatUtil.participates(target))
				&& !CombatUtil.canTarget(event.getEntity(), target))) {
			event.setNewAboutToBeSetTarget(null);
		}
	}

	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
				|| event.getEntity().level().isClientSide()) {
			return;
		}

		boolean canHarm = CombatUtil.canHarm(attacker, event.getEntity());
		if (canHarm || attacker != event.getEntity() && event.getSource().is(CAN_HURT_ALLIES)) {
			return;
		}
		event.setCanceled(true);
	}

	public static void onDamagePost(LivingDamageEvent.Post event) {
		if (event.getSource().getEntity() instanceof LivingEntity attacker) {
			CombatUtil.recordAttack(event.getEntity(), attacker);
		}
	}
}
