package org.unitego.lobecorp.client.horde;

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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.client.visual.sky.DirectionalSkyCoverage;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;
import org.unitego.lobecorp.util.visual.TimelineUtil;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

/// 事件走廊中的持续氛围；标题寿命、当地尾波与全屏后期请求彼此独立。
public class HordeEventAtmosphere {

	private static final double ENTER_TICKS = 20, LEAVE_TICKS = 30;
	/// 覆盖由24至512格的实际距离推进，横向内缘128格渐强，8刻响应消除位置更新跳变。
	private static final DirectionalSkyCoverage SKY = new DirectionalSkyCoverage(
			new DirectionalSkyCoverage.Profile(0.025F, 24, 512, 128, 8));
	private static final Map<UUID, Double> started = new HashMap<>();
	private static final PostEffectStack SCREEN = new PostEffectStack();
	private static ClientLevel world;
	private static double sampledAt = Double.NaN;
	private static UUID selected;
	private static float strength, coverage;
	private static Vec3 direction = new Vec3(0, 0, 1);
	private static BuiltinResourceProvider<CompoundTag> provider;
	private static IResourcePath effect;
	private static boolean failed;

	public record Sky(Vec3 direction, float strength, float coverage, float time) {
	}

	public static void prepare() {
		if (provider != null || failed) {
			return;
		}

		try {
			var resource = FullscreenShaderGraphResource.INSTANCE;
			provider = new BuiltinResourceProvider<>("lobecorp_midnight_cleanup", resource.getResourceInstance());
			effect = provider.createSubPath("atmosphere");
			provider.addResource(effect, resource.serializeGraph(new AtmosphereGraph()));
			resource.getResourceInstance().addUnlistedProvider(provider);
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

		double elapsed = Double.isNaN(sampledAt) ? 0 : Math.clamp(now - sampledAt, 0, 5);
		sampledAt = now;
		var events = HordeClient.events();
		HordeClient.ClientEvent chosen = null;
		DirectionalSkyCoverage.Sample chosenSky = null;
		double chosenCoverage = -1;
		if (HordeVisualConfig.ENABLED.get() && player.isAlive() && !player.isSpectator()) {
			for (var event : events) {
				if (!event.ready || event.failed || event.tide == null || !MidnightCleanup.ID.equals(event.profile)
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
				if (now - entry.getValue() < MidnightCleanup.OPENING_TICKS
						&& events.stream().noneMatch(event -> event.id.equals(entry.getKey()) && event.ready)) {
					pending = entry.getKey();
					break;
				}
			}
		}
		float target = chosen == null && pending == null ? 0 : 1;
		strength = (float) (target > strength ? Math.min(target, strength + elapsed / ENTER_TICKS)
				: Math.max(target, strength - elapsed / LEAVE_TICKS));
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

	private static DirectionalSkyCoverage.Sample sampleSky(HordeClient.ClientEvent event, Vec3 observer, float partial) {
		double time = event.time(partial);
		double head = Math.clamp(HordeTide.head(time, event.speed), 0, event.length);
		double tail = Math.clamp(HordeTide.head(time - event.stop, event.speed), 0, head);
		double station = Math.clamp(event.tide.particleStation(observer), tail, head);
		var sample = SKY.sample(observer, event.tide.world(head, 0), event.tide.world(station, 0),
				event.tide.forward(station), event.rules.width() / 2);
		double arrival = event.tide.particleStation(observer) / event.speed - time;
		float approach = TimelineUtil.phase((float) arrival, MidnightCleanup.OPENING_WARNING_TICKS, 0);
		float limit = 0.025F + 0.975F * approach * approach;
		return new DirectionalSkyCoverage.Sample(sample.direction(), Math.min(sample.coverage(), limit));
	}

	private static boolean inside(HordeClient.ClientEvent event, Vec3 point) {
		double station = event.tide.particleStation(point);
		var center = event.tide.world(station, 0);
		var forward = event.tide.forward(station);
		double dx = point.x - center.x, dz = point.z - center.z;
		double along = dx * forward.x + dz * forward.z;
		return !(station < 0.001 && along < 0 || station > event.length - 0.001 && along > 0)
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
		if (world != Minecraft.getInstance().level || HordeClient.events().isEmpty()) {
			resetPresence();
		}
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
					List.of(new ExpressionNode.PortSpec("result", "VEC4")), """
					vec2 size = vec2(textureSize(source, 0));
					float aspect = size.x / size.y;
					vec2 edge = (uv - vec2(0.5)) * vec2(aspect, 1.0);
					float radius = length(edge) / length(vec2(aspect, 1.0) * 0.5);
					float shade = smoothstep(0.30, 0.98, radius) * 0.58;
					// 管面轻微弯曲，屏外像素归黑，不把边缘拉伸成固定色条。
					vec2 tube = uv * 2.0 - 1.0;
					vec2 curved = tube * (1.0 + 0.004 * dot(tube, tube)) * 0.5 + 0.5;
					float visible = step(0.0, curved.x) * step(curved.x, 1.0)
					        * step(0.0, curved.y) * step(curved.y, 1.0);
					float cycle = floor(time * 0.34);
					float band = 1.0 - smoothstep(0.006, 0.024, abs(uv.y - fract(cycle * 0.381 + 0.19)));
					float tear = step(0.95, fract(time * 0.34)) * band * 0.0020;
					float rolling = 1.0 - smoothstep(0.025, 0.075, abs(uv.y - fract(time * 0.18)));
					float wave = sin(uv.y * 38.0 + time * 2.1) * 0.00012
					        + rolling * sin(time * 17.0) * 0.00025;
					vec2 sampleUv = clamp(curved + vec2(wave + tear, 0.0), vec2(0.0), vec2(1.0));
					vec4 scene = texture(source, sampleUv);
					vec2 chroma = vec2((1.05 + 0.35 * sin(time * 1.7)) / size.x, 0.0);
					vec3 color = vec3(texture(source, clamp(sampleUv + chroma, vec2(0.0), vec2(1.0))).r,
					        scene.g, texture(source, clamp(sampleUv - chroma, vec2(0.0), vec2(1.0))).b);
					vec2 cell = floor(uv * size);
					float grain = fract(sin(dot(cell, vec2(12.9898, 78.233)) + floor(time * 24.0) * 0.73) * 43758.5453) - 0.5;
					// 三个输出像素一组的扫描线和RGB荫罩，强度不再是不可辨认的千分之几。
					float scan = 0.84 + 0.16 * pow(0.5 + 0.5 * sin(uv.y * size.y * 2.094395), 1.4);
					float stripe = mod(cell.x, 3.0);
					vec3 phosphor = stripe < 1.0 ? vec3(1.0, 0.88, 0.88)
					        : stripe < 2.0 ? vec3(0.88, 1.0, 0.88) : vec3(0.88, 0.88, 1.0);
					float hum = 1.0 - rolling * 0.055;
					color = max(vec3(0.0), color * scan * phosphor * hum + grain * 0.028);
					result = vec4(color * mix(vec3(1.0), vec3(0.24, 0.18, 0.32), shade) * visible, scene.a);
					""");
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
