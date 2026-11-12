package org.unitego.lobecorp.client.encounter.midnightcleanup;

import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.lowdragmc.photon.client.postfx.runtime.RenderGraphExecutor;
import com.lowdragmc.photon.client.postfx.runtime.SceneBlit;
import com.lowdragmc.photon.client.postfx.shadergraph.runtime.FullscreenGraphRuntime;
import com.lowdragmc.photon.gui.editor.resource.FullscreenShaderGraphResource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.util.horde.HordePresenceUtil;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.horde.HordeIrisShadows;
import org.unitego.lobecorp.client.visual.sky.DirectionalSkyCoverage;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.util.visual.TimelineUtil;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;

import java.util.*;

/// 事件走廊中的持续氛围；标题寿命、当地尾波与全屏后期请求彼此独立。
public class MidnightCleanupAtmosphere {
	/// 覆盖由24至512格的实际距离推进，横向内缘128格渐强，8刻响应消除位置更新跳变。
	private static final DirectionalSkyCoverage SKY = new DirectionalSkyCoverage(
			new DirectionalSkyCoverage.Profile(MidnightCleanupEncounter.SKY_MINIMUM_COVERAGE, MidnightCleanupEncounter.SKY_NEAR_DISTANCE,
					MidnightCleanupEncounter.SKY_FAR_DISTANCE, MidnightCleanupEncounter.SKY_LATERAL_FADE, MidnightCleanupEncounter.SKY_RESPONSE_TICKS));
	private static final Map<UUID, Double> started = new HashMap<>();
	private static final PostEffectStack SCREEN = new PostEffectStack();
	private static ClientLevel world;
	private static double sampledAt = Double.NaN;
	private static UUID selected;
	private static float strength, skyStrength, coverage;
	private static Vec3 direction = new Vec3(0, 0, 1);
	private static IResourcePath effect;
	private static boolean failed;

	public static ResourceManagerReloadListener resourceReloadListener() {
		return MidnightCleanupAtmosphere::reload;
	}

	public static void prepare() {
		if (effect != null || failed) {
			return;
		}

		try {
			var path = new FilePath(MidnightCleanupEncounter.ATMOSPHERE_RESOURCE);
			if (FullscreenShaderGraphResource.INSTANCE.getResourceInstance().getResource(path) == null) {
				throw new IllegalStateException("深夜清扫全屏后期图资源不存在：" + MidnightCleanupEncounter.ATMOSPHERE_RESOURCE);
			}

			effect = path;
		} catch (RuntimeException exception) {
			failed = true;
			Lobecorp.LOGGER.error("停止本代际清道夫屏幕后期，天空和开幕继续", exception);
		}
	}

