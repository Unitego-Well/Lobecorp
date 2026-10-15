package org.unitego.lobecorp.client.attachment;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.unitego.lobecorp.attachment.LcAnchorReference;
import org.unitego.lobecorp.attachment.LcAttachmentSnapshot;
import org.unitego.lobecorp.attachment.LcAttachments;
import org.unitego.lobecorp.attachment.LcRenderAnchorReference;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.client.animation.LcAnimationDisplays;
import org.unitego.lobecorp.client.animation.LcClientAnimationResources;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import org.unitego.lobecorp.world.item.animation.LcAttachedItemSnapshot;
import org.unitego.lobecorp.world.item.animation.LcAttachedItems;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/// 服务端镜像与客户端局部绑定分区；网络全量替换不会释放本地创建的绑定。
public class LcClientAttachments {
	private static final Map<UUID, Sample> SERVER = new LinkedHashMap<>();
	private static final Map<UUID, LcAttachedItemSnapshot> SERVER_ITEMS = new LinkedHashMap<>();
	private static final Map<UUID, Sample> LOCAL = new LinkedHashMap<>();
	private static final Set<UUID> LOCAL_ITEMS = new HashSet<>();
	private static final Map<AnchorKey, Matrix4f> RENDER_ANCHORS = new HashMap<>();
	private static final Map<ContinuityKey, Correction> CORRECTIONS = new HashMap<>();
	private static long lastServerTick = Long.MIN_VALUE;
	private static long receivedServerTick;

	public static void accept(boolean full, long serverTick, List<LcAttachmentSnapshot> updates,
	                          Set<UUID> removed, List<LcAttachedItemSnapshot> items) {
		if (serverTick < lastServerTick) {
			return;
		}

		lastServerTick = serverTick;
		receivedServerTick = gameTime();
		var visible = new HashSet<UUID>();
		updates.forEach(snapshot -> visible.add(snapshot.id()));
		if (full) {
			SERVER.keySet().stream().filter(id -> !visible.contains(id)).toList().forEach(LcClientAttachments::removeServer);
		}

		removed.forEach(LcClientAttachments::removeServer);
		var descriptions = new HashMap<UUID, LcAttachedItemSnapshot>();
		items.forEach(item -> descriptions.put(item.bindingId(), item));
		for (var snapshot : updates) {
			SERVER.put(snapshot.id(), sample(SERVER.get(snapshot.id()), snapshot, gameTime()));
			var item = descriptions.get(snapshot.id());
			if (item == null) {
				if (SERVER_ITEMS.remove(snapshot.id()) != null) {
					LcAnimationDisplays.remove(snapshot.id());
				}
			} else {
				SERVER_ITEMS.put(snapshot.id(), item);
				LcAnimationDisplays.registerItem(item);
			}
		}
	}

	public static Map<UUID, Frame> extract(Level level, float partialTick) {
		RENDER_ANCHORS.clear();
		var local = LcAttachments.snapshots(level);
		var visible = new HashSet<UUID>();
		for (var snapshot : local) {
			visible.add(snapshot.id());
			LOCAL.put(snapshot.id(), sample(LOCAL.get(snapshot.id()), snapshot, level.getGameTime()));
		}

		LOCAL.keySet().removeIf(id -> !visible.contains(id));
		var items = LcAttachedItems.snapshots(level);
		var itemIds = new HashSet<UUID>();
		for (var item : items) {
			if (!SERVER.containsKey(item.bindingId())) {
				itemIds.add(item.bindingId());
				LcAnimationDisplays.registerItem(item);
			}
		}

		LOCAL_ITEMS.stream().filter(id -> !itemIds.contains(id)).toList().forEach(LcAnimationDisplays::remove);
		LOCAL_ITEMS.clear();
		LOCAL_ITEMS.addAll(itemIds);
		var result = new LinkedHashMap<UUID, Frame>();
		LOCAL.forEach((id, sample) -> result.put(id, frame(level, sample, partialTick)));
		SERVER.forEach((id, sample) -> result.put(id, frame(level, sample, partialTick)));
		CORRECTIONS.keySet().removeIf(key -> !result.containsKey(key.id()));
		return Map.copyOf(result);
	}

