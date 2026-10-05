package org.unitego.lobecorp.client.photon.runtime;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.KeyFrameEvent;
import com.geckolib.cache.animation.keyframeevent.ParticleKeyframeData;
import com.geckolib.cache.model.GeoLocator;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.util.RenderUtil;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.mesdag.particlestorm.api.geckolib.WithCurrentEntity;
import org.unitego.lobecorp.Lobecorp;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 关键帧与代码入口共用的客户端 locator 绑定；ParticleStorm 继续处理其自身标记。
 */
public class PhotonGeoEffects {
	/**
	 * GeckoLib effect 字段的分流前缀，后接完整 Photon 资源 ID。
	 */
	public static final String KEYFRAME_PREFIX = "photon:";
	/**
	 * 资源缺失时只在请求播放时报告，不在每帧重试。
	 */
	private static final String MISSING_FX = "Unable to load Photon GeckoLib effect: {}";
	/**
	 * 无效标记不交给 ParticleStorm 二次处理。
	 */
	private static final String INVALID_FX = "Invalid Photon GeckoLib keyframe effect: {}";
	/**
	 * 模型中没有对应 locator 时结束该实例，避免长期等待或错误位置发射。
	 */
	private static final String MISSING_LOCATOR = "Unable to bind Photon effect to GeckoLib locator: {}";
	private static final Map<AnimatableManager<?>, List<Binding>> BINDINGS = new IdentityHashMap<>();

	public static <T extends Entity & GeoAnimatable> Optional<PhotonGeoEffect> play(
			T entity, Identifier effectId, String locator) {
		return play(entity, entity.getId(), entity.level(), entity.position(), effectId, locator);
	}

	/**
	 * instanceId 必须与 renderer.getInstanceId 使用相同值；所有调用都在客户端线程执行。
	 */
	public static Optional<PhotonGeoEffect> play(GeoAnimatable animatable, long instanceId, Level level,
	                                             Vec3 position, Identifier effectId, String locator) {
		if (!level.isClientSide() || Minecraft.getInstance().level != level) {
			return Optional.empty();
		}
		return create(animatable.getAnimatableInstanceCache().getManagerForId(instanceId),
				animatable, level, position, effectId, locator, null);
	}

	/**
	 * 返回 true 表示 Photon 标记已被消费，包括资源缺失及上下文不可用的情况。
	 */
	public static boolean processKeyframe(KeyFrameEvent<? extends GeoAnimatable, ParticleKeyframeData> event) {
		String name = event.keyframeData().getEffect();
		if (!name.startsWith(KEYFRAME_PREFIX)) {
			return false;
		}
		Identifier id = Identifier.tryParse(name.substring(KEYFRAME_PREFIX.length()));
		if (id == null) {
			Lobecorp.LOGGER.warn(INVALID_FX, name);
			return true;
		}
		GeoRenderState state = event.renderState();
		AnimatableManager<?> manager = state.getGeckolibData(DataTickets.ANIMATABLE_MANAGER);
		Level level = Minecraft.getInstance().level;
		if (manager == null || level == null) {
			return true;
		}
		Object source = event.animatable();
		if (source instanceof WithCurrentEntity replaced && replaced.getCurrentEntity() != null) {
			source = replaced.getCurrentEntity();
		}
		Vec3 position = state.getOrDefaultGeckolibData(DataTickets.POSITION,
				source instanceof Entity entity ? entity.position() : Vec3.ZERO);
		String locator = event.keyframeData().getLocatorName();
		List<Binding> bindings = BINDINGS.get(manager);
		if (bindings != null) {
			for (Binding binding : bindings) {
				if (binding.controller() == event.controller() && binding.id().equals(id)
						&& binding.effect().locator().equals(locator == null ? "" : locator)
						&& !binding.effect().isStopped() && binding.effect().isAlive()) {
					return true;
				}
			}
		}
		create(manager, source, level, position, id, locator, event.controller());
		return true;
	}

	private static Optional<PhotonGeoEffect> create(AnimatableManager<?> manager, Object source, Level level,
	                                                Vec3 position, Identifier id, @Nullable String locator, @Nullable AnimationController<?> controller) {
		FX fx = FXHelper.getFX(id);
		if (fx == null) {
			Lobecorp.LOGGER.warn(MISSING_FX, id);
			return Optional.empty();
		}
		PhotonGeoEffect effect = new PhotonGeoEffect(fx, level, source, locator == null ? "" : locator, position);
		BINDINGS.computeIfAbsent(manager, ignored -> new ArrayList<>())
				.add(new Binding(id, effect, controller, controller == null ? null : controller.getCurrentRawAnimation()));
		if (effect.locator().isBlank()) {
			effect.start();
		}
		return Optional.of(effect);
	}

