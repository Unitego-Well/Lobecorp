package org.unitego.lobecorp.entity.ai.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Brain、战斗控制器与女皇导航协调器之间传递的移动目的。
public record TheQueenOfHatredMovementIntent(Type type, Priority priority, @Nullable LivingEntity target,
		@Nullable Vec3 destination, double minimumDistance, double maximumDistance, int timeoutTicks) {
	/// 表示意图在完成或被覆盖前不按时间自动过期。
	public static final int NO_TIMEOUT = -1;

	public TheQueenOfHatredMovementIntent {
		if (minimumDistance < 0.0 || maximumDistance < minimumDistance) {
			throw new IllegalArgumentException("Invalid movement distance range");
		}
		if (timeoutTicks < NO_TIMEOUT) {
			throw new IllegalArgumentException("Invalid movement intent timeout");
		}
	}

	public static TheQueenOfHatredMovementIntent combatPosition(LivingEntity target,
			double minimumDistance, double maximumDistance) {
		return new TheQueenOfHatredMovementIntent(Type.COMBAT_POSITION, Priority.COMBAT_POSITION, target, null,
				minimumDistance, maximumDistance, NO_TIMEOUT);
	}

	public static TheQueenOfHatredMovementIntent idleStroll(Vec3 destination) {
		return new TheQueenOfHatredMovementIntent(Type.IDLE_STROLL, Priority.IDLE_STROLL, null, destination,
				0.0, 0.0, NO_TIMEOUT);
	}

	public static TheQueenOfHatredMovementIntent landing(boolean forced) {
		return landing(forced, null);
	}

	public static TheQueenOfHatredMovementIntent landing(boolean forced, @Nullable Vec3 destination) {
		return new TheQueenOfHatredMovementIntent(Type.LANDING,
				forced ? Priority.FORCED_LANDING : Priority.TRANSITION_LANDING,
				null, destination, 0.0, 0.0, NO_TIMEOUT);
	}

	public boolean hasTimeout() {
		return timeoutTicks != NO_TIMEOUT;
	}

	public enum Type {
		IDLE_STROLL,
		COMBAT_POSITION,
		THREE_DIMENSIONAL_PURSUIT,
		SKILL_REPOSITION,
		EMERGENCY_DODGE,
		HOLD,
		LANDING
	}

	/// 数值越大，越能覆盖当前移动意图。
	public enum Priority {
		IDLE_STROLL(0),
		TRANSITION_LANDING(1),
		COMBAT_POSITION(2),
		THREE_DIMENSIONAL_PURSUIT(3),
		SKILL_REPOSITION(4),
		EMERGENCY_DODGE(5),
		UNINTERRUPTIBLE_SKILL(6),
		FORCED_LANDING(7);

		private final int value;

		Priority(int value) {
			this.value = value;
		}

		public boolean canReplace(Priority other) {
			return value >= other.value;
		}
	}
}
