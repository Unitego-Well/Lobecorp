package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.hud.host.HudFrame;
import org.unitego.lobecorp.client.hud.host.HudSession;
import org.unitego.lobecorp.client.hud.host.HudSessionTracker;
import org.unitego.lobecorp.client.hud.host.IHudHost;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/// 按 `Identifier` 统一管理 HUD 宿主、显隐和会话清理；不包含具体 HUD 业务。
///
/// 绘制时机与层序由 NeoForge 的 GuiLayer/事件回调决定，本类不创建额外绘制循环。
@NullMarked
public final class HudLayerManager {
	public static final HudLayerManager INSTANCE = new HudLayerManager();
	private final Map<Identifier, Entry> entries = new LinkedHashMap<>();
	private final HudSessionTracker session = new HudSessionTracker();

	private HudLayerManager() {
	}

	/// 按 `Identifier` 注册宿主工厂与独立可见条件；重复 ID 明确拒绝。
	public void register(Identifier id, BooleanSupplier visible, Supplier<? extends IHudHost> factory) {
		Objects.requireNonNull(id);
		Objects.requireNonNull(visible);
		Objects.requireNonNull(factory);
		if (entries.putIfAbsent(id, new Entry(visible, factory)) != null) {
			throw new IllegalArgumentException("HUD 已注册：" + id);
		}
	}

	/// 返回是否已注册该 ID。
	public boolean contains(Identifier id) {
		return entries.containsKey(id);
	}

	/// 在 NeoForge 图层回调中提取对应 HUD；首次调用先创建并初始化宿主。
	public void render(Identifier id, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		var minecraft = Minecraft.getInstance();
		var frame = new HudFrame(graphics, deltaTracker, minecraft.level, minecraft.player,
				graphics.guiWidth(), graphics.guiHeight());
		render(id, frame);
	}

	/// 在已构造的当帧上下文中提取对应 HUD。
	public void render(Identifier id, HudFrame frame) {
		renderForSession(id, frame, frame.session());
	}

	/// 使用显式会话身份提取；调用方负责保证会话与当帧快照一致。
	public void renderForSession(Identifier id, HudFrame frame, HudSession nextSession) {
		if (session.update(nextSession)) {
			try {
				releaseAll();
			} finally {
				resetTemporaryState();
			}
		}

		if (session.unavailable()) {
			return;
		}

		var entry = requireEntry(id);
		if (entry.temporarilyHidden || !entry.visible.getAsBoolean()) {
			return;
		}

		ensureHost(entry, frame.screenWidth(), frame.screenHeight());
		Objects.requireNonNull(entry.host).render(frame);
	}

	/// 暂隐是当前会话状态；只停止提交，不释放宿主，换会话或清理时复位。
	public void setTemporarilyHidden(Identifier id, boolean hidden) {
		requireEntry(id).temporarilyHidden = hidden;
	}

	/// 释放单个宿主但保留注册信息；重复调用安全。
	public void clear(Identifier id) {
		var entry = entries.get(id);
		if (entry != null) {
			entry.release();
		}
	}

	/// 释放全部宿主但保留注册定义；暂隐状态随当前会话一并复位。
	public void clear() {
		try {
			releaseAll();
		} finally {
			resetTemporaryState();
			session.clear();
		}
	}

	private Entry requireEntry(Identifier id) {
		var entry = entries.get(id);
		if (entry == null) {
			throw new IllegalArgumentException("HUD 未注册：" + id);
		}

		return entry;
	}

	private void ensureHost(Entry entry, int screenWidth, int screenHeight) {
		if (entry.host == null) {
			var created = Objects.requireNonNull(entry.factory.get(), "HUD 宿主工厂返回 null");
			try {
				created.ensureInitialized(screenWidth, screenHeight);
				entry.host = created;
			} catch (RuntimeException exception) {
				try {
					created.release();
				} catch (RuntimeException cleanupFailure) {
					exception.addSuppressed(cleanupFailure);
				}

				throw exception;
			}

			return;
		}

		entry.host.ensureInitialized(screenWidth, screenHeight);
	}

	private void resetTemporaryState() {
		for (var entry : entries.values()) {
			entry.temporarilyHidden = false;
		}
	}

	private void releaseAll() {
		RuntimeException failure = null;
		for (var entry : entries.values()) {
			try {
				entry.release();
			} catch (RuntimeException exception) {
				if (failure == null) {
					failure = exception;
				} else {
					failure.addSuppressed(exception);
				}
			}
		}

		if (failure != null) {
			throw failure;
		}
	}

	private static final class Entry {
		private final BooleanSupplier visible;
		private final Supplier<? extends IHudHost> factory;
		private @Nullable IHudHost host;
		private boolean temporarilyHidden;

		private Entry(BooleanSupplier visible, Supplier<? extends IHudHost> factory) {
			this.visible = visible;
			this.factory = factory;
		}

		private void release() {
			var oldHost = host;
			host = null;
			if (oldHost != null) {
				oldHost.release();
			}
		}
	}
}
