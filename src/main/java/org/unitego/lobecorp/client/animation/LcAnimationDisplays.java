package org.unitego.lobecorp.client.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.util.GeckoLibUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcRotationTransitionMode;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.client.attachment.LcClientAttachments;
import org.unitego.lobecorp.client.attachment.LcRenderAnchorContext;
import org.unitego.lobecorp.util.animation.LcRotationTransitionUtil;
import org.unitego.lobecorp.world.item.animation.LcAttachedItemSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// 显示适配必须由调用方显式登记；世界与渲染锚点共用同一份提取结果。
public class LcAnimationDisplays {
	private static final ContextKey<List<DisplayFrame>> FRAMES =
			new ContextKey<>(Lobecorp.id("animation_displays"));
	private static final Map<UUID, ILcAnimationDisplay> DISPLAYS = new LinkedHashMap<>();
	// 显示对象随快照重建，旋转分支由绑定生命周期保存。
	private static final Map<UUID, ItemRotationHistory> ITEM_ROTATIONS = new LinkedHashMap<>();
	private static List<DisplayFrame> firstPersonFrames = List.of();

	public static void register(UUID targetId, ILcAnimationDisplay display) {
		DISPLAYS.put(targetId, display);
		if (!(display instanceof ItemDisplay itemDisplay) || itemDisplay.item() == null) {
			ITEM_ROTATIONS.remove(targetId);
		}
	}

	/// targetId 可以是播放或附着 UUID，animationId 指定该显示模型自身的动画实例。
	public static void registerModel(UUID targetId, UUID animationId, Identifier texture) {
		register(targetId, new ModelDisplay(animationId, texture));
	}

	public static void registerItem(UUID targetId, ItemStack stack, ItemDisplayContext context) {
		registerItem(targetId, stack, context, null, null);
	}

	/// 整体物品只消费独立动画的一个局部通道，不附加动画实例的世界根变换。
	public static void registerItem(UUID targetId, ItemStack stack, ItemDisplayContext context,
	                                @Nullable UUID animationId, @Nullable String bone) {
		register(targetId, new ItemDisplay(stack.copy(), context, animationId, bone, null));
	}

	public static void registerItem(LcAttachedItemSnapshot item) {
		register(item.bindingId(), new ItemDisplay(item.stack(), item.displayContext(), item.animationId(), item.bone(), item));
	}

	public static void onExtract(ExtractLevelRenderStateEvent event) {
		var partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		var bindings = LcClientAttachments.extract(event.getLevel(), partialTick);
		List<DisplayFrame> frames = new ArrayList<>();
		for (var entry : DISPLAYS.entrySet()) {
			var binding = bindings.get(entry.getKey());
			var transform = binding == null ? LcClientAnimations.worldTransform(entry.getKey(), partialTick) : null;
			if (binding == null && transform == null) {
				continue;
			}

			var world = binding == null ? new Matrix4f(transform.matrix()) : binding.worldMatrix();
			var position = world.getTranslation(new Vector3f());
			var light = LevelRenderer.getLightCoords(event.getLevel(), BlockPos.containing(position.x, position.y, position.z));
			frames.add(new DisplayFrame(entry.getValue().extract(event.getLevel(), partialTick), binding, world, light));
		}

		firstPersonFrames = List.copyOf(frames);
		event.getRenderState().setRenderData(FRAMES, firstPersonFrames);
	}

