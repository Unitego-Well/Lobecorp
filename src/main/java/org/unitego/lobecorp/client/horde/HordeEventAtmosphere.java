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
import com.lowdragmc.photon.client.postfx.PhotonPostFX;
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
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// 事件走廊中的持续氛围；标题寿命、当地尾波与全屏后期请求彼此独立。
public class HordeEventAtmosphere {

	private static final double APPROACH_TICKS = 400, ENTER_TICKS = 20, LEAVE_TICKS = 30;
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

	/// 在世界后期消费前提交；不依赖被完整开幕取消的GUI Post事件。
	public static void extract(float partial) {
		if (HordeIrisShadows.shadowPass()) {
			return;
		}
		update(partial);
		if (strength <= 0) {
			return;
		}

		prepare();
		if (!failed && effect != null) {
			float time = (float) sampledAt / 20;
			PhotonPostFX.submit(effect, Map.of("Time", time), strength);
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
		HordeClient.ClientEvent chosen = null;
		double chosenCoverage = -1;
		if (HordeVisualConfig.ENABLED.get() && player.isAlive() && !player.isSpectator()) {
			for (var event : HordeClient.events()) {
				if (!event.ready || event.failed || event.tide == null || !MidnightCleanup.ID.equals(event.profile)
						|| event.time(partial) >= event.stop + event.length / event.speed || !inside(event, player.position())) {
					continue;
				}

				double arrival = event.tide.particleStation(player.position()) / event.speed - event.time(partial);
				double value = Math.clamp(1 - Math.max(0, arrival) / APPROACH_TICKS, 0, 1);
				value = value * value * (3 - 2 * value);
				if (chosen == null || event.id.equals(selected) || !chosen.id.equals(selected) && value > chosenCoverage) {
					chosen = event;
					chosenCoverage = value;
				}
			}
		}

		float target = chosen == null ? 0 : 1;
		strength = (float) (target > strength ? Math.min(target, strength + elapsed / ENTER_TICKS)
				: Math.max(target, strength - elapsed / LEAVE_TICKS));
		if (chosen != null) {
			coverage = chosen.id.equals(selected) ? Math.max(coverage, (float) chosenCoverage) : (float) chosenCoverage;
			selected = chosen.id;
			var entry = chosen.tide.world(0, 0).subtract(player.position()).multiply(1, 0, 1);
			direction = entry.lengthSqr() > 1 ? entry.normalize() : chosen.tide.forward(0).scale(-1);
		} else if (strength == 0) {
			selected = null;
			coverage = 0;
		}
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
		strength = coverage = 0;
	}

	public static void clear() {
		resetPresence();
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
					float cycle = floor(time * 0.31);
					float band = 1.0 - smoothstep(0.006, 0.018, abs(uv.y - fract(cycle * 0.381 + 0.19)));
					float tear = step(0.94, fract(time * 0.31)) * band * 0.0018;
					float wave = sin(uv.y * 44.0 + time * 1.2) * 0.00012;
					vec2 sampleUv = clamp(uv + vec2(wave + tear, 0.0), vec2(0.0), vec2(1.0));
					vec4 scene = texture(source, sampleUv);
					vec2 chroma = vec2((0.22 + 0.12 * sin(time)) / size.x, 0.0);
					vec3 color = vec3(texture(source, clamp(sampleUv + chroma, vec2(0.0), vec2(1.0))).r,
					        scene.g, texture(source, clamp(sampleUv - chroma, vec2(0.0), vec2(1.0))).b);
					vec2 cell = floor(uv * size);
					float grain = fract(sin(dot(cell, vec2(12.9898, 78.233)) + floor(time * 12.0) * 0.73) * 43758.5453) - 0.5;
					color = max(vec3(0.0), color * (0.997 + 0.003 * sin(uv.y * size.y * 3.14159)) + grain * 0.006);
					result = vec4(color * mix(vec3(1.0), vec3(0.24, 0.18, 0.32), shade), scene.a);
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
