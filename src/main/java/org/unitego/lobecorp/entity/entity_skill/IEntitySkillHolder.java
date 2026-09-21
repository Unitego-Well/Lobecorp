package org.unitego.lobecorp.entity.entity_skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.unitego.lobecorp.entity.util.EntitySkillManager;

/// 可选的实体技能行为钩子。技能与运行状态由数据附件持有，实体无需通过此接口提供技能集合。
public interface IEntitySkillHolder {
	/// 在实体首次创建技能相关附件后调用。
	/// 可用于向实体添加初始技能或调整默认分组配置；重复调用
	/// {@link EntitySkillManager#initialize(LivingEntity)} 不会重复触发此钩子。
	///
	/// @param entity 已完成附件创建的当前实体
	default void onSkillAttachmentsInitialized(LivingEntity entity) {
	}

	/// 决定实体拥有的技能附件是否应同步给指定玩家。
	/// 该钩子只控制技能所有权附件，其他技能附件遵循各自的同步配置。
	///
	/// @param player 即将接收附件数据的服务端玩家
	/// @return 允许同步返回 {@code true}；拒绝同步返回 {@code false}
	default boolean shouldSyncEntitySkills(ServerPlayer player) {
		return true;
	}

	/// 在通用所有权、冷却、互斥和分组检查之外，执行实体级施放限制。
	/// 此方法只应检查条件，不应启动、取消技能或修改运行态。
	///
	/// @param skill 正在尝试施放的技能
	/// @return 允许继续施放检查时返回 {@code true}
	default boolean canCastEntitySkill(IEntitySkill<?> skill) {
		return true;
	}

	/// 技能运行实例加入实体运行态附件后、技能前摇回调前调用。
	/// @param runtime 新创建的运行实例
	default void onEntitySkillStarted(EntitySkillRuntime<?> runtime) {
	}

	/// 技能进入持续阶段或后摇阶段时调用。
	/// 调用时 {@link EntitySkillRuntime#state()} 已经是新阶段。
	/// @param runtime 发生阶段变化的运行实例
	default void onEntitySkillPhaseChanged(EntitySkillRuntime<?> runtime) {
	}

	/// 技能正常完成后摇、写入冷却并移出运行态时调用。
	/// @param runtime 已完成的运行实例
	default void onEntitySkillEnded(EntitySkillRuntime<?> runtime) {
	}

	/// 技能被取消、写入冷却并移出运行态后调用。
	/// @param runtime 被取消的运行实例
	default void onEntitySkillCancelled(EntitySkillRuntime<?> runtime) {
	}

	/// 在通用技能系统写入最终冷却前调整持续时间。
	default int adjustEntitySkillCooldown(EntitySkillRuntime<?> runtime, int cooldownTicks) {
		return cooldownTicks;
	}

	/// 分组满载时，旧运行实例即将被新技能强制覆盖前调用。
	/// 随后旧实例仍会正常执行取消回调和取消钩子。
	///
	/// @param runtime 即将被覆盖的旧运行实例
	/// @param replacement 触发覆盖的新技能
	default void onEntitySkillOverridden(EntitySkillRuntime<?> runtime, IEntitySkill<?> replacement) {
	}
}
