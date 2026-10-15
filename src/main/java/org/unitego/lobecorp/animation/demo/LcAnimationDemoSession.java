package org.unitego.lobecorp.animation.demo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.LcTransitionMode;
import org.unitego.lobecorp.animation.runtime.LcAnimationContext;
import org.unitego.lobecorp.attachment.ILcTransformTarget;
import org.unitego.lobecorp.attachment.LcAttachmentAnchors;
import org.unitego.lobecorp.attachment.LcAttachmentBinding;
import org.unitego.lobecorp.attachment.LcRenderAnchorReference;
import org.unitego.lobecorp.attachment.adapter.EntityAnimationTarget;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;
import org.unitego.lobecorp.animation.runtime.LcServerAnimations;
import org.unitego.lobecorp.world.item.animation.LcItemAnimationSession;

import java.util.Objects;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/// 真实玩家的附着示例；切锚不重建物品，也不重启物品自身的动画。
public class LcAnimationDemoSession implements ILcTransformTarget {
	private static final String MISSING_BINDING = "Animation demo blade binding has not been initialized";
	private static final String MISSING_ANIMATION = "Attachment demo animation has not been initialized";
	private static final Map<ServerPlayer, LcAnimationDemoSession> PLAYING = new IdentityHashMap<>();
	private final ServerPlayer player;
	private final ServerLevel level;
	private final EntityAnimationTarget owner;
	private final ItemStack stack;
	private final LcAnimationPlaybackOptions itemOptions;
	private final long startedTick;
	private boolean available = true;
	private boolean started;
	private boolean requestedPaused;
	@Nullable
	private LcItemAnimationSession blade;
	@Nullable
	private LcAnimationInstance animation;

	private LcAnimationDemoSession(ServerPlayer player, ItemStack stack, LcAnimationPlaybackOptions options,
	                               double itemSpeed, long startedTick) {
		this.player = player;
		level = player.level();
		owner = new EntityAnimationTarget(player, Set.of(EntityAnimationTarget.Channel.POSITION));
		this.stack = stack.copy();
		itemOptions = new LcAnimationPlaybackOptions(itemSpeed, options.transitions());
		this.startedTick = startedTick;
		started = level.getGameTime() >= startedTick;
	}

	@SuppressWarnings("resource")
	public static LcAnimationInstance play(ServerPlayer player, ItemStack stack, LcAnimationPlaybackOptions options,
	                                       double itemSpeed) {
		var previous = PLAYING.get(player);
		stop(player);
		long startedTick = player.level().getGameTime();
		if (previous != null && previous.blade != null
				&& options.transitions().animationTransitionMode() == LcTransitionMode.SEQUENTIAL) {
			startedTick = Math.max(startedTick, previous.blade.fadeEndTick());
		}

		var session = new LcAnimationDemoSession(player, stack, options, itemSpeed, startedTick);
		PLAYING.put(player, session);
		try {
			var instance = LcServerAnimations.play(player, session, LcAnimationDemo.DEFINITION_ID);
			session.animation = instance;
			options.apply(instance);
			instance.setPaused(!session.started);
			instance.setParameter(LcAnimationDemo.HAND_X_PARAMETER, session.handX() * LcAnimationDemo.MODEL_UNITS);
			instance.setParameter(LcAnimationDemo.FLOURISH_PARAMETER, LcAnimationDemo.DEFAULT_FLOURISH);
			return instance;
		} catch (RuntimeException | Error failure) {
			PLAYING.remove(player, session);
			session.available = false;
			if (session.blade != null) {
				session.blade.close();
			}
			if (session.animation != null) {
				session.animation.stop();
			}

			throw failure;
		}
	}

	public static void attachBlade(LcAnimationInstance instance) {
		var session = session(instance);
		session.blade = LcItemAnimationSession.create(session.player, session.stack, session.itemOptions,
				LcAttachmentAnchors.entity(session.player, LcAnimationDemo.BACK_OFFSET),
				session.renderAnchor(LcAnimationDemo.BACK_RENDER_NODE), session.startedTick);
		session.binding().setTrajectory(() -> instance.pose().get(LcAnimationDemo.DRAW_BONE));
		session.binding().update();
	}

	/// 顺序替换等待旧物品淡出后再推进父时间线，与物品自身时钟在同一世界 tick 开始。
	public static void tick(LcAnimationContext context) {
		var session = session(context.instance());
		if (!session.started && session.level.getGameTime() >= session.startedTick) {
			session.started = true;
			context.instance().setPaused(session.requestedPaused);
		}
	}

