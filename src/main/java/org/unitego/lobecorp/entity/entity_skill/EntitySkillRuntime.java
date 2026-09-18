package org.unitego.lobecorp.entity.entity_skill;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.network.EntitySkillSyncPayload;

/// 单个技能运行时的可变状态，存放于实体 brain 内存（不持久化）。
public final class EntitySkillRuntime<T extends Mob> {
	private final T owner;
	private final IEntitySkill<T> skill;
	private SkillState state;
	private int ticksLeft;
	private int activeTicks;
	private boolean successful;
	@Nullable
	private Entity target;
	@Nullable
	private Vec3 plannedMovement;

	public EntitySkillRuntime(T owner, IEntitySkill<T> skill, SkillState state, int ticksLeft) {
		this.owner = owner;
		this.skill = skill;
		this.state = state;
		this.ticksLeft = ticksLeft;
	}

	public T owner() {
		return owner;
	}

	public IEntitySkill<T> skill() {
		return skill;
	}

	public boolean canUse() {
		return skill.canUse(owner, this);
	}

	public void onWindupStart() {
		sync(EntitySkillSyncPayload.Callback.WINDUP_START);
		skill.onWindupStart(owner, this);
	}

	public void onWindupTick() {
		sync(EntitySkillSyncPayload.Callback.WINDUP_TICK);
		skill.onWindupTick(owner, this);
	}

	public void onActivate() {
		sync(EntitySkillSyncPayload.Callback.ACTIVATE);
		skill.onActivate(owner, this);
	}

	public void onTick() {
		sync(EntitySkillSyncPayload.Callback.TICK);
		skill.onTick(owner, this);
	}

	public void onEnd() {
		sync(EntitySkillSyncPayload.Callback.END);
		skill.onEnd(owner, this);
	}

	public void onRecoveryEnd() {
		sync(EntitySkillSyncPayload.Callback.RECOVERY_END);
		skill.onRecoveryEnd(owner, this);
	}

	public void onCancel() {
		sync(EntitySkillSyncPayload.Callback.CANCEL);
		skill.onCancel(owner, this);
	}

	private void sync(EntitySkillSyncPayload.Callback callback) {
		if (!owner.level().isClientSide()) {
			EntitySkillSyncPayload.send(this, callback);
		}
	}

	public SkillState state() {
		return state;
	}

	public void setState(SkillState state) {
		this.state = state;
	}

	/// 当前阶段剩余 tick。-1 仅用于无限持续的 ACTIVE 阶段
	public int ticksLeft() {
		return ticksLeft;
	}

	public void setTicksLeft(int ticksLeft) {
		this.ticksLeft = ticksLeft;
	}

	/// 当前技能进入持续阶段后的 tick 数。
	public int activeTicks() {
		return activeTicks;
	}

	public void incrementActiveTicks() {
		activeTicks++;
	}

	public void setActiveTicks(int activeTicks) {
		this.activeTicks = activeTicks;
	}

	/// 标记本次技能已经成功产生效果。
	public void markSuccessful() {
		successful = true;
	}

	public boolean isSuccessful() {
		return successful;
	}

	/// 锁定本次施放使用的目标，避免 Sensor 更新时切换目标。
	public void setTarget(@Nullable Entity target) {
		this.target = target;
	}

	@Nullable
	public Entity target() {
		return target;
	}

	@Nullable
	public <E extends Entity> E target(Class<E> targetType) {
		return targetType.isInstance(target) ? targetType.cast(target) : null;
	}

	public void setPlannedMovement(@Nullable Vec3 plannedMovement) {
		this.plannedMovement = plannedMovement;
	}

	@Nullable
	public Vec3 plannedMovement() {
		return plannedMovement;
	}

	/// 技能状态机阶段
	public enum SkillState {
		/// 前摇：施放准备，效果尚未生效
		WINDUP,
		/// 持续：技能生效中（-1 为无限）
		ACTIVE,
		/// 后摇：效果结束，收招阶段
		RECOVERY
	}
}
