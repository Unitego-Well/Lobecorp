package org.unitego.lobecorp.util;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.unitego.lobecorp.entity_skill.*;
import org.unitego.lobecorp.entity_skill.effect.EntitySkillEffectManager;
import org.unitego.lobecorp.event.EntitySkillEvent;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.network.tc.EntitySkillSyncPayload;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.registry.entity_skill.LcEntitySkillGroups;
import org.unitego.lobecorp.registry.entity_skill.EntitySkillDefaults;

import java.util.*;

/// 实体技能附件状态机的内部实现。业务代码通过 EntitySkillAccess 能力访问技能、分组、运行态、冷却和攻击段数。
public class EntitySkillUtil {

	/// 初始化实体的技能附件，并确保默认的当前执行组与被动组存在。
	/// 重复调用不会覆盖实体已经拥有的技能、分组上限、冷却或运行态。
	///
	/// @param entity 需要初始化技能数据的实体
	public static void initialize(LivingEntity entity) {
		ensureDefaultGroups(entity);
		entity.getData(LcAttachmentTypes.ENTITY_SKILLS);
		entity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS);
		entity.getData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS);
		entity.getData(LcAttachmentTypes.ATTACK_COMBO);
		for (IEntitySkill<?> skill : EntitySkillDefaults.forType(entity.getType())) {
			addGroup(entity, skill.group());
		}
	}

	/// 获取实体当前拥有的全部技能。
	/// 返回值是默认定义与实体修正合并后的不可变视图；添加或移除技能应分别使用
	/// {@link #addSkill} 与 {@link #removeSkill}。
	///
	/// @param entity 技能拥有者
	/// @return 当前拥有的已注册技能集合
	public static Set<IEntitySkill<?>> getSkills(LivingEntity entity) {
		initialize(entity);
		return entity.getData(LcAttachmentTypes.ENTITY_SKILLS).apply(EntitySkillDefaults.forType(entity.getType()));
	}

	/// 获取实体当前的技能运行实例快照。
	/// 运行态附件不存在时返回空列表，不会为了查询而初始化实体技能数据。
	///
	/// @param entity 技能拥有者
	/// @return 当前运行实例的不可变快照
	public static List<EntitySkillRuntime<?>> getActiveSkills(LivingEntity entity) {
		if (!entity.hasData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS)) {
			return List.of();
		}
		return List.copyOf(activeSkills(entity));
	}

	/// 为实体添加技能，并自动确保技能所属分组存在。
	/// 重复添加同一个注册技能不会产生重复项，也不会重置该技能的冷却或运行态。
	/// 已拥有技能附件会按照附件配置持久化并同步。
	///
	/// @param entity 接收技能的实体
	/// @param skill  要添加的已注册技能
	public static void addSkill(LivingEntity entity, IEntitySkill<?> skill) {
		initialize(entity);
		EntitySkillState state = entity.getData(LcAttachmentTypes.ENTITY_SKILLS);
		EntitySkillState next = state.with(skill, true, EntitySkillDefaults.forType(entity.getType()).contains(skill));
		if (!next.equals(state)) {
			entity.setData(LcAttachmentTypes.ENTITY_SKILLS, next);
		}
		addGroup(entity, skill.group());
	}

	/// 为实体依次添加多个技能。
	/// 每个技能均使用 {@link #addSkill(LivingEntity, IEntitySkill)} 的规则处理，重复技能不会重置状态。
	///
	/// @param entity 接收技能的实体
	/// @param skills 要添加的已注册技能
	public static void addSkills(LivingEntity entity, IEntitySkill<?>... skills) {
		for (IEntitySkill<?> skill : skills) {
			addSkill(entity, skill);
		}
	}

	/// 从实体拥有的技能中移除指定技能。
	/// 如果该技能正在运行，其全部运行实例会被强制取消；实体未拥有该技能时不执行任何操作。
	///
	/// @param entity 技能拥有者
	/// @param skill  要移除的技能
	public static void removeSkill(LivingEntity entity, IEntitySkill<?> skill) {
		if (!getSkills(entity).contains(skill)) {
			return;
		}
		cancelSkills(entity, skill, true);
		if (skill instanceof MultiStageSkill<?> multiStage) {
			entity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS).multiStageSequences.remove(multiStage);
		}
		entity.setData(LcAttachmentTypes.ENTITY_SKILLS, entity.getData(LcAttachmentTypes.ENTITY_SKILLS)
				.with(skill, false, EntitySkillDefaults.forType(entity.getType()).contains(skill)));
	}

	/// 从实体拥有的技能中依次移除多个技能。
	/// 正在运行的对应实例会按照 {@link #removeSkill(LivingEntity, IEntitySkill)} 的规则强制取消。
	///
	/// @param entity 技能拥有者
	/// @param skills 要移除的技能
	public static void removeSkills(LivingEntity entity, IEntitySkill<?>... skills) {
		for (IEntitySkill<?> skill : skills) {
			removeSkill(entity, skill);
		}
	}

	/// 获取实体拥有的技能组及各组当前的同时运行上限。
	/// 调用时会补齐不可移除的默认组。返回值为不可变副本，修改上限应使用
	/// {@link #setGroupMaximum}。
	///
	/// @param entity 要查询的实体
	/// @return 技能组到同时运行上限的映射
	public static Map<EntitySkillGroup, Integer> getGroups(LivingEntity entity) {
		ensureDefaultGroups(entity);
		return Map.copyOf(entity.getData(LcAttachmentTypes.ENTITY_SKILL_GROUPS));
	}

	/// 获取已经创建的技能组附件，不为调试或显示查询补建默认组。
	///
	/// @param entity 要查询的实体
	/// @return 当前附件中的技能组，不存在时返回空映射
	public static Map<EntitySkillGroup, Integer> getGroupsIfPresent(LivingEntity entity) {
		if (!entity.hasData(LcAttachmentTypes.ENTITY_SKILL_GROUPS)) {
			return Map.of();
		}
		return Map.copyOf(entity.getData(LcAttachmentTypes.ENTITY_SKILL_GROUPS));
	}

	/// 为实体添加技能组。
	/// 新增组使用 {@link EntitySkillGroup#defaultMaximumActiveSkills()} 作为初始上限；
	/// 已存在的组不会被重置。
	///
	/// @param entity 接收技能组的实体
	/// @param group  要添加的已注册技能组
	public static void addGroup(LivingEntity entity, EntitySkillGroup group) {
		Map<EntitySkillGroup, Integer> groups = new LinkedHashMap<>(entity.getData(LcAttachmentTypes.ENTITY_SKILL_GROUPS));
		if (groups.putIfAbsent(group, group.defaultMaximumActiveSkills()) == null) {
			entity.setData(LcAttachmentTypes.ENTITY_SKILL_GROUPS, Map.copyOf(groups));
		}
	}

	/// 为实体依次添加多个技能组。
	/// 每个新增组使用自身的默认同时运行上限，已有组不会被重置。
	///
	/// @param entity 接收技能组的实体
	/// @param groups 要添加的已注册技能组
	public static void addGroups(LivingEntity entity, EntitySkillGroup... groups) {
		for (EntitySkillGroup group : groups) {
			addGroup(entity, group);
		}
	}

	/// 移除实体拥有的自定义技能组，并强制取消该组内全部运行实例。
	/// 当前执行组和被动组属于默认组，不能移除，但可将上限设置为零。
	///
	/// @param entity 技能组拥有者
	/// @param group  要移除的技能组
	/// @return 成功移除返回 {@code true}；默认组或实体未拥有该组时返回 {@code false}
	public static boolean removeGroup(LivingEntity entity, EntitySkillGroup group) {
		if (isDefaultGroup(group)) {
			return false;
		}
		Map<EntitySkillGroup, Integer> groups = new LinkedHashMap<>(entity.getData(LcAttachmentTypes.ENTITY_SKILL_GROUPS));
		if (groups.remove(group) == null) {
			return false;
		}
		cancelGroup(entity, group);
		entity.setData(LcAttachmentTypes.ENTITY_SKILL_GROUPS, Map.copyOf(groups));
		return true;
	}

	/// 依次移除实体拥有的多个自定义技能组。
	/// 默认组与不存在的组会被跳过，返回值用于区分是否实际移除了至少一个组。
	///
	/// @param entity 技能组拥有者
	/// @param groups 要移除的技能组
	/// @return 至少成功移除一个组时返回 {@code true}
	public static boolean removeGroups(LivingEntity entity, EntitySkillGroup... groups) {
		boolean removed = false;
		for (EntitySkillGroup group : groups) {
			if (removeGroup(entity, group)) {
				removed = true;
			}
		}
		return removed;
	}

	/// 设置实体指定技能组的同时运行上限。
	/// 实体尚未拥有该组时会先添加该组。降低上限后，系统按开始顺序从最早的运行实例开始
	/// 强制取消，直到运行数量不超过新上限。上限为零表示该组不能开始新技能。
	///
	/// @param entity  技能组拥有者
	/// @param group   要修改的技能组
	/// @param maximum 新的同时运行上限，必须大于或等于零
	/// @throws IllegalArgumentException 当 {@code maximum} 小于零时抛出
	public static void setGroupMaximum(LivingEntity entity, EntitySkillGroup group, int maximum) {
		if (maximum < 0) {
			throw new IllegalArgumentException("maximum must not be negative");
		}
		addGroup(entity, group);
		Map<EntitySkillGroup, Integer> groups = new LinkedHashMap<>(entity.getData(LcAttachmentTypes.ENTITY_SKILL_GROUPS));
		groups.put(group, maximum);
		entity.setData(LcAttachmentTypes.ENTITY_SKILL_GROUPS, Map.copyOf(groups));
		trimGroup(entity, group, maximum);
	}

	/// 为多个技能组设置相同的同时运行上限。
	/// 尚未拥有的组会先被添加；降低上限时，每个组分别裁剪超出的运行实例。
	///
	/// @param entity  技能组拥有者
	/// @param maximum 新的同时运行上限，必须大于或等于零
	/// @param groups  要修改的技能组
	/// @throws IllegalArgumentException 当 {@code maximum} 小于零时抛出
	public static void setGroupMaximums(LivingEntity entity, int maximum, EntitySkillGroup... groups) {
		for (EntitySkillGroup group : groups) {
			setGroupMaximum(entity, group, maximum);
		}
	}

	/// 判断技能是否仍处于冷却。
	/// 冷却使用世界游戏时间记录结束时刻，因此实体重新加载后仍能保持持久化的剩余冷却。
	///
	/// @param entity 技能拥有者
	/// @param skill  要查询的技能
	/// @return 当前游戏时间早于冷却结束时间时返回 {@code true}
	public static boolean isOnCooldown(LivingEntity entity, IEntitySkill<?> skill) {
		return cooldownTicks(entity, skill) > 0;
	}

	/// 获取技能当前剩余冷却 tick。
	/// 冷却附件不存在或已经结束时返回零，不会为了查询创建技能附件。
	///
	/// @param entity 技能拥有者
	/// @param skill  要查询的技能
	/// @return 当前剩余冷却 tick
	public static int cooldownTicks(LivingEntity entity, IEntitySkill<?> skill) {
		if (!entity.hasData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS)) {
			return 0;
		}
		Long until = entity.getData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS).get(skill);
		if (until == null) {
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, until - entity.level().getGameTime()));
	}

	/// 获取实体当前冷却结束时间快照。
	///
	/// @param entity 技能拥有者
	/// @return 技能到冷却结束游戏时间的不可变映射
	public static Map<IEntitySkill<?>, Long> getCooldowns(LivingEntity entity) {
		if (!entity.hasData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS)) {
			return Map.of();
		}
		return Map.copyOf(entity.getData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS));
	}

	/// 检查技能此刻是否可以施放，但不创建运行实例，也不取消可覆盖的旧技能。
	/// 检查内容包括技能所有权、冷却、持有者钩子、同技能互斥、分组所有权、技能业务条件，
	/// 以及组满时是否存在足够的可覆盖运行实例。
	///
	/// @param entity 尝试施放技能的实体
	/// @param skill  要检查的技能，实体类型必须与技能泛型一致
	/// @return 所有施放条件均满足时返回 {@code true}
	public static <T extends LivingEntity> boolean canCast(T entity, IEntitySkill<T> skill) {
		EntitySkillRuntime<T> runtime = prepareCast(entity, skill);
		if (runtime == null) {
			return false;
		}
		if (!runtime.canUse()) {
			return false;
		}
		return replacementsFor(entity, skill) != null;
	}

	/// 尝试施放技能并进入前摇阶段。
	/// 组满时会先按开始顺序覆盖允许被覆盖的旧运行实例；施放成功后应用移动限制、调用持有者开始钩子，
	/// 再调用技能的前摇开始回调。默认冷却从成功开始施放时计时，与全部运行阶段同时推进。
	/// 零 tick 阶段会在本次调用中立即推进。
	///
	/// @param entity 施法实体
	/// @param skill  要施放的技能，实体必须已经拥有该技能
	/// @return 本次施放的结构化结果
	public static <T extends LivingEntity> EntitySkillCastResult<T> cast(T entity, IEntitySkill<T> skill) {
		return cast(entity, EntitySkillCastRequest.of(skill));
	}

	/// 兼容带实体或位置目标的旧布尔入口，实际施放仍由结构化请求入口处理。
	public static <T extends LivingEntity> boolean cast(T entity, IEntitySkill<T> skill,
	                                                    Entity target, Vec3 targetPosition) {
		return cast(entity, new EntitySkillCastRequest<>(skill, target, targetPosition)).started();
	}

	/// 按类型安全的请求施放技能，并返回结构化结果。
	///
	/// 旧的布尔返回入口继续委托到此方法；技能定义、事件、动画和同步回调的执行顺序保持不变。
	///
	/// @param entity  施法实体
	/// @param request 本次施放请求
	/// @return 施放结果以及成功时创建的运行实例
	public static <T extends LivingEntity> EntitySkillCastResult<T> cast(
			T entity, EntitySkillCastRequest<T> request) {
		IEntitySkill<T> skill = request.skill();
		initialize(entity);
		expireMultiStageSequences(entity);
		if (!getSkills(entity).contains(skill)) {
			return new EntitySkillCastResult<>(EntitySkillCastResult.Status.SKILL_NOT_OWNED, null);
		}
		if (isOnCooldown(entity, skill)) {
			return new EntitySkillCastResult<>(EntitySkillCastResult.Status.ON_COOLDOWN, null);
		}
		if (skill.mutuallyExclusive()
				&& activeSkills(entity).stream().anyMatch(runtime -> runtime.skill() == skill && !canOverride(runtime, skill))) {
			return new EntitySkillCastResult<>(EntitySkillCastResult.Status.MUTUALLY_EXCLUSIVE, null);
		}
		if (!getGroups(entity).containsKey(skill.group())) {
			return new EntitySkillCastResult<>(EntitySkillCastResult.Status.GROUP_NOT_AVAILABLE, null);
		}
		long runtimeId = entity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS).allocateRuntimeId();
		EntitySkillRuntime<T> runtime = new EntitySkillRuntime<>(runtimeId, entity, skill,
				EntitySkillRuntime.SkillState.WINDUP, skill.windupTicks());
		runtime.setTarget(request.target());
		runtime.setTargetPosition(request.targetPosition());
		if (!runtime.canUse()) {
			return new EntitySkillCastResult<>(EntitySkillCastResult.Status.SKILL_REJECTED, null);
		}
		List<EntitySkillRuntime<?>> replacements = replacementsFor(entity, skill);
		if (replacements == null) {
			return new EntitySkillCastResult<>(EntitySkillCastResult.Status.GROUP_FULL, null);
		}
		if (!skill.prepareAim(entity, runtime)) {
			return new EntitySkillCastResult<>(EntitySkillCastResult.Status.AIMING, null);
		}
		if (!entity.level().isClientSide()) {
			EntitySkillEvent.Cast event = new EntitySkillEvent.Cast(runtime);
			NeoForge.EVENT_BUS.post(event);
			if (event.isCanceled()) {
				return new EntitySkillCastResult<>(EntitySkillCastResult.Status.EVENT_CANCELLED, null);
			}
		}
		replacements = replacementsFor(entity, skill);
		if (replacements == null) {
			return new EntitySkillCastResult<>(EntitySkillCastResult.Status.GROUP_FULL, null);
		}
		for (EntitySkillRuntime<?> replaced : replacements) {
			if (replaced.skill() instanceof MultiStageBasicSkill<?> basic
					&& replaced.state() == EntitySkillRuntime.SkillState.RECOVERY) {
				if (replaced.skill() == skill) {
					finish(replaced);
					continue;
				}
				basic.expireSequence(replaced);
			}
			cancelRuntime(entity, replaced, true);
		}
		activeSkills(entity).add(runtime);
		startCooldown(runtime, skill.cooldownTicks());
		applyMovementLocks(entity, skill);
		runtime.onWindupStart();
		if (isCurrent(runtime) && runtime.state() == EntitySkillRuntime.SkillState.WINDUP) {
			postServer(new EntitySkillEvent.Started(runtime));
			if (isCurrent(runtime)) {
				advanceImmediateStates(runtime);
			}
		}
		return new EntitySkillCastResult<>(EntitySkillCastResult.Status.STARTED, runtime);
	}

	/// 依次尝试施放多个技能，并返回成功启动的运行实例数量。
	/// 每个技能均独立使用 {@link #cast(LivingEntity, IEntitySkill)} 的完整检查；单个技能失败不会中止后续尝试。
	/// 同一批次中先启动的技能会参与后续技能的分组上限与覆盖判断。
	///
	/// @param entity 施法实体
	/// @param skills 要依次尝试施放的技能
	/// @param <T>    施法实体类型
	/// @return 成功启动的技能数量
	@SafeVarargs
	public static <T extends LivingEntity> int castSkills(T entity, IEntitySkill<T>... skills) {
		int successfulCasts = 0;
		for (IEntitySkill<T> skill : skills) {
			if (cast(entity, skill).started()) {
				successfulCasts++;
			}
		}
		return successfulCasts;
	}

	private static <T extends LivingEntity> EntitySkillRuntime<T> prepareCast(T entity, IEntitySkill<T> skill) {
		initialize(entity);
		if (!getSkills(entity).contains(skill)) {
			return null;
		}
		if (isOnCooldown(entity, skill)) {
			return null;
		}
		if (skill.mutuallyExclusive()
				&& activeSkills(entity).stream().anyMatch(runtime -> runtime.skill() == skill && !canOverride(runtime, skill))) {
			return null;
		}
		if (!getGroups(entity).containsKey(skill.group())) {
			return null;
		}
		return new EntitySkillRuntime<>(entity, skill, EntitySkillRuntime.SkillState.WINDUP, skill.windupTicks());
	}

	/// 当前技能阶段是否允许开始新施放；与真正施放复用同一互斥和覆盖判定。
	public static boolean canBeginCast(LivingEntity entity, IEntitySkill<?> skill) {
		return getGroups(entity).containsKey(skill.group()) && replacementsFor(entity, skill) != null;
	}

	private static List<EntitySkillRuntime<?>> replacementsFor(LivingEntity entity, IEntitySkill<?> skill) {
		List<EntitySkillRuntime<?>> inGroup = activeSkills(entity).stream()
				.filter(runtime -> runtime.skill().group() == skill.group()).toList();
		int needed = inGroup.size() - getGroups(entity).getOrDefault(skill.group(), 0) + 1;
		List<EntitySkillRuntime<?>> result = new ArrayList<>();
		if (skill.mutuallyExclusive()) {
			for (EntitySkillRuntime<?> runtime : inGroup) {
				if (runtime.skill() != skill) continue;
				if (!canOverride(runtime, skill)) return null;
				result.add(runtime);
			}
		}
		if (result.size() >= needed) return result;
		for (EntitySkillRuntime<?> runtime : inGroup) {
			if (!result.contains(runtime) && canOverride(runtime, skill)) {
				result.add(runtime);
			}
			if (result.size() == needed) {
				return result;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T extends LivingEntity> boolean canOverride(EntitySkillRuntime<?> runtime, IEntitySkill<?> replacement) {
		EntitySkillRuntime<T> typed = (EntitySkillRuntime<T>) runtime;
		return replacement.interruptsSkills() && typed.isInterruptibleBySkill()
				&& (typed.state() != EntitySkillRuntime.SkillState.WINDUP
				|| typed.skill().canInterruptDuringWindup(typed.owner(), typed))
				&& typed.skill().canBeOverridden(typed.owner(), typed, replacement);
	}

	/// 结束实体当前处于持续阶段的全部技能，使其进入各自的后摇阶段。
	/// 前摇或已经处于后摇的技能不受影响；该操作不同于取消，不会跳过后摇。
	///
	/// @param entity 技能运行实体
	public static void endSkill(LivingEntity entity) {
		List.copyOf(activeSkills(entity)).stream()
				.filter(runtime -> runtime.state() == EntitySkillRuntime.SkillState.ACTIVE)
				.forEach(EntitySkillUtil::endRuntime);
	}

	/// 结束指定技能当前处于持续阶段的全部运行实例，使其进入后摇阶段。
	///
	/// @param entity 技能运行实体
	/// @param skill  要结束的技能
	public static void endSkill(LivingEntity entity, IEntitySkill<?> skill) {
		List.copyOf(activeSkills(entity)).stream()
				.filter(runtime -> runtime.skill() == skill
						&& runtime.state() == EntitySkillRuntime.SkillState.ACTIVE)
				.forEach(EntitySkillUtil::endRuntime);
	}

	/// 依次结束多个指定技能当前处于持续阶段的全部运行实例。
	/// 前摇或已经处于后摇的实例不受影响。
	///
	/// @param entity 技能运行实体
	/// @param skills 要结束的技能
	public static void endSkills(LivingEntity entity, IEntitySkill<?>... skills) {
		for (IEntitySkill<?> skill : skills) {
			endSkill(entity, skill);
		}
	}

	private static void endRuntime(EntitySkillRuntime<?> runtime) {
		runtime.onEnd();
		if (isCurrent(runtime)) {
			enterRecovery(runtime);
			if (isCurrent(runtime)) {
				advanceImmediateStates(runtime);
			}
		}
	}

	/// 尝试取消实体的全部运行技能。不允许普通取消的技能会继续运行。
	///
	/// @param entity 技能运行实体
	public static void cancelSkill(LivingEntity entity) {
		cancelAll(entity, false);
	}

	/// 强制取消实体的全部运行技能，无视技能的前摇中断限制。
	///
	/// @param entity 技能运行实体
	public static void forceCancelSkill(LivingEntity entity) {
		cancelAll(entity, true);
	}

	/// 尝试取消指定技能的全部运行实例。不允许普通取消的实例会继续运行。
	///
	/// @param entity 技能运行实体
	/// @param skill  要取消的技能
	public static void cancelSkill(LivingEntity entity, IEntitySkill<?> skill) {
		cancelSkills(entity, skill, false);
	}

	/// 强制取消指定技能的全部运行实例，无视技能的前摇中断限制。
	///
	/// @param entity 技能运行实体
	/// @param skill  要取消的技能
	public static void forceCancelSkill(LivingEntity entity, IEntitySkill<?> skill) {
		cancelSkills(entity, skill, true);
	}

	/// 尝试取消多个指定技能的全部运行实例。
	/// 不允许普通取消的实例会继续运行。
	///
	/// @param entity 技能运行实体
	/// @param skills 要取消的技能
	public static void cancelSkills(LivingEntity entity, IEntitySkill<?>... skills) {
		for (IEntitySkill<?> skill : skills) {
			cancelSkills(entity, skill, false);
		}
	}

	/// 尝试取消实体当前运行且属于指定标签的全部技能实例。
	/// 不允许普通取消的实例会继续运行；取消不会重启默认冷却，仅应用运行实例的显式冷却覆盖。
	///
	/// @param entity 技能运行实体
	/// @param tag    用于筛选运行技能的实体技能标签
	public static void cancelSkills(LivingEntity entity, TagKey<IEntitySkill<?>> tag) {
		List.copyOf(activeSkills(entity)).stream()
				.filter(runtime -> runtime.skill().is(tag))
				.forEach(runtime -> cancelRuntime(entity, runtime, false));
	}

	/// 强制取消多个指定技能的全部运行实例。
	/// 该操作忽略每个运行实例的前摇中断限制。
	///
	/// @param entity 技能运行实体
	/// @param skills 要强制取消的技能
	public static void forceCancelSkills(LivingEntity entity, IEntitySkill<?>... skills) {
		for (IEntitySkill<?> skill : skills) {
			cancelSkills(entity, skill, true);
		}
	}

	/// 取消指定技能的全部运行实例，不重启默认冷却，仅在取消成功后应用显式冷却覆盖。
	///
	/// @param entity 技能运行实体
	/// @param skill  要取消的技能
	/// @param forced 是否忽略前摇阶段的不可中断限制
	public static void cancelSkills(LivingEntity entity, IEntitySkill<?> skill, boolean forced) {
		List.copyOf(activeSkills(entity)).stream()
				.filter(runtime -> runtime.skill() == skill)
				.forEach(runtime -> cancelRuntime(entity, runtime, forced));
	}

	/// 取消实体的全部运行技能，不重启默认冷却，仅在每个实例取消成功后应用显式冷却覆盖。
	///
	/// @param entity 技能运行实体
	/// @param forced 是否忽略前摇阶段的不可中断限制
	public static void cancelAll(LivingEntity entity, boolean forced) {
		List.copyOf(activeSkills(entity))
				.forEach(runtime -> cancelRuntime(entity, runtime, forced));
	}

	private static void cancelRuntime(LivingEntity entity, EntitySkillRuntime<?> runtime, boolean forced) {
		if (!isCurrent(runtime)) {
			return;
		}
		if (!forced && !canCancel(runtime)) {
			EntitySkillDebugUtil.log(runtime, "cancel-rejected:not-cancellable");
			return;
		}
		runtime.onCancel();
		EntitySkillEffectManager.removeForSkillRuntime(runtime);
		HitboxManager.removeForSkillRuntime(runtime);
		activeSkills(entity).remove(runtime);
		if (runtime.hasCooldownTicksOverride()) {
			startCooldown(runtime, runtime.cooldownTicks());
		}
		postServer(new EntitySkillEvent.Cancelled(runtime));
	}

	@SuppressWarnings("unchecked")
	private static <T extends LivingEntity> boolean canCancel(EntitySkillRuntime<?> runtime) {
		EntitySkillRuntime<T> typed = (EntitySkillRuntime<T>) runtime;
		return typed.skill().canBeCancelled(typed.owner(), typed);
	}

	/// 判断实体是否存在指定技能的任意运行实例，不区分前摇、持续或后摇阶段。
	///
	/// @param entity 要查询的实体
	/// @param skill  要查询的技能
	/// @return 存在至少一个运行实例时返回 {@code true}
	public static boolean isCasting(LivingEntity entity, IEntitySkill<?> skill) {
		return activeSkills(entity).stream().anyMatch(runtime -> runtime.skill() == skill);
	}

	/// 判断指定技能是否存在处于持续阶段的运行实例。
	/// 前摇和后摇实例不会被视为技能效果正在生效。
	///
	/// @param entity 要查询的实体
	/// @param skill  要查询的技能
	/// @return 存在持续阶段实例时返回 {@code true}
	public static boolean isActive(LivingEntity entity, IEntitySkill<?> skill) {
		return activeSkills(entity).stream().anyMatch(runtime -> runtime.skill() == skill
				&& runtime.state() == EntitySkillRuntime.SkillState.ACTIVE);
	}

	/// 判断实体是否存在任意正在运行的技能。
	///
	/// @param entity 要查询的实体
	/// @return 活跃运行态附件非空时返回 {@code true}
	public static boolean hasActiveSkills(LivingEntity entity) {
		return !activeSkills(entity).isEmpty();
	}

	/// 判断实体当前是否被任一运行中的技能锁定移动。
	/// 前摇、持续和后摇均属于技能运行期；锁定期间 AI 不应提交新的移动模式或移动目标。
	///
	/// @param entity 要查询的实体
	/// @return 至少存在一个锁定移动的运行实例时返回 {@code true}
	public static boolean isMovementLocked(LivingEntity entity) {
		return activeSkills(entity).stream().anyMatch(runtime -> runtime.skill().locksMovement());
	}

	/// 检查实体是否正在运行属于指定原版注册表标签的技能。
	///
	/// @param entity 要检查的技能拥有者
	/// @param tag    技能注册表标签
	/// @return 至少存在一个匹配运行实例时返回 {@code true}
	public static boolean hasActiveSkill(LivingEntity entity, TagKey<IEntitySkill<?>> tag) {
		return activeSkills(entity).stream()
				.anyMatch(runtime -> runtime.skill().is(tag));
	}

	/// 在服务端推进实体全部技能运行实例一个 tick。
	/// 客户端或尚未创建运行态附件的实体会被直接忽略。该方法由统一实体 tick 事件调用，
	/// 普通业务代码不应额外调用，以免同一游戏 tick 重复推进。
	///
	/// @param entity 要推进技能状态机的实体
	public static void tick(LivingEntity entity) {
		if (entity.level().isClientSide()) {
			return;
		}
		if (!entity.hasData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS)) {
			return;
		}
		for (EntitySkillRuntime<?> runtime : List.copyOf(activeSkills(entity))) {
			tickRuntime(entity, runtime);
		}
		expireMultiStageSequences(entity);
	}

	/// 能力实现使用的多段技能状态入口，不保存第二份实体数据。
	public static MultiStageSkill.Sequence multiStageSequence(LivingEntity entity, MultiStageSkill<?> skill) {
		return entity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS).multiStageSequences
				.computeIfAbsent(skill, ignored -> new MultiStageSkill.Sequence());
	}

	private static void expireMultiStageSequences(LivingEntity entity) {
		if (entity.level().isClientSide() || !entity.hasData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS)) return;
		var sequences = entity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS).multiStageSequences;
		for (var entry : List.copyOf(sequences.entrySet())) {
			EntitySkillRuntime<?> runtime = entry.getValue().pendingRuntime();
			if (runtime != null && entity.level().getGameTime() >= entry.getValue().expiresAt()
					&& !isCasting(entity, entry.getKey())) {
				entry.getKey().expireSequence(runtime);
				startCooldown(runtime, runtime.cooldownTicks());
			}
		}
	}

	private static void tickRuntime(LivingEntity entity, EntitySkillRuntime<?> runtime) {
		if (runtime.owner() != entity) {
			activeSkills(entity).remove(runtime);
			return;
		}
		applyMovementLocks(entity, runtime.skill());
		runtime.incrementElapsedTicks();
		switch (runtime.state()) {
			case WINDUP -> {
				runtime.onWindupTick();
				if (!isCurrent(runtime)) {
					return;
				}
				if (runtime.state() != EntitySkillRuntime.SkillState.WINDUP) {
					return;
				}
				runtime.setTicksLeft(runtime.ticksLeft() - 1);
				if (runtime.ticksLeft() <= 0) {
					activate(runtime);
					if (isCurrent(runtime)) {
						advanceImmediateStates(runtime);
					}
				}
			}
			case ACTIVE -> {
				runtime.incrementActiveTicks();
				runtime.onTick();
				if (!isCurrent(runtime)) {
					return;
				}
				if (runtime.state() != EntitySkillRuntime.SkillState.ACTIVE) {
					return;
				}
				if (runtime.ticksLeft() == -1) {
					return;
				}
				runtime.setTicksLeft(runtime.ticksLeft() - 1);
				if (runtime.ticksLeft() <= 0) {
					endRuntime(runtime);
				}
			}
			case RECOVERY -> {
				runtime.setTicksLeft(runtime.ticksLeft() - 1);
				if (runtime.ticksLeft() <= 0) {
					finish(runtime);
				}
			}
		}
	}

	private static void activate(EntitySkillRuntime<?> runtime) {
		runtime.setState(EntitySkillRuntime.SkillState.ACTIVE);
		runtime.setTicksLeft(runtime.skill().durationTicks());
		runtime.onActivate();
		if (isCurrent(runtime) && runtime.state() == EntitySkillRuntime.SkillState.ACTIVE) {
			postServer(new EntitySkillEvent.Activated(runtime));
		}
	}

	private static void enterRecovery(EntitySkillRuntime<?> runtime) {
		runtime.setState(EntitySkillRuntime.SkillState.RECOVERY);
		runtime.setTicksLeft(runtime.skill().recoveryTicks());
		if (isCurrent(runtime) && runtime.state() == EntitySkillRuntime.SkillState.RECOVERY) {
			EntitySkillSyncPayload.send(runtime, EntitySkillSyncPayload.Callback.ENTER_RECOVERY);
			postServer(new EntitySkillEvent.RecoveryStarted(runtime));
		}
	}

	private static void advanceImmediateStates(EntitySkillRuntime<?> runtime) {
		if (!isCurrent(runtime)) {
			return;
		}
		if (runtime.state() == EntitySkillRuntime.SkillState.WINDUP && runtime.ticksLeft() <= 0) {
			activate(runtime);
		}
		if (!isCurrent(runtime)) {
			return;
		}
		if (runtime.state() == EntitySkillRuntime.SkillState.ACTIVE && runtime.ticksLeft() == 0) {
			runtime.onEnd();
			if (isCurrent(runtime)) {
				enterRecovery(runtime);
			}
		}
		if (isCurrent(runtime) && runtime.state() == EntitySkillRuntime.SkillState.RECOVERY && runtime.ticksLeft() <= 0) {
			finish(runtime);
		}
	}

	private static void finish(EntitySkillRuntime<?> runtime) {
		runtime.onRecoveryEnd();
		if (!isCurrent(runtime)) {
			return;
		}
		if (runtime.skill() instanceof MultiStageSkill<?> multiStage) multiStage.completeStage(runtime);
		EntitySkillEffectManager.removeForSkillRuntime(runtime);
		HitboxManager.removeForSkillRuntime(runtime);
		LivingEntity owner = runtime.owner();
		if (runtime.hasCooldownTicksOverride()) {
			startCooldown(runtime, runtime.cooldownTicks());
		}
		activeSkills(owner).remove(runtime);
		postServer(new EntitySkillEvent.Completed(runtime));
	}

	private static void postServer(EntitySkillEvent event) {
		if (!event.getEntity().level().isClientSide()) {
			NeoForge.EVENT_BUS.post(event);
		}
	}

	private static boolean isCurrent(EntitySkillRuntime<?> runtime) {
		return activeSkills(runtime.owner()).contains(runtime);
	}

	private static void applyMovementLocks(LivingEntity entity, IEntitySkill<?> skill) {
		if (skill.locksNavigation() && entity instanceof Mob mob) {
			mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			mob.getNavigation().stop();
		}
		if (skill.locksMovement()) {
			Vec3 movement = entity.getDeltaMovement();
			entity.setDeltaMovement(0.0, movement.y, 0.0);
		}
	}

	/// 设置或清除技能冷却。
	/// 正数以当前世界游戏时间为基准写入冷却结束时间；零或负数会移除现有冷却。
	/// 冷却附件会按附件配置持久化并同步。
	///
	/// @param entity 技能拥有者
	/// @param skill  要修改冷却的技能
	/// @param ticks  从当前时刻开始计算的冷却 tick；小于等于零表示清除
	public static void setCooldown(LivingEntity entity, IEntitySkill<?> skill, int ticks) {
		Map<IEntitySkill<?>, Long> cooldowns = new LinkedHashMap<>(entity.getData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS));
		if (ticks <= 0) {
			cooldowns.remove(skill);
		} else {
			cooldowns.put(skill, entity.level().getGameTime() + ticks);
		}
		entity.setData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS, Map.copyOf(cooldowns));
	}

	/// 为多个技能设置或清除相同长度的冷却。
	/// 正数设置从当前游戏时间开始的冷却，零或负数清除对应技能的现有冷却。
	///
	/// @param entity 技能拥有者
	/// @param ticks  从当前时刻开始计算的冷却 tick；小于等于零表示清除
	/// @param skills 要修改冷却的技能
	public static void setCooldowns(LivingEntity entity, int ticks, IEntitySkill<?>... skills) {
		for (IEntitySkill<?> skill : skills) {
			setCooldown(entity, skill, ticks);
		}
	}

	private static void startCooldown(EntitySkillRuntime<?> runtime, int cooldownTicks) {
		AttributeInstance cooldownMultiplier = runtime.owner().getAttribute(
				LcAttributes.ENTITY_SKILL_COOLDOWN_MULTIPLIER);
		if (cooldownMultiplier != null) {
			cooldownTicks = (int) Math.ceil(cooldownTicks * cooldownMultiplier.getValue());
		}
		setCooldown(runtime.owner(), runtime.skill(), cooldownTicks);
	}

	/// 获取实体当前攻击段数。该值不持久化，但会同步给追踪实体的客户端。
	///
	/// @param entity 要查询的实体
	/// @return 当前攻击段数；附件尚未创建时返回默认值
	public static int getAttackCombo(LivingEntity entity) {
		return entity.getData(LcAttachmentTypes.ATTACK_COMBO);
	}

	/// 设置实体当前攻击段数，并触发附件同步。
	///
	/// @param entity 要修改的实体
	/// @param combo  新的攻击段数；具体有效范围由使用该值的技能定义
	public static void setAttackCombo(LivingEntity entity, int combo) {
		entity.setData(LcAttachmentTypes.ATTACK_COMBO, combo);
	}

	/// 清除实体在移除、克隆或复活后不应保留的技能临时状态。
	/// 该操作会强制取消全部运行实例，并清空运行态、全部冷却和攻击段数；
	/// 实体已经拥有的技能和技能组配置不会被移除。
	///
	/// @param entity 要清理临时技能状态的实体
	public static void clearTemporaryState(LivingEntity entity) {
		cancelAll(entity, true);
		activeSkills(entity).clear();
		entity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS).multiStageSequences.clear();
		entity.setData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS, Map.of());
		entity.setData(LcAttachmentTypes.ATTACK_COMBO, 0);
	}

	/// 清理卸载、换维度或死亡时的非持久化运行状态，但保留技能所有权和冷却附件。
	public static void clearRuntimeState(LivingEntity entity) {
		cancelAll(entity, true);
		activeSkills(entity).clear();
		entity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS).multiStageSequences.clear();
		entity.setData(LcAttachmentTypes.ATTACK_COMBO, 0);
	}

	private static List<EntitySkillRuntime<?>> activeSkills(LivingEntity entity) {
		return entity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS).active;
	}

	private static void ensureDefaultGroups(LivingEntity entity) {
		Map<EntitySkillGroup, Integer> groups = new LinkedHashMap<>(entity.getData(LcAttachmentTypes.ENTITY_SKILL_GROUPS));
		boolean changed = groups.putIfAbsent(LcEntitySkillGroups.CURRENT.get(), LcEntitySkillGroups.CURRENT.get().defaultMaximumActiveSkills()) == null;
		changed |= groups.putIfAbsent(LcEntitySkillGroups.PASSIVE.get(), LcEntitySkillGroups.PASSIVE.get().defaultMaximumActiveSkills()) == null;
		if (changed) {
			entity.setData(LcAttachmentTypes.ENTITY_SKILL_GROUPS, Map.copyOf(groups));
		}
	}

	private static boolean isDefaultGroup(EntitySkillGroup group) {
		return group == LcEntitySkillGroups.CURRENT.get() || group == LcEntitySkillGroups.PASSIVE.get();
	}

	private static void trimGroup(LivingEntity entity, EntitySkillGroup group, int maximum) {
		while (activeSkills(entity).stream().filter(runtime -> runtime.skill().group() == group).count() > maximum) {
			EntitySkillRuntime<?> oldest = activeSkills(entity).stream()
					.filter(runtime -> runtime.skill().group() == group)
					.findFirst()
					.orElse(null);
			if (oldest == null) {
				return;
			}
			cancelRuntime(entity, oldest, true);
		}
	}

	private static void cancelGroup(LivingEntity entity, EntitySkillGroup group) {
		List.copyOf(activeSkills(entity)).stream()
				.filter(runtime -> runtime.skill().group() == group)
				.forEach(runtime -> cancelRuntime(entity, runtime, true));
	}

	/// 兼容旧调用方的能力入口。正常实体都会返回已注册的能力；能力不可用时回退到附件状态机，
	/// 使实体构造和第三方实体注册顺序不会丢失既有技能数据。
	public static EntitySkillAccess require(LivingEntity entity) {
		EntitySkillAccess access = EntitySkillAccess.get(entity);
		return access == null ? new EntitySkillAttachmentAccess(entity) : access;
	}

	/// 读取运行态附件，供同步载荷生成实体技能快照。
	public static EntitySkillRuntimeData activeRuntimeData(LivingEntity entity) {
		return entity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS);
	}
}