	public static void moveToHand(LcAnimationInstance instance) {
		var session = session(instance);
		if (!session.hasBlade()) {
			return;
		}

		var binding = session.binding();
		// 时间点在本 tick 姿态采样之后触发，先更新旧锚点，避免用上一 tick 的位置计算切换校正。
		binding.update();
		binding.setTrajectory(LcTransform::identity);
		binding.switchAnchor(LcAttachmentAnchors.entity(session.player, session.handOffset()), true);
		binding.setRenderAnchor(session.renderAnchor(session.player.getMainArm() == HumanoidArm.RIGHT
				? LcAnimationDemo.RIGHT_HAND_RENDER_NODE : LcAnimationDemo.LEFT_HAND_RENDER_NODE));
		binding.update();
	}

	public static void moveToWorld(LcAnimationInstance instance) {
		var session = session(instance);
		if (!session.hasBlade()) {
			return;
		}

		var binding = session.binding();
		binding.update();
		binding.setTrajectory(() -> instance.pose().get(LcAnimationDemo.DROP_BONE));
		binding.switchAnchor(LcAttachmentAnchors.world(session.level, binding.target().transform()), true);
		binding.setRenderAnchor(null);
		binding.update();
	}

	public static void finish(LcAnimationInstance instance) {
		var session = session(instance);
		session.available = false;
		PLAYING.remove(session.player, session);
		if (session.blade != null) {
			session.blade.requestStop();
		}
	}

	private static LcAnimationDemoSession session(LcAnimationInstance instance) {
		return (LcAnimationDemoSession) instance.ownerTarget();
	}

	private LcAttachmentBinding binding() {
		return blade().binding();
	}

	private LcItemAnimationSession blade() {
		return Objects.requireNonNull(blade, MISSING_BINDING);
	}

	private boolean hasBlade() {
		return blade != null && blade.isAvailable() && !blade.binding().isReleased();
	}

	public static boolean pauseItem(ServerPlayer player, boolean paused) {
		var session = PLAYING.get(player);
		return session != null && session.hasBlade() && session.blade().setPaused(paused);
	}

	public static boolean speedItem(ServerPlayer player, double speed) {
		var session = PLAYING.get(player);
		return session != null && session.hasBlade() && session.blade().setSpeed(speed);
	}

	public static boolean stopItem(ServerPlayer player) {
		var session = PLAYING.get(player);
		if (session == null || !session.hasBlade()) {
			return false;
		}

		session.blade().requestStop();
		return true;
	}

	private LcRenderAnchorReference renderAnchor(String node) {
		return new LcRenderAnchorReference(LcAnimationDemo.PLAYER_RENDER_PROVIDER,
				player.getUUID(), player.getId(), node);
	}

	private double handX() {
		return player.getMainArm() == HumanoidArm.RIGHT ? -LcAnimationDemo.HAND_X : LcAnimationDemo.HAND_X;
	}

	private LcTransform handOffset() {
		return new LcTransform(new Vec3(handX(), LcAnimationDemo.HAND_HEIGHT, 0),
				new Quaternionf(), LcTransform.identity().scale());
	}

	public static void stop(ServerPlayer player) {
		var session = PLAYING.get(player);
		if (session != null) {
			Objects.requireNonNull(session.animation, MISSING_ANIMATION).stop();
		}
	}

	public static boolean setPaused(ServerPlayer player, boolean paused) {
		var session = PLAYING.get(player);
		if (session == null) {
			return false;
		}

		session.requestedPaused = paused;
		Objects.requireNonNull(session.animation, MISSING_ANIMATION).setPaused(!session.started || paused);
		return true;
	}

	public static boolean setSpeed(ServerPlayer player, double speed) {
		var session = PLAYING.get(player);
		if (session == null) {
			return false;
		}

		Objects.requireNonNull(session.animation, MISSING_ANIMATION).setSpeed(speed);
		return true;
	}

	public static boolean switchAnchor(ServerPlayer player, Anchor anchor) {
		var session = PLAYING.get(player);
		if (session == null || !session.hasBlade()) {
			return false;
		}

		var instance = Objects.requireNonNull(session.animation, MISSING_ANIMATION);
		switch (anchor) {
			case BACK -> {
				var binding = session.binding();
				binding.update();
				binding.setTrajectory(LcTransform::identity);
				binding.switchAnchor(LcAttachmentAnchors.entity(player, LcAnimationDemo.BACK_OFFSET), true);
				binding.setRenderAnchor(session.renderAnchor(LcAnimationDemo.BACK_RENDER_NODE));
				binding.update();
			}
			case HAND -> moveToHand(instance);
			case WORLD -> moveToWorld(instance);
		}
		return true;
	}

	public enum Anchor {
		BACK, HAND, WORLD
	}

	@Override
	public ServerLevel level() {
		return level;
	}

	@Override
	public LcTransform transform() {
		return owner.transform();
	}

	@Override
	public void applyTransform(LcTransform transform) {
		owner.applyTransform(transform);
	}

	@Override
	public Object identity() {
		return player;
	}

	@Override
	@SuppressWarnings("resource")
	public boolean isAvailable() {
		return available && owner.isAvailable() && player.level() == level;
	}
}
