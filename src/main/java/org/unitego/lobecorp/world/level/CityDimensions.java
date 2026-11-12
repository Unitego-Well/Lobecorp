package org.unitego.lobecorp.world.level;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.unitego.lobecorp.registry.world.LcLevels;

public class CityDimensions {

	public static boolean isProtected(LevelAccessor level) {
		return level instanceof Level world && world.dimension().equals(LcLevels.MIDNIGHT_CLEANUP);
	}
}
