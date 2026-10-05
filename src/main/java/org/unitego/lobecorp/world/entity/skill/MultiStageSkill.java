package org.unitego.lobecorp.world.entity.skill;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;

/// 分段施放的技能契约：后摇结束后开放续段窗口，整套完成或窗口超时后进入冷却。
@NullMarked
public abstract class MultiStageSkill<T extends LivingEntity> extends EntitySkill<T> {
	/// 多段技能构造参数不满足段数、续段窗口或冷却约束时的异常说明。
	private static final String INVALID_TIMING_MESSAGE = "Invalid multi-stage skill timing";
	private final int stageCount;
	private final int continuationWindowTicks;
	private final int sequenceCooldownTicks;

	public MultiStageSkill(Properties properties, int stageCount, int continuationWindowTicks, int sequenceCooldownTicks) {
		super(properties);
		if (stageCount < 1 || continuationWindowTicks < 1 || sequenceCooldownTicks < 0) {
			throw new IllegalArgumentException(INVALID_TIMING_MESSAGE);
		}
		this.stageCount = stageCount;
		this.continuationWindowTicks = continuationWindowTicks;
		this.sequenceCooldownTicks = sequenceCooldownTicks;
	}

	public int stageCount() {
		return stageCount;
	}

	public int continuationWindowTicks() {
		return continuationWindowTicks;
	}

	public int sequenceCooldownTicks() {
		return sequenceCooldownTicks;
	}

	/// 当前施放固定使用的段数，从 0 开始计数；由已有运行态载荷同步。
	protected int currentStage(EntitySkillRuntime<T> runtime) {
		return runtime.sequenceStage();
	}

	/// 由运行实例在开始同步之前调用，施放失败与事件拒绝不会消耗续段机会。
	public void beginStage(EntitySkillRuntime<?> runtime) {
		if (!runtime.owner().level().isClientSide()) {
			Sequence sequence = sequence(runtime.owner());
			runtime.setSequenceStage(sequence.nextStage);
		}
	}

	/// 由运行实例在后摇回调完成后调用；取消路径不推进段数。
	public void completeStage(EntitySkillRuntime<?> runtime) {
		if (runtime.owner().level().isClientSide() || !advancesStage(runtime))
			return;
		Sequence sequence = sequence(runtime.owner());
		int nextStage = runtime.sequenceStage() + 1;
		if (nextStage >= stageCount()) {
			sequence.reset();
			setSequenceCooldown(runtime);
			onStageChanged(runtime.owner(), 0);
		} else {
			sequence.nextStage = nextStage;
			sequence.expiresAt = runtime.owner().level().getGameTime() + continuationWindowTicks();
			sequence.pendingRuntime = runtime;
			onStageChanged(runtime.owner(), nextStage);
		}
	}

	/// 由服务端状态机关闭已超时的续段窗口，冷却仍通过原倍率和附件机制写入。
	public void expireSequence(EntitySkillRuntime<?> runtime) {
		Sequence sequence = sequence(runtime.owner());
		sequence.reset();
		setSequenceCooldown(runtime);
		onStageChanged(runtime.owner(), 0);
	}

	protected boolean advancesStage(EntitySkillRuntime<?> runtime) {
		return true;
	}

	protected void onStageChanged(LivingEntity owner, int nextStage) {
	}

	private void setSequenceCooldown(EntitySkillRuntime<?> runtime) {
		if (!runtime.hasCooldownTicksOverride())
			runtime.setCooldownTicks(sequenceCooldownTicks());
	}

	private Sequence sequence(LivingEntity owner) {
		return EntitySkillUtil.require(owner).multiStageSequence(this);
	}

	/// 属于单个实体、单个技能的非持久化续段状态，不占用技能运行组或移动锁。
	public static class Sequence {
		private int nextStage;
		private long expiresAt;
		@Nullable
		private EntitySkillRuntime<?> pendingRuntime;

		public long expiresAt() {
			return expiresAt;
		}

		@Nullable
		public EntitySkillRuntime<?> pendingRuntime() {
			return pendingRuntime;
		}

		private void reset() {
			nextStage = 0;
			expiresAt = 0;
			pendingRuntime = null;
		}
	}
}
