package org.unitego.lobecorp.world.entity.skill;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.util.entity.skill.EntitySkillDebugUtil;
import org.unitego.lobecorp.network.tc.EntitySkillSyncPayload;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.HashMap;
import java.util.Map;

/// 单次技能施放的可变运行状态。
/// 实例由 {@link EntitySkillAccess} 能力实现创建并存放在实体的非持久化运行态附件中；
/// 技能实现可以使用目标、计划位移、成功标记和阶段计时保存本次施放数据。
public final class EntitySkillRuntime<T extends LivingEntity> {
	private final long id;
	private final T owner;
	private final IEntitySkill<T> skill;
	private final Map<TypedDataKey<?>, Object> data = new HashMap<>();
	private SkillState state;
	private int ticksLeft;
	private int activeTicks;
	private int elapsedTicks;
	@Nullable
	private Integer cooldownTicksOverride;
	private boolean successful;
	/// 本次多段技能施放的段数，从 0 开始；与运行实例一起同步。
	private int sequenceStage;
	@Nullable
	private Entity target;
	@Nullable
	private Vec3 targetPosition;
	@Nullable
	private Vec3 plannedMovement;

	/// 创建指定阶段的技能运行实例。
	///
	/// @param owner     本次施放的实体拥有者
	/// @param skill     正在运行的技能定义
	/// @param state     初始生命周期阶段
	/// @param ticksLeft 初始阶段剩余 tick；持续阶段可使用 {@code -1} 表示无限
	public EntitySkillRuntime(T owner, IEntitySkill<T> skill, SkillState state, int ticksLeft) {
		this(0L, owner, skill, state, ticksLeft);
	}

	/// 创建带有实体内运行实例 ID 的技能运行实例。
	public EntitySkillRuntime(long id, T owner, IEntitySkill<T> skill, SkillState state, int ticksLeft) {
		this.id = id;
		this.owner = owner;
		this.skill = skill;
		this.state = state;
		this.ticksLeft = ticksLeft;
	}

	/// @return 实体内递增的运行实例 ID；临时兼容实例可能返回零
	public long id() {
		return id;
	}

	/// @return 本次技能运行所属的实体
	public T owner() {
		return owner;
	}

	/// @return 本次运行使用的技能定义
	public IEntitySkill<T> skill() {
		return skill;
	}

	/// 执行技能定义的额外施放条件检查。
	///
	/// @return {@link IEntitySkill#canUse} 的检查结果
	public boolean canUse() {
		return skill.canUse(owner, this);
	}

	public void onWindupStart() {
		if (skill instanceof MultiStageSkill<?> multiStage)
			multiStage.beginStage(this);
		EntitySkillDebugUtil.log(this, "windup-start");
		sync(EntitySkillSyncPayload.Callback.WINDUP_START);
		skill.onWindupStart(owner, this);
	}

	public void onWindupTick() {
		skill.onWindupTick(owner, this);
	}

	public void onActivate() {
		sync(EntitySkillSyncPayload.Callback.ACTIVATE);
		skill.onActivate(owner, this);
	}

	public void onTick() {
		skill.onTick(owner, this);
	}

	public void onEnd() {
		EntitySkillDebugUtil.log(this, "effect-end");
		sync(EntitySkillSyncPayload.Callback.END);
		skill.onEnd(owner, this);
	}

	public void onRecoveryEnd() {
		EntitySkillDebugUtil.log(this, "recovery-end");
		sync(EntitySkillSyncPayload.Callback.RECOVERY_END);
		skill.onRecoveryEnd(owner, this);
	}

	public int sequenceStage() {
		return sequenceStage;
	}

	public void setSequenceStage(int sequenceStage) {
		this.sequenceStage = sequenceStage;
	}

	public void onCancel() {
		EntitySkillDebugUtil.log(this, "cancel");
		sync(EntitySkillSyncPayload.Callback.CANCEL);
		skill.onCancel(owner, this);
	}

	private void sync(EntitySkillSyncPayload.Callback callback) {
		if (!owner.level().isClientSide()) {
			EntitySkillSyncPayload.send(this, callback);
		}
	}

	/// @return 当前生命周期阶段
	public SkillState state() {
		return state;
	}

	/// 读取本次施放当前阶段的技能打断资格，不影响普通或强制取消。
	public boolean isInterruptibleBySkill() {
		return skill.isInterruptibleBySkill(owner, this);
	}

	/// 更新当前生命周期阶段。通常仅由技能管理器推进状态机时调用。
	///
	/// @param state 新阶段
	public void setState(SkillState state) {
		EntitySkillDebugUtil.log(this, "state-change " + this.state + " -> " + state);
		this.state = state;
	}

	/// 当前阶段剩余 tick。-1 仅用于无限持续的 ACTIVE 阶段
	///
	/// @return 当前阶段剩余 tick
	public int ticksLeft() {
		return ticksLeft;
	}

	/// 设置当前阶段剩余 tick。通常仅由技能管理器或同步处理使用。
	///
	/// @param ticksLeft 新的剩余 tick
	public void setTicksLeft(int ticksLeft) {
		this.ticksLeft = ticksLeft;
	}

