package org.unitego.lobecorp.event;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

/// 技能运行实例的生命周期事件。客户端事件中的运行实例是同步消息构造的当前阶段快照。
public abstract class EntitySkillEvent extends Event {
	private final EntitySkillRuntime<?> runtime;

	protected EntitySkillEvent(EntitySkillRuntime<?> runtime) {
		this.runtime = runtime;
	}

	public EntitySkillRuntime<?> getRuntime() {
		return runtime;
	}

	public LivingEntity getEntity() {
		return runtime.owner();
	}

	public IEntitySkill<?> getSkill() {
		return runtime.skill();
	}

	/// 服务端完成施放条件检查后、覆盖旧技能前发布；取消后本次施放不会开始。
	public static class Cast extends EntitySkillEvent implements ICancellableEvent {
		public Cast(EntitySkillRuntime<?> runtime) {
			super(runtime);
		}
	}

	public static class Started extends EntitySkillEvent {
		public Started(EntitySkillRuntime<?> runtime) {
			super(runtime);
		}
	}

	public static class Activated extends EntitySkillEvent {
		public Activated(EntitySkillRuntime<?> runtime) {
			super(runtime);
		}
	}

	public static class RecoveryStarted extends EntitySkillEvent {
		public RecoveryStarted(EntitySkillRuntime<?> runtime) {
			super(runtime);
		}
	}

	public static class Completed extends EntitySkillEvent {
		public Completed(EntitySkillRuntime<?> runtime) {
			super(runtime);
		}
	}

	public static class Cancelled extends EntitySkillEvent {
		public Cancelled(EntitySkillRuntime<?> runtime) {
			super(runtime);
		}
	}
}
