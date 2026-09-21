package org.unitego.lobecorp.entity.entity_skill;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.unitego.lobecorp.registry.entity_skill.LcEntitySkillGroups;

/// 由 {@link Properties} 集中提供基础配置的实体技能基类。
/// 构造后基础配置不可变，具体技能只需实现生命周期回调和额外业务条件。
public abstract class EntitySkill<T extends LivingEntity> implements IEntitySkill<T> {
	private final Identifier id;
	private final int windupTicks;
	private final int durationTicks;
	private final int recoveryTicks;
	private final int cooldownTicks;
	private final boolean locksNavigation;
	private final boolean locksMovement;
	private final EntitySkillGroup group;
	private final boolean mutuallyExclusive;
	private final boolean overridable;

	/// 使用完整的基础属性创建技能。
	/// @param properties 注册技能时构建的属性对象；调用方应至少设置唯一标识
	public EntitySkill(Properties properties) {
		this.id = properties.id;
		this.windupTicks = properties.windupTicks;
		this.durationTicks = properties.durationTicks;
		this.recoveryTicks = properties.recoveryTicks;
		this.cooldownTicks = properties.cooldownTicks;
		this.locksNavigation = properties.locksNavigation;
		this.locksMovement = properties.locksMovement;
        this.group = properties.group;
		this.mutuallyExclusive = properties.mutuallyExclusive;
		this.overridable = properties.overridable;
	}

	@Override
	public final Identifier id() {
		return id;
	}

	@Override
	public final int windupTicks() {
		return windupTicks;
	}

	@Override
	public final int durationTicks() {
		return durationTicks;
	}

	@Override
	public final int recoveryTicks() {
		return recoveryTicks;
	}

	@Override
	public final int cooldownTicks() {
		return cooldownTicks;
	}

	@Override
	public final boolean locksNavigation() {
		return locksNavigation;
	}

	@Override
	public final boolean locksMovement() {
		return locksMovement;
	}

    @Override
    public final EntitySkillGroup group() {
        return group == null ? LcEntitySkillGroups.CURRENT.get() : group;
    }

	@Override
	public final boolean mutuallyExclusive() {
		return mutuallyExclusive;
	}

	@Override
	public boolean canBeOverridden(T entity, EntitySkillRuntime<T> runtime, IEntitySkill<?> replacement) {
		return overridable;
	}

	/// 实体技能的链式基础属性配置。
	/// 每次注册应创建独立实例；构造出的 {@link EntitySkill} 会复制配置值，后续不再读取本对象。
	public static class Properties {
		private Identifier id;
		private int windupTicks;
		private int durationTicks;
		private int recoveryTicks;
		private int cooldownTicks;
		private boolean locksNavigation;
		private boolean locksMovement;
		private EntitySkillGroup group;
		private boolean mutuallyExclusive = true;
		private boolean overridable = true;

		/// 设置技能唯一注册标识。
		/// @param id 注册器分配的技能标识
		/// @return 当前属性对象
		public Properties id(Identifier id) {
			this.id = id;
			return this;
		}

		/// 设置技能进入持续阶段前的准备时长。
		/// @param windupTicks 前摇 tick 数
		/// @return 当前属性对象
		public Properties windupTicks(int windupTicks) {
			this.windupTicks = windupTicks;
			return this;
		}

		/// 设置技能持续阶段时长。
		/// @param durationTicks 持续 tick 数；{@code -1} 表示无限持续
		/// @return 当前属性对象
		public Properties durationTicks(int durationTicks) {
			this.durationTicks = durationTicks;
			return this;
		}

		/// 设置技能效果结束后的收招时长。
		/// @param recoveryTicks 后摇 tick 数
		/// @return 当前属性对象
		public Properties recoveryTicks(int recoveryTicks) {
			this.recoveryTicks = recoveryTicks;
			return this;
		}

		/// 设置技能结束或取消后写入的冷却时长。
		/// @param cooldownTicks 冷却 tick 数
		/// @return 当前属性对象
		public Properties cooldownTicks(int cooldownTicks) {
			this.cooldownTicks = cooldownTicks;
			return this;
		}

		/// 配置技能在全部运行阶段持续停止 {@code Mob} 导航。
		/// @return 当前属性对象
		public Properties locksNavigation() {
			this.locksNavigation = true;
			return this;
		}

		/// 配置技能在全部运行阶段清除实体水平位移，同时保留垂直位移。
		/// @return 当前属性对象
		public Properties locksMovement() {
			this.locksMovement = true;
			return this;
		}

		/// 设置技能运行分组，覆盖默认的当前执行组。
		/// @param group 已注册的技能组
		/// @return 当前属性对象
		public Properties group(EntitySkillGroup group) {
			this.group = group;
			return this;
		}

		/// 关闭默认的同技能互斥，使同一实体可以同时持有该技能的多个运行实例。
		/// 分组上限仍然会限制最终可同时运行的数量。
		/// @return 当前属性对象
		public Properties allowsConcurrentInstances() {
			this.mutuallyExclusive = false;
			return this;
		}

		/// 禁止该技能的运行实例在分组满载时被新技能覆盖。
		/// 主动取消、强制取消和实体临时状态清理不受此配置影响。
		/// @return 当前属性对象
		public Properties cannotBeOverridden() {
			this.overridable = false;
			return this;
		}
	}
}
