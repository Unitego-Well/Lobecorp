package org.unitego.lobecorp.world.horde;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/// 只存在于运行期的空间快照；截止时间不依赖目标集合的顺序和大小。
public class HordeTargetSpatialState {
	/// 玩家三维距离32格以内每tick更新，不要求玩家可见。
	public static final double NEAR_DISTANCE = 32;
	/// 其余已加载目标的空间快照和完整发现扫描最多相隔10tick。
	public static final int UPDATE_INTERVAL = 10;
	private final int phase;
	private long nextUpdate = Long.MIN_VALUE;
	private Vec3 position = Vec3.ZERO;
	private HordeCombatRegion region;

	public HordeTargetSpatialState(UUID target) {
		phase = Math.floorMod(target.hashCode(), UPDATE_INTERVAL);
	}


	public static boolean fullScanDue(long clock) {
		return Math.floorMod(clock, UPDATE_INTERVAL) == 0;
	}

	public static boolean near(double distanceSquared) {
		return distanceSquared <= NEAR_DISTANCE * NEAR_DISTANCE;
	}

	public boolean due(long clock, boolean near) {
		return near || region == null || clock >= nextUpdate;
	}

	public boolean refresh(long clock, Vec3 point, AABB bounds, double margin) {
		var nextRegion = HordeCombatRegion.around(bounds, margin);
		boolean changed = !point.equals(position) || !nextRegion.equals(region);
		position = point;
		region = nextRegion;
		nextUpdate = clock + 1 + Math.floorMod(phase - Math.floorMod(clock + 1, UPDATE_INTERVAL), UPDATE_INTERVAL);
		return changed;
	}

	public Vec3 position() {
		return position;
	}

	public HordeCombatRegion region() {
		return region;
	}
}
