package org.unitego.lobecorp.client.animation;

import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoLocator;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;

import java.util.function.BiConsumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LcAnimationLodCacheTest {
	private static class State implements GeoRenderState {
		private double age;
		private final Map<DataTicket<?>, Object> data = new HashMap<>();

		@Override
		public Map<DataTicket<?>, Object> getDataMap() {
			return data;
		}

		@Override
		public double getAnimatableAge() {
			return age;
		}
	}

	private static class Bone extends GeoBone {
		private Bone() {
			super(null, "bone", new GeoBone[0], new GeoLocator[0], 0, 0, 0, 0, 0, 0);
		}

		@Override
		public <R extends GeoRenderState> void render(RenderPassInfo<R> pass, PoseStack pose, VertexConsumer consumer, int light, int overlay, int color) {
		}
	}

	@Test
	public void cachedContributionDoesNotRestoreAnotherLayersAbsolutePose() {
		var cache = new LcAnimationLodCache();
		var request = new LcAnimationLodCache.Request(cache, 10, 0);
		var animation = Animation.generateWaitAnimation(100);
		var state = new State();
		var bone = new Bone();
		var snapshot = BoneSnapshot.create(bone).setRotation(2, 0, 0);
		var calls = new AtomicInteger();
		BiConsumer<ControllerState, BoneSnapshots> sample = (_, snapshots) -> {
			calls.incrementAndGet();
			var target = snapshots.get("bone").orElseThrow();
			target.setRotX(target.getRotX() + 3);
		};
		var point = AnimationPoint.createFor(animation, null, animation.loopType(), 0);
		var first = new ControllerState(point, null, -1, 0, true, null, state, new Reference2DoubleOpenHashMap<>());
		cache.apply(first, _ -> Optional.of(snapshot), request, sample);
		assertEquals(5, snapshot.getRotX());
		state.age = 1;
		var next = BoneSnapshot.create(bone).setRotation(10, 0, 0);
		cache.apply(first, name -> Optional.of(next), request, sample);
		assertEquals(13, next.getRotX());
		assertEquals(1, calls.get());
		state.age = 2;
		cache.apply(first, name -> Optional.of(BoneSnapshot.create(bone)), request, sample);
		assertEquals(2, calls.get());
	}

	@Test
	public void triggerRevisionForcesImmediateResampleInSameBucket() {
		var cache = new LcAnimationLodCache();
		var animation = Animation.generateWaitAnimation(100);
		var state = new State();
		var bone = new Bone();
		var point = AnimationPoint.createFor(animation, null, animation.loopType(), 0);
		var controller = new ControllerState(point, null, -1, 0, false, null, state, new Reference2DoubleOpenHashMap<>());
		var calls = new AtomicInteger();
		BiConsumer<ControllerState, BoneSnapshots> sample = (_, snapshots) -> {
			calls.incrementAndGet();
			snapshots.get("bone").orElseThrow().setRotX(2);
		};
		cache.apply(controller, name -> Optional.of(BoneSnapshot.create(bone)), new LcAnimationLodCache.Request(cache, 10, 0), sample);
		cache.apply(controller, name -> Optional.of(BoneSnapshot.create(bone)), new LcAnimationLodCache.Request(cache, 10, 1), sample);
		assertEquals(2, calls.get());
	}
}
