package org.unitego.lobecorp.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.level.CityDimensions;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class CityDimensionEvents {
	@SubscribeEvent
	public static void onPlace(BlockEvent.EntityPlaceEvent event) {
		// 父事件同时覆盖多方块放置，不再重复订阅EntityMultiPlaceEvent。
		if (CityDimensions.isProtected(event.getLevel())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
		if (CityDimensions.isProtected(event.getLevel())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
		if (CityDimensions.isProtected(event.getLevel())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onExplosion(ExplosionEvent.Detonate event) {
		if (CityDimensions.isProtected(event.getLevel())) {
			event.getAffectedBlocks().clear();
		}
	}

	@SubscribeEvent
	public static void onMobGriefing(EntityMobGriefingEvent event) {
		if (CityDimensions.isProtected(event.getEntity().level())) {
			event.setCanGrief(false);
		}
	}

	@SubscribeEvent
	public static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
		if (CityDimensions.isProtected(event.getLevel().getLevel())) {
			event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
		}
	}

	@SubscribeEvent
	public static void onSpawnPosition(MobSpawnEvent.PositionCheck event) {
		if (CityDimensions.isProtected(event.getLevel().getLevel())) {
			event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
		}
	}
}