	/**
	 * 在本帧动画采样完成后注册监听，使同帧产生的关键帧效果也能取得最终姿态。
	 */
	public static void attachLocators(RenderPassInfo<?> pass) {
		AnimatableManager<?> manager = pass.renderState().getGeckolibData(DataTickets.ANIMATABLE_MANAGER);
		List<Binding> bindings = BINDINGS.get(manager);
		if (bindings == null) {
			return;
		}
		for (Binding binding : List.copyOf(bindings)) {
			PhotonGeoEffect effect = binding.effect();
			if (effect.isStopped() || !effect.isAlive() || effect.locator().isBlank()) {
				continue;
			}
			if (pass.model().getLocator(effect.locator()).isEmpty()) {
				Lobecorp.LOGGER.warn(MISSING_LOCATOR, effect.locator());
				effect.stop(true);
				continue;
			}
			pass.addLocatorPositionListener(effect.locator(), (worldPos, _, _) -> {
				if (worldPos != null && !effect.isStopped()) {
					effect.runtime().root.updatePos(worldPos.toVector3f());
				}
			});
		}
	}

	/**
	 * 复用 GeckoLib 的根矩阵剥离，避免把相机或 GUI 的平移当作粒子的世界位置。
	 */
	public static void captureLocator(GeoLocator locator, PoseStack poseStack, RenderPassInfo<?> pass) {
		AnimatableManager<?> manager = pass.renderState().getGeckolibData(DataTickets.ANIMATABLE_MANAGER);
		List<Binding> bindings = BINDINGS.get(manager);
		if (bindings == null) {
			return;
		}
		Matrix4f local = RenderUtil.extractPoseFromRoot(poseStack.last().pose(), pass.getPreRenderMatrixState());
		Vec3 base = pass.renderState().getGeckolibData(DataTickets.POSITION);
		if (base == null) {
			return;
		}
		Vector3f position = local.getTranslation(new Vector3f()).add(base.toVector3f());
		Quaternionf rotation = local.getUnnormalizedRotation(new Quaternionf()).normalize();
		Vector3f scale = local.getScale(new Vector3f());
		if (!position.isFinite() || !rotation.isFinite() || !scale.isFinite()) {
			return;
		}
		PhotonAnchorTransform transform = new PhotonAnchorTransform(position, rotation, scale);
		for (Binding binding : List.copyOf(bindings)) {
			if (binding.effect().locator().equals(locator.name())) {
				binding.effect().updateAnchor(transform);
			}
		}
	}

	/**
	 * 只清理当前控制器的关键帧效果，代码效果和其他动画控制器不受影响。
	 */
	public static void stopKeyframes(GeoRenderState state, AnimationController<?> controller) {
		List<Binding> bindings = BINDINGS.get(state.getGeckolibData(DataTickets.ANIMATABLE_MANAGER));
		if (bindings != null) {
			for (Binding binding : List.copyOf(bindings)) {
				if (binding.controller() == controller) {
					binding.effect().stop(false);
				}
			}
		}
	}

	public static void tick() {
		var iterator = BINDINGS.entrySet().iterator();
		while (iterator.hasNext()) {
			List<Binding> bindings = iterator.next().getValue();
			for (Binding binding : bindings) {
				AnimationController<?> controller = binding.controller();
				if (controller != null && (controller.getPlayState() == PlayState.STOP
						|| controller.hasAnimationFinished() || controller.getCurrentRawAnimation() != binding.animation())) {
					binding.effect().stop(false);
				}
				binding.effect().tick();
			}
			bindings.removeIf(binding -> !binding.effect().isAlive());
			if (bindings.isEmpty()) {
				iterator.remove();
			}
		}
	}

	public static void clear() {
		for (List<Binding> bindings : BINDINGS.values()) {
			for (Binding binding : bindings) {
				binding.effect().stop(true);
			}
		}
		BINDINGS.clear();
	}

	private record Binding(Identifier id, PhotonGeoEffect effect, @Nullable AnimationController<?> controller,
	                       @Nullable RawAnimation animation) {
	}
}
