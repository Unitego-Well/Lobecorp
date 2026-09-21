package org.unitego.lobecorp.entity.entity_skill.sweeper;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperAnim;
import org.unitego.lobecorp.registry.brain.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.entity_state.SweeperStates;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;

/// 清道夫清扫技能：靠近尸体或物品后持续进行清理，并将清理量转化为自身生命。
public class SweeperSweepSkill extends SweeperSkill {
	/// 每次清扫物品恢复的生命值
	private static final float ITEM_HEALTH = 2.0F;
	/// 每 tick 清扫尸体造成的伤害
	private static final float CORPSE_PROCESS_HEALTH = 5.0F;
	/// 清扫物品时允许的最大距离平方
	private static final double ITEM_DISPOSE_DISTANCE_SQUARED = 4.0;
	/// 每 tick 在目标上生成的粒子数量
	private static final int PARTICLE_COUNT = 4;
	/// 粒子的最大水平飞溅速度范围
	private static final double PARTICLE_HORIZONTAL_SPEED = 0.16;
	/// 粒子的最小向上速度
	private static final double PARTICLE_MIN_VERTICAL_SPEED = 0.08;
	/// 粒子额外随机向上速度的范围
	private static final double PARTICLE_RANDOM_VERTICAL_SPEED = 0.12;

	public SweeperSweepSkill(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canUse(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		if (entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()) {
			return false;
		}
		Entity target = getCleanupTarget(entity);
		if (target == null) {
			return false;
		}
		runtime.setTarget(target);
		return true;
	}

	@Override
	public void onWindupStart(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.addEntityState(SweeperStates.SWEEP);
		entity.playActionAnimation(SweeperAnim.CLEAR1);
	}

	@Override
	public void onActivate(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.playActionAnimation(SweeperAnim.CLEAR2);
	}

	@Override
	public void onTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		Entity target = runtime.target();
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (target instanceof EntityCorpse<?> corpse) {
			sweepCorpse(level, entity, corpse);
			return;
		}
		if (target instanceof ItemEntity itemEntity) {
			sweepItem(level, entity, itemEntity);
			return;
		}
		EntitySkillManager.endSkill(entity, this);
	}

	private void sweepCorpse(ServerLevel level, Sweeper entity, EntityCorpse<?> corpse) {
		if (!corpse.isAlive() || corpse.getOwnerEntity() instanceof Sweeper
				|| !entity.isWithinMeleeAttackRange(corpse)) {
			EntitySkillManager.endSkill(entity, this);
			return;
		}

		spawnBlood(level, entity, corpse);
		float consumedHealth = Math.min(corpse.getHealth(), CORPSE_PROCESS_HEALTH);
		corpse.setHealth(corpse.getHealth() - consumedHealth);
		entity.heal(consumedHealth);

		if (!corpse.isAlive()) {
			corpse.discard();
			EntitySkillManager.endSkill(entity, this);
		}
	}

	private void sweepItem(ServerLevel level, Sweeper entity, ItemEntity itemEntity) {
		if (!itemEntity.isAlive() || itemEntity.getItem().isEmpty()
				|| entity.distanceToSqr(itemEntity) > ITEM_DISPOSE_DISTANCE_SQUARED) {
			EntitySkillManager.endSkill(entity, this);
			return;
		}
		spawnItemParticles(level, entity, itemEntity);
		itemEntity.getItem().shrink(1);
		entity.heal(ITEM_HEALTH);
		if (itemEntity.getItem().isEmpty()) {
			itemEntity.discard();
			EntitySkillManager.endSkill(entity, this);
		}
	}

	static void spawnBlood(ServerLevel level, Sweeper entity, LivingEntity corpse) {
		RandomSource random = entity.getRandom();
		AABB bounds = corpse.getBoundingBox();
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			double x = bounds.minX + random.nextDouble() * bounds.getXsize();
			double y = bounds.minY + random.nextDouble() * bounds.getYsize();
			double z = bounds.minZ + random.nextDouble() * bounds.getZsize();
			double velocityX = (random.nextDouble() - 0.5) * PARTICLE_HORIZONTAL_SPEED;
			double velocityY = PARTICLE_MIN_VERTICAL_SPEED + random.nextDouble() * PARTICLE_RANDOM_VERTICAL_SPEED;
			double velocityZ = (random.nextDouble() - 0.5) * PARTICLE_HORIZONTAL_SPEED;
			level.sendParticles(LcParticleTypes.BLOOD.get(), x, y, z, 0,
					velocityX, velocityY, velocityZ, 1.0);
		}
	}

	private void spawnItemParticles(ServerLevel level, Sweeper entity, ItemEntity itemEntity) {
		RandomSource random = entity.getRandom();
		AABB bounds = itemEntity.getBoundingBox();
		ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, itemEntity.getItem().getItem());
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			double x = bounds.minX + random.nextDouble() * bounds.getXsize();
			double y = bounds.minY + random.nextDouble() * bounds.getYsize();
			double z = bounds.minZ + random.nextDouble() * bounds.getZsize();
			double velocityX = (random.nextDouble() - 0.5) * PARTICLE_HORIZONTAL_SPEED;
			double velocityY = PARTICLE_MIN_VERTICAL_SPEED + random.nextDouble() * PARTICLE_RANDOM_VERTICAL_SPEED;
			double velocityZ = (random.nextDouble() - 0.5) * PARTICLE_HORIZONTAL_SPEED;
			level.sendParticles(particle, x, y, z, 0, velocityX, velocityY, velocityZ, 1.0);
		}
	}

	@Override
	public void onEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.playActionAnimation(SweeperAnim.CLEAR3);
	}

	@Override
	public void onRecoveryEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.stopActionAnimation();
		entity.removeEntityState(SweeperStates.SWEEP);
	}

	@Override
	public void onCancel(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.removeEntityState(SweeperStates.SWEEP);
		entity.stopActionAnimation();
	}

	private Entity getCleanupTarget(Sweeper entity) {
		return entity.getBrain().getMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get())
				.filter(target -> target.isAlive() && (target instanceof EntityCorpse<?> corpse
						&& !(corpse.getOwnerEntity() instanceof Sweeper) && entity.isWithinMeleeAttackRange(corpse)
						|| target instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty()
						&& entity.distanceToSqr(itemEntity) <= ITEM_DISPOSE_DISTANCE_SQUARED))
				.orElse(null);
	}

	public static boolean isWithinRange(Sweeper entity, Entity target) {
		return target instanceof LivingEntity livingEntity
				? entity.isWithinMeleeAttackRange(livingEntity)
				: entity.distanceToSqr(target) <= ITEM_DISPOSE_DISTANCE_SQUARED;
	}
}