	public static void onSubmit(SubmitCustomGeometryEvent event) {
		var frames = event.getLevelRenderState().getRenderData(FRAMES);
		if (frames == null) {
			return;
		}

		var camera = event.getLevelRenderState().cameraRenderState;
		for (var frame : frames) {
			if (frame.binding() != null && frame.binding().snapshot().renderAnchor() != null) {
				continue;
			}

			var matrix = frame.binding() == null ? frame.worldMatrix()
					: LcClientAttachments.worldMatrix(frame.binding());
			if (matrix == null) {
				continue;
			}

			var poses = event.getPoseStack();
			poses.pushPose();
			try {
				poses.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z);
				poses.mulPose(matrix);
				frame.display().submit(poses, event.getSubmitNodeCollector(), camera, frame.light());
			} finally {
				poses.popPose();
			}
		}
	}

	/// 由实际模型的渲染层调用；显式渲染来源仅走此路，不再提交世界副本。
	public static void submitRenderAnchors(LcRenderAnchorContext context, SubmitNodeCollector collector,
	                                       CameraRenderState camera, int light) {
		LcClientAttachments.publish(context);
		// 世界提交结束会清空 LevelRenderState，第一人称随后继续消费本次提取冻结的显示数据。
		var frames = context.view().equals(LcRenderAnchorContext.FIRST_PERSON_VIEW) ? firstPersonFrames
				: Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.getRenderData(FRAMES);
		if (frames == null) {
			return;
		}

		for (var frame : frames) {
			if (frame.binding() == null) {
				continue;
			}

			var matrix = LcClientAttachments.renderMatrix(frame.binding(), context);
			if (matrix == null) {
				continue;
			}

			var poses = new PoseStack();
			poses.mulPose(matrix);
			frame.display().submit(poses, collector, camera, light);
		}
	}

	/// 接入方在显示对象自身结束时注销；失去追踪只隐藏，不改变显示对象生命周期。
	public static void remove(UUID id) {
		DISPLAYS.remove(id);
		ITEM_ROTATIONS.remove(id);
	}

	public static void clear() {
		DISPLAYS.clear();
		ITEM_ROTATIONS.clear();
		firstPersonFrames = List.of();
	}

	private record DisplayFrame(ILcAnimationDisplay.Frame display, LcClientAttachments.@Nullable Frame binding,
	                            Matrix4f worldMatrix, int light) {
	}

	private record ItemDisplay(ItemStack stack, ItemDisplayContext context, @Nullable UUID animationId,
	                           @Nullable String bone,
	                           @Nullable LcAttachedItemSnapshot item) implements ILcAnimationDisplay {
		private ItemDisplay {
			stack = stack.copy();
		}

		@Override
		public Frame extract(Level level, float partialTick) {
			if (item != null && LcClientAttachments.renderTick(item, partialTick) < item.startedTick()) {
				return (_, _, _, _) -> {
				};
			}

			var state = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, stack.hashCode());
			var local = localTransform(partialTick);
			return (poses, collector, _, light) -> {
				poses.pushPose();
				try {
					poses.mulPose(new Matrix4f(local.matrix()));
					state.submit(poses, collector, light, OverlayTexture.NO_OVERLAY, 0);
				} finally {
					poses.popPose();
				}
			};
		}

		private LcTransform localTransform(float partialTick) {
			var pose = animationId == null ? null : LcClientAnimations.visualPose(animationId, partialTick);
			if (pose == null || bone == null) {
				if (item != null) {
					ITEM_ROTATIONS.remove(item.bindingId());
				}

				return LcTransform.identity();
			}

			var direct = pose.get(bone);
			if (item == null) {
				return direct;
			}

			var settings = item.transitions();
			var snapshot = LcClientAnimations.snapshot(Objects.requireNonNull(animationId));
			long revision = snapshot == null ? 0 : snapshot.playbackRevision();
			var history = ITEM_ROTATIONS.get(item.bindingId());
			if (history == null || !history.matches(item, revision)) {
				history = new ItemRotationHistory(item, revision);
				ITEM_ROTATIONS.put(item.bindingId(), history);
			}

			if (history.stoppingTick != item.stoppingTick()) {
				history.stoppingTick = item.stoppingTick();
				history.exit = null;
			}

			double now = LcClientAttachments.renderTick(item, partialTick);
			double entryTime = item.stoppingTick() < 0 ? now : Math.min(now, item.stoppingTick());
			double entry = settings.fadeInTicks() <= 0 ? 1
					: Math.clamp((entryTime - item.startedTick()) / settings.fadeInTicks(), 0, 1);
			var entrySample = blend(LcTransform.identity(), Vec3.ZERO, direct, pose.rotationAngles(bone),
					entry, settings.fadeInRotationTransitionMode(), history.entry);
			history.entry = entrySample.state();
			var entered = entrySample.transform();
			var entryAngles = settings.fadeInRotationTransitionMode() == LcRotationTransitionMode.NORMAL
					? pose.rotationAngles(bone).scale(entry)
					: angles(LcRotationTransitionUtil.nearbyEuler(entered.rotation(), history.entryAngles));
			history.entryAngles = new Vector3f((float) entryAngles.x, (float) entryAngles.y, (float) entryAngles.z);
			if (item.stoppingTick() < 0) {
				return entered;
			}

			double exit = settings.fadeOutTicks() <= 0 ? 1
					: Math.clamp((now - item.stoppingTick()) / settings.fadeOutTicks(), 0, 1);
			var exitSample = blend(entered, entryAngles, LcTransform.identity(), Vec3.ZERO,
					exit, settings.fadeOutRotationTransitionMode(), history.exit);
			history.exit = exitSample.state();
			return exitSample.transform();
		}

		private static Vec3 angles(Vector3f angles) {
			return new Vec3(angles.x, angles.y, angles.z);
		}

		private static TransformSample blend(LcTransform previous, Vec3 previousAngles, LcTransform next, Vec3 nextAngles,
		                                     double progress, LcRotationTransitionMode rotationMode,
		                                     LcRotationTransitionUtil.@Nullable State state) {
			if (rotationMode == LcRotationTransitionMode.SHORTEST_PATH) {
				var sample = LcRotationTransitionUtil.interpolate(previous.rotation(), next.rotation(), progress, state);
				return new TransformSample(new LcTransform(previous.position().lerp(next.position(), progress),
						sample.rotation(), previous.scale().lerp(next.scale(), progress)), sample.state());
			}

			var angles = previousAngles.lerp(nextAngles, progress);
			return new TransformSample(new LcTransform(previous.position().lerp(next.position(), progress),
					new Quaternionf().rotationZYX((float) angles.z, (float) angles.y, (float) angles.x),
					previous.scale().lerp(next.scale(), progress)), null);
		}
	}

	private record TransformSample(LcTransform transform, LcRotationTransitionUtil.@Nullable State state) {
	}

	private static class ItemRotationHistory {
		private final LcAttachedItemSnapshot item;
		private final long revision;
		private final Object models = GeckoLibResources.getBakedModels();
		private final Object animations = GeckoLibResources.getBakedAnimations();
		private long stoppingTick;
		private @Nullable Vector3f entryAngles;
		private LcRotationTransitionUtil.@Nullable State entry;
		private LcRotationTransitionUtil.@Nullable State exit;

		private ItemRotationHistory(LcAttachedItemSnapshot item, long revision) {
			this.item = item;
			this.revision = revision;
			this.stoppingTick = item.stoppingTick();
		}

		private boolean matches(LcAttachedItemSnapshot next, long nextRevision) {
			return models == GeckoLibResources.getBakedModels() && animations == GeckoLibResources.getBakedAnimations()
					&& revision == nextRevision && item.startedTick() == next.startedTick()
					&& Objects.equals(item.animationId(), next.animationId()) && Objects.equals(item.bone(), next.bone())
					&& item.transitions().equals(next.transitions());
		}
	}

	private static class ModelDisplay implements GeoAnimatable, ILcAnimationDisplay {
		private final UUID animationId;
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
		private final GeoObjectRenderer<ModelDisplay, Void, GeoRenderState> renderer;
		private @Nullable LcAnimationContent content;

		private ModelDisplay(UUID animationId, Identifier texture) {
			this.animationId = animationId;
			renderer = new GeoObjectRenderer<>(new LcServerGeoModel<>(() -> Objects.requireNonNull(content), texture)) {
				@Override
				public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
					// GeckoLib 模型原生面向 -Z，公共模型空间面向 +Z。
					renderPassInfo.poseStack().mulPose(Axis.YP.rotation((float) Math.PI));
				}
			};
		}

		@Override
		public Frame extract(Level level, float partialTick) {
			var snapshot = LcClientAnimations.snapshot(animationId);
			if (snapshot == null) {
				return (_, _, _, _) -> {
				};
			}

			content = snapshot.content();
			var state = renderer.fillRenderState(this, null, renderer.createRenderState(this, null), partialTick);
			var pose = Objects.requireNonNull(LcClientAnimations.visualPose(animationId, partialTick));
			return (poses, collector, camera, light) -> {
				state.addGeckolibData(DataTickets.PACKED_LIGHT, light);
				renderer.performRenderPass(state, poses, collector, camera,
						List.of((_, bones) -> LcAnimationBoneAdapter.apply(pose, bones)));
			};
		}

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}
	}
}