	public static double renderTick(LcAttachedItemSnapshot item, float partialTick) {
		return SERVER_ITEMS.containsKey(item.bindingId())
				? item.serverTick() + Math.max(0, gameTime() - receivedServerTick + partialTick)
				: item.serverTick() + partialTick;
	}

	public static void publish(LcRenderAnchorContext context) {
		context.nodes().keySet().forEach(node -> {
			var reference = new LcRenderAnchorReference(context.provider(), context.sourceId(), context.entityId(), node);
			var matrix = LcRenderAnchorProviders.resolve(reference, context);
			if (matrix != null) {
				RENDER_ANCHORS.put(new AnchorKey(reference, context.view()),
						new Matrix4f(context.worldFromRender()).mul(matrix));
			}
		});
	}

	public static @Nullable Matrix4f renderMatrix(Frame frame, LcRenderAnchorContext context) {
		var reference = frame.snapshot().renderAnchor();
		if (reference == null || !context.visibleNodes().contains(reference.node())) {
			return null;
		}

		var matrix = LcRenderAnchorProviders.resolve(reference, context);
		if (matrix == null) {
			return null;
		}

		var world = new Matrix4f(context.worldFromRender()).mul(matrix);
		var correction = correction(frame, context.view(), world, frame.snapshot().renderOffset());
		return correction == null ? new Matrix4f(context.worldFromRender()).invert().mul(frame.worldMatrix())
				: matrix.mul(correction).mul(new Matrix4f(frame.snapshot().renderOffset().matrix()));
	}

	public static @Nullable Matrix4f worldMatrix(Frame frame) {
		var offset = new Matrix4f(frame.snapshot().offset().matrix());
		if (singular(offset)) {
			return frame.worldMatrix();
		}

		var base = new Matrix4f(frame.worldMatrix()).mul(offset.invert());
		var correction = correction(frame, LcRenderAnchorContext.WORLD_VIEW, base, frame.snapshot().offset());
		if (correction == null) {
			// 旧渲染来源尚未在本帧提交时采用创建侧结果，不借用上一帧矩阵保证连续。
			CORRECTIONS.put(new ContinuityKey(frame.snapshot().id(), LcRenderAnchorContext.WORLD_VIEW),
					new Correction(frame.snapshot().revision(), new Matrix4f()));
			return frame.worldMatrix();
		}

		return base.mul(correction).mul(new Matrix4f(frame.snapshot().offset().matrix()));
	}

	private static @Nullable Matrix4f correction(Frame frame, Identifier view, Matrix4f base, LcTransform offset) {
		var key = new ContinuityKey(frame.snapshot().id(), view);
		var previous = CORRECTIONS.get(key);
		if (previous != null && previous.revision() == frame.snapshot().revision()) {
			return new Matrix4f(previous.matrix());
		}

		var matrix = new Matrix4f();
		var old = frame.previousRoute();
		if (old != null && frame.snapshot().preserveWorldTransform() && routeChanged(old, frame.snapshot())) {
			var oldWorld = old.renderAnchor() == null ? frame.previousWorld()
					: RENDER_ANCHORS.get(new AnchorKey(old.renderAnchor(), view));
			if (oldWorld == null || singular(base)) {
				return null;
			}

			if (old.renderAnchor() != null) {
				oldWorld = new Matrix4f(oldWorld).mul(previous == null ? new Matrix4f() : previous.matrix())
						.mul(new Matrix4f(old.renderOffset().matrix()));
			}

			var local = new Matrix4f(offset.matrix());
			if (singular(local)) {
				return null;
			}

			matrix.set(base).invert().mul(oldWorld).mul(local.invert());
		}

		CORRECTIONS.put(key, new Correction(frame.snapshot().revision(), matrix));
		return new Matrix4f(matrix);
	}

	private static boolean singular(Matrix4f matrix) {
		return !Float.isFinite(matrix.determinant()) || matrix.determinant() == 0;
	}

	private static Sample sample(@Nullable Sample previous, LcAttachmentSnapshot current, long tick) {
		var route = previous == null ? null : routeChanged(previous.current(), current)
				? previous.current() : previous.previousRoute();
		if (previous != null && previous.receivedTick() == tick) {
			return new Sample(previous.previous(), current, route, tick);
		}

		return new Sample(previous == null ? current : previous.current(), current, route, tick);
	}

	private static boolean routeChanged(LcAttachmentSnapshot previous, LcAttachmentSnapshot current) {
		return !previous.anchor().equals(current.anchor()) || !Objects.equals(previous.renderAnchor(), current.renderAnchor());
	}

