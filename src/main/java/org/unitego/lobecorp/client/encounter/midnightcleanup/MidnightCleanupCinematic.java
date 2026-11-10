package org.unitego.lobecorp.client.encounter.midnightcleanup;

import net.minecraft.client.Minecraft;
import org.unitego.lobecorp.registry.translation.MidnightCleanupTranslationKeys;
import org.unitego.lobecorp.client.visual.text.CinematicText;
import org.unitego.lobecorp.client.visual.PresentationTimeline;
import org.unitego.lobecorp.client.visual.input.PlayerInteractionControl;
import org.unitego.lobecorp.client.visual.camera.CameraControl;
import org.unitego.lobecorp.client.visual.camera.ICameraController;
import org.unitego.lobecorp.network.ts.encounter.EncounterSkipPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupOpening;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.util.encounter.MidnightCleanupViewUtil;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;

import java.util.ArrayDeque;
import java.util.UUID;

/// 独立于普通HUD可见性的开闭幕表现；不写入用户选项。
public class MidnightCleanupCinematic {
	private static final CinematicText CHAPTER = new CinematicText(MidnightCleanupTranslationKeys.BANNER_TITLE, MidnightCleanupEncounter.CHAPTER_COLOR);
	private static final CinematicText NAME = new CinematicText(MidnightCleanupTranslationKeys.BANNER_NAME, MidnightCleanupEncounter.NAME_COLOR);
	private static final CinematicText START = new CinematicText(MidnightCleanupTranslationKeys.BANNER_START, MidnightCleanupEncounter.SUBTITLE_COLOR);
	private static final CinematicText END = new CinematicText(MidnightCleanupTranslationKeys.BANNER_END, MidnightCleanupEncounter.SUBTITLE_COLOR);
	private static final PresentationTimeline timeline = new PresentationTimeline();
	private static final ArrayDeque<Presentation> pending = new ArrayDeque<>();
	private static ClientLevel world;
	private static long began;
	private static int duration;
	private static boolean opening;
	private static boolean bannerOnly;
	private static boolean released;
	private static float startYaw, startPitch, targetYaw, targetPitch;
	private static UUID eventId;

	public static void receive(UUID event, CompoundTag body) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		if (world != level) {
			clear();
		}

		var incoming = new Presentation(event, body.getBooleanOr("opening", true),
				body.getBooleanOr("banner_only", false), body.getFloatOr("target_yaw", 0),
				body.getFloatOr("target_pitch", 0));
		if (active() && opening && !bannerOnly && (!incoming.opening() || incoming.bannerOnly())) {
			if (!event.equals(eventId)) {
				// 其它事件的弱横幅不能覆盖正在锁速的完整开幕。
				pending.removeIf(queued -> queued.event().equals(event));
				pending.addLast(incoming);
				return;
			}
			if (incoming.opening()) {
				return;
			}
		}

