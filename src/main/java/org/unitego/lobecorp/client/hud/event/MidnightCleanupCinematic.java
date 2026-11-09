package org.unitego.lobecorp.client.hud.event;

import net.minecraft.client.Minecraft;
import org.unitego.lobecorp.client.visual.PresentationTimeline;
import org.unitego.lobecorp.client.visual.input.PlayerInteractionControl;
import org.unitego.lobecorp.client.visual.camera.CameraControl;
import org.unitego.lobecorp.client.visual.camera.ICameraController;
import org.unitego.lobecorp.network.ts.horde.HordeOpeningSkipPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import org.unitego.lobecorp.client.horde.HordeEventAtmosphere;
import org.unitego.lobecorp.world.horde.MidnightCleanupOpening;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.util.horde.MidnightCleanupViewUtil;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

import java.util.ArrayDeque;
import java.util.UUID;

/// 独立于普通HUD可见性的开闭幕表现；不写入用户选项。
public class MidnightCleanupCinematic {
	/// 完整开幕十四秒；补播和闭幕仍为50刻且不接管镜头。
	private static final int CLOSING_TICKS = 50;
	private static final CinematicText CHAPTER = new CinematicText("horde.lobecorp.banner.title", 0x8E85CF);
	private static final CinematicText NAME = new CinematicText("horde.lobecorp.banner.name", 0xD4C8FF);
	private static final CinematicText START = new CinematicText("horde.lobecorp.banner.start", 0xAEA2C5);
	private static final CinematicText END = new CinematicText("horde.lobecorp.banner.end", 0xAEA2C5);
	private static ClientLevel world;
	private static long began;
	private static final PresentationTimeline timeline = new PresentationTimeline();
	private static int duration;
	private static boolean opening;
	private static boolean bannerOnly;
	private static boolean released;
	private static float startYaw, startPitch, targetYaw, targetPitch;
	private static UUID eventId;
	private static final ArrayDeque<Presentation> pending = new ArrayDeque<>();

	private record Presentation(UUID event, boolean opening, boolean bannerOnly, float yaw, float pitch) {
	}

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
			HordeEventAtmosphere.start(presentation.event(), presentation.yaw());
		}
		duration = opening && !bannerOnly ? MidnightCleanup.OPENING_TICKS : CLOSING_TICKS;
		timeline.start(world.getGameTime(), duration, MidnightCleanupCinematic::releaseControl);
		var player = Minecraft.getInstance().player;
		if (opening && !bannerOnly && player != null) {
			startYaw = player.getYRot();
			startPitch = player.getXRot();
			targetYaw = Float.isFinite(presentation.yaw()) ? presentation.yaw() : startYaw;
			targetPitch = Float.isFinite(presentation.pitch()) ? presentation.pitch() : startPitch;
			PlayerInteractionControl.acquire(MidnightCleanupCinematic.class, 200, MidnightCleanupCinematic::controlsView,
					PlayerInteractionControl.Policy.CINEMATIC, MidnightCleanupCinematic::skip);
			CameraControl.register(MidnightCleanupCinematic.class, 200, MidnightCleanupCinematic::controlsView, new ICameraController() {
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
		ClientPacketDistributor.sendToServer(new HordeOpeningSkipPayload(eventId));
		releaseControl();
		bannerOnly = true;
		duration = CLOSING_TICKS;
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
		return length < 0.001 ? normal : normal.add(offset.scale(Math.max(0,
				(normal.distanceTo(hit.getLocation()) - 0.15) / length)));
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
		if (!Float.isFinite(normal) || normal <= 0 || normal >= 179) {
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
			float alpha = MidnightCleanupViewUtil.phase(age, 0, 5);
			float dissolve = MidnightCleanupViewUtil.phase(age, duration - 18, duration);
			float center = width / 2, titleY = height * 0.67F;
			float gap = Math.min(24, height * 0.10F);
			CHAPTER.extract(graphics, center, titleY - gap, 0.9F, width * 0.72F, alpha, dissolve);
			NAME.extract(graphics, center, titleY, 2.6F, width * 0.72F, alpha, dissolve);
			(opening ? START : END).extract(graphics, center, titleY + gap, 0.85F, width * 0.84F, alpha, dissolve);
			if (alpha > 0.01F) {
				graphics.lobecorp$fill(center - 8, titleY + gap * 0.60F, center + 8, titleY + gap * 0.60F + 1,
						Math.round(alpha * 255) << 24 | 0x7770BD);
			}
			return;
		}

		float ambience = MidnightCleanupViewUtil.phase(age, 0, 124)
				* (1 - MidnightCleanupViewUtil.phase(age, 212, MidnightCleanupViewUtil.CENTER_END));
		fill(graphics, width, height, ambience * 0.10F, 0x301020);
		float edge = height * 0.18F;
		if (ambience > 0) {
			int shade = Math.round(ambience * 60) << 24;
			graphics.lobecorp$fillGradient(0, 0, width, edge, shade, 0);
			graphics.lobecorp$fillGradient(0, height - edge, width, height, 0, shade);
		}
		float blackout = MidnightCleanupViewUtil.blackout(age);
		fill(graphics, width, height, blackout, 0);
		float chapter = MidnightCleanupViewUtil.phase(age, 10, 22);
		float name = MidnightCleanupViewUtil.phase(age, 128, 140);
		float subtitle = MidnightCleanupViewUtil.phase(age, 172, MidnightCleanupViewUtil.TEXT_READY);
		float rise = MidnightCleanupViewUtil.phase(age, 208, 240);
		drawText(graphics, width, height, height * 0.44F - rise * 3,
				chapter, name, subtitle, rise, age);
		float bar = height * MidnightCleanupViewUtil.letterbox(age);
		if (bar > 0) {
			graphics.lobecorp$fill(0, 0, width, bar, 0xFF000000);
			graphics.lobecorp$fill(0, height - bar, width, height, 0xFF000000);
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
		float gap = Math.min(28, height * 0.10F);
		float scale = Math.clamp(height / 220F, 0.75F, 1.3F);
		CHAPTER.extract(graphics, center, titleY - gap, scale, width * 0.72F, chapter,
				MidnightCleanupViewUtil.phase(age, 88, 112));
		NAME.extract(graphics, center, titleY, scale * 3, width * 0.72F, name,
				MidnightCleanupViewUtil.phase(age, MidnightCleanupViewUtil.DISSOLVE_START, 236));
		(opening ? START : END).extract(graphics, center, titleY + gap - rise,
				scale * 0.9F, width * 0.84F, subtitle, MidnightCleanupViewUtil.phase(age, 208, 228));
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
}