	/// 当前技能进入持续阶段后的 tick 数。
	///
	/// @return 已经过的持续阶段 tick 数
	public int activeTicks() {
		return activeTicks;
	}

	/// 将持续阶段计数增加一 tick。
	public void incrementActiveTicks() {
		activeTicks++;
	}

	/// 覆盖持续阶段计数，主要用于同步恢复运行快照。
	///
	/// @param activeTicks 新的持续阶段 tick 数
	public void setActiveTicks(int activeTicks) {
		this.activeTicks = activeTicks;
	}

	/// 当前施放从技能开始起已经进入的 tick 数，包含前摇。
	/// 首次由技能管理器推进时为 1，不会因生命周期阶段切换而重置。
	///
	/// @return 从技能开始计算的绝对 tick
	public int elapsedTicks() {
		return elapsedTicks;
	}

	/// 将技能绝对时间推进一 tick。
	/// 仅由技能管理器在处理本次实体 tick 前调用。
	public void incrementElapsedTicks() {
		elapsedTicks++;
	}

	/// 覆盖本次施放结束或取消后写入的冷却时间。
	/// 该值只属于当前运行实例，不持久化也不同步；结束或取消时以当时的游戏时间为基准应用冷却倍率并写入。
	/// 未设置时保留成功开始施放时写入的默认冷却，不在结束或取消时重新计时。
	///
	/// @param cooldownTicks 本次施放的冷却 tick；小于等于零表示不保留冷却
	public void setCooldownTicks(int cooldownTicks) {
		cooldownTicksOverride = cooldownTicks;
	}

	/// @return 本次施放是否显式设置了结束或取消时应用的冷却覆盖值
	public boolean hasCooldownTicksOverride() {
		return cooldownTicksOverride != null;
	}

	/// 返回本次施放的冷却配置值；未显式覆盖时，此方法不会使结束或取消重新开始默认冷却。
	///
	/// @return 运行态覆盖值；未设置时返回技能定义的默认冷却
	public int cooldownTicks() {
		return cooldownTicksOverride == null ? skill.cooldownTicks() : cooldownTicksOverride;
	}

	/// 标记本次技能已经成功产生效果。
	public void markSuccessful() {
		successful = true;
	}

	/// @return 技能是否已经被标记为成功产生效果
	public boolean isSuccessful() {
		return successful;
	}

	/// 锁定本次施放使用的目标，避免 Sensor 更新时切换目标。
	///
	/// @param target 本次施放目标；{@code null} 表示清除目标
	public void setTarget(@Nullable Entity target) {
		this.target = target;
	}

	/// @return 当前锁定目标；没有目标时返回 {@code null}
	@Nullable
	public Entity target() {
		return target;
	}

	/// 按期望类型获取当前目标。
	///
	/// @param targetType 目标实体类型
	/// @return 目标类型匹配时返回目标，否则返回 {@code null}
	@Nullable
	public <E extends Entity> E target(Class<E> targetType) {
		return targetType.isInstance(target) ? targetType.cast(target) : null;
	}

	/// 锁定本次施放使用的坐标目标。
	public void setTargetPosition(@Nullable Vec3 targetPosition) {
		this.targetPosition = targetPosition;
	}

	/// @return 本次施放使用的坐标目标；未指定时返回 {@code null}
	@Nullable
	public Vec3 targetPosition() {
		return targetPosition;
	}

	/// 设置技能预先计算的位移向量。
	///
	/// @param plannedMovement 计划位移；{@code null} 表示清除
	public void setPlannedMovement(@Nullable Vec3 plannedMovement) {
		this.plannedMovement = plannedMovement;
	}

	/// @return 当前计划位移；未设置时返回 {@code null}
	@Nullable
	public Vec3 plannedMovement() {
		return plannedMovement;
	}

	/// 保存只属于本次施放且不持久化、不同步的类型安全数据。
	///
	/// @param key   数据键
	/// @param value 数据值
	/// @param <V>   数据类型
	public <V> void setData(TypedDataKey<V> key, V value) {
		data.put(key, value);
	}

	/// 读取本次施放的类型安全临时数据。
	///
	/// @param key 数据键
	/// @param <V> 数据类型
	/// @return 已保存的值；未设置时返回 {@code null}
	@SuppressWarnings("unchecked")
	@Nullable
	public <V> V getData(TypedDataKey<V> key) {
		return (V) data.get(key);
	}

	/// 移除并返回本次施放的类型安全临时数据。
	///
	/// @param key 数据键
	/// @param <V> 数据类型
	/// @return 被移除的值；未设置时返回 {@code null}
	@SuppressWarnings("unchecked")
	@Nullable
	public <V> V removeData(TypedDataKey<V> key) {
		return (V) data.remove(key);
	}

	/// 检查本次施放是否保存了指定数据键。
	///
	/// @param key 数据键
	/// @return 数据键是否存在
	public boolean hasData(TypedDataKey<?> key) {
		return data.containsKey(key);
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
