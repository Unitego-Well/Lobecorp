package org.unitego.lobecorp.events;

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
import org.unitego.lobecorp.animation.runtime.LcServerAnimations;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.conductor.lifecycle.ConductorEvents;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.skill.IEntitySkillAccess;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.network.tc.ConductorSnapshotPayload;
import org.unitego.lobecorp.particle.damage.DamageParticles;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.util.entity.EntityGrabUtil;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class LivingEntityEvents {
	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (event.getEntity() instanceof LivingEntity livingEntity && !livingEntity.level().isClientSide()) {
			IEntitySkillAccess access = IEntitySkillAccess.get(livingEntity);

			if (access != null) {
				access.tick();
			} else {
				EntitySkillUtil.tick(livingEntity);
			}

			LcServerAnimations.tick(livingEntity);
			EntityGrabUtil.tick(livingEntity);
		} else if (!event.getEntity().level().isClientSide()) {
			LcServerAnimations.tick(event.getEntity());
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
	public static void onLivingDamagePost(LivingDamageEvent.Post event) {
		DamageParticles.onDamage(event);
	}

	@SubscribeEvent
	public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity entity = event.getEntity();
		float multiplier = (float) entity.getAttributeValue(LcAttributes.DAMAGE_TAKEN_MULTIPLIER);
		event.setAmount(event.getAmount() * multiplier);
		ConductorEvents.onIncomingDamage(event);
	}

	@SubscribeEvent
	public static void onLivingDeath(EntityLeaveLevelEvent event) {
		Entity entity = event.getEntity();
		Level level = entity.level();

		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		if (entity instanceof LivingEntity living) {
			EntitySkillUtil.clearRuntimeState(living);
			EntityGrabUtil.clear(living);
		}

		ConductorData data = ConductorData.get(serverLevel.getServer());

		if (entity instanceof Mob leaving && data.unit(leaving.getUUID()) != null) {
			boolean permanent = entity.getRemovalReason() == Entity.RemovalReason.KILLED
					|| entity.getRemovalReason() == Entity.RemovalReason.DISCARDED;
			ConductorController.stop(leaving);
			data.detach(leaving, permanent);

			if (permanent) {
				ConductorSnapshotPayload.sendAll(serverLevel.getServer(), data);
			}
		}

		if (entity.getRemovalReason() != Entity.RemovalReason.KILLED) {
			return;
		}

		if (entity instanceof EntityCorpse<?>) {
			return;
		}

		if (!(entity instanceof Mob _)) {
			return;
		}

		serverLevel.addFreshEntity(EntityCorpse.createCorpse(entity));
	}
}
