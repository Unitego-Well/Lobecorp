package org.unitego.lobecorp.world.item.animation;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;
import org.unitego.lobecorp.animation.LcTransitionMode;
import org.unitego.lobecorp.animation.demo.LcAnimationDemo;
import org.unitego.lobecorp.animation.demo.LcAnimationPlaybackOptions;
import org.unitego.lobecorp.animation.runtime.LcAnimationContext;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;
import org.unitego.lobecorp.animation.runtime.LcServerAnimations;
import org.unitego.lobecorp.attachment.ILcAttachmentAnchor;
import org.unitego.lobecorp.attachment.ILcTransformTarget;
import org.unitego.lobecorp.attachment.LcAttachmentAnchors;
import org.unitego.lobecorp.attachment.LcAttachmentBinding;
import org.unitego.lobecorp.attachment.LcAttachments;
import org.unitego.lobecorp.attachment.LcRenderAnchorReference;
import org.unitego.lobecorp.attachment.LcTransform;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/// 独立物品显示对象不注册实体、不写入背包；绑定位置和自身动画分别使用各自的时钟。
public class LcItemAnimationSession implements ILcTransformTarget {
	public static final Identifier DEFINITION_ID = Lobecorp.id("example/item_pose");
	public static final String ANIMATION_NAME = "animation.example.item_pose";
	public static final String ANIMATION_BONE = "root";
	private static final String MISSING_ANIMATION = "Item animation has not been initialized";
	private static final String MISSING_BINDING = "Item attachment has not been initialized";
	/// 世界示例抬高一格，避免默认物品原点落在脚下。
	private static final double WORLD_HEIGHT = 1;
	private static final Map<ServerPlayer, LcItemAnimationSession> WORLD_PLAYING = new IdentityHashMap<>();
	private final ServerLevel level;
	private final ServerPlayer source;
	private final LcAnimationTransitionSettings transitions;
	private final long startedTick;
	private LcTransform transform;
	private boolean available = true;
	private boolean stopping;
	private boolean started;
	private boolean requestedPaused;
	private long stoppingTick = -1;
	@Nullable
	private LcAttachmentBinding binding;
	@Nullable
	private LcAnimationInstance animation;

	private LcItemAnimationSession(ServerPlayer player, LcTransform transform,
	                               LcAnimationTransitionSettings transitions, long startedTick) {
		this.level = player.level();
		source = player;
		this.transform = transform;
		this.transitions = transitions;
		this.startedTick = startedTick;
		started = level.getGameTime() >= startedTick;
	}

	@SuppressWarnings("resource")
	public static LcItemAnimationSession create(ServerPlayer player, ItemStack stack,
	                                            LcAnimationPlaybackOptions options, ILcAttachmentAnchor anchor,
	                                            @Nullable LcRenderAnchorReference renderAnchor) {
		return create(player, stack, options, anchor, renderAnchor, player.level().getGameTime());
	}

	public static LcItemAnimationSession create(ServerPlayer player, ItemStack stack,
	                                            LcAnimationPlaybackOptions options, ILcAttachmentAnchor anchor,
	                                            @Nullable LcRenderAnchorReference renderAnchor, long startedTick) {
		var session = new LcItemAnimationSession(player, anchor.resolve(), options.transitions(), startedTick);
		try {
			session.binding = LcAttachments.bind(session, anchor);
			session.binding.setRenderAnchor(renderAnchor);
			session.binding.update();
			session.animation = LcServerAnimations.play(player.level(), session, DEFINITION_ID);
			options.apply(session.animation);
			session.animation.setPaused(!session.started);
			LcAttachedItems.register(session.binding, stack, ItemDisplayContext.FIXED,
					session.animation.id(), ANIMATION_BONE, options.transitions(), startedTick);
			return session;
		} catch (RuntimeException | Error failure) {
			session.close();
			throw failure;
		}
	}

