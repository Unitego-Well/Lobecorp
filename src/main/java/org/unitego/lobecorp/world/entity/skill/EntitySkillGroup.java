package org.unitego.lobecorp.world.entity.skill;

/// 已注册的技能运行分组。
/// 每个实体分别保存自己拥有的分组及同时运行上限，因此修改某个实体的上限不会影响其他实体或注册值。
///
/// @param defaultMaximumActiveSkills 实体首次获得该组时使用的同时运行上限，必须大于或等于零
public record EntitySkillGroup(int defaultMaximumActiveSkills) {
	/// 技能组默认同时运行上限小于零时的错误说明。
	private static final String NEGATIVE_LIMIT_MESSAGE = "defaultMaximumActiveSkills must not be negative";

	/// 创建技能组定义并校验默认上限。
	///
	/// @throws IllegalArgumentException 当默认上限小于零时抛出
	public EntitySkillGroup {
		if (defaultMaximumActiveSkills < 0) {
			throw new IllegalArgumentException(NEGATIVE_LIMIT_MESSAGE);
		}
	}
}