		pending.removeIf(queued -> queued.event().equals(event));
		begin(incoming);
	}

	private static void begin(Presentation presentation) {
		timeline.cancel();
		world = Minecraft.getInstance().level;
		if (world == null) {
			return;
		}

		began = world.getGameTime();
		eventId = presentation.event();
		opening = presentation.opening();
		bannerOnly = presentation.bannerOnly();
		released = false;
		if (opening) {
			MidnightCleanupAtmosphere.start(presentation.event(), presentation.yaw());
		}
		duration = opening && !bannerOnly ? MidnightCleanupEncounter.OPENING_TICKS : MidnightCleanupEncounter.CLOSING_TICKS;
		timeline.start(world.getGameTime(), duration, MidnightCleanupCinematic::releaseControl);
		var player = Minecraft.getInstance().player;
		if (opening && !bannerOnly && player != null) {
			startYaw = player.getYRot();
			startPitch = player.getXRot();
			targetYaw = Float.isFinite(presentation.yaw()) ? presentation.yaw() : startYaw;
			targetPitch = Float.isFinite(presentation.pitch()) ? presentation.pitch() : startPitch;
			PlayerInteractionControl.acquire(MidnightCleanupCinematic.class, MidnightCleanupEncounter.CONTROL_PRIORITY, MidnightCleanupCinematic::controlsView,
					PlayerInteractionControl.Policy.CINEMATIC, MidnightCleanupCinematic::skip);
			CameraControl.register(MidnightCleanupCinematic.class, MidnightCleanupEncounter.CONTROL_PRIORITY, MidnightCleanupCinematic::controlsView, new ICameraController() {
				@Override
				public Vec3 position(Vec3 normal, float partial) {
					return cameraPosition(normal, partial);
				}

				@Override
				public void angles(ViewportEvent.ComputeCameraAngles event) {
					camera(event);
				}

				@Override
				public void fov(ViewportEvent.ComputeFov event) {
					zoomFov(event);
				}
			});
		}
	}

	public static void skip() {
		if (!controlsView()) {
			return;
		}
		ClientPacketDistributor.sendToServer(new EncounterSkipPayload(eventId));
		releaseControl();
		bannerOnly = true;
		duration = MidnightCleanupEncounter.CLOSING_TICKS;
		began = world.getGameTime();
		timeline.start(began, duration, MidnightCleanupCinematic::releaseControl);
	}

	private static void releaseControl() {
		if (!released && opening && !bannerOnly && world == Minecraft.getInstance().level) {
			var player = Minecraft.getInstance().player;
			if (player != null) {
				player.setYRot(startYaw);
				player.setXRot(startPitch);
				player.setYHeadRot(startYaw);
				player.yRotO = startYaw;
				player.xRotO = startPitch;
			}
		}
		released = true;
		PlayerInteractionControl.release(MidnightCleanupCinematic.class);
		CameraControl.unregister(MidnightCleanupCinematic.class);
	}

	public static boolean hidesHud() {
		return controlsView();
	}

	public static boolean controlsView() {
		if (!active() || !opening || bannerOnly) {
			return false;
		}
		if (timeline.time(world.getGameTime()) < MidnightCleanupViewUtil.CENTER_END) {
			return true;
		}
		releaseControl();
		return false;
	}

	public static void holdView() {
		var player = Minecraft.getInstance().player;
		if (!controlsView() || player == null) {
			return;
		}

		float age = timeline.time(world.getGameTime());
		var current = MidnightCleanupViewUtil.sample(startYaw, startPitch, targetYaw, targetPitch, age);
		var previous = MidnightCleanupViewUtil.sample(startYaw, startPitch, targetYaw, targetPitch, age - 1);
		player.setYRot(current.yaw());
		player.setXRot(current.pitch());
		player.setYHeadRot(current.yaw());
		player.yRotO = previous.yaw();
		player.xRotO = previous.pitch();
	}

	/// 观察阶段只接管基础角度，事件浪潮震动由共同入口继续叠加。
	public static boolean camera(ViewportEvent.ComputeCameraAngles event) {
		if (!controlsView()) {
			return false;
		}

		var minecraft = Minecraft.getInstance();
		float partial = minecraft.isPaused() ? 0 : (float) event.getPartialTick();
		float age = timeline.time(world.getGameTime() + partial);
		var angles = MidnightCleanupViewUtil.sample(startYaw, startPitch, targetYaw, targetPitch, age);
		event.setYaw(angles.yaw());
		event.setPitch(angles.pitch());
		event.setRoll(event.getRoll() + MidnightCleanupViewUtil.roll(age));
		return true;
	}

	/// 只偏移本帧相机，不移动玩家；遇到实心方块时缩短偏移。
	public static Vec3 cameraPosition(Vec3 normal, float partial) {
		var minecraft = Minecraft.getInstance();
		if (!controlsView() || minecraft.player == null) {
			return normal;
		}
		float age = timeline.time(world.getGameTime() + (minecraft.isPaused() ? 0 : partial));
		double yaw = Math.toRadians(targetYaw);
		float side = MidnightCleanupViewUtil.side(age);
		float forward = MidnightCleanupViewUtil.forward(age);
		var desired = normal.add(Math.cos(yaw) * side - Math.sin(yaw) * forward,
				MidnightCleanupViewUtil.height(age), Math.sin(yaw) * side + Math.cos(yaw) * forward);
		var hit = world.clip(new ClipContext(normal, desired, ClipContext.Block.VISUAL,
				ClipContext.Fluid.NONE, minecraft.player));
		if (hit.getType() == HitResult.Type.MISS) {
			return desired;
		}
		var offset = desired.subtract(normal);
		double length = offset.length();
		return length < MidnightCleanupEncounter.CAMERA_OFFSET_EPSILON ? normal : normal.add(offset.scale(Math.max(0,
				(normal.distanceTo(hit.getLocation()) - MidnightCleanupEncounter.CAMERA_COLLISION_MARGIN) / length)));
	}

	/// 变焦只作用于世界配置视野，不改用户选项或把GUI文字一并放大。
	public static void zoomFov(ViewportEvent.ComputeFov event) {
		if (!event.usedConfiguredFov() || !active() || !opening || bannerOnly) {
			return;
		}

		var minecraft = Minecraft.getInstance();
		float partial = minecraft.isPaused() ? 0 : (float) event.getPartialTick();
		float age = timeline.time(world.getGameTime() + partial);
		float factor = MidnightCleanupViewUtil.magnification(age);
		float normal = event.getFOV();
		if (!Float.isFinite(normal) || normal <= 0 || normal >= MidnightCleanupEncounter.MAXIMUM_VALID_FOV) {
			return;
		}

		double halfAngle = Math.toRadians(normal) * 0.5;
		event.setFOV((float) Math.toDegrees(2 * Math.atan(Math.tan(halfAngle) / factor)));
	}

	/// 仅剔除本任务锁速对FOV的贡献，保留飞行、拉弓与用户效果倍率。
	public static void correctFov(ComputeFovModifierEvent event) {
		var player = event.getPlayer();
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		float walking = player.getAbilities().getWalkingSpeed();
		if (speed == null || walking == 0 || !speed.hasModifier(MidnightCleanupOpening.speedModifierId())) {
			return;
		}

		var unlocked = new AttributeInstance(Attributes.MOVEMENT_SPEED, _ -> {});
		unlocked.replaceFrom(speed);
		unlocked.removeModifier(MidnightCleanupOpening.speedModifierId());
		float actualFactor = ((float) speed.getValue() / walking + 1) / 2;
		float unlockedFactor = ((float) unlocked.getValue() / walking + 1) / 2;
		float corrected = event.getFovModifier() * unlockedFactor / actualFactor;
		float delta = Mth.lerp(event.getFovScale(), 1, corrected)
				- Mth.lerp(event.getFovScale(), 1, event.getFovModifier());
		event.setNewFovModifier(event.getNewFovModifier() + delta);
	}

	private static boolean active() {
		var level = Minecraft.getInstance().level;
		if (level == null || world != level) {
			clear();
			return false;
		}
		var player = Minecraft.getInstance().player;
		if (player == null || !player.isAlive() || player.isSpectator()) {
			clear();
			return false;
		}
		if (duration > 0 && timeline.active(level.getGameTime())) {
			return true;
		}
		if (!pending.isEmpty()) {
			begin(pending.removeFirst());
			return true;
		}

		return false;
	}

	public static void render(GuiGraphicsExtractor graphics) {
		if (!active()) {
			return;
		}

		var minecraft = Minecraft.getInstance();
		float partial = minecraft.isPaused() ? 0 : minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float age = timeline.time(world.getGameTime() + partial);
		float width = graphics.guiWidth(), height = graphics.guiHeight();
		boolean full = opening && !bannerOnly;
		if (!full) {
			float alpha = MidnightCleanupViewUtil.phase(age, 0, MidnightCleanupEncounter.BANNER_ENTER_TICKS);
			float dissolve = MidnightCleanupViewUtil.phase(age, duration - MidnightCleanupEncounter.BANNER_DISSOLVE_TICKS, duration);
			float center = width / 2, titleY = height * MidnightCleanupEncounter.BANNER_TITLE_Y;
			float gap = Math.min(MidnightCleanupEncounter.BANNER_MAX_GAP, height * MidnightCleanupEncounter.TEXT_GAP_HEIGHT);
			CHAPTER.extract(graphics, center, titleY - gap, MidnightCleanupEncounter.BANNER_CHAPTER_SCALE, width * MidnightCleanupEncounter.TITLE_MAX_WIDTH, alpha, dissolve);
			NAME.extract(graphics, center, titleY, MidnightCleanupEncounter.BANNER_NAME_SCALE, width * MidnightCleanupEncounter.TITLE_MAX_WIDTH, alpha, dissolve);
			(opening ? START : END).extract(graphics, center, titleY + gap, MidnightCleanupEncounter.BANNER_SUBTITLE_SCALE,
					width * MidnightCleanupEncounter.SUBTITLE_MAX_WIDTH, alpha, dissolve);
			if (alpha > MidnightCleanupEncounter.BANNER_LINE_ALPHA_THRESHOLD) {
				graphics.lobecorp$fill(center - MidnightCleanupEncounter.BANNER_LINE_HALF_WIDTH, titleY + gap * MidnightCleanupEncounter.BANNER_LINE_GAP_FACTOR,
						center + MidnightCleanupEncounter.BANNER_LINE_HALF_WIDTH,
						titleY + gap * MidnightCleanupEncounter.BANNER_LINE_GAP_FACTOR + MidnightCleanupEncounter.BANNER_LINE_HEIGHT,
						Math.round(alpha * 255) << 24 | MidnightCleanupEncounter.BANNER_LINE_COLOR);
			}
			return;
		}

		float ambience = MidnightCleanupViewUtil.phase(age, 0, MidnightCleanupEncounter.AMBIENCE_ENTER_END)
				* (1 - MidnightCleanupViewUtil.phase(age, MidnightCleanupEncounter.AMBIENCE_LEAVE_START, MidnightCleanupViewUtil.CENTER_END));
		fill(graphics, width, height, ambience * MidnightCleanupEncounter.AMBIENCE_OVERLAY_OPACITY, MidnightCleanupEncounter.AMBIENCE_OVERLAY_COLOR);
		float edge = height * MidnightCleanupEncounter.AMBIENCE_EDGE_HEIGHT;
		if (ambience > 0) {
			int shade = Math.round(ambience * MidnightCleanupEncounter.AMBIENCE_EDGE_ALPHA) << 24;
			graphics.lobecorp$fillGradient(0, 0, width, edge, shade, 0);
			graphics.lobecorp$fillGradient(0, height - edge, width, height, 0, shade);
		}
		float blackout = MidnightCleanupViewUtil.blackout(age);
		fill(graphics, width, height, blackout, MidnightCleanupEncounter.BLACKOUT_COLOR);
		float chapter = MidnightCleanupViewUtil.phase(age, MidnightCleanupEncounter.CHAPTER_ENTER_START, MidnightCleanupEncounter.CHAPTER_ENTER_END);
		float name = MidnightCleanupViewUtil.phase(age, MidnightCleanupEncounter.NAME_ENTER_START, MidnightCleanupEncounter.NAME_ENTER_END);
		float subtitle = MidnightCleanupViewUtil.phase(age, MidnightCleanupEncounter.SUBTITLE_ENTER_START, MidnightCleanupViewUtil.TEXT_READY);
		float rise = MidnightCleanupViewUtil.phase(age, MidnightCleanupEncounter.TEXT_RISE_START, MidnightCleanupEncounter.TEXT_RISE_END);
		drawText(graphics, width, height, height * MidnightCleanupEncounter.TITLE_Y - rise * MidnightCleanupEncounter.TEXT_RISE_DISTANCE,
				chapter, name, subtitle, rise, age);
		float bar = height * MidnightCleanupViewUtil.letterbox(age);
		if (bar > 0) {
			graphics.lobecorp$fill(0, 0, width, bar, MidnightCleanupEncounter.LETTERBOX_COLOR);
			graphics.lobecorp$fill(0, height - bar, width, height, MidnightCleanupEncounter.LETTERBOX_COLOR);
		}
	}

	private static void fill(GuiGraphicsExtractor graphics, float width, float height, float opacity, int color) {
		if (opacity > 0) {
			graphics.lobecorp$fill(0, 0, width, height, Math.round(opacity * 255) << 24 | color);
		}
	}

	private static void drawText(GuiGraphicsExtractor graphics, float width, float height, float titleY,
	                             float chapter, float name, float subtitle, float rise, float age) {
		float center = width / 2;
		float gap = Math.min(MidnightCleanupEncounter.FULL_MAX_GAP, height * MidnightCleanupEncounter.TEXT_GAP_HEIGHT);
		float scale = Math.clamp(height / MidnightCleanupEncounter.TEXT_REFERENCE_HEIGHT, MidnightCleanupEncounter.TEXT_MIN_SCALE, MidnightCleanupEncounter.TEXT_MAX_SCALE);
		CHAPTER.extract(graphics, center, titleY - gap, scale, width * MidnightCleanupEncounter.TITLE_MAX_WIDTH, chapter,
				MidnightCleanupViewUtil.phase(age, MidnightCleanupEncounter.CHAPTER_LEAVE_START, MidnightCleanupEncounter.CHAPTER_LEAVE_END));
		NAME.extract(graphics, center, titleY, scale * MidnightCleanupEncounter.FULL_NAME_SCALE, width * MidnightCleanupEncounter.TITLE_MAX_WIDTH, name,
				MidnightCleanupViewUtil.phase(age, MidnightCleanupViewUtil.DISSOLVE_START, MidnightCleanupEncounter.NAME_LEAVE_END));
		(opening ? START : END).extract(graphics, center, titleY + gap - rise,
				scale * MidnightCleanupEncounter.FULL_SUBTITLE_SCALE, width * MidnightCleanupEncounter.SUBTITLE_MAX_WIDTH, subtitle, MidnightCleanupViewUtil.phase(age,
						MidnightCleanupEncounter.SUBTITLE_LEAVE_START, MidnightCleanupEncounter.SUBTITLE_LEAVE_END));
	}

	public static void clear() {
		timeline.cancel();
		releaseControl();
		world = null;
		duration = 0;
		opening = false;
		bannerOnly = false;
		eventId = null;
		pending.clear();
	}

	private record Presentation(UUID event, boolean opening, boolean bannerOnly, float yaw, float pitch) {
	}
}
