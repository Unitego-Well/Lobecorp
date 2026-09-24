package org.unitego.lobecorp.entity.entity_skill.sweeper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperAnim;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.registry.brain.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.entity_state.SweeperStates;

/// 清道夫重组技能：持续消耗清道夫尸体生命并复活原清道夫。
public class SweeperReassembleSkill extends SweeperSkill {
	/// 重组所需的尸体生命与目标清道夫最大生命的比例
	private static final float REQUIRED_HEALTH_RATIO = 0.5F;
	/// 复活后的固定最大生命比例
	private static final float REVIVE_HEALTH_RATIO = 0.1F;

	public SweeperReassembleSkill(Properties properties) {
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
		entity.addEntityState(SweeperStates.REASSEMBLE);
		faceTarget(entity, runtime.target(EntityCorpse.class));
		entity.triggerActionAnimation(SweeperAnim.CLEAR1);
	}

	@Override
	public void onWindupTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		faceTarget(entity, runtime.target(EntityCorpse.class));
	}

	@Override
	public void onActivate(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		faceTarget(entity, runtime.target(EntityCorpse.class));
		entity.triggerActionAnimation(SweeperAnim.CLEAR2);
	}

	@Override
	public void onTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		EntityCorpse<?> corpse = runtime.target(EntityCorpse.class);
		faceTarget(entity, corpse);
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (corpse == null || !corpse.isAlive() || !entity.isWithinMeleeAttackRange(corpse)) {
			EntitySkillManager.endSkill(entity, this);
			return;
		}
		if (!(corpse.getOwnerEntity() instanceof Sweeper corpseSweeper)) {
			EntitySkillManager.endSkill(entity, this);
			return;
		}

		SweeperSweepSkill.spawnBlood(level, entity, corpse);
		float requiredHealth = corpseSweeper.getMaxHealth() * REQUIRED_HEALTH_RATIO;
		float reassemblyProgress = corpse.getData(LcAttachmentTypes.REASSEMBLY_PROGRESS);
		if (reassemblyProgress < requiredHealth) {
			float healthBefore = corpse.getHealth();
			float requestedHealth = Math.min(PROCESS_HEALTH, requiredHealth - reassemblyProgress);
			corpse.setHealth(healthBefore - requestedHealth);
			float consumedHealth = Math.max(0.0F, healthBefore - corpse.getHealth());
			corpse.setData(LcAttachmentTypes.REASSEMBLY_PROGRESS, reassemblyProgress + consumedHealth);
		}

		if (corpse.getData(LcAttachmentTypes.REASSEMBLY_PROGRESS) < requiredHealth) {
			if (!corpse.isAlive()) {
				EntitySkillManager.endSkill(entity, this);
			}
			return;
		}
		if (!reviveSweeper(level, entity, corpse)) {
			EntitySkillManager.endSkill(entity, this);
			return;
		}
		corpse.discard();
		runtime.markSuccessful();
		EntitySkillManager.endSkill(entity, this);
	}

	private boolean reviveSweeper(ServerLevel level, Sweeper entity, EntityCorpse<?> corpse) {
		if (!(corpse.createRevivedOwnerEntity() instanceof Sweeper corpseSweeper)) {
			return false;
		}
		float reviveHealth = corpseSweeper.getMaxHealth() * REVIVE_HEALTH_RATIO;
		if (entity.getBiomass() < reviveHealth) {
			return false;
		}
		corpseSweeper.absSnapTo(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getYRot(), corpse.getXRot());
		corpseSweeper.setHealth(reviveHealth);
		if (!level.addFreshEntity(corpseSweeper)) {
			return false;
		}
		entity.setBiomass(entity.getBiomass() - reviveHealth);
		return true;
	}

	private void faceTarget(Sweeper entity, EntityCorpse<?> corpse) {
		if (corpse == null) {
			return;
		}
		double offsetX = corpse.getX() - entity.getX();
		double offsetZ = corpse.getZ() - entity.getZ();
		float targetYaw = (float) (Mth.atan2(offsetZ, offsetX) * Mth.RAD_TO_DEG) - 90.0F;
		entity.setYRot(targetYaw);
		entity.setYBodyRot(targetYaw);
		entity.setYHeadRot(targetYaw);
		entity.getLookControl().setLookAt(corpse, entity.getMaxHeadYRot(), entity.getMaxHeadXRot());
	}

	@Override
	public void onEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.triggerActionAnimation(SweeperAnim.CLEAR3);
	}

	@Override
	public void onRecoveryEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.stopTriggeredActionAnimation();
		entity.removeEntityState(SweeperStates.REASSEMBLE);
	}

	@Override
	public void onCancel(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.removeEntityState(SweeperStates.REASSEMBLE);
		entity.stopTriggeredActionAnimation();
	}

	private EntityCorpse<?> getCorpse(Sweeper entity) {
		return entity.getBrain().getMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get())
				.filter(EntityCorpse.class::isInstance)
				.map(EntityCorpse.class::cast)
				.filter(corpse -> canReassemble(entity, corpse)
						&& entity.isWithinMeleeAttackRange(corpse))
				.orElse(null);
	}

	public static boolean canReassemble(Sweeper entity, EntityCorpse<?> corpse) {
		return corpse.isAlive() && corpse.getOwnerEntity() instanceof Sweeper corpseSweeper
				&& entity.getBiomass() >= corpseSweeper.getMaxHealth() * REVIVE_HEALTH_RATIO;
	}

}
