package org.unitego.lobecorp.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.registry.entity.LcAttributes;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class LivingEntityEvents {
	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (event.getEntity() instanceof LivingEntity livingEntity && !livingEntity.level().isClientSide()) {
			EntitySkillManager.tick(livingEntity);
		}
	}

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent.Pre event) {
		if (event.getEntity() instanceof Sweeper sweeper) {
			sweeper.markCombat();
			float remainingDamage = sweeper.absorbDamageWithBiomass(event.getNewDamage());
			event.setNewDamage(remainingDamage);
			if (remainingDamage <= 0.0F && event.getSource().getEntity() instanceof LivingEntity attacker) {
				sweeper.trySetAttackTargetFromDamage(attacker);
			}
		}
	}

	@SubscribeEvent
	public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity entity = event.getEntity();
		float multiplier = (float) entity.getAttributeValue(LcAttributes.DAMAGE_TAKEN_MULTIPLIER);
		event.setAmount(event.getAmount() * multiplier);
	}

	@SubscribeEvent
	public static void onLivingDeath(EntityLeaveLevelEvent event) {
		Entity entity = event.getEntity();
		Level level = entity.level();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (entity.getRemovalReason() != Entity.RemovalReason.KILLED) {
			return;
		}
		if (entity instanceof EntityCorpse<?>) {
			return;
		}
		if (!(entity instanceof Mob)) {
			return;
		}

		serverLevel.addFreshEntity(EntityCorpse.createCorpse(entity));
	}
}
