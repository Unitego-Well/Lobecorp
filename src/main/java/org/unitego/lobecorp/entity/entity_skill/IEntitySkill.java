package org.unitego.lobecorp.entity.entity_skill;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/// 通用技能定义。
/// <p>
/// 技能状态机由 {@link EntitySkillBrain} 驱动：
/// <pre>
///  前摇(WINDUP) → 持续(ACTIVE) → 后摇(RECOVERY) → 冷却(COOLDOWN)
/// </pre>
/// 实现此接口并注册到 {@link EntitySkillHolder#skills()} 即可使用，只重写需要的方法。
public interface IEntitySkill<T extends Mob> {

	/// 技能唯一标识，用于冷却表与日志
	Identifier id();

	/// 前摇 tick（施放准备阶段，效果不生效）
	int windupTicks();

	/// 持续时间 tick。-1 表示无限，直到调用 {@link EntitySkillBrain#endSkill} 主动结束
	int durationTicks();

	/// 后摇 tick（效果结束后收招阶段）
	int recoveryTicks();

	/// 冷却 tick（技能完全结束后开始计时）
	int cooldownTicks();

	/// 技能运行期间是否禁止实体 AI 寻路
	boolean locksNavigation();

	/// 技能运行期间是否禁止实体产生水平位移
	boolean locksMovement();

	/// 前摇期间是否允许外部逻辑取消技能。目标失效等技能自身取消不受此限制。
	default boolean interruptibleDuringWindup() {
		return true;
	}

	/// 施放前的额外条件（冷却与占用检查由系统完成，此方法补充业务条件）
	default boolean canUse(T entity, EntitySkillRuntime<T> runtime) {
		return true;
	}

	/// 前摇开始
	void onWindupStart(T entity, EntitySkillRuntime<T> runtime);

	/// 前摇期间每 tick 调用
	default void onWindupTick(T entity, EntitySkillRuntime<T> runtime) {
	}

	/// 效果触发（前摇结束，技能生效）
	void onActivate(T entity, EntitySkillRuntime<T> runtime);

	/// 持续期间每 tick 调用
	void onTick(T entity, EntitySkillRuntime<T> runtime);

	/// 持续结束（进入后摇前）
	void onEnd(T entity, EntitySkillRuntime<T> runtime);

	/// 后摇结束（进入冷却前）
	void onRecoveryEnd(T entity, EntitySkillRuntime<T> runtime);

	/// 技能被立即取消；用于清理技能建立的临时状态
	default void onCancel(T entity, EntitySkillRuntime<T> runtime) {
	}
}
