package org.unitego.lobecorp.util.animation;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.VisualQualityConfig;
import org.unitego.lobecorp.registry.animation.client.LcDataTickets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/// 复杂立方体模型只简化面片，不替换骨架、骨骼名称、动画或定位器。
public class LcModelLodUtil {
	private static final Map<Entity, State> STATES = new WeakHashMap<>();
	private static final LinkedHashMap<BakedGeoModel, Bake> CACHE = new LinkedHashMap<>(16, 0.75f, true);
	private static long bytes;
	private static int bakedThisFrame;
	/// 两档代理数组、映射与引用的保守预算，不含GeckoLib自己持有的原模型。
	private static final int BYTES_PER_CUBE = 256;

	private static class State {
		private int band;
		private boolean exempt;
	}

	private record Bake(int cubes, int minimum, double mediumSize, double farSize, Set<GeoCube> medium,
	                    Set<GeoCube> far) {
	}

	public static void setExempt(Entity entity, boolean exempt) {
		STATES.computeIfAbsent(entity, _ -> new State()).exempt = exempt;
	}

	/// 仅由本模组选择接入的实体渲染器调用；其他实体缺少该票据时保留完整模型。
	public static void capture(Entity entity, GeoRenderState renderState) {
		var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		int band = 0;
		if (id.getNamespace().equals(Lobecorp.NAMESPACE) && VisualQualityConfig.MODELS.get()
				&& entity.level() == Minecraft.getInstance().level && !VisualQualityConfig.MODEL_EXEMPT.get().contains(id.toString())) {
			var state = STATES.computeIfAbsent(entity, _ -> new State());
			if (!state.exempt) {
				var distance = Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceTo(entity.position());
				band = LcVisualQualityUtil.band(distance, state.band, VisualQualityConfig.MODEL_NEAR.get(),
						VisualQualityConfig.MODEL_FAR.get(), VisualQualityConfig.HYSTERESIS.get());
				state.band = band;
			}
		}
		renderState.addGeckolibData(LcDataTickets.MODEL_LOD_BAND, band);
	}

	public static boolean shouldRenderCube(BakedGeoModel model, GeoCube original, int band) {
		if (band == 0 || !VisualQualityConfig.MODELS.get()) {
			return true;
		}
		var minimum = VisualQualityConfig.MIN_CUBES.get();
		var mediumSize = VisualQualityConfig.MEDIUM_CUBE_DETAIL.get();
		var farSize = VisualQualityConfig.FAR_CUBE_DETAIL.get();
		var bake = CACHE.get(model);
		if (bake != null && (bake.minimum() != minimum || bake.mediumSize() != mediumSize || bake.farSize() != farSize)) {
			CACHE.remove(model);
			bytes -= (long) bake.cubes() * BYTES_PER_CUBE;
			bake = null;
		}
		if (bake == null) {
			if (bakedThisFrame >= VisualQualityConfig.MAX_BAKES.get()) {
				return true;
			}
			bakedThisFrame++;
			var cubes = new ArrayList<GeoCube>();
			for (var bone : model.topLevelBones()) {
				collect(bone, cubes);
			}
			var estimate = (long) cubes.size() * BYTES_PER_CUBE;
			if (estimate > ((long) VisualQualityConfig.CACHE_MIB.get() * 1024 * 1024)) {
				return true;
			}
			while (!CACHE.isEmpty() && (CACHE.size() >= VisualQualityConfig.CACHE_ENTRIES.get() || bytes + estimate > ((long) VisualQualityConfig.CACHE_MIB.get() * 1024 * 1024))) {
				var iterator = CACHE.entrySet().iterator();
				bytes -= (long) iterator.next().getValue().cubes() * BYTES_PER_CUBE;
				iterator.remove();
			}
			Set<GeoCube> medium = Collections.newSetFromMap(new IdentityHashMap<>());
			Set<GeoCube> far = Collections.newSetFromMap(new IdentityHashMap<>());
			if (cubes.size() >= minimum) {
				for (var bone : model.topLevelBones()) {
					collectDetails(bone, mediumSize, medium);
					collectDetails(bone, farSize, far);
				}
			}
			bake = new Bake(cubes.size(), minimum, mediumSize, farSize, medium, far);
			CACHE.put(model, bake);
			bytes += estimate;
		}
		if (bake.cubes() < VisualQualityConfig.MIN_CUBES.get()) {
			return true;
		}
		return !(band == 1 ? bake.medium() : bake.far()).contains(original);
	}

	private static void collect(GeoBone bone, ArrayList<GeoCube> cubes) {
		if (bone instanceof CuboidGeoBone cuboid) {
			Collections.addAll(cubes, cuboid.cubes);
		}
		for (var child : bone.children()) {
			collect(child, cubes);
		}
	}

	private static void collectDetails(GeoBone bone, double threshold, Set<GeoCube> result) {
		if (bone instanceof CuboidGeoBone cuboid && bone.locators().length == 0) {
			double largest = 0;
			for (var cube : cuboid.cubes) {
				largest = Math.max(largest, detailSize(cube));
			}
			// 全部由小体素拼成的部件无法安全判断装饰与主体，保留完整部件。
			if (largest > threshold) {
				for (var cube : cuboid.cubes) {
					if (detailSize(cube) < threshold && detailSize(cube) < largest) {
						result.add(cube);
					}
				}
			}
		}
		for (var child : bone.children()) {
			collectDetails(child, threshold, result);
		}
	}

	private static double detailSize(GeoCube cube) {
		return Math.max(Math.abs(cube.size().x), Math.max(Math.abs(cube.size().y), Math.abs(cube.size().z)));
	}

	public static void endFrame() {
		bakedThisFrame = 0;
	}

	public static void clear() {
		STATES.clear();
		CACHE.clear();
		bytes = 0;
		bakedThisFrame = 0;
	}
}
