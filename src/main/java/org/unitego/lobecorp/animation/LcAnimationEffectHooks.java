package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.state.KeyFrameEvent;
import com.geckolib.cache.animation.keyframeevent.ParticleKeyframeData;
import com.geckolib.constant.DataTickets;

public final class LcAnimationEffectHooks {
	private static Sink sink = Sink.NONE;

	private LcAnimationEffectHooks() {
	}

	public static void setSink(Sink sink) {
		LcAnimationEffectHooks.sink = sink;
	}

	static <T extends GeoAnimatable> void particle(String layerName,
			KeyFrameEvent<T, ParticleKeyframeData> event, double speed) {
		long instanceId = event.renderState().getOrDefaultGeckolibData(
				DataTickets.ANIMATABLE_INSTANCE_ID, (long)event.animatable().hashCode());
		sink.particle(event.animatable(), instanceId,
				layerName, event.keyframeData(), speed);
	}

	static void play(GeoAnimatable animatable, String layerName) {
		sink.play(animatable, layerName);
	}

	static void pause(GeoAnimatable animatable, String layerName) {
		sink.pause(animatable, layerName);
	}

	static void resume(GeoAnimatable animatable, String layerName, double speed) {
		sink.resume(animatable, layerName, speed);
	}

	static void speed(GeoAnimatable animatable, String layerName, double speed) {
		sink.speed(animatable, layerName, speed);
	}

	static void end(GeoAnimatable animatable, String layerName) {
		sink.end(animatable, layerName);
	}

	public interface Sink {
		Sink NONE = new Sink() {
		};

		default void particle(GeoAnimatable animatable, long instanceId, String layerName,
				ParticleKeyframeData keyframe, double speed) {
		}

		default void play(GeoAnimatable animatable, String layerName) {
		}

		default void pause(GeoAnimatable animatable, String layerName) {
		}

		default void resume(GeoAnimatable animatable, String layerName, double speed) {
		}

		default void speed(GeoAnimatable animatable, String layerName, double speed) {
		}

		default void end(GeoAnimatable animatable, String layerName) {
		}
	}
}
