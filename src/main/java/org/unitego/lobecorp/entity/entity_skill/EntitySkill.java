package org.unitego.lobecorp.entity.entity_skill;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

public abstract class EntitySkill<T extends Mob> implements IEntitySkill<T> {
	private final Identifier id;
	private final int windupTicks;
	private final int durationTicks;
	private final int recoveryTicks;
	private final int cooldownTicks;
	private final boolean locksNavigation;
	private final boolean locksMovement;

	public EntitySkill(Properties properties) {
		this.id = properties.id;
		this.windupTicks = properties.windupTicks;
		this.durationTicks = properties.durationTicks;
		this.recoveryTicks = properties.recoveryTicks;
		this.cooldownTicks = properties.cooldownTicks;
		this.locksNavigation = properties.locksNavigation;
		this.locksMovement = properties.locksMovement;
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

	/// 实体技能的基础属性配置。
	public static class Properties {
		private Identifier id;
		private int windupTicks;
		private int durationTicks;
		private int recoveryTicks;
		private int cooldownTicks;
		private boolean locksNavigation;
		private boolean locksMovement;

		/// 设置技能唯一标识。
		public Properties id(Identifier id) {
			this.id = id;
			return this;
		}

		/// 设置技能前摇时长。
		public Properties windupTicks(int windupTicks) {
			this.windupTicks = windupTicks;
			return this;
		}

		/// 设置技能持续时长。
		public Properties durationTicks(int durationTicks) {
			this.durationTicks = durationTicks;
			return this;
		}

		/// 设置技能后摇时长。
		public Properties recoveryTicks(int recoveryTicks) {
			this.recoveryTicks = recoveryTicks;
			return this;
		}

		/// 设置技能冷却时长。
		public Properties cooldownTicks(int cooldownTicks) {
			this.cooldownTicks = cooldownTicks;
			return this;
		}

		/// 禁止技能运行期间的实体 AI 寻路。
		public Properties locksNavigation() {
			this.locksNavigation = true;
			return this;
		}

		/// 禁止技能运行期间的实体水平位移，保留重力产生的垂直位移。
		public Properties locksMovement() {
			this.locksMovement = true;
			return this;
		}
	}
}
