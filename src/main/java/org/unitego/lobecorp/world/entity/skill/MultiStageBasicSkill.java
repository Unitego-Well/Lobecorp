package org.unitego.lobecorp.world.entity.skill;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.world.hitbox.HitboxInstance;
import org.unitego.lobecorp.world.hitbox.HitboxManager;
import org.unitego.lobecorp.util.TypedDataKey;

/// 多段普通攻击：AI 与手动施放均可在后摇中提前接下一段，空挥也可续段。
@NullMarked
public abstract class MultiStageBasicSkill<T extends LivingEntity> extends MultiStageSkill<T> {
	/// 普通攻击上一段后摇结束后的默认续段窗口，单位为游戏刻。
	public static final int DEFAULT_CONTINUATION_WINDOW_TICKS = 10;
	/// 手动方向施放标记，由具体普攻的指挥家施放条件设置。
	protected static final TypedDataKey<Boolean> DIRECTION_CAST = TypedDataKey.create();
	/// 本次普通攻击的判断框编号，完成和取消时使用同一入口清理。
	protected static final TypedDataKey<Integer> HITBOX_ID = TypedDataKey.create();

	public MultiStageBasicSkill(Properties properties, int stageCount, int sequenceCooldownTicks) {
		this(properties, stageCount, DEFAULT_CONTINUATION_WINDOW_TICKS, sequenceCooldownTicks);
	}

	public MultiStageBasicSkill(Properties properties, int stageCount, int continuationWindowTicks, int sequenceCooldownTicks) {
		super(properties, stageCount, continuationWindowTicks, sequenceCooldownTicks);
	}

	@Override
	public boolean isBasicAttack() {
		return true;
	}

	@Override
	public boolean isInterruptibleBySkill(T entity, EntitySkillRuntime<T> runtime) {
		return runtime.state() == EntitySkillRuntime.SkillState.RECOVERY || super.isInterruptibleBySkill(entity, runtime);
	}

	@Override
	public boolean canBeOverridden(T entity, EntitySkillRuntime<T> runtime, IEntitySkill<?> replacement) {
		return super.canBeOverridden(entity, runtime, replacement)
				&& (replacement != this || runtime.sequenceStage() + 1 < stageCount());
	}

	@Nullable
	protected HitboxInstance getHitbox(ServerLevel level, EntitySkillRuntime<T> runtime) {
		Integer id = runtime.getData(HITBOX_ID);
		return id == null ? null : HitboxManager.get(level, id);
	}

	protected void removeHitbox(T owner, EntitySkillRuntime<T> runtime) {
		Integer id = runtime.removeData(HITBOX_ID);
		if (id != null && owner.level() instanceof ServerLevel level) {
			HitboxManager.remove(level, id);
		}
	}
}