	@SuppressWarnings("resource")
	public static void playWorld(ServerPlayer player, ItemStack stack, LcAnimationPlaybackOptions options) {
		var previous = WORLD_PLAYING.get(player);
		stopWorld(player);
		long startedTick = player.level().getGameTime();
		if (previous != null && options.transitions().animationTransitionMode() == LcTransitionMode.SEQUENTIAL) {
			startedTick = Math.max(startedTick, previous.fadeEndTick());
		}

		var forward = Vec3.directionFromRotation(0, player.getYRot());
		var position = player.position().add(forward.scale(LcAnimationDemo.SPAWN_DISTANCE)).add(0, WORLD_HEIGHT, 0);
		var transform = new LcTransform(position, new Quaternionf(), LcTransform.identity().scale());
		var session = create(player, stack, options, LcAttachmentAnchors.world(player.level(), transform), null, startedTick);
		WORLD_PLAYING.put(player, session);
	}

	public static void stopWorld(ServerPlayer player) {
		var session = WORLD_PLAYING.remove(player);
		if (session != null) {
			session.requestStop();
		}
	}

	public static boolean pauseWorld(ServerPlayer player, boolean paused) {
		var session = WORLD_PLAYING.get(player);
		return session != null && session.setPaused(paused);
	}

	public static boolean speedWorld(ServerPlayer player, double speed) {
		var session = WORLD_PLAYING.get(player);
		return session != null && session.setSpeed(speed);
	}

	public LcAttachmentBinding binding() {
		return Objects.requireNonNull(binding, MISSING_BINDING);
	}

	public void requestStop() {
		if (!available || stopping) {
			return;
		}

		stopping = true;
		stoppingTick = level.getGameTime();
		if (!started) {
			close();
			return;
		}

		animation().setPaused(true);
		LcAttachedItems.beginStopping(level, binding().id());
		if (LcAttachedItems.fadeFinished(level, binding().id())) {
			animation().stop();
		}
	}

	/// 未开始的替换仍继承前一显示的排队终点，不为不可见物品增加淡出时长。
	public long fadeEndTick() {
		return started ? (stoppingTick < 0 ? level.getGameTime() : stoppingTick) + transitions.fadeOutTicks() : startedTick;
	}

	public boolean setPaused(boolean paused) {
		if (!available || stopping) {
			return false;
		}

		requestedPaused = paused;
		animation().setPaused(!started || paused);
		return true;
	}

	public boolean setSpeed(double speed) {
		if (!available || stopping) {
			return false;
		}

		animation().setSpeed(speed);
		return true;
	}

	/// 逻辑 tick 回调在暂停时仍运行，淡出只使用世界时钟，不能被 speed=0 或暂停阻塞。
	public static void tick(LcAnimationContext context) {
		var session = (LcItemAnimationSession) context.instance().ownerTarget();
		if (session.binding().isReleased()
				|| session.stopping && LcAttachedItems.fadeFinished(session.level, session.binding().id())) {
			context.instance().stop();
			return;
		}

		if (!session.started && session.level.getGameTime() >= session.startedTick) {
			session.started = true;
			context.instance().setPaused(session.requestedPaused);
		}
	}

	public static void finish(LcAnimationInstance instance) {
		((LcItemAnimationSession) instance.ownerTarget()).close();
	}

	public void close() {
		available = false;
		if (binding != null) {
			binding.release();
			LcAttachedItems.remove(binding.id());
		}

		if (animation != null && !animation.isStopped()) {
			animation.stop();
		}

		WORLD_PLAYING.values().removeIf(session -> session == this);
	}

	private LcAnimationInstance animation() {
		return Objects.requireNonNull(animation, MISSING_ANIMATION);
	}

	@Override
	public ServerLevel level() {
		return level;
	}

	@Override
	public LcTransform transform() {
		return transform;
	}

	@Override
	public void applyTransform(LcTransform transform) {
		this.transform = transform;
	}

	@Override
	@SuppressWarnings("resource")
	public boolean isAvailable() {
		return available && !source.isRemoved() && source.level() == level;
	}
}
