package org.unitego.lobecorp.client.encounter.midnightcleanup;

import com.lowdragmc.kilagraph.rendertype.nodes.input.UVNode;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.ExpressionNode;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.lowdragmc.photon.client.postfx.runtime.RenderGraphExecutor;
import com.lowdragmc.photon.client.postfx.runtime.SceneBlit;
import com.lowdragmc.photon.client.postfx.shadergraph.FullscreenShaderGraph;
import com.lowdragmc.photon.client.postfx.shadergraph.runtime.FullscreenGraphRuntime;
import com.lowdragmc.photon.gui.editor.resource.FullscreenShaderGraphResource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.horde.HordeIrisShadows;
import org.unitego.lobecorp.client.horde.HordeTide;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.client.visual.sky.DirectionalSkyCoverage;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;
import org.unitego.lobecorp.util.visual.TimelineUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

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
	private static float strength, coverage;
	private static Vec3 direction = new Vec3(0, 0, 1);
	private static BuiltinResourceProvider<CompoundTag> provider;
	private static IResourcePath effect;
	private static String expressionSource;
	private static boolean failed;

	public static ResourceManagerReloadListener resourceReloadListener() {
		return MidnightCleanupAtmosphere::reload;
	}

	public static void prepare() {
		if (provider != null || failed) {
			return;
		}

		try {
			if (expressionSource == null) {
				expressionSource = readExpression(Minecraft.getInstance().getResourceManager());
			}
			var resource = FullscreenShaderGraphResource.INSTANCE;
			provider = new BuiltinResourceProvider<>(MidnightCleanupEncounter.ATMOSPHERE_PROVIDER, resource.getResourceInstance());
			effect = provider.createSubPath(MidnightCleanupEncounter.ATMOSPHERE_PATH);
			provider.addResource(effect, resource.serializeGraph(new AtmosphereGraph()));
			resource.getResourceInstance().addUnlistedProvider(provider);
		} catch (IOException | RuntimeException exception) {
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
		return strength <= 0 ? null : new Sky(direction, strength, coverage, (float) sampledAt);
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
		if (HordeVisualConfig.ENABLED.get() && player.isAlive() && !player.isSpectator()) {
			for (var event : events) {
				if (!event.ready || event.failed || event.tide == null || !MidnightCleanupEncounter.ID.equals(event.profile)
						|| event.time(partial) >= event.stop + event.length / event.speed || !inside(event, player.position())) {
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
		float target = chosen == null && pending == null ? 0 : 1;
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
		} else if (strength == 0) {
			selected = null;
			coverage = 0;
			SKY.reset(direction);
		}
	}

	private static DirectionalSkyCoverage.Sample sampleSky(HordeClient.ClientHorde event, Vec3 observer, float partial) {
		double time = event.time(partial);
		double head = Math.clamp(HordeTide.head(time, event.speed), 0, event.length);
		double tail = Math.clamp(HordeTide.head(time - event.stop, event.speed), 0, head);
		double station = Math.clamp(event.tide.particleStation(observer), tail, head);
		var sample = SKY.sample(observer, event.tide.world(head, 0), event.tide.world(station, 0),
				event.tide.forward(station), event.rules.width() / 2);
		double arrival = event.tide.particleStation(observer) / event.speed - time;
		float approach = TimelineUtil.phase((float) arrival, MidnightCleanupEncounter.OPENING_WARNING_TICKS, 0);
		float limit = MidnightCleanupEncounter.SKY_MINIMUM_COVERAGE + MidnightCleanupEncounter.SKY_APPROACH_COVERAGE * approach * approach;
		return new DirectionalSkyCoverage.Sample(sample.direction(), Math.min(sample.coverage(), limit));
	}

	private static boolean inside(HordeClient.ClientHorde event, Vec3 point) {
		double station = event.tide.particleStation(point);
		var center = event.tide.world(station, 0);
		var forward = event.tide.forward(station);
		double dx = point.x - center.x, dz = point.z - center.z;
		double along = dx * forward.x + dz * forward.z;
		return !(station < MidnightCleanupEncounter.CORRIDOR_END_EPSILON && along < 0 || station > event.length - MidnightCleanupEncounter.CORRIDOR_END_EPSILON && along > 0)
				&& dx * dx + dz * dz <= event.rules.width() * event.rules.width() / 4;
	}

	private static void resetPresence() {
		world = null;
		sampledAt = Double.NaN;
		selected = null;
		started.clear();
		strength = coverage = 0;
		SKY.reset(new Vec3(0, 0, 1));
	}

	public static void clear() {
		if (world != Minecraft.getInstance().level || MidnightCleanupDisplay.instances().isEmpty()) {
			resetPresence();
		}
		releaseEffect();
		expressionSource = null;
	}

	/// 资源代际只替换后期定义，保留活动遭遇的入场起点和天空进度。
	private static void reload(ResourceManager resources) {
		try {
			releaseEffect();
			expressionSource = readExpression(resources);
		} catch (IOException | RuntimeException exception) {
			expressionSource = null;
			failed = true;
			Lobecorp.LOGGER.error("深夜清扫大气表达式重载失败，停止本代际屏幕后期，天空和开幕继续", exception);
		}
	}

	private static String readExpression(ResourceManager resources) throws IOException {
		try (var input = resources.open(MidnightCleanupEncounter.ATMOSPHERE_EXPRESSION_RESOURCE)) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static void releaseEffect() {
		SCREEN.onFrameEnd();
		if (effect != null) {
			FullscreenGraphRuntime.invalidate(effect);
		}
		if (provider != null) {
			FullscreenShaderGraphResource.INSTANCE.getResourceInstance().removeUnlistedProvider(provider);
		}
		provider = null;
		effect = null;
		failed = false;
	}

	public record Sky(Vec3 direction, float strength, float coverage, float time) {
	}

	/// 使用Photon原生图序列化和后期执行器，不持有主目标或创建另一套全屏渲染器。
	private static class AtmosphereGraph extends FullscreenShaderGraph {
		@Override
		protected void wireDefaultOutput(NodeModel textureSample, NodeModel output) {
			var uv = createNode(UVNode.class, 200, 100);
			var time = (VariableDeclarationModelBase) graphModel.createVariable("Time", TypeHandles.FLOAT, 0F, VariableKind.INPUT);
			var timeNode = graphModel.createVariableNode(time, new Vector2f(200, 220), null, null);
			var expression = createNode(ExpressionNode.class, 460, 0);
			var spec = new ExpressionNode.ExpressionSpec(
					List.of(new ExpressionNode.PortSpec("source", "SAMPLER2D"), new ExpressionNode.PortSpec("uv", "VEC2"),
							new ExpressionNode.PortSpec("time", "FLOAT")),
					List.of(new ExpressionNode.PortSpec("result", "VEC4")), Objects.requireNonNull(expressionSource));
			var option = (NodeOption) expression.getNodeOptionById(ExpressionNode.OPTION);
			Objects.requireNonNull(option.portModel.getEmbeddedValue()).setValue(ExpressionNode.toJson(spec));
			expression.defineNode();
			graphModel.createWire(expression.getInputsById().get("source"), textureSample.getInputsById().get("sampler").getConnectedPorts().getFirst());
			graphModel.createWire(expression.getInputsById().get("uv"), uv.getOutputsById().get("out"));
			graphModel.createWire(expression.getInputsById().get("time"), timeNode.getOutputPort());
			graphModel.createWire(output.getInputsById().get("color"), expression.getOutputsById().get("result"));
		}
	}
}
