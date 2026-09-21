package org.unitego.lobecorp.entity.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;

/// 将当前导航节点转换为地面或悬浮移动输入的持久控制器。
public class TheQueenOfHatredMoveControl extends MoveControl {
	/// 悬浮移动每 tick 允许的最大水平转向角。
	private static final float MAXIMUM_HOVER_YAW_CHANGE = 90.0F;
	/// 悬浮移动控制的性能分析区段。
	private static final String PROFILER_HOVER_MOVE_CONTROL = "queenHoverMoveControl";

	private final TheQueenOfHatred queen;
	private final TheQueenOfHatredHoverController hoverController;

	public TheQueenOfHatredMoveControl(TheQueenOfHatred queen) {
		super(queen);
		this.queen = queen;
		hoverController = new TheQueenOfHatredHoverController(queen);
	}

	@Override
	public void tick() {
		if (!queen.isHovering()) {
			hoverController.tick(Double.NaN, false);
			super.tick();
			return;
		}
		ProfilerFiller profiler = Profiler.get();
		profiler.push(PROFILER_HOVER_MOVE_CONTROL);
		queen.setNoGravity(false);
		queen.setXxa(0.0F);
		queen.setYya(0.0F);
		queen.setZza(0.0F);
		boolean moving = operation == Operation.MOVE_TO;
		double targetY = moving ? wantedY : Double.NaN;
		if (moving) {
			double offsetX = wantedX - queen.getX();
			double offsetY = wantedY - queen.getY();
			double offsetZ = wantedZ - queen.getZ();
			double distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
			if (distanceSquared >= MIN_SPEED_SQR) {
				double distance = Math.sqrt(distanceSquared);
				double horizontalDistance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
				float targetYaw = (float) (Mth.atan2(offsetZ, offsetX) * Mth.RAD_TO_DEG) - 90.0F;
				queen.setYRot(rotlerp(queen.getYRot(), targetYaw, MAXIMUM_HOVER_YAW_CHANGE));
				queen.setYBodyRot(queen.getYRot());
				float speed = (float) (speedModifier * queen.getAttributeValue(Attributes.FLYING_SPEED));
				queen.setSpeed(speed);
				queen.setZza((float) (horizontalDistance / distance));
				queen.setYya((float) (offsetY / distance));
			} else {
				setWait();
				moving = false;
			}
		}
		hoverController.tick(targetY, moving);
		profiler.pop();
	}

	public void stop() {
		setWait();
		hoverController.stop();
	}

	public int noProgressTicks() {
		return hoverController.noProgressTicks();
	}
}
