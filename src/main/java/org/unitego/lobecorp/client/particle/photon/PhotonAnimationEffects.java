package org.unitego.lobecorp.client.particle.photon;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.animation.keyframeevent.ParticleKeyframeData;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcAnimationEffectHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PhotonAnimationEffects implements LcAnimationEffectHooks.Sink {
	public static final PhotonAnimationEffects INSTANCE = new PhotonAnimationEffects();

	private final Map<Long, Map<String, List<ActiveEffect>>> effects = new HashMap<>();
	private final Map<GeoAnimatable, Long> instanceIds = new IdentityHashMap<>();

	private PhotonAnimationEffects() {
	}

	@Override
	public void particle(GeoAnimatable animatable, long instanceId, String layerName,
			ParticleKeyframeData keyframe, double speed) {
		if (!(animatable instanceof Entity entity) || keyframe.getLocatorName() == null) {
			return;
		}
		Identifier effectId = Identifier.tryParse(keyframe.getEffect());
		if (effectId == null) {
			Lobecorp.LOGGER.warn("Invalid Photon effect id in animation particle keyframe: {}", keyframe.getEffect());
			return;
		}
		FX fx = FXHelper.getFX(effectId);
		if (fx == null) {
			Lobecorp.LOGGER.warn("Unable to load Photon effect from animation particle keyframe: {}", effectId);
			return;
		}
		PhotonGeoAnchorExecutor executor = new PhotonGeoAnchorExecutor(fx, entity.level(), entity);
		PhotonAnimationEffectHandle handle = new PhotonAnimationEffectHandle(executor, speed);
		executor.setHandle(handle);
		instanceIds.put(animatable, instanceId);
		effects.computeIfAbsent(instanceId, ignored -> new HashMap<>())
				.computeIfAbsent(layerName, ignored -> new ArrayList<>())
				.add(new ActiveEffect(keyframe.getLocatorName(), executor, handle));
	}

	@Override
	public void play(GeoAnimatable animatable, String layerName) {
		end(animatable, layerName);
	}

	@Override
	public void pause(GeoAnimatable animatable, String layerName) {
		forEach(animatable, layerName, PhotonAnimationEffectHandle::pause);
	}

	@Override
	public void resume(GeoAnimatable animatable, String layerName, double speed) {
		forEach(animatable, layerName, handle -> {
			handle.setSpeed(speed);
			handle.resume();
		});
	}

	@Override
	public void speed(GeoAnimatable animatable, String layerName, double speed) {
		forEach(animatable, layerName, handle -> handle.setSpeed(speed));
	}

	@Override
	public void end(GeoAnimatable animatable, String layerName) {
		long instanceId = getInstanceId(animatable);
		Map<String, List<ActiveEffect>> byLayer = effects.get(instanceId);
		if (byLayer == null) {
			return;
		}
		List<ActiveEffect> removed = byLayer.remove(layerName);
		if (removed != null) {
			removed.forEach(effect -> effect.handle().stop());
		}
		if (byLayer.isEmpty()) {
			effects.remove(instanceId);
			instanceIds.remove(animatable);
		}
	}

	public List<PhotonAnimationEffectHandle> getHandles(GeoAnimatable animatable, String layerName) {
		Map<String, List<ActiveEffect>> byLayer = effects.get(getInstanceId(animatable));
		if (byLayer == null) {
			return List.of();
		}
		List<ActiveEffect> active = byLayer.get(layerName);
		if (active == null) {
			return List.of();
		}
		return active.stream().map(ActiveEffect::handle).toList();
	}

	List<ActiveEffect> getActiveEffects(long instanceId) {
		Map<String, List<ActiveEffect>> byLayer = effects.get(instanceId);
		if (byLayer == null) {
			return List.of();
		}
		List<ActiveEffect> active = new ArrayList<>();
		for (List<ActiveEffect> layerEffects : byLayer.values()) {
			layerEffects.removeIf(effect -> !effect.handle().isAlive());
			active.addAll(layerEffects);
		}
		byLayer.values().removeIf(List::isEmpty);
		if (byLayer.isEmpty()) {
			effects.remove(instanceId);
		}
		return active;
	}

	private void forEach(GeoAnimatable animatable, String layerName,
			java.util.function.Consumer<PhotonAnimationEffectHandle> operation) {
		for (PhotonAnimationEffectHandle handle : getHandles(animatable, layerName)) {
			operation.accept(handle);
		}
	}

	private long getInstanceId(GeoAnimatable animatable) {
		return instanceIds.getOrDefault(animatable, (long)animatable.hashCode());
	}

	record ActiveEffect(String anchorName, PhotonGeoAnchorExecutor executor,
			PhotonAnimationEffectHandle handle) {
	}
}
