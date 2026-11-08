package org.unitego.lobecorp.client.hud.event;

import net.minecraft.client.Minecraft;
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
import org.unitego.lobecorp.util.horde.MidnightCleanupViewUtil;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

import java.util.ArrayDeque;
import java.util.UUID;

/// 独立于普通HUD可见性的开闭幕表现；不写入用户选项。
public class MidnightCleanupCinematic {
	/// 完整开幕保持200刻；补播和闭幕仍为50刻且不接管镜头。
	private static final int CLOSING_TICKS = 50;
	private static final int INDIGO = 0x7770BD;
	private static final CinematicText CHAPTER = new CinematicText("horde.lobecorp.banner.title", 0x8E85CF);
	private static final CinematicText NAME = new CinematicText("horde.lobecorp.banner.name", 0xD4C8FF);
	private static final CinematicText START = new CinematicText("horde.lobecorp.banner.start", 0xAEA2C5);
	private static final CinematicText END = new CinematicText("horde.lobecorp.banner.end", 0xAEA2C5);
	private static ClientLevel world;
	private static long began;
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
		var player = Minecraft.getInstance().player;
		if (opening && !bannerOnly && player != null) {
			startYaw = player.getYRot();
			startPitch = player.getXRot();
			targetYaw = Float.isFinite(presentation.yaw()) ? presentation.yaw() : startYaw;
			targetPitch = Float.isFinite(presentation.pitch()) ? presentation.pitch() : startPitch;
		}
	}

	public static boolean hidesHud() {
		return controlsView();
	}

	public static boolean controlsView() {
		if (!active() || !opening || bannerOnly) {
			return false;
		}
		if (world.getGameTime() - began < MidnightCleanupViewUtil.CENTER_END) {
			return true;
		}
		if (!released) {
			var player = Minecraft.getInstance().player;
			if (player != null) {
				player.setYRot(targetYaw);
				player.setXRot(targetPitch);
				player.setYHeadRot(targetYaw);
				player.yRotO = targetYaw;
				player.xRotO = targetPitch;
			}
			released = true;
		}
		return false;
	}

	public static void holdView() {
		var player = Minecraft.getInstance().player;
		if (!controlsView() || player == null) {
			return;
		}

		float age = world.getGameTime() - began;
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
		float age = world.getGameTime() - began + partial;
		var angles = MidnightCleanupViewUtil.sample(startYaw, startPitch, targetYaw, targetPitch, age);
		event.setYaw(angles.yaw());
		event.setPitch(angles.pitch());
		return true;
	}

	/// 变焦只作用于世界配置视野，不改用户选项或把GUI文字一并放大。
	public static void zoomFov(ViewportEvent.ComputeFov event) {
		if (!event.usedConfiguredFov() || !active() || !opening || bannerOnly) {
			return;
		}

		var minecraft = Minecraft.getInstance();
		float partial = minecraft.isPaused() ? 0 : (float) event.getPartialTick();
		float age = world.getGameTime() - began + partial;
		float factor = MidnightCleanupViewUtil.magnification(age);
		float normal = event.getFOV();
		if (factor <= 1 || !Float.isFinite(normal) || normal <= 0 || normal >= 179) {
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
		if (duration > 0 && level.getGameTime() - began < duration) {
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
		float age = world.getGameTime() - began + partial;
		boolean full = opening && !bannerOnly;
		float enterAt = full ? MidnightCleanupViewUtil.CENTER_END : 0;
		float readyAt = full ? MidnightCleanupViewUtil.TEXT_READY : 5;
		float alpha = MidnightCleanupViewUtil.phase(age, enterAt, readyAt);
		float dissolve = full ? Math.clamp((age - MidnightCleanupViewUtil.DISSOLVE_START)
				/ (duration - (float) MidnightCleanupViewUtil.DISSOLVE_START), 0, 1) : 0;
		if (!full) {
			alpha *= 1 - MidnightCleanupViewUtil.phase(age, duration - 10, duration);
		}
		float width = graphics.guiWidth(), height = graphics.guiHeight();

		if (alpha <= 0) {
			return;
		}

		float center = width / 2, titleY = height * 0.67F;
		float gap = Math.min(24, height * 0.10F);
		CHAPTER.extract(graphics, center, titleY - gap, 0.9F, width * 0.72F, alpha, dissolve);
		NAME.extract(graphics, center, titleY, 2.6F, width * 0.72F, alpha, dissolve);
		(opening ? START : END).extract(graphics, center, titleY + gap, 0.85F, width * 0.84F, alpha, dissolve);
		float accent = alpha * (1 - dissolve);
		if (accent > 0.01F) {
			graphics.lobecorp$fill(center - 8, titleY + gap * 0.60F, center + 8, titleY + gap * 0.60F + 1,
					Math.round(accent * 255) << 24 | INDIGO);
		}
	}

	public static void clear() {
		world = null;
		duration = 0;
		opening = false;
		bannerOnly = false;
		eventId = null;
		pending.clear();
	}
}
