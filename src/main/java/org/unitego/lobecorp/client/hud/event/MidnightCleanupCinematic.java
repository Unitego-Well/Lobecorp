package org.unitego.lobecorp.client.hud.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

import java.util.ArrayDeque;
import java.util.UUID;

/// 独立于普通HUD可见性的开闭幕表现；不写入用户选项。
public class MidnightCleanupCinematic {
	/// 完整开幕200刻，开闭幕横幅共用50刻；警告条先到位，再显示中央内容。
	private static final int CLOSING_TICKS = 50;
	private static final int INDIGO = 0x4565FF;
	private static ClientLevel world;
	private static long began;
	private static int duration;
	private static boolean opening;
	private static boolean bannerOnly;
	private static float lockedYaw, lockedPitch;
	private static UUID eventId;
	private static final ArrayDeque<Presentation> pending = new ArrayDeque<>();

	private record Presentation(UUID event, boolean opening, boolean bannerOnly) {
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
				body.getBooleanOr("banner_only", false));
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
		duration = opening && !bannerOnly ? MidnightCleanup.OPENING_TICKS : CLOSING_TICKS;
		var player = Minecraft.getInstance().player;
		if (opening && !bannerOnly && player != null) {
			// 原版LookAt包先于表现包到达，保存服务端给定的入场朝向。
			lockedYaw = player.getYRot();
			lockedPitch = player.getXRot();
		}
	}

	public static boolean hidesHud() {
		return active() && opening && !bannerOnly;
	}

	public static void holdView() {
		var player = Minecraft.getInstance().player;
		if (!hidesHud() || player == null) {
			return;
		}

		player.setYRot(lockedYaw);
		player.setXRot(lockedPitch);
		player.setYHeadRot(lockedYaw);
		player.yRotO = lockedYaw;
		player.xRotO = lockedPitch;
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
		if (minecraft.level == null) {
			return;
		}

		float partial = minecraft.isPaused() ? 0 : minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float age = minecraft.level.getGameTime() - began + partial;
		float width = graphics.guiWidth(), height = graphics.guiHeight();
		if (opening && !bannerOnly) {
			float barEnter = smooth(Math.clamp(age / 20, 0, 1));
			float barLeave = smooth(Math.clamp((age - (duration - 10)) / 10, 0, 1));
			float bars = height * 0.12F * (barEnter - barLeave);
			graphics.lobecorp$fill(0, 0, width, bars, 0xFF000000);
			graphics.lobecorp$fill(0, height - bars, width, height, 0xFF000000);
		}

		// 开幕横幅复用结束横幅的50刻时间轴，黑幕与输入锁仍持续完整200刻。
		if (age >= CLOSING_TICKS) {
			return;
		}

		float enterTicks = bannerOnly ? 0 : 10;
		float exitAt = CLOSING_TICKS - 10;
		float enter = enterTicks == 0 ? 1 : smooth(Math.clamp(age / enterTicks, 0, 1));
		float leave = smooth(Math.clamp((age - exitAt) / 10, 0, 1));
		float bannerHeight = Math.min(height * 0.4F, 90);
		float left = width * 0.025F, right = width - left;
		float top = (height - bannerHeight) / 2;
		float slide = (1 - enter + leave) * width;
		if (!bannerOnly) {
			warning(graphics, left - slide, top, right - left, false);
			warning(graphics, left + slide, top + bannerHeight - 5, right - left, true);
		}
		// 中央内容严格等待警告条到位；退出时先淡出，再撤走警告条。
		float fadeTicks = 5;
		float alpha = Math.min(Math.clamp((age - enterTicks) / fadeTicks, 0, 1),
				Math.clamp((exitAt - age) / fadeTicks, 0, 1));
		if (alpha <= 0) {
			return;
		}

		int tint = Math.round(alpha * 255) << 24 | INDIGO;
		graphics.lobecorp$fill(left, top + 5, right, top + bannerHeight - 5, Math.round(alpha * 240) << 24);
		graphics.lobecorp$fill(left, top + 5, left + 1, top + bannerHeight - 5, tint);
		graphics.lobecorp$fill(right - 1, top + 5, right, top + bannerHeight - 5, tint);
		line(graphics, Component.translatable("horde.lobecorp.banner.title"), width / 2, top + bannerHeight * 0.22F,
				0.8F, right - left - 12, tint);
		line(graphics, Component.translatable("horde.lobecorp.banner.name"), width / 2, top + bannerHeight * 0.45F,
				1.8F, right - left - 12, tint);
		line(graphics, Component.translatable(opening ? "horde.lobecorp.banner.start" : "horde.lobecorp.banner.end"),
				width / 2, top + bannerHeight * 0.77F, 0.8F, right - left - 12, tint);
	}

	private static void warning(GuiGraphicsExtractor graphics, float left, float top, float width, boolean reverse) {
		if (left + width <= 0 || left >= graphics.guiWidth()) {
			return;
		}

		graphics.enableScissor((int) Math.max(0, left), (int) top,
				(int) Math.min(graphics.guiWidth(), left + width), (int) top + 5);
		try {
			graphics.lobecorp$fill(left, top, left + width, top + 5, 0xFF000000);
			for (int row = 0; row < 5; row++) {
				for (float x = left - 12; x < left + width + 12; x += 12) {
					float offset = reverse ? row : -row;
					graphics.lobecorp$fill(x + offset, top + row, x + offset + 6, top + row + 1, 0xFF000000 | INDIGO);
				}
			}
		} finally {
			graphics.disableScissor();
		}
	}

	private static void line(GuiGraphicsExtractor graphics, Component text, float x, float y,
	                         float scale, float available, int color) {
		var font = Minecraft.getInstance().font;
		int width = font.width(text);
		float fitted = Math.min(scale, available / Math.max(1, width));
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(x, y);
			pose.scale(fitted, fitted);
			graphics.text(font, text, -width / 2, -font.lineHeight / 2, color, false);
		} finally {
			pose.popMatrix();
		}
	}

	private static float smooth(float value) {
		return value * value * (3 - 2 * value);
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