	/// 入场包是天空和屏幕效果的共同起点，不提前消费世界后期。
	public static void start(UUID event, float yaw) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		if (world != level) {
			resetPresence();
			world = level;
		}
		if (!started.containsKey(event)) {
			started.put(event, (double) level.getGameTime());
			if (strength == 0) {
				selected = event;
				double radians = Math.toRadians(yaw);
				direction = new Vec3(-Math.sin(radians), 0, Math.cos(radians));
				SKY.reset(direction);
				coverage = SKY.minimum(direction).coverage();
			}
		}
	}

	public static void remove(UUID event) {
		started.remove(event);
	}

	public static void extract(float partial) {
		if (!HordeIrisShadows.shadowPass()) {
			update(partial);
		}
	}

	/// GUI和手绘制之后、窗口提交之前，用Photon独立栈处理最终画面。
	public static void frameEnd() {
		try {
			var minecraft = Minecraft.getInstance();
			if (minecraft.level == null || minecraft.player == null || HordeIrisShadows.shadowPass()) {
				return;
			}
			if (minecraft.gameRenderer.getGameRenderState().windowRenderState.isMinimized) {
				return;
			}
			float partial = minecraft.isPaused() ? 0 : minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			update(partial);
			if (strength <= 0) {
				return;
			}
			prepare();
			var color = minecraft.getMainRenderTarget().getColorTextureView();
			if (failed || effect == null || color == null) {
				return;
			}

			SCREEN.submit(effect, Map.of("Time", (float) sampledAt / 20), strength);
			var output = SCREEN.consumeAndExecute(RenderGraphExecutor.FrameInputs.of(color, null), null);
			if (output != null) {
				SceneBlit.writeBack(output, color);
			}
		} catch (RuntimeException exception) {
			failed = true;
			Lobecorp.LOGGER.error("停止本代际清道夫最终屏幕效果，天空和事件继续", exception);
		} finally {
			SCREEN.onFrameEnd();
		}
	}

	public static Sky sky(float partial) {
		update(partial);
		return skyStrength <= 0 ? null : new Sky(direction, skyStrength, coverage, (float) sampledAt);
	}

	private static void update(float partial) {
		var minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		var player = minecraft.player;
		if (level == null || player == null) {
			resetPresence();
			return;
		}
		if (world != level) {
			resetPresence();
			world = level;
		}

		double now = level.getGameTime() + partial;
		if (!Double.isNaN(sampledAt) && now <= sampledAt) {
			return;
		}

		double elapsed = Double.isNaN(sampledAt) ? 0 : Math.clamp(now - sampledAt, 0, MidnightCleanupEncounter.ATMOSPHERE_MAX_ELAPSED_TICKS);
		sampledAt = now;
		var events = MidnightCleanupDisplay.instances();
		HordeClient.ClientHorde chosen = null;
		DirectionalSkyCoverage.Sample chosenSky = null;
		double chosenCoverage = -1;
		boolean insideWave = false;
		if (HordeVisualConfig.ENABLED.get() && player.isAlive() && !player.isSpectator()) {
			for (var event : events) {
				if (!event.ready || event.failed || event.tide == null || !MidnightCleanupEncounter.ID.equals(event.profile)
						|| event.time(partial) >= event.stop + event.length / event.speed) {
					continue;
				}

				double arrival = event.tide.particleStation(player.position()) / event.speed - event.time(partial);
				Double began = started.get(event.id);
				if (began == null) {
					if (arrival > 0) {
						continue;
					}
					// 已到达的重连/迟到快照恢复既有氛围，不补播镜头。
					began = now;
					started.put(event.id, began);
				}
				insideWave |= inside(event, player.position(), partial);
				double time = event.time(partial);
				double station = Math.clamp(event.tide.particleStation(player.position()),
						HordePresenceUtil.tail(time, event.stop, event.speed, event.length),
						HordePresenceUtil.head(time, event.speed, event.length));
				if (event.tide.world(station, 0).distanceTo(player.position())
						> MidnightCleanupEncounter.SKY_FAR_DISTANCE + event.rules.width() / 2) {
					continue;
				}
				var sample = sampleSky(event, player.position(), partial);
				double value = sample.coverage();
				if (chosen == null || event.id.equals(selected) || !chosen.id.equals(selected) && value > chosenCoverage) {
					chosen = event;
					chosenCoverage = value;
					chosenSky = sample;
				}
			}
		}

		UUID pending = null;
		if (chosen == null && HordeVisualConfig.ENABLED.get() && player.isAlive() && !player.isSpectator()) {
			for (var entry : started.entrySet()) {
				if (now - entry.getValue() < MidnightCleanupEncounter.OPENING_TICKS
						&& events.stream().noneMatch(event -> event.id.equals(entry.getKey()) && event.ready)) {
					pending = entry.getKey();
					break;
				}
			}
		}
		float skyTarget = chosen == null && pending == null ? 0 : 1;
		skyStrength = (float) (skyTarget > skyStrength ? Math.min(skyTarget, skyStrength + elapsed / MidnightCleanupEncounter.ATMOSPHERE_ENTER_TICKS)
				: Math.max(skyTarget, skyStrength - elapsed / MidnightCleanupEncounter.ATMOSPHERE_LEAVE_TICKS));
		float target = insideWave ? 1 : 0;
		strength = (float) (target > strength ? Math.min(target, strength + elapsed / MidnightCleanupEncounter.ATMOSPHERE_ENTER_TICKS)
				: Math.max(target, strength - elapsed / MidnightCleanupEncounter.ATMOSPHERE_LEAVE_TICKS));
		if (chosen != null) {
			selected = chosen.id;
			var sample = SKY.update(chosenSky, elapsed);
			direction = sample.direction();
			coverage = sample.coverage();
		} else if (pending != null) {
			selected = pending;
			var sample = SKY.update(SKY.minimum(direction), elapsed);
			direction = sample.direction();
			coverage = sample.coverage();
		} else if (skyStrength == 0) {
			selected = null;
			coverage = 0;
			SKY.reset(direction);
		}
	}

	private static DirectionalSkyCoverage.Sample sampleSky(HordeClient.ClientHorde event, Vec3 observer, float partial) {
		double time = event.time(partial);
		double head = HordePresenceUtil.head(time, event.speed, event.length);
		double tail = HordePresenceUtil.tail(time, event.stop, event.speed, event.length);
		double station = Math.clamp(event.tide.particleStation(observer), tail, head);
		var sample = SKY.sample(observer, event.tide.world(head, 0), event.tide.world(station, 0),
				event.tide.forward(station), event.rules.width() / 2);
		double arrival = HordePresenceUtil.arrival(event.tide.particleStation(observer), time, event.speed);
		float approach = TimelineUtil.phase((float) arrival, MidnightCleanupEncounter.OPENING_WARNING_TICKS, 0);
		float limit = MidnightCleanupEncounter.SKY_MINIMUM_COVERAGE + MidnightCleanupEncounter.SKY_APPROACH_COVERAGE * approach * approach;
		return new DirectionalSkyCoverage.Sample(sample.direction(), Math.min(sample.coverage(), limit));
	}

	private static boolean inside(HordeClient.ClientHorde event, Vec3 point, float partial) {
		double time = event.time(partial);
		double station = event.tide.particleStation(point);
		return HordePresenceUtil.contains(point, station, event.tide.world(station, 0), event.tide.forward(station),
				HordePresenceUtil.tail(time, event.stop, event.speed, event.length),
				HordePresenceUtil.head(time, event.speed, event.length), event.rules.width() / 2);
	}

	private static void resetPresence() {
		world = null;
		sampledAt = Double.NaN;
		selected = null;
		started.clear();
		strength = skyStrength = coverage = 0;
		SKY.reset(new Vec3(0, 0, 1));
	}

	public static void clear() {
		if (world != Minecraft.getInstance().level || MidnightCleanupDisplay.instances().isEmpty()) {
			resetPresence();
		}
		releaseEffect();
	}

	/// 资源代际只替换后期定义，保留活动遭遇的入场起点和天空进度。
	private static void reload(ResourceManager resources) {
		releaseEffect();
	}

	private static void releaseEffect() {
		SCREEN.onFrameEnd();
		if (effect != null) {
			FullscreenGraphRuntime.invalidate(effect);
		}
		effect = null;
		failed = false;
	}

	public record Sky(Vec3 direction, float strength, float coverage, float time) {
	}

}
