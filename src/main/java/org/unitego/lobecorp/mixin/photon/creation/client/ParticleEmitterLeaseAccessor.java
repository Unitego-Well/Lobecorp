package org.unitego.lobecorp.mixin.photon.creation.client;

import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRuntime;
import com.lowdragmc.photon.client.gameobject.forcefield.ForceFieldObject;
import com.lowdragmc.photon.client.gameobject.particle.SpawnFrame;
import com.lowdragmc.photon.client.gameobject.particle.renderer.AraTrailParticleRenderer;
import com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer;
import com.lowdragmc.photon.client.gameobject.particle.renderer.TrailParticleRenderer;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Queue;
import java.util.Map;
import com.lowdragmc.photon.client.collision.CollisionScene;
import com.lowdragmc.photon.client.collision.CollisionWorld;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.gen.Invoker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

/// 配置由主线恢复后丢弃旧 runtime，使下一次访问绑定新配置。
@Mixin(value = ParticleEmitter.class, remap = false)
public interface ParticleEmitterLeaseAccessor {
	@Accessor("runtime")
	void lobecorp$setLeaseRuntime(@Nullable ParticleRuntime value);

	@Accessor("cachedSpawnFrame")
	void lobecorp$setLeaseCachedSpawnFrame(@Nullable SpawnFrame value);

	@Accessor("cachedFrameEmitterToWorld")
	void lobecorp$setLeaseCachedFrameEmitterToWorld(@Nullable Matrix4f value);

	@Accessor("cachedFrameSimToWorld")
	void lobecorp$setLeaseCachedFrameSimToWorld(@Nullable Matrix4f value);

	@Accessor("activeForceFields")
	void lobecorp$setLeaseActiveForceFields(List<ForceFieldObject> value);

	@Accessor("extractRenderer")
	void lobecorp$setLeaseExtractRenderer(@Nullable TileParticleRenderer value);

	@Accessor("trailExtractRenderer")
	void lobecorp$setLeaseTrailExtractRenderer(@Nullable TrailParticleRenderer value);

	@Accessor("araTrailExtractRenderer")
	void lobecorp$setLeaseAraTrailExtractRenderer(@Nullable AraTrailParticleRenderer value);
	@Invoker("registerLights")
	void lobecorp$registerLeaseLights(@Nullable Level level);

	@Accessor("collisionScene")
	void lobecorp$setLeaseCollisionScene(@Nullable CollisionScene value);

	@Accessor("collisionWorld")
	void lobecorp$setLeaseCollisionWorld(@Nullable CollisionWorld value);

	@Accessor("deferredFailed")
	void lobecorp$setLeaseDeferredFailed(boolean value);

	@Accessor("collisionNanos")
	void lobecorp$setLeaseCollisionNanos(long value);

	@Accessor("collisionParallel")
	void lobecorp$setLeaseCollisionParallel(boolean value);

	@Accessor("lastCollisionMillis")
	void lobecorp$setLeaseLastCollisionMillis(double value);

	@Accessor("lastCollisionParallel")
	void lobecorp$setLeaseLastCollisionParallel(boolean value);

	@Accessor("contactLog")
	void lobecorp$setLeaseContactLog(@Nullable float[] value);

	@Accessor("simMotionX")
	void lobecorp$setLeaseSimMotionX(double value);

	@Accessor("simMotionY")
	void lobecorp$setLeaseSimMotionY(double value);

	@Accessor("simMotionZ")
	void lobecorp$setLeaseSimMotionZ(double value);

	@Accessor("targetMotionScale")
	void lobecorp$setLeaseTargetMotionScale(double value);

	@Accessor("collisionListeners")
	List<ParticleEmitter.CollisionListener> lobecorp$getLeaseCollisionListeners();

	@Accessor("pendingSubEmitterSpawns")
	Queue<?> lobecorp$getLeasePendingSubEmitterSpawns();

	@Accessor("pendingCollisionReports")
	Queue<?> lobecorp$getLeasePendingCollisionReports();

	@Accessor("dedupeVerdicts")
	Map<?, ?> lobecorp$getLeaseDedupeVerdicts();

	@Accessor("lastCollisionStats")
	long[] lobecorp$getLeaseLastCollisionStats();

}