	private static Frame frame(Level level, Sample sample, float partialTick) {
		double weight = Math.clamp(level.getGameTime() - sample.receivedTick() + partialTick, 0, 1);
		var fallback = sample.previous().revision() == sample.current().revision()
				? sample.previous().worldTransform().interpolate(sample.current().worldTransform(), weight)
				: sample.current().worldTransform();
		var world = resolveWorld(level, sample.current(), partialTick, new Matrix4f(fallback.matrix()));
		var previous = sample.previousRoute();
		var previousWorld = previous == null ? null
				: resolveWorld(level, previous, partialTick, new Matrix4f(previous.worldTransform().matrix()));
		return new Frame(sample.current(), previous, world, previousWorld);
	}

	private static Matrix4f resolveWorld(Level level, LcAttachmentSnapshot snapshot, float partialTick, Matrix4f fallback) {
		var reference = snapshot.anchor();
		if (reference.kind() == LcAnchorReference.Kind.ENTITY) {
			var entity = level.getEntity(reference.entityId());
			if (entity == null || !entity.getUUID().equals(reference.sourceId())) {
				return fallback;
			}

			float yaw = entity instanceof LivingEntity living
					? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : entity.getViewYRot(partialTick);
			double scale = entity instanceof LivingEntity living ? living.getScale() : 1;
			var position = entity.getPosition(partialTick);
			return new Matrix4f().translation((float) position.x, (float) position.y, (float) position.z)
					.rotate(new Quaternionf().rotationYXZ(-yaw * Mth.DEG_TO_RAD, entity.getViewXRot(partialTick) * Mth.DEG_TO_RAD, 0))
					.scale((float) scale).mul(new Matrix4f(snapshot.offset().matrix()));
		}

		if (reference.kind() != LcAnchorReference.Kind.ANIMATION || reference.sourceId() == null) {
			return fallback;
		}

		var animation = LcClientAnimations.snapshot(reference.sourceId());
		var pose = LcClientAnimations.visualPose(reference.sourceId(), partialTick);
		var root = LcClientAnimations.worldTransform(reference.sourceId(), partialTick);
		if (animation == null || pose == null || root == null) {
			return fallback;
		}

		var anchor = root;
		if (reference.bone() != null) {
			var skeleton = LcClientAnimationResources.skeleton(animation.content());
			anchor = reference.locator() == null ? skeleton.boneTransform(pose, reference.bone(), root)
					: skeleton.locatorTransform(pose, reference.bone(), reference.locator(), root);
		}

		return new Matrix4f(anchor.matrix()).mul(new Matrix4f(snapshot.offset().matrix()));
	}

	private static void removeServer(UUID id) {
		SERVER.remove(id);
		CORRECTIONS.keySet().removeIf(key -> key.id().equals(id));
		if (SERVER_ITEMS.remove(id) != null) {
			LcAnimationDisplays.remove(id);
		}
	}

	public static void clear() {
		SERVER_ITEMS.keySet().forEach(LcAnimationDisplays::remove);
		LOCAL_ITEMS.forEach(LcAnimationDisplays::remove);
		SERVER.clear();
		SERVER_ITEMS.clear();
		LOCAL.clear();
		LOCAL_ITEMS.clear();
		LcAttachedItems.clearClientWorlds();
		RENDER_ANCHORS.clear();
		CORRECTIONS.clear();
		lastServerTick = Long.MIN_VALUE;
		LcAttachments.clearClientWorlds();
	}

	private static long gameTime() {
		var level = Minecraft.getInstance().level;
		return level == null ? 0 : level.getGameTime();
	}

	public record Frame(LcAttachmentSnapshot snapshot, @Nullable LcAttachmentSnapshot previousRoute,
	                    Matrix4f worldMatrix, @Nullable Matrix4f previousWorld) {
	}

	private record Sample(LcAttachmentSnapshot previous, LcAttachmentSnapshot current,
	                      @Nullable LcAttachmentSnapshot previousRoute, long receivedTick) {
	}

	private record AnchorKey(LcRenderAnchorReference reference, Identifier view) {
	}

	private record ContinuityKey(UUID id, Identifier view) {
	}

	private record Correction(long revision, Matrix4f matrix) {
	}
}
