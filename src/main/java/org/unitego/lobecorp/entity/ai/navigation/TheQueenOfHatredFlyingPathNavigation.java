package org.unitego.lobecorp.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;

/// 为憎恶女皇保留目标高度的三维路径导航。
public class TheQueenOfHatredFlyingPathNavigation extends FlyingPathNavigation {
	public TheQueenOfHatredFlyingPathNavigation(TheQueenOfHatred queen, Level level) {
		super(queen, level);
		setCanFloat(true);
	}

	@Override
	public boolean isStableDestination(BlockPos position) {
		Vec3 destination = Vec3.atBottomCenterOf(position);
		return level.noCollision(mob, mob.getBoundingBox().move(destination.subtract(mob.position())));
	}
}
