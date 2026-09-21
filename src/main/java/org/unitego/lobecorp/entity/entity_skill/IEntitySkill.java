package org.unitego.lobecorp.entity.entity_skill;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity_skill.LcEntitySkillGroups;
import org.unitego.lobecorp.registry.LcRegistrys;

/// 通用技能定义。
/// <p>
/// 技能状态机由 {@link EntitySkillManager} 驱动：
/// <pre>
///  前摇(WINDUP) → 持续(ACTIVE) → 后摇(RECOVERY) → 冷却(COOLDOWN)
/// </pre>
/// 实现此接口并通过 {@link EntitySkillManager#addSkill} 添加到实体即可使用，只重写需要的方法。
public interface IEntitySkill<T extends LivingEntity> {
	/// 检查技能是否属于指定的原版注册表标签。
	/// 标签内容由数据包的 {@code tags/entity_skill} JSON 提供，支持标签引用和其他命名空间追加。
	///
	/// @param tag 要查询的实体技能标签
	/// @return 当前已注册技能属于该标签时返回 {@code true}
	default boolean is(TagKey<IEntitySkill<?>> tag) {
		return LcRegistrys.ENTITY_SKILL.wrapAsHolder(this).is(tag);
	}

	/// 返回技能的唯一注册标识，用于网络同步、冷却附件和诊断信息。
	/// @return 注册时分配的稳定标识
	Identifier id();

	/// 返回技能所属的运行分组。分组决定同时运行上限以及满载时的覆盖范围。
	/// @return 技能组；默认使用当前执行组
	default EntitySkillGroup group() {
		return LcEntitySkillGroups.CURRENT.get();
	}

	/// 决定同一个技能是否禁止同时存在多个运行实例。
	/// @return {@code true} 时，已有同技能实例会使新施放失败；默认返回 {@code true}
	default boolean mutuallyExclusive() {
		return true;
	}

	/// 分组满载时，判断当前运行实例是否允许被准备施放的新技能覆盖。
	/// 该方法应只判断条件；返回允许后，管理器会依次调用覆盖钩子、取消回调并开始冷却。
	///
	/// @param entity 当前运行实例的拥有者
	/// @param runtime 可能被覆盖的当前运行实例
	/// @param replacement 准备占用分组位置的新技能
	/// @return 允许覆盖时返回 {@code true}；默认允许覆盖
	default boolean canBeOverridden(T entity, EntitySkillRuntime<T> runtime, IEntitySkill<?> replacement) {
		return true;
	}

	/// 返回前摇阶段持续的 tick 数。零表示施放后立即进入持续阶段。
	/// @return 非负前摇时长
	int windupTicks();

	/// 返回持续阶段的 tick 数。{@code -1} 表示无限持续，直到调用
	/// {@link EntitySkillManager#endSkill(LivingEntity, IEntitySkill)} 或取消技能。
	/// @return {@code -1} 或非负持续时长
	int durationTicks();

	/// 返回效果结束后的后摇 tick 数。零表示直接完成并进入冷却。
	/// @return 非负后摇时长
	int recoveryTicks();

	/// 返回技能正常完成或被取消后写入的冷却 tick 数。
	/// 零或负数表示不保留冷却记录。
	/// @return 冷却时长
	int cooldownTicks();

	/// 决定技能运行期间是否持续清除 {@link net.minecraft.world.entity.Mob} 的行走目标并停止导航。
	/// 对非 {@code Mob} 的 {@link LivingEntity} 不产生导航操作。
	/// @return 需要锁定导航时返回 {@code true}
	boolean locksNavigation();

	/// 决定技能运行期间是否每 tick 清除实体的水平速度。垂直速度会被保留。
	/// @return 需要锁定水平移动时返回 {@code true}
	boolean locksMovement();

	/// 决定普通取消请求能否在前摇阶段中断技能。
	/// 强制取消以及技能自身的失败处理不受该返回值限制。
	/// @return 允许普通取消时返回 {@code true}；默认允许
	default boolean interruptibleDuringWindup() {
		return true;
	}

	/// 根据当前前摇进度判断普通取消请求能否中断本次运行。
	/// 默认沿用 {@link #interruptibleDuringWindup()} 的固定配置；需要分阶段锁定前摇的技能可以覆盖此方法。
	///
	/// @param entity 技能拥有者
	/// @param runtime 当前运行实例
	/// @return 当前时刻允许普通取消或覆盖时返回 {@code true}
	default boolean canInterruptDuringWindup(T entity, EntitySkillRuntime<T> runtime) {
		return interruptibleDuringWindup();
	}

	/// 判断普通取消请求能否取消当前运行实例，适用于前摇、持续和后摇全部阶段。
	/// 默认仅限制不可打断的前摇，持续与后摇阶段允许取消；需要完整施放且不允许普通逻辑中断的技能
	/// 可以覆盖此方法并始终返回 {@code false}。强制取消不调用本方法，因此实体死亡、移除和通用运行态清理
	/// 仍能可靠释放技能建立的临时状态。
	///
	/// @param entity 技能拥有者
	/// @param runtime 准备取消的当前运行实例
	/// @return 普通取消请求可以移除该运行实例时返回 {@code true}
	default boolean canBeCancelled(T entity, EntitySkillRuntime<T> runtime) {
		return runtime.state() != EntitySkillRuntime.SkillState.WINDUP
				|| canInterruptDuringWindup(entity, runtime);
	}

	/// 执行技能自身的施放条件检查。
	/// 调用前管理器已经完成所有权、冷却、互斥和分组检查；实现应只检查技能业务条件，且避免修改运行态。
	///
	/// @param entity 尝试施放技能的实体
	/// @param runtime 尚未加入运行态附件的候选实例，可用于预先设置目标等本次施放数据
	/// @return 允许施放时返回 {@code true}
	default boolean canUse(T entity, EntitySkillRuntime<T> runtime) {
		return true;
	}

	/// 在运行实例加入附件并进入前摇时调用一次。
	/// @param entity 技能拥有者
	/// @param runtime 当前运行实例
	void onWindupStart(T entity, EntitySkillRuntime<T> runtime);

	/// 前摇期间由服务端状态机每 tick 调用。
	/// @param entity 技能拥有者
	/// @param runtime 当前运行实例
	default void onWindupTick(T entity, EntitySkillRuntime<T> runtime) {
	}

	/// 前摇结束、运行态已经切换为持续阶段时调用一次。
	/// @param entity 技能拥有者
	/// @param runtime 当前运行实例
	void onActivate(T entity, EntitySkillRuntime<T> runtime);

	/// 持续阶段由服务端状态机每 tick 调用。
	/// @param entity 技能拥有者
	/// @param runtime 当前运行实例
	void onTick(T entity, EntitySkillRuntime<T> runtime);

	/// 持续阶段正常结束、切换到后摇之前调用一次。
	/// 立即取消不会调用此方法，而会调用 {@link #onCancel}。
	/// @param entity 技能拥有者
	/// @param runtime 当前运行实例
	void onEnd(T entity, EntitySkillRuntime<T> runtime);

	/// 后摇正常结束、写入冷却并移除运行实例之前调用一次。
	/// @param entity 技能拥有者
	/// @param runtime 当前运行实例
	void onRecoveryEnd(T entity, EntitySkillRuntime<T> runtime);

	/// 技能被立即取消时调用，用于清除技能建立的动画、属性修饰符或其他临时状态。
	/// 该回调发生在运行实例移除及冷却写入之前。
	/// @param entity 技能拥有者
	/// @param runtime 被取消的运行实例
	default void onCancel(T entity, EntitySkillRuntime<T> runtime) {
	}
}
