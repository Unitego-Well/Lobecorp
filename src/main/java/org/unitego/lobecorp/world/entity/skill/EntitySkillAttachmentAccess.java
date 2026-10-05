package org.unitego.lobecorp.world.entity.skill;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

/// 基于 NeoForge Data Attachment 的实体技能能力实现。
///
/// 技能、技能组、冷却和运行实例均从当前实体附件读取；实现本身不缓存实体状态，
/// 因此每次通过 EntityCapability 查询到的对象都能看到同一份附件数据。
public class EntitySkillAttachmentAccess implements EntitySkillAccess {
	protected final LivingEntity entity;

	public EntitySkillAttachmentAccess(LivingEntity entity) {
		this.entity = entity;
	}

	@Override
	public LivingEntity entity() {
		return entity;
	}

	@Override
	public boolean supports(IEntitySkill<?> skill) {
		return EntitySkillUtil.getSkills(entity).contains(skill);
	}

	@Override
	public Set<IEntitySkill<?>> skills() {
		return EntitySkillUtil.getSkills(entity);
	}

	@Override
	public void initialize() {
		EntitySkillUtil.initialize(entity);
	}

	@Override
	public List<EntitySkillRuntime<?>> activeSkills() {
		return EntitySkillUtil.getActiveSkills(entity);
	}

	@Override
	public void addSkill(IEntitySkill<?> skill) {
		EntitySkillUtil.addSkill(entity, skill);
	}

	@Override
	public void removeSkill(IEntitySkill<?> skill) {
		EntitySkillUtil.removeSkill(entity, skill);
	}

	@Override
	public Map<EntitySkillGroup, Integer> groups() {
		return EntitySkillUtil.getGroups(entity);
	}

	@Override
	public Map<EntitySkillGroup, Integer> groupsIfPresent() {
		return EntitySkillUtil.getGroupsIfPresent(entity);
	}

	@Override
	public void addGroup(EntitySkillGroup group) {
		EntitySkillUtil.addGroup(entity, group);
	}

	@Override
	public boolean removeGroup(EntitySkillGroup group) {
		return EntitySkillUtil.removeGroup(entity, group);
	}

	@Override
	public void setGroupMaximum(EntitySkillGroup group, int maximum) {
		EntitySkillUtil.setGroupMaximum(entity, group, maximum);
	}

	@Override
	public void setGroupMaximums(int maximum, EntitySkillGroup... groups) {
		EntitySkillUtil.setGroupMaximums(entity, maximum, groups);
	}

	@Override
	public boolean isOnCooldown(IEntitySkill<?> skill) {
		return EntitySkillUtil.isOnCooldown(entity, skill);
	}

	@Override
	public Map<IEntitySkill<?>, Long> cooldowns() {
		return EntitySkillUtil.getCooldowns(entity);
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public boolean canCast(EntitySkillCastRequest<?> request) {
		return supports(request.skill())
				&& EntitySkillUtil.canCast(entity, (IEntitySkill) request.skill());
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public EntitySkillCastResult<?> cast(EntitySkillCastRequest<?> request) {
		if (!supports(request.skill())) {
			return new EntitySkillCastResult<>(EntitySkillCastResult.Status.UNSUPPORTED_BY_ENTITY, null);
		}
		return EntitySkillUtil.cast(entity, (EntitySkillCastRequest) request);
	}

	@Override
	public void tick() {
		EntitySkillUtil.tick(entity);
	}

	@Override
	public void cancelAll(boolean forced) {
		EntitySkillUtil.cancelAll(entity, forced);
	}

	@Override
	public int cooldownTicks(IEntitySkill<?> skill) {
		return EntitySkillUtil.cooldownTicks(entity, skill);
	}

	@Override
	public void endSkill() {
		EntitySkillUtil.endSkill(entity);
	}

	@Override
	public void endSkill(IEntitySkill<?> skill) {
		EntitySkillUtil.endSkill(entity, skill);
	}

	@Override
	public void endSkills(IEntitySkill<?>... skills) {
		EntitySkillUtil.endSkills(entity, skills);
	}

	@Override
	public void cancelSkill() {
		EntitySkillUtil.cancelSkill(entity);
	}

	@Override
	public void cancelSkill(IEntitySkill<?> skill) {
		EntitySkillUtil.cancelSkill(entity, skill);
	}

	@Override
	public void forceCancelSkill() {
		EntitySkillUtil.forceCancelSkill(entity);
	}

	@Override
	public void forceCancelSkill(IEntitySkill<?> skill) {
		EntitySkillUtil.forceCancelSkill(entity, skill);
	}

	@Override
	public void cancelSkills(IEntitySkill<?>... skills) {
		EntitySkillUtil.cancelSkills(entity, skills);
	}

	@Override
	public void forceCancelSkills(IEntitySkill<?>... skills) {
		EntitySkillUtil.forceCancelSkills(entity, skills);
	}

	@Override
	public boolean isCasting(IEntitySkill<?> skill) {
		return EntitySkillUtil.isCasting(entity, skill);
	}

	@Override
	public boolean isActive(IEntitySkill<?> skill) {
		return EntitySkillUtil.isActive(entity, skill);
	}

	@Override
	public boolean hasActiveSkills() {
		return EntitySkillUtil.hasActiveSkills(entity);
	}

	@Override
	public boolean isMovementLocked() {
		return EntitySkillUtil.isMovementLocked(entity);
	}

	@Override
	public boolean hasActiveSkill(TagKey<IEntitySkill<?>> tag) {
		return EntitySkillUtil.hasActiveSkill(entity, tag);
	}

	@Override
	public void setCooldown(IEntitySkill<?> skill, int ticks) {
		EntitySkillUtil.setCooldown(entity, skill, ticks);
	}

	@Override
	public void setCooldowns(int ticks, IEntitySkill<?>... skills) {
		EntitySkillUtil.setCooldowns(entity, ticks, skills);
	}

	@Override
	public int attackCombo() {
		return EntitySkillUtil.getAttackCombo(entity);
	}

	@Override
	public void setAttackCombo(int combo) {
		EntitySkillUtil.setAttackCombo(entity, combo);
	}

	@Override
	public void clearTemporaryState() {
		EntitySkillUtil.clearTemporaryState(entity);
	}

	@Override
	public void clearRuntimeState() {
		EntitySkillUtil.clearRuntimeState(entity);
	}
}
