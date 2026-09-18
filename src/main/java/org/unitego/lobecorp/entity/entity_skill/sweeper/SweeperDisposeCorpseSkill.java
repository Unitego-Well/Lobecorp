package org.unitego.lobecorp.entity.entity_skill.sweeper;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillBrain;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperAnim;
import org.unitego.lobecorp.registry.brain.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.entity_state.SweeperStates;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;

/// 清道夫清理尸体技能：靠近尸体后持续消耗尸体生命，并将消耗量转化为自身生命。
public class SweeperDisposeCorpseSkill extends SweeperSkill {
	/// 每 tick 消耗的尸体生命值
	private static final float PROCESS_HEALTH = 2.0F;
	/// 每 tick 在尸体上生成的血粒子数量
	private static final int BLOOD_PARTICLE_COUNT = 4;
	/// 血粒子的最大水平飞溅速度范围
	private static final double BLOOD_HORIZONTAL_SPEED = 0.16;
	/// 血粒子的最小向上速度
	private static final double BLOOD_MIN_VERTICAL_SPEED = 0.08;
	/// 血粒子额外随机向上速度的范围
	private static final double BLOOD_RANDOM_VERTICAL_SPEED = 0.12;

	public SweeperDisposeCorpseSkill(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canUse(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		if (entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()) {
			return false;
		}
		EntityCorpse<?> corpse = getCorpse(entity);
		if (corpse == null) {
			return false;
		}
		runtime.setTarget(corpse);
		return true;
	}

	@Override
	public void onWindupStart(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.addEntityState(SweeperStates.DISPOSE_CORPSE);
		entity.triggerAnim(Sweeper.ACTION_ANIMATION_CONTROLLER, SweeperAnim.CLEAR1.getId());
	}

	@Override
	public void onActivate(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.triggerAnim(Sweeper.ACTION_ANIMATION_CONTROLLER, SweeperAnim.CLEAR2.getId());
	}

	@Override
	public void onTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity corpse = getTarget(runtime);
		if (entity.level().isClientSide()) {
			if (corpse != null && corpse.isAlive()) {
				spawnBlood(entity, corpse);
			}
			return;
		}
		if (!(corpse instanceof EntityCorpse<?>) || !corpse.isAlive() || !entity.isWithinMeleeAttackRange(corpse)) {
			EntitySkillBrain.endSkill(entity);
			return;
		}

		float consumedHealth = Math.min(corpse.getHealth(), PROCESS_HEALTH);
		corpse.setHealth(corpse.getHealth() - consumedHealth);
		entity.heal(consumedHealth);

		if (!corpse.isAlive()) {
			corpse.discard();
			EntitySkillBrain.endSkill(entity);
		}
	}

	private void spawnBlood(Sweeper entity, LivingEntity corpse) {
		RandomSource random = entity.getRandom();
		AABB bounds = corpse.getBoundingBox();
		for (int i = 0; i < BLOOD_PARTICLE_COUNT; i++) {
			double x = bounds.minX + random.nextDouble() * bounds.getXsize();
			double y = bounds.minY + random.nextDouble() * bounds.getYsize();
			double z = bounds.minZ + random.nextDouble() * bounds.getZsize();
			double velocityX = (random.nextDouble() - 0.5) * BLOOD_HORIZONTAL_SPEED;
			double velocityY = BLOOD_MIN_VERTICAL_SPEED + random.nextDouble() * BLOOD_RANDOM_VERTICAL_SPEED;
			double velocityZ = (random.nextDouble() - 0.5) * BLOOD_HORIZONTAL_SPEED;
			entity.level().addParticle(LcParticleTypes.BLOOD.get(), x, y, z, velocityX, velocityY, velocityZ);
		}
	}

	@Override
	public void onEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.triggerAnim(Sweeper.ACTION_ANIMATION_CONTROLLER, SweeperAnim.CLEAR3.getId());
	}

	@Override
	public void onRecoveryEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.removeEntityState(SweeperStates.DISPOSE_CORPSE);
	}

	@Override
	public void onCancel(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.removeEntityState(SweeperStates.DISPOSE_CORPSE);
		entity.stopTriggeredAnim(Sweeper.ACTION_ANIMATION_CONTROLLER, null);
	}

	private EntityCorpse<?> getCorpse(Sweeper entity) {
		return entity.getBrain().getMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get())
				.filter(EntityCorpse.class::isInstance)
				.map(EntityCorpse.class::cast)
				.filter(corpse -> corpse.isAlive() && entity.isWithinMeleeAttackRange(corpse))
				.orElse(null);
	}
}
