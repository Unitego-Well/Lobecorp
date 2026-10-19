package org.unitego.lobecorp.mixin.geckolib.client;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.loading.math.MolangQueries;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcAnimationLodAccess;
import org.unitego.lobecorp.animation.LcAnimationControllerTransitions;
import org.unitego.lobecorp.client.animation.LcAnimationLodCache;
import org.unitego.lobecorp.client.animation.LcAnimationLodStateAccess;
import org.unitego.lobecorp.config.client.VisualQualityConfig;
import org.unitego.lobecorp.util.animation.LcVisualQualityUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffects;

/// 动画切换或重启时结束本控制器旧的关键帧效果，保留代码管理的实例。
@Mixin(AnimationController.class)
public abstract class AnimationControllerMixin<T extends GeoAnimatable> implements LcAnimationLodAccess {
	@Unique
	private boolean lobecorp$distanceLod;
	@Unique
	private LcAnimationLodCache lobecorp$poseCache;
	@Unique
	private int lobecorp$sampleBand;

	@Override
	public void lobecorp$setDistanceLod(boolean enabled) {
		lobecorp$distanceLod = enabled;
		lobecorp$poseCache = null;
	}

	@ModifyReturnValue(method = "extractControllerState", at = @At("RETURN"))
	private ControllerState lobecorp$distanceSampling(ControllerState result, T animatable, GeoRenderState renderState,
	                                                  AnimatableManager<T> manager, MolangQueries.Actor<T> actor, GeoModel<T> model) {
		if (result == null || !lobecorp$distanceLod || !VisualQualityConfig.ANIMATIONS.get()
				|| !(animatable instanceof Entity entity) || entity.level() != Minecraft.getInstance().level) {
			return result;
		}
		var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		var controller = (AnimationController<?>) (Object) this;
		if (!id.getNamespace().equals(Lobecorp.NAMESPACE) || VisualQualityConfig.ANIMATION_EXEMPT.get().contains(id.toString())
				|| VisualQualityConfig.CONTROLLER_EXEMPT.get().contains(controller.getName())) {
			lobecorp$poseCache = null;
			return result;
		}
		var distance = Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceTo(entity.position());
		lobecorp$sampleBand = LcVisualQualityUtil.band(distance, lobecorp$sampleBand, VisualQualityConfig.ANIMATION_NEAR.get(),
				VisualQualityConfig.ANIMATION_FAR.get(), VisualQualityConfig.HYSTERESIS.get());
		if (lobecorp$sampleBand == 0) {
			lobecorp$poseCache = null;
			return result;
		}
		if (lobecorp$poseCache == null) {
			lobecorp$poseCache = new LcAnimationLodCache();
		}
		var fps = lobecorp$sampleBand == 1 ? VisualQualityConfig.MEDIUM_FPS.get() : VisualQualityConfig.FAR_FPS.get();
		var revision = ((LcAnimationControllerTransitions<?>) controller).lobecorp$getAnimationTriggerRevision();
		((LcAnimationLodStateAccess) (Object) result).lobecorp$setLodRequest(new LcAnimationLodCache.Request(lobecorp$poseCache, fps, revision));
		return result;
	}

	@Inject(method = "initializeNewAnimation", at = @At("HEAD"))
	private void lobecorp$stopPreviousPhotonEffects(T animatable, GeoRenderState renderState, GeoModel<T> geoModel,
	                                                double prevAnimSpeed, int prevTransitionTicks, CallbackInfo ci) {
		PhotonGeoEffects.stopKeyframes(renderState, (AnimationController<?>) (Object) this);
	}
}
