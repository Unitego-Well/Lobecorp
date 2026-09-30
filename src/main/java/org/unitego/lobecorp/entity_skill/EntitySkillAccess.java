package org.unitego.lobecorp.entity_skill;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.util.EntitySkillUtil;
import org.unitego.lobecorp.registry.LcCapabilities;

import java.util.List;
import java.util.Map;
import java.util.Set;

/// 实体技能能力的操作边界。
///
/// 能力对象只代理当前实体，不持有技能附件的第二份数据；每次调用都应从实体重新查询能力。
@SuppressWarnings("unused")
public interface EntitySkillAccess {
	/// 从实体获取能力；普通查看对象可能没有该能力。
	@Nullable
	static EntitySkillAccess get(LivingEntity entity) {
		return entity.getCapability(LcCapabilities.ENTITY_SKILL);
	}

	static <T extends LivingEntity> boolean canCast(T entity, IEntitySkill<T> skill) {
		return EntitySkillUtil.require(entity).canCast(EntitySkillCastRequest.of(skill));
	}

	/// @return 当前能力所属实体
	LivingEntity entity();

	/// 判断实体当前是否拥有并支持指定技能。
	boolean supports(IEntitySkill<?> skill);

	/// @return 当前技能附件的不可变视图
	Set<IEntitySkill<?>> skills();

	/// 初始化实体技能附件。只在实体确实需要技能时调用。
	void initialize();

	/// @return 当前运行实例附件的不可变快照
	List<EntitySkillRuntime<?>> activeSkills();

	/// 添加或移除实体拥有的技能。
	void addSkill(IEntitySkill<?> skill);

	void removeSkill(IEntitySkill<?> skill);

	/// @return 当前技能组及其并发上限
	Map<EntitySkillGroup, Integer> groups();

	/// @return 不创建默认组的技能组快照
	Map<EntitySkillGroup, Integer> groupsIfPresent();

	void addGroup(EntitySkillGroup group);

	boolean removeGroup(EntitySkillGroup group);

	void setGroupMaximum(EntitySkillGroup group, int maximum);

	void setGroupMaximums(int maximum, EntitySkillGroup... groups);

	boolean isOnCooldown(IEntitySkill<?> skill);

	Map<IEntitySkill<?>, Long> cooldowns();

	/// 无副作用检查一次施放请求。
	boolean canCast(EntitySkillCastRequest<?> request);

	/// 提交一次技能施放请求。
	EntitySkillCastResult<?> cast(EntitySkillCastRequest<?> request);

	/// 便于实体目标或位置目标调用的请求包装。
	@SuppressWarnings({"rawtypes", "unchecked"})
	default EntitySkillCastResult<?> cast(IEntitySkill<?> skill, Entity target, Vec3 targetPosition) {
		return cast(new EntitySkillCastRequest(skill, target, targetPosition));
	}

	/// 推进当前实体的技能状态机。
	void tick();

	/// 停止并清理当前实体的运行技能。
	void cancelAll(boolean forced);

	/// @return 指定技能当前剩余冷却 tick
	int cooldownTicks(IEntitySkill<?> skill);

	void endSkill();

	void endSkill(IEntitySkill<?> skill);

	void endSkills(IEntitySkill<?>... skills);

	void cancelSkill();

	void cancelSkill(IEntitySkill<?> skill);

	void forceCancelSkill();

	void forceCancelSkill(IEntitySkill<?> skill);

	void cancelSkills(IEntitySkill<?>... skills);

	void forceCancelSkills(IEntitySkill<?>... skills);

	boolean isCasting(IEntitySkill<?> skill);

	boolean isActive(IEntitySkill<?> skill);

	boolean hasActiveSkills();

	boolean isMovementLocked();

	boolean hasActiveSkill(TagKey<IEntitySkill<?>> tag);

	void setCooldown(IEntitySkill<?> skill, int ticks);

	void setCooldowns(int ticks, IEntitySkill<?>... skills);

	int attackCombo();

	void setAttackCombo(int combo);

	void clearTemporaryState();

	void clearRuntimeState();

}
