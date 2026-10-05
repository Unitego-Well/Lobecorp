package org.unitego.lobecorp.world.entity.skill;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.util.TypedDataKey;

/// 单次施放的手动控制状态；资格、实际接管与恢复生物目标分别处理，不改变技能生命周期。
public class EntitySkillManualControl {
	/// 仅在手动施放成功后建立，随运行实例一起清理，不持久化或另行同步。
	private static final TypedDataKey<EntitySkillManualControl> CONTROL = TypedDataKey.create();
	private final EntitySkillRuntime<?> runtime;
	private Mode mode = Mode.READY;
	private @Nullable Vec3 frozenDirection;

	public EntitySkillManualControl(EntitySkillRuntime<?> runtime) {
		this.runtime = runtime;
	}

	public static void prepare(EntitySkillRuntime<?> runtime) {
		runtime.setData(CONTROL, new EntitySkillManualControl(runtime));
	}

	public static @Nullable EntitySkillManualControl get(EntitySkillRuntime<?> runtime) {
		return runtime.getData(CONTROL);
	}

	public void update(@Nullable LivingEntity target, Vec3 position, boolean following) {
		if (!following && mode != Mode.MANUAL)
			return;
		mode = Mode.MANUAL;
		runtime.setTarget(target);
		runtime.setTargetPosition(position);
		frozenDirection = following ? null : runtime.owner().getLookAngle();
	}

	/// 退出选择后持续读取生物当前目标；没有有效目标时保持当前朝向。
	public void release() {
		mode = Mode.AUTOMATIC;
		frozenDirection = null;
		runtime.setTarget(null);
		runtime.setTargetPosition(null);
	}

	public @Nullable Vec3 direction() {
		if (mode == Mode.AUTOMATIC && runtime.owner() instanceof Mob mob) {
			LivingEntity target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			runtime.setTarget(target != null && target.isAlive() ? target : null);
			runtime.setTargetPosition(null);
			if (target == null || !target.isAlive())
				return mob.getLookAngle();
		}
		return frozenDirection;
	}

	private enum Mode {
		READY,
		MANUAL,
		AUTOMATIC
	}
}
