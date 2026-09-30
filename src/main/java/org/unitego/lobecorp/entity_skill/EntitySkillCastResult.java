package org.unitego.lobecorp.entity_skill;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/// 一次技能施放请求的结构化结果。
///
/// 失败原因只反映管理器在本次请求中观察到的第一条拒绝规则；技能定义和事件监听器
/// 仍然可以在后续阶段取消已经启动的运行实例。
public record EntitySkillCastResult<T extends LivingEntity>(
		Status status,
		@Nullable EntitySkillRuntime<T> runtime
) {
	/// @return 请求是否成功创建技能运行实例
	public boolean started() {
		return status == Status.STARTED;
	}

	/// 技能施放结果状态。
	public enum Status {
		STARTED,
		SKILL_NOT_OWNED,
		UNSUPPORTED_BY_ENTITY,
		ON_COOLDOWN,
		HOLDER_REJECTED,
		MUTUALLY_EXCLUSIVE,
		GROUP_NOT_AVAILABLE,
		SKILL_REJECTED,
		GROUP_FULL,
		EVENT_CANCELLED
	}
}
